package com.example.auraplay

import com.example.auraplay.ui.MotionButton as Button

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.auraplay.data.Song
import com.example.auraplay.service.MusicService
import com.example.auraplay.ui.*
import com.example.auraplay.features.*
import kotlinx.coroutines.flow.receiveAsFlow
import com.example.auraplay.ui.theme.AuraPlayTheme
import com.google.accompanist.permissions.*
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application, (application as AuraPlayApplication).database.playlistDao(),
            (application as AuraPlayApplication).settingsDataStore)
    }
    private var mediaController by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playRequest: Job? = null
    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK) viewModel.confirmPendingDelete() else viewModel.cancelPendingDelete()
    }

    private val legacyDeleteLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.continueLegacyDelete(granted)
    }

    override fun onStart() {
        super.onStart()
        // Reattach to existing playback. A cold library launch should not construct ExoPlayer.
        if (MusicService.getInstance() != null) connectController()
    }

    private fun connectController(): ListenableFuture<MediaController> {
        controllerFuture?.let { return it }
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, MusicService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (mediaController === controller) {
                        mediaController = null; controllerFuture?.let(MediaController::releaseFuture); controllerFuture = null
                    }
                }
            }).buildAsync()
        controllerFuture = future
        future.addListener({
            if (controllerFuture === future) {
                try { mediaController = future.get() }
                catch (error: Exception) { controllerFuture = null; android.util.Log.e("MainActivity", "Unable to connect to player", error) }
            }
        }, ContextCompat.getMainExecutor(this))
        return future
    }

    private suspend fun awaitController(): MediaController {
        mediaController?.let { MusicService.awaitRestoration(); return it }
        val future = connectController()
        val controller = suspendCancellableCoroutine<MediaController> { continuation ->
            future.addListener({
                if (continuation.isActive) {
                    try { continuation.resume(future.get()) }
                    catch (error: Exception) { continuation.resumeWithException(error) }
                }
            }, ContextCompat.getMainExecutor(this))
        }
        MusicService.awaitRestoration()
        return controller
    }

    private fun playerAction(action: (MediaController) -> Unit) = lifecycleScope.launch {
        try { action(awaitController()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { viewModel.showFeatureMessage("Unable to connect to playback. Please try again.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.deletePendingIntent.collect { deleteLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) } }
                launch { viewModel.legacyDeletePermission.collect { legacyDeleteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) } }
                launch { viewModel.queueRequests.receiveAsFlow().collect { (song, next) ->
                    try {
                        awaitController()
                        MusicService.enqueue(song, next)
                        viewModel.showFeatureMessage(if (next) "Playing next: ${song.title}" else "Added to queue: ${song.title}")
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { viewModel.showFeatureMessage("Could not add this song to the queue.") }
                } }
            }
        }
        setContent {
            val dark by viewModel.darkTheme.collectAsStateWithLifecycle()
            val accent by viewModel.accentTheme.collectAsStateWithLifecycle()
            val pureBlack by viewModel.pureBlack.collectAsStateWithLifecycle()
            val artwork by remember {
                MusicService.playerState.map { it.currentSong?.albumArtUri }.distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = null)
            val palette = rememberDominantColors(this, if (accent == "DYNAMIC") artwork else null)
            val effectiveAccent = resolveAccentColor(accent, palette.dominantColor)
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            AuraPlayTheme(darkTheme = dark, accentColor = effectiveAccent, pureBlack = pureBlack) {
                val nav = rememberNavController()
                val focus = LocalFocusManager.current
                val keyboard = LocalSoftwareKeyboardController.current
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                val snackbar = remember { SnackbarHostState() }
                val message by viewModel.featureMessage.collectAsStateWithLifecycle()
                LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.clearFeatureMessage() } }
                AmbientBackground {
                    Scaffold(
                        modifier = Modifier.systemBarsPadding(),
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbar) },
                        bottomBar = {
                            if (route != null && route != "player") {
                                Column {
                                    HomeMiniPlayer(nav,
                                        onPlayPause = { playerAction { if (it.isPlaying) it.pause() else it.playOrRetry() } },
                                        onNext = { playerAction { it.seekToNextOrRetry() } },
                                        onPrevious = { playerAction {
                                            it.seekToPreviousMediaItem()
                                            it.prepareAfterSeek()
                                        } })
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp,
                                        windowInsets = WindowInsets(0, 0, 0, 0),
                                        modifier = Modifier.height(76.dp)
                                    ) {
                                        val destinations = listOf("home" to "Feed", "stats" to "Stats", "playlists" to "Playlists")
                                        destinations.forEach { (destination, label) ->
                                            val interaction = remember(destination) { MutableInteractionSource() }
                                            NavigationBarItem(
                                                modifier = Modifier.pressFeedback(interaction),
                                                interactionSource = interaction,
                                                selected = route == destination,
                                                onClick = {
                                                    focus.clearFocus()
                                                    keyboard?.hide()
                                                    if (destination == "home") viewModel.onSearchQueryChange("")
                                                    nav.openTopLevel(destination)
                                                },
                                                icon = { Icon(when (destination) {
                                                    "home" -> Icons.Rounded.Home
                                                    "stats" -> Icons.Rounded.BarChart
                                                    else -> Icons.Rounded.PlaylistPlay
                                                }, null) },
                                                label = { Text(label, fontWeight = FontWeight.SemiBold) },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) { padding ->
                        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding),
                            enterTransition = { fadeIn(tween(220)) },
                            exitTransition = { fadeOut(tween(160)) },
                            popEnterTransition = { fadeIn(tween(220)) },
                            popExitTransition = { fadeOut(tween(160)) }) {
                            composable("home") { HomeWithPermission(nav) }
                            composable("player",
                                enterTransition = {
                                    slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(260))
                                },
                                popExitTransition = {
                                    slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(220))
                                }) {
                                LaunchedEffect(Unit) { playerAction { } }
                                val state by MusicService.playerState.collectAsStateWithLifecycle()
                                PlayerScreen(nav, viewModel, state,
                                    onPlayPause = { playerAction { if (it.isPlaying) it.pause() else it.playOrRetry() } },
                                    onSeek = { position -> playerAction { it.seekTo(position.toLong()) } },
                                    onNext = { playerAction { it.seekToNextOrRetry() } },
                                    onPrevious = { playerAction { it.seekToPreviousOrRetry() } },
                                    onToggleShuffle = { playerAction { it.shuffleModeEnabled = !it.shuffleModeEnabled } },
                                    onToggleRepeatMode = { playerAction { it.repeatMode = when (it.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    } } }
                                )
                            }
                            composable("playlists") { PlaylistScreen(nav, viewModel) }
                            composable("stats") { StatsScreen(nav, viewModel) { tracks, song -> playSongs(nav, tracks, song) } }
                            composable("favorites") {
                                val favorites by viewModel.favoriteSongs.collectAsStateWithLifecycle()
                                FavoritesScreen(nav, viewModel) { playSongs(nav, favorites, it) }
                            }
                            composable("settings") { SettingsScreen(nav, viewModel) }
                            composable("equalizer") {
                                LaunchedEffect(Unit) { playerAction { } }
                                val manager by MusicService.equalizerManagerState.collectAsStateWithLifecycle()
                                EqualizerScreen(nav, manager)
                            }
                            composable("playback_options") { PlaybackOptionsScreen(nav, viewModel) }
                            composable("music_folders") { FolderSettingsScreen(nav, viewModel) }
                            composable("backup") { BackupScreen(nav, viewModel) }
                            composable("smart_playlists") { SmartPlaylistsScreen(nav, viewModel) { tracks, song -> playSongs(nav, tracks, song) } }
                            composable("bookmarks") { BookmarksScreen(nav, viewModel) { song, position -> playSongs(nav, listOf(song), song, position) } }
                            composable("widget_styles") { WidgetStylesScreen(nav) }
                            composable("song_edit/{songId}", arguments = listOf(navArgument("songId") { type = NavType.LongType })) { backStack ->
                                SongEditorScreen(backStack.arguments?.getLong("songId") ?: 0L, nav, viewModel)
                            }
                            composable("playlist_details/{playlistId}", arguments = listOf(navArgument("playlistId") { type = NavType.LongType })) { backStack ->
                                val id = backStack.arguments?.getLong("playlistId") ?: 0L
                                val playlist by remember(id) { viewModel.getPlaylistWithSongs(id) }.collectAsStateWithLifecycle(initialValue = null)
                                PlaylistDetailsScreen(id, nav, viewModel) { playSongs(nav, playlist?.songs.orEmpty(), it) }
                            }
                            composable("add_songs/{playlistId}", arguments = listOf(navArgument("playlistId") { type = NavType.LongType })) { backStack ->
                                AddSongsToPlaylistScreen(backStack.arguments?.getLong("playlistId") ?: 0L, nav, viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun playSongs(nav: NavController, songs: List<Song>, song: Song, position: Long = 0L) {
        val index = songs.indexOfFirst { it.id == song.id }
        if (index < 0) return
        playRequest?.cancel()
        playRequest = lifecycleScope.launch {
            val controller = try { awaitController() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Toast.makeText(this@MainActivity, "Unable to connect to playback. Please try again.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val items = withContext(Dispatchers.Default) {
                songs.map { it.toMediaItem() }
            }
            if (controller !== mediaController) return@launch
            controller.setMediaItems(items, index, position.coerceIn(0L, song.duration.coerceAtLeast(0L)))
            controller.prepare()
            controller.play()
            nav.navigate("player") { launchSingleTop = true }
        }
    }

    @Composable
    private fun HomeWithPermission(nav: NavController) {
        val permission = rememberPermissionState(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        val granted = permission.status.isGranted
        var requested by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(granted) { if (granted) viewModel.ensureLibraryLoaded() }
        // Request notifications only after the audio permission flow has completed.
        if (Build.VERSION.SDK_INT >= 33 && granted) {
            val notifications = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
            var asked by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!asked && !notifications.status.isGranted) { asked = true; notifications.launchPermissionRequest() }
            }
        }
        if (granted) HomeScreen(nav, viewModel,
            onPlaySong = { playSongs(nav, viewModel.songs.value, it) },
            onPlaySongList = { tracks, song -> playSongs(nav, tracks, song) })
        else Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(20.dp))
            Text("Your music, your space", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Allow audio access to discover the songs on your device.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            val openSettings = requested && !permission.status.shouldShowRationale
            Button(onClick = {
                if (openSettings) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                else { requested = true; permission.launchPermissionRequest() }
            }, shape = RoundedCornerShape(24.dp)) { Text(if (openSettings) "Open settings" else "Allow audio access") }
        }
    }

    override fun onStop() {
        playRequest?.cancel()
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        mediaController = null
        super.onStop()
    }
}
