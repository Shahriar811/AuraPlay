package com.example.auraplay

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.example.auraplay.service.PlayerState
import com.example.auraplay.ui.*
import com.example.auraplay.ui.theme.AuraPlayTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.common.util.concurrent.MoreExecutors

@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalPermissionsApi::class)
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        // Update the factory to pass the settingsDataStore
        MainViewModelFactory(
            application,
            (application as AuraPlayApplication).database.playlistDao(),
            (application as AuraPlayApplication).settingsDataStore
        )
    }
    private var mediaController: MediaController? = null

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaController = controllerFuture.get()
            },
            MoreExecutors.directExecutor()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable 120Hz support
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            // Request highest available refresh rate
            window.attributes.preferredDisplayModeId = 0 // 0 means use highest available
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
        
        setContent {
            // This will now read from DataStore
            val darkTheme by viewModel.darkTheme.collectAsState()
            
            // Update status bar visibility based on theme
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                // Use light status bar icons (dark icons) for better visibility on light backgrounds
                // Use dark status bar icons (light icons) for better visibility on dark backgrounds
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
                // Ensure system bars are visible
                insetsController.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }

            AuraPlayTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentDestination = currentBackStack?.destination

                val fullPlayerState by MusicService.playerState.collectAsState()
                val songs by viewModel.songs.collectAsState()

                // Determine if the bottom bar should be shown
                val showBottomBar = currentDestination?.route in listOf("home", "playlists", "favorites", "settings")

                Scaffold(
                    modifier = Modifier.systemBarsPadding(),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp
                            ) {
                                NavigationBarItem(
                                    icon = { 
                                        Icon(
                                            Icons.Rounded.Home, 
                                            contentDescription = null
                                        ) 
                                    },
                                    label = { Text("Home") },
                                    selected = currentDestination?.route == "home",
                                    onClick = {
                                        navController.navigate("home") {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    icon = { 
                                        Icon(
                                            Icons.Rounded.PlaylistPlay, 
                                            contentDescription = null
                                        ) 
                                    },
                                    label = { Text("Playlists") },
                                    selected = currentDestination?.route == "playlists",
                                    onClick = {
                                        navController.navigate("playlists") {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    icon = { 
                                        Icon(
                                            Icons.Rounded.Favorite, 
                                            contentDescription = null
                                        ) 
                                    },
                                    label = { Text("Favorites") },
                                    selected = currentDestination?.route == "favorites",
                                    onClick = {
                                        navController.navigate("favorites") {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                                NavigationBarItem(
                                    icon = { 
                                        Icon(
                                            Icons.Rounded.Settings, 
                                            contentDescription = null
                                        ) 
                                    },
                                    label = { Text("Settings") },
                                    selected = currentDestination?.route == "settings",
                                    onClick = {
                                        navController.navigate("settings") {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(navController, startDestination = "home", Modifier.padding(innerPadding)) {
                        composable("home") {
                            HomeScreenWithPermission(
                                navController = navController,
                                viewModel = viewModel,
                                playerState = fullPlayerState,
                                songs = songs,
                                mediaController = mediaController
                            )
                        }
                        composable("player") {
                            PlayerScreen(
                                navController = navController,
                                viewModel = viewModel, // Pass ViewModel
                                playerState = fullPlayerState,
                                onPlayPause = {
                                    if (fullPlayerState.isPlaying) mediaController?.pause() else mediaController?.play()
                                },
                                onSeek = { mediaController?.seekTo(it.toLong()) },
                                onNext = { mediaController?.seekToNext() },
                                onPrevious = { mediaController?.seekToPrevious() },
                                onToggleShuffle = { mediaController?.shuffleModeEnabled = !fullPlayerState.isShuffleOn },
                                onToggleRepeatMode = {
                                    mediaController?.repeatMode = when (fullPlayerState.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                }
                            )
                        }
                        composable("playlists") {
                            PlaylistScreen(navController = navController, viewModel = viewModel)
                        }
                        composable(
                            "playlist_details/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                            PlaylistDetailsScreenWrapper(
                                playlistId = playlistId,
                                navController = navController,
                                viewModel = viewModel,
                                mediaController = mediaController
                            )
                        }
                        composable(
                            "add_songs/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
                            AddSongsToPlaylistScreen(
                                playlistId = playlistId,
                                navController = navController,
                                viewModel = viewModel
                            )
                        }
                        // Favorites Composable
                        composable("favorites") {
                            val favSongs by viewModel.favoriteSongs.collectAsState()
                            FavoritesScreen(
                                navController = navController,
                                viewModel = viewModel,
                                onSongSelected = { song ->
                                    val mediaItems = favSongs.map { s -> // Use favSongs list
                                        MediaItem.Builder()
                                            .setUri(s.data)
                                            .setMediaId(s.id.toString())
                                            .setMediaMetadata(
                                                MediaMetadata.Builder()
                                                    .setTitle(s.title)
                                                    .setArtist(s.artist)
                                                    .setArtworkUri(android.net.Uri.parse(s.albumArtUri))
                                                    .build()
                                            )
                                            .build()
                                    }
                                    mediaController?.setMediaItems(mediaItems, favSongs.indexOf(song), 0)
                                    mediaController?.prepare()
                                    mediaController?.play()
                                    navController.navigate("player")
                                }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(navController = navController, viewModel = viewModel)
                        }
                        composable("equalizer") {
                            EqualizerScreen(
                                navController = navController,
                                equalizerManager = MusicService.getEqualizerManager()
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun HomeScreenWithPermission(
        navController: NavController,
        viewModel: MainViewModel,
        playerState: PlayerState,
        songs: List<Song>,
        mediaController: MediaController?
    ) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val permissionState = rememberPermissionState(permission)

        if (permissionState.status.isGranted) {
            HomeScreen(
                navController = navController,
                viewModel = viewModel,
                playerState = playerState,
                onPlaySong = { song ->
                    val mediaItems = songs.map { s ->
                        MediaItem.Builder()
                            .setUri(s.data)
                            .setMediaId(s.id.toString())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(s.title)
                                    .setArtist(s.artist)
                                    .setArtworkUri(android.net.Uri.parse(s.albumArtUri))
                                    .build()
                            )
                            .build()
                    }
                    mediaController?.setMediaItems(mediaItems, songs.indexOf(song), 0)
                    mediaController?.prepare()
                    mediaController?.play()
                    navController.navigate("player")
                },
                onTogglePlayPause = {
                    if (playerState.isPlaying) mediaController?.pause() else mediaController?.play()
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Permission required to access music files.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }

    @Composable
    fun PlaylistDetailsScreenWrapper(
        playlistId: Long,
        navController: NavController,
        viewModel: MainViewModel,
        mediaController: MediaController?
    ) {
        val playlistWithSongs by viewModel.getPlaylistWithSongs(playlistId).collectAsState(initial = null)
        val playlistSongs = playlistWithSongs?.songs ?: emptyList()

        PlaylistDetailsScreen(
            playlistId = playlistId,
            navController = navController,
            viewModel = viewModel,
            onSongSelected = { song ->
                val mediaItems = playlistSongs.map { s ->
                    MediaItem.Builder()
                        .setUri(s.data)
                        .setMediaId(s.id.toString())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(s.title)
                                .setArtist(s.artist)
                                .setArtworkUri(android.net.Uri.parse(s.albumArtUri))
                                .build()
                        )
                        .build()
                }
                mediaController?.setMediaItems(mediaItems, playlistSongs.indexOf(song), 0)
                mediaController?.prepare()
                mediaController?.play()
                navController.navigate("player")
            }
        )
    }

    override fun onStop() {
        mediaController?.release()
        super.onStop()
    }
}