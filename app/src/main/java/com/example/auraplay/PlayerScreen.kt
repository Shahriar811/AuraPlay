package com.example.auraplay.ui

import com.example.auraplay.ui.MotionIconButton as IconButton
import com.example.auraplay.ui.MotionFilledTonalIconButton as FilledTonalIconButton
import com.example.auraplay.ui.MotionButton as Button
import com.example.auraplay.ui.MotionFilledTonalButton as FilledTonalButton
import com.example.auraplay.ui.MotionOutlinedButton as OutlinedButton
import com.example.auraplay.ui.MotionTextButton as TextButton

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import com.example.auraplay.R
import coil.compose.AsyncImage
import com.example.auraplay.LyricsManager
import com.example.auraplay.MainViewModel
import com.example.auraplay.SongLyrics
import com.example.auraplay.data.Song
import com.example.auraplay.service.PlayerState
import com.example.auraplay.service.SleepTimerState
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavController,
    viewModel: MainViewModel,
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatMode: () -> Unit
) {
    val songId = playerState.currentSong?.id
    val storedSong by remember(songId) { viewModel.getSongById(songId ?: 0L) }
        .collectAsStateWithLifecycle(initialValue = null)
    val songFromDb = storedSong?.takeIf { it.id == songId }
    val song = songFromDb ?: playerState.currentSong
    val colors = glassCardColors()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.isLyricsLoading.collectAsStateWithLifecycle()
    val visualizerEnabled by viewModel.visualizerEnabled.collectAsStateWithLifecycle()
    var showLyrics by rememberSaveable { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackLabel by remember { mutableStateOf("") }
    val latestState by rememberUpdatedState(playerState)
    val latestSeek by rememberUpdatedState(onSeek)
    val latestNext by rememberUpdatedState(onNext)
    val latestPrevious by rememberUpdatedState(onPrevious)
    val background = MaterialTheme.colorScheme.background
    val trackKey = playerState.trackMotionKey() ?: songId ?: 0L
    val playbackOrder = remember(playerState.queueKeys, playerState.queueOrder) {
        playerState.queueOrder.mapNotNull { playerState.queueKeys.getOrNull(it) }
    }
    val trackDirection = rememberTrackDirection(trackKey, playbackOrder)
    var artworkDrag by remember(trackKey) { mutableFloatStateOf(0f) }
    val artworkOffset by animateFloatAsState(artworkDrag, spring(stiffness = 650f), label = "artwork-swipe")
    val shuffleTint by animateColorAsState(if (playerState.isShuffleOn) colors.accentColor else colors.subTextColor,
        tween(200), label = "shuffle-tint")
    val shuffleBackground by animateColorAsState(if (playerState.isShuffleOn) colors.accentColor.copy(alpha = 0.12f) else Color.Transparent,
        tween(200), label = "shuffle-background")
    val repeatBackground by animateColorAsState(if (playerState.repeatMode != 0) colors.accentColor.copy(alpha = 0.12f) else Color.Transparent,
        tween(200), label = "repeat-background")

    LaunchedEffect(feedback) { if (feedback != null) { delay(900); feedback = null } }
    if (showSpeed) PlaybackSpeedDialog(playerState.playbackSpeed, { viewModel.setPlaybackSpeed(it); showSpeed = false }, { showSpeed = false })
    if (showTimer) SleepTimerDialog(sleepTimer,
        { viewModel.startSleepTimer(it); showTimer = false },
        { viewModel.startSleepTimerEndOfTrack(); showTimer = false },
        { viewModel.cancelSleepTimer(); showTimer = false }, { showTimer = false })
    if (showQueue) QueueBottomSheet(playerState, { showQueue = false }, viewModel::playQueueItem, viewModel::removeQueueItem,
        onMoveQueueItem = { from, to -> com.example.auraplay.service.MusicService.moveQueueItem(from, to) })
    if (showBookmarks && song != null) com.example.auraplay.features.PlayerBookmarksSheet(song, playerState.currentPosition, viewModel,
        onDismiss = { showBookmarks = false }, onSeek = { onSeek(it.toFloat()); showBookmarks = false })

    if (song == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.MusicNote, null, tint = colors.accentColor, modifier = Modifier.size(64.dp))
            Text("Choose something to play", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { navController.popBackStack() }) { Text("Back to your library") }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(background)) {
        FixedPlayerLayout(
            header = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { navController.popBackStack() }, shape = CircleShape) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Close player")
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NOW PLAYING", fontSize = 11.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold,
                            color = colors.textColor)
                        Text("Your library", color = colors.subTextColor, fontSize = 12.sp)
                    }
                    Box {
                        FilledTonalIconButton(onClick = { showMenu = true }, shape = CircleShape) { Icon(Icons.Rounded.MoreVert, "Player options") }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Edit song information") }, onClick = { showMenu = false; navController.navigate("song_edit/${song.id}") })
                            DropdownMenuItem(text = { Text("Playback speed · ${playerState.playbackSpeed}x") }, onClick = { showMenu = false; showSpeed = true })
                        }
                    }
                }
            },
            presentation = {
                AnimatedContent(targetState = showLyrics, modifier = Modifier.fillMaxSize().clipToBounds(),
                    transitionSpec = {
                        (fadeIn(tween(260, delayMillis = 50)) + slideInVertically(tween(300)) { it / 12 }) togetherWith
                            fadeOut(tween(180)) using null
                    }, label = "lyrics-presentation") { lyricsVisible ->
                    if (lyricsVisible) {
                        Box(Modifier.fillMaxSize().padding(16.dp)) {
                            SpotifyLyricsView(lyrics, playerState.currentPosition, lyricsLoading, colors.accentColor, onSeek,
                                { viewModel.loadLyrics(song, true) }, { showLyrics = false })
                        }
                    } else Box(
                        Modifier.fillMaxSize()
                            .pointerInput(songId) {
                                var drag = 0f
                                val threshold = 80.dp.toPx()
                                detectHorizontalDragGestures(
                                    onDragStart = { drag = 0f }, onDragCancel = { drag = 0f; artworkDrag = 0f },
                                    onDragEnd = {
                                        if (drag > threshold) latestPrevious() else if (drag < -threshold) latestNext()
                                        drag = 0f
                                        artworkDrag = 0f
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        drag += amount
                                        artworkDrag = drag.coerceIn(-size.width.toFloat(), size.width.toFloat())
                                    })
                            }
                            .pointerInput(songId) {
                                detectTapGestures(onDoubleTap = { offset ->
                                    val step = if (offset.x > size.width / 2f) 10_000L else -10_000L
                                    latestSeek((latestState.currentPosition + step).coerceIn(0L, latestState.totalDuration.coerceAtLeast(0L)).toFloat())
                                    feedback = if (step > 0) "+10s" else "−10s"
                                    feedbackLabel = feedback.orEmpty()
                                })
                            }, contentAlignment = Alignment.Center
                    ) {
                        AnimatedTrackContent(song, Modifier.fillMaxSize()
                            .graphicsLayer { translationX = artworkOffset * 0.35f }, trackKey, trackDirection) { displayedSong ->
                            BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
                                contentAlignment = Alignment.Center) {
                                var artworkRatio by remember(displayedSong.albumArtUri) { mutableFloatStateOf(1f) }
                                // Fit both dimensions so wide thumbnails and portrait covers remain complete.
                                val artworkWidth = minOf(maxWidth, maxHeight * artworkRatio, 480.dp)
                                AsyncImage(model = displayedSong.albumArtUri, contentDescription = "Cover art for ${displayedSong.title}",
                                    contentScale = ContentScale.Fit,
                                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                                    error = painterResource(R.drawable.ic_music_placeholder),
                                    onSuccess = { result ->
                                        val size = result.painter.intrinsicSize
                                        if (size.width > 0f && size.height > 0f) artworkRatio = size.width / size.height
                                    },
                                    modifier = Modifier.width(artworkWidth).aspectRatio(artworkRatio)
                                        .clip(RoundedCornerShape(20.dp)))
                            }
                        }
                        AnimatedVisibility(feedback != null, enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.8f),
                            exit = fadeOut(tween(180)) + scaleOut(targetScale = 1.1f)) {
                            Text(feedbackLabel, color = Color.White, fontWeight = FontWeight.Bold,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.65f), CircleShape).padding(16.dp))
                        }
                    }
                }
            },
            controls = {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("player-controls"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AnimatedTrackContent(song, Modifier.weight(1f), trackKey, trackDirection) { displayedSong ->
                            PlayerTrackDetails(displayedSong.title, displayedSong.artist)
                        }
                        FilledTonalIconButton(onClick = { songFromDb?.let(viewModel::toggleFavorite) }, enabled = songFromDb != null) {
                            AnimatedControlIcon(if (songFromDb?.isFavorite == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                if (songFromDb?.isFavorite == true) "Unlike song" else "Like song",
                                tint = if (songFromDb?.isFavorite == true) colors.accentColor else colors.textColor)
                        }
                        FilledTonalIconButton(onClick = { showLyrics = !showLyrics },
                            modifier = Modifier.semantics { selected = showLyrics }) {
                            AnimatedControlIcon(Icons.Rounded.FormatQuote, "Toggle lyrics",
                                tint = if (showLyrics) colors.accentColor else colors.textColor)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(24.dp)) {
                        if (visualizerEnabled) AudioVisualizer(playerState.isPlaying, accentColor = colors.accentColor,
                            secondaryColor = colors.accentColor.copy(alpha = 0.4f), height = 24.dp)
                    }
                    key(trackKey) { PlayerProgressSlider(playerState.currentPosition, playerState.totalDuration, onSeek, colors,
                        modifier = Modifier.testTag("player-progress")) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipPrevious, "Previous track") }
                        Button(onClick = onPlayPause, shape = CircleShape, modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentColor, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                            AnimatedControlIcon(if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            AnimatedContent(playerState.isPlaying, transitionSpec = {
                                fadeIn(tween(160, delayMillis = 60)) togetherWith fadeOut(tween(100)) using null
                            }, label = "play-pause-label") { playing ->
                                Text(if (playing) "Pause" else "Play", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                        }
                        FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(54.dp)) { Icon(Icons.Rounded.SkipNext, "Next track") }
                    }
                    PlayerQuickActions(sleepTimer,
                        onEqualizer = { navController.navigate("equalizer") },
                        onBookmarks = { showBookmarks = true }, onSleepTimer = { showTimer = true })
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onToggleShuffle, shape = CircleShape, modifier = Modifier.weight(1f).semantics {
                            selected = playerState.isShuffleOn
                            stateDescription = if (playerState.isShuffleOn) "Shuffle on" else "Shuffle off"
                        }, colors = ButtonDefaults.outlinedButtonColors(containerColor = shuffleBackground),
                            contentPadding = PaddingValues(10.dp)) {
                            Icon(Icons.Rounded.Shuffle, if (playerState.isShuffleOn) "Disable shuffle" else "Enable shuffle",
                                tint = shuffleTint)
                        }
                        FilledTonalButton(onClick = { showQueue = true }, shape = CircleShape, modifier = Modifier.weight(2f), contentPadding = PaddingValues(10.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Queue · ${playerState.queueSize}", maxLines = 1)
                        }
                        OutlinedButton(onClick = onToggleRepeatMode, shape = CircleShape, modifier = Modifier.weight(1f).semantics {
                            stateDescription = when (playerState.repeatMode) { 1 -> "Repeat one"; 2 -> "Repeat all"; else -> "Repeat off" }
                        }, colors = ButtonDefaults.outlinedButtonColors(containerColor = repeatBackground),
                            contentPadding = PaddingValues(10.dp)) {
                            AnimatedControlIcon(if (playerState.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                when (playerState.repeatMode) { 1 -> "Repeat one"; 2 -> "Repeat all"; else -> "Repeat off" },
                                tint = if (playerState.repeatMode != 0) colors.accentColor else colors.subTextColor)
                        }
                    }
                }
            }
        )
    }
}

@Composable
internal fun PlayerQuickActions(
    sleepTimer: SleepTimerState,
    onEqualizer: () -> Unit,
    onBookmarks: () -> Unit,
    onSleepTimer: () -> Unit
) {
    val colors = glassCardColors()
    Row(Modifier.fillMaxWidth().testTag("player-quick-actions"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onEqualizer, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            contentPadding = PaddingValues(4.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Equalizer, null, modifier = Modifier.size(22.dp))
                Text("Equalizer", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onBookmarks, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            contentPadding = PaddingValues(4.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Bookmarks, null, modifier = Modifier.size(22.dp))
                Text("Bookmarks", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        TextButton(onClick = onSleepTimer, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
            selected = sleepTimer.isActive
            stateDescription = when {
                sleepTimer.isEndOfTrack -> "Pauses at end of track"
                sleepTimer.isActive -> "Sleep timer active"
                else -> "Sleep timer off"
            }
        }, contentPadding = PaddingValues(4.dp),
            colors = ButtonDefaults.textButtonColors(
                containerColor = if (sleepTimer.isActive) colors.accentColor.copy(alpha = 0.14f) else Color.Transparent
            )) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(22.dp))
                Text("Sleep timer", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// 🎤 Authentic Spotify-Style Interactive Karaoke Lyrics Component
@Composable
fun SpotifyLyricsView(
    lyrics: SongLyrics,
    currentPosition: Long,
    isLoading: Boolean,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    onRefreshLyrics: () -> Unit,
    onToggleLyrics: () -> Unit
) {
    val darkTheme = com.example.auraplay.ui.theme.LocalThemeIsDark.current
    val glassColors = glassCardColors(customAccent = accentColor)
    val listState = rememberLazyListState()

    val activeIndex = remember(lyrics, currentPosition) {
        LyricsManager.getActiveLyricIndex(lyrics.lines, currentPosition)
    }

    // Auto-scroll so active line stays in upper third / center view like Spotify
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.lines.size) {
            listState.animateScrollToItem(
                index = (activeIndex - 1).coerceAtLeast(0),
                scrollOffset = 0
            )
        }
    }

    val lyricsBackground = if (darkTheme) {
        Color(0xFF1B1E19).copy(alpha = 0.88f)
    } else {
        Color(0xFFF3F5EF).copy(alpha = 0.94f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .glassmorphic(
                shape = RoundedCornerShape(28.dp),
                backgroundColor = lyricsBackground,
                borderColor = glassColors.borderColor
            )
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Lyrics Header Bar (Spotify Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = if (darkTheme) 0.20f else 0.15f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        val sourceText = when (lyrics.source) {
                            "LOCAL" -> "📁 LOCAL .LRC"
                            "LRCLIB" -> "✨ SYNCED • LRCLIB"
                            else -> if (lyrics.isSynced) "✨ SYNCED" else "📝 LYRICS"
                        }
                        Text(
                            text = sourceText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp,
                            color = accentColor
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshLyrics,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = "Refresh Lyrics",
                            tint = glassColors.textColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    IconButton(
                        onClick = onToggleLyrics,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Lyrics",
                            tint = glassColors.textColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                // Loading shimmer state
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(38.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Searching lyrics on LRCLIB...",
                            color = glassColors.textColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            } else if (lyrics.lines.isEmpty() && lyrics.plainText.isBlank()) {
                // Empty state with retry action
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Lyrics,
                            contentDescription = null,
                            tint = accentColor.copy(alpha = 0.85f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "No lyrics found",
                            fontWeight = FontWeight.Bold,
                            color = glassColors.textColor,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap below to retry search",
                            color = glassColors.subTextColor,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRefreshLyrics,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Search Online Lyrics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (lyrics.isSynced) {
                // 🎵 Spotify-Style Left-Aligned Bold Karaoke Scrolling Lyrics
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 40.dp, bottom = 120.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    itemsIndexed(lyrics.lines) { index, line ->
                        val isActive = index == activeIndex
                        val activeColor = if (darkTheme) Color.White else Color(0xFF19231B)
                        val inactiveColor = if (darkTheme) Color.White.copy(alpha = 0.40f) else Color(0xFF19231B).copy(alpha = 0.40f)

                        val animatedTextColor by animateColorAsState(
                            targetValue = if (isActive) activeColor else inactiveColor,
                            animationSpec = tween(durationMillis = 220),
                            label = "spotifyLyricColor"
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = if (isActive) 1.02f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "spotifyLyricScale"
                        )

                        Text(
                            text = line.text,
                            fontSize = if (isActive) 24.sp else 20.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                            color = animatedTextColor,
                            textAlign = TextAlign.Start,
                            lineHeight = if (isActive) 32.sp else 28.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSeek(line.timeMs.toFloat()) }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                                .graphicsLayer {
                                    scaleX = animatedScale
                                    scaleY = animatedScale
                                }
                        )
                    }
                }
            } else {
                // 📝 Spotify-Style Plain Text Lyrics (Split into Clean Left-Aligned Lines)
                val plainLines = remember(lyrics.plainText) {
                    lyrics.plainText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 28.dp, bottom = 120.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    items(plainLines.size) { index ->
                        Text(
                            text = plainLines[index],
                            color = if (darkTheme) Color.White.copy(alpha = 0.88f) else Color(0xFF19231B).copy(alpha = 0.88f),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                            lineHeight = 28.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val glassColors = glassCardColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Playback Speed",
                fontWeight = FontWeight.Bold,
                color = glassColors.textColor
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                speeds.forEach { speed ->
                    val isSelected = currentSpeed == speed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) glassColors.accentColor.copy(alpha = 0.2f) else glassColors.backgroundColor.copy(alpha = 0.2f))
                            .clickable { onSpeedSelected(speed) }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${speed}x ${if (speed == 1.0f) "(Normal)" else ""}",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) glassColors.accentColor else glassColors.textColor
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = glassColors.accentColor
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = glassColors.accentColor)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = glassColors.backgroundColor,
        modifier = Modifier.border(1.dp, glassColors.borderColor, RoundedCornerShape(24.dp))
    )
}

@Composable
fun SleepTimerDialog(
    sleepTimerState: SleepTimerState,
    onStartTimer: (Int) -> Unit,
    onStartEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val glassColors = glassCardColors()
    val presets = listOf(15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Sleep Timer",
                fontWeight = FontWeight.Bold,
                color = glassColors.textColor
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sleepTimerState.isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(glassColors.accentColor.copy(alpha = 0.15f))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val displayTime = if (sleepTimerState.isEndOfTrack) {
                            "Pauses at end of track"
                        } else {
                            val mins = sleepTimerState.remainingSeconds / 60
                            val secs = sleepTimerState.remainingSeconds % 60
                            String.format(Locale.ROOT, "Remaining: %02d:%02d", mins, secs)
                        }
                        Text(
                            text = displayTime,
                            fontWeight = FontWeight.Bold,
                            color = glassColors.accentColor
                        )
                    }

                    Button(
                        onClick = onCancelTimer,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel Sleep Timer")
                    }
                } else {
                    presets.forEach { minutes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(glassColors.backgroundColor.copy(alpha = 0.2f))
                                .clickable { onStartTimer(minutes) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = glassColors.accentColor
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "$minutes Minutes",
                                fontWeight = FontWeight.Medium,
                                color = glassColors.textColor
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(glassColors.backgroundColor.copy(alpha = 0.2f))
                            .clickable { onStartEndOfTrack() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = glassColors.accentColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "End of Current Song",
                            fontWeight = FontWeight.Medium,
                            color = glassColors.textColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = glassColors.accentColor)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = glassColors.backgroundColor,
        modifier = Modifier.border(1.dp, glassColors.borderColor, RoundedCornerShape(24.dp))
    )
}

@Composable
fun PlayerProgressSlider(
    currentPosition: Long,
    totalDuration: Long,
    onSeek: (Float) -> Unit,
    glassColors: GlassCardColors,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentPosition) {
        if (!isDragging) {
            dragPosition = currentPosition.toFloat()
        }
    }

    val maxDuration = maxOf(1f, totalDuration.toFloat())
    val displayPosition = if (isDragging) dragPosition.coerceIn(0f, maxDuration) else currentPosition.toFloat().coerceIn(0f, maxDuration)

    Box(
        modifier = modifier
            .fillMaxWidth()

    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 0.dp)
        ) {
            Slider(
                value = displayPosition,
                onValueChange = {
                    isDragging = true
                    dragPosition = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek(dragPosition)
                },
                valueRange = 0f..maxDuration,
                colors = SliderDefaults.colors(
                    thumbColor = glassColors.accentColor,
                    activeTrackColor = glassColors.accentColor,
                    inactiveTrackColor = glassColors.textColor.copy(alpha = 0.08f)
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(displayPosition.toLong()),
                    fontSize = 11.sp,
                    color = glassColors.subTextColor,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "−" + formatTime((totalDuration - displayPosition.toLong()).coerceAtLeast(0L)),
                    fontSize = 11.sp,
                    color = glassColors.subTextColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
