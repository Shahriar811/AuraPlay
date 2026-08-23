package com.example.auraplay.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.auraplay.LyricsManager
import com.example.auraplay.MainViewModel
import com.example.auraplay.service.PlayerState
import com.example.auraplay.service.SleepTimerState
import java.util.concurrent.TimeUnit

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
    val context = LocalContext.current
    val songId = playerState.currentSong?.id
    val songFromDb by viewModel.getSongById(songId ?: 0L).collectAsState(initial = null)
    val song = songFromDb ?: playerState.currentSong

    val accentTheme by viewModel.accentTheme.collectAsState()
    val isPureBlack by viewModel.pureBlack.collectAsState()
    val sleepTimerState by viewModel.sleepTimerState.collectAsState()
    val currentLyrics by viewModel.currentLyrics.collectAsState()

    // Dynamic color extraction from active album art
    val extractedColors = rememberDominantColors(context, song?.albumArtUri)
    val dynamicAccent = resolveAccentColor(accentTheme, extractedColors.dominantColor)
    val glassColors = glassCardColors(
        customAccent = dynamicAccent,
        isPureBlack = isPureBlack
    )

    var showLyrics by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    // Load lyrics when current song changes
    LaunchedEffect(song?.data) {
        song?.data?.let { path ->
            viewModel.loadLyrics(path)
        }
    }

    // Breathing Glow Pulse for Album Art on GPU Layer
    val infiniteTransition = rememberInfiniteTransition(label = "breathingPulse")
    val pulseScaleState = infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    // Dialogs & Sheets
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = playerState.playbackSpeed,
            onSpeedSelected = { speed ->
                viewModel.setPlaybackSpeed(speed)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            sleepTimerState = sleepTimerState,
            onStartTimer = { minutes ->
                viewModel.startSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onStartEndOfTrack = {
                viewModel.startSleepTimerEndOfTrack()
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                viewModel.cancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            playerState = playerState,
            onDismiss = { showQueueSheet = false },
            onPlayQueueItem = { index ->
                viewModel.playQueueItem(index)
            },
            onRemoveQueueItem = { index ->
                viewModel.removeQueueItem(index)
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Now Playing",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = glassColors.textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = glassColors.textColor
                        )
                    }
                },
                actions = {
                    // Playback Speed Button
                    TextButton(onClick = { showSpeedDialog = true }) {
                        Text(
                            text = "${playerState.playbackSpeed}x",
                            fontWeight = FontWeight.Bold,
                            color = glassColors.accentColor,
                            fontSize = 14.sp
                        )
                    }

                    // Sleep Timer Button
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (sleepTimerState.isActive) Icons.Rounded.Bedtime else Icons.Rounded.BedtimeOff,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerState.isActive) glassColors.accentColor else glassColors.textColor
                            )
                        }
                    }

                    // Equalizer
                    IconButton(onClick = { navController.navigate("equalizer") }) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = glassColors.textColor
                        )
                    }

                    // Favorite
                    songFromDb?.let { s ->
                        IconButton(onClick = { viewModel.toggleFavorite(s) }) {
                            Icon(
                                imageVector = if (s.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (s.isFavorite) glassColors.accentColor else glassColors.textColor
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            song?.let { s ->
                val hasAlbumArt = !s.albumArtUri.isNullOrBlank() && s.albumArtUri != "null"

                // Center Stage: Album Art or Karaoke Lyrics
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLyrics) {
                        // Lyrics View
                        LyricsDisplayView(
                            lyrics = currentLyrics,
                            currentPosition = playerState.currentPosition,
                            accentColor = glassColors.accentColor,
                            onToggleLyrics = { showLyrics = false }
                        )
                    } else {
                        // Glowing Breathing Album Art
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playerState.isPlaying && hasAlbumArt) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize(0.92f)
                                        .graphicsLayer {
                                            scaleX = pulseScaleState.value
                                            scaleY = pulseScaleState.value
                                            alpha = pulseAlphaState.value
                                        }
                                        .shadow(
                                            elevation = 32.dp,
                                            shape = RoundedCornerShape(36.dp),
                                            clip = false,
                                            ambientColor = dynamicAccent,
                                            spotColor = dynamicAccent
                                        )
                                        .background(dynamicAccent, RoundedCornerShape(36.dp))
                                )
                            }

                            // Frosted Glass Album Card
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .glassmorphic(
                                        shape = RoundedCornerShape(32.dp),
                                        backgroundColor = glassColors.backgroundColor,
                                        borderColor = glassColors.borderColor
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!hasAlbumArt) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(26.dp))
                                            .background(glassColors.accentColor.copy(alpha = 0.08f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = "No Album Art",
                                            tint = glassColors.accentColor.copy(alpha = 0.8f),
                                            modifier = Modifier.size(96.dp)
                                        )
                                    }
                                } else {
                                    AsyncImage(
                                        model = s.albumArtUri,
                                        contentDescription = "Album Art",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(26.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                }

                // Song Title & Artist Glass Deck
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassmorphic(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = glassColors.backgroundColor,
                            borderColor = glassColors.borderColor
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = glassColors.textColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${s.artist} • ${s.album}",
                                fontSize = 13.sp,
                                color = glassColors.subTextColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Lyrics Toggle Button
                        IconButton(onClick = { showLyrics = !showLyrics }) {
                            Icon(
                                imageVector = if (showLyrics) Icons.Rounded.Album else Icons.Rounded.Lyrics,
                                contentDescription = "Toggle Lyrics",
                                tint = if (showLyrics) glassColors.accentColor else glassColors.subTextColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Real-time Audio Spectrum Visualizer
                AudioVisualizer(
                    isPlaying = playerState.isPlaying,
                    accentColor = dynamicAccent,
                    secondaryColor = dynamicAccent.copy(alpha = 0.5f),
                    height = 36.dp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Slider
                PlayerProgressSlider(
                    currentPosition = playerState.currentPosition,
                    totalDuration = playerState.totalDuration,
                    onSeek = onSeek,
                    glassColors = glassColors
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Glass Playback Controls Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassmorphic(
                            shape = RoundedCornerShape(24.dp),
                            backgroundColor = glassColors.backgroundColor,
                            borderColor = glassColors.borderColor
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clickable(onClick = onToggleShuffle)
                                .glassmorphic(
                                    shape = CircleShape,
                                    backgroundColor = if (playerState.isShuffleOn) glassColors.accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                    borderColor = if (playerState.isShuffleOn) glassColors.accentColor.copy(alpha = 0.4f) else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playerState.isShuffleOn) glassColors.accentColor else glassColors.textColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Previous Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(onClick = onPrevious)
                                .glassmorphic(
                                    shape = CircleShape,
                                    backgroundColor = glassColors.backgroundColor.copy(alpha = 0.2f),
                                    borderColor = glassColors.borderColor
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(24.dp),
                                tint = glassColors.textColor
                            )
                        }

                        // Play/Pause Button with Dynamic Glow
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clickable(onClick = onPlayPause)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = CircleShape,
                                    clip = false,
                                    ambientColor = dynamicAccent,
                                    spotColor = dynamicAccent
                                )
                                .background(dynamicAccent, CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(34.dp),
                                tint = Color.White
                            )
                        }

                        // Next Button
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(onClick = onNext)
                                .glassmorphic(
                                    shape = CircleShape,
                                    backgroundColor = glassColors.backgroundColor.copy(alpha = 0.2f),
                                    borderColor = glassColors.borderColor
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(24.dp),
                                tint = glassColors.textColor
                            )
                        }

                        // Repeat Button
                        val (repeatIcon, repeatTint) = when (playerState.repeatMode) {
                            androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne to glassColors.accentColor
                            androidx.media3.common.Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat to glassColors.accentColor
                            else -> Icons.Rounded.Repeat to glassColors.textColor.copy(alpha = 0.4f)
                        }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clickable(onClick = onToggleRepeatMode)
                                .glassmorphic(
                                    shape = CircleShape,
                                    backgroundColor = if (playerState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) glassColors.accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                    borderColor = if (playerState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) glassColors.accentColor.copy(alpha = 0.4f) else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = repeatIcon,
                                contentDescription = "Repeat",
                                tint = repeatTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Queue Bottom Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showQueueSheet = true }) {
                        Icon(
                            Icons.Rounded.QueueMusic,
                            contentDescription = "Queue",
                            tint = glassColors.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Up Next (${playerState.queueSize})",
                            color = glassColors.textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsDisplayView(
    lyrics: com.example.auraplay.SongLyrics,
    currentPosition: Long,
    accentColor: Color,
    onToggleLyrics: () -> Unit
) {
    val glassColors = glassCardColors()
    val listState = rememberLazyListState()
    val activeIndex = remember(lyrics, currentPosition) {
        LyricsManager.getActiveLyricIndex(lyrics.lines, currentPosition)
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyrics.lines.size) {
            listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .glassmorphic(
                shape = RoundedCornerShape(28.dp),
                backgroundColor = glassColors.backgroundColor,
                borderColor = glassColors.borderColor
            )
            .padding(16.dp)
    ) {
        if (lyrics.lines.isEmpty() && lyrics.plainText.isBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = glassColors.subTextColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No lyrics found (.lrc file)",
                        color = glassColors.subTextColor,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (lyrics.isSynced) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    val isActive = index == activeIndex
                    Text(
                        text = line.text,
                        fontSize = if (isActive) 19.sp else 15.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) accentColor else glassColors.textColor.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = if (isActive) 1.05f else 1f
                                scaleY = if (isActive) 1.05f else 1f
                            }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Text(
                        text = lyrics.plainText,
                        color = glassColors.textColor,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    playerState: PlayerState,
    onDismiss: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit
) {
    val glassColors = glassCardColors()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = glassColors.backgroundColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playing Queue (${playerState.queue.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = glassColors.textColor
                )
                Text(
                    text = "Tap to jump",
                    fontSize = 12.sp,
                    color = glassColors.subTextColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(playerState.queue) { index, song ->
                    val isCurrentTrack = index == playerState.currentQueueIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCurrentTrack) glassColors.accentColor.copy(alpha = 0.2f) else glassColors.backgroundColor.copy(alpha = 0.2f))
                            .clickable { onPlayQueueItem(index) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCurrentTrack) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = "Active",
                                tint = glassColors.accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                color = glassColors.subTextColor,
                                modifier = Modifier.width(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrentTrack) glassColors.accentColor else glassColors.textColor
                            )
                            Text(
                                text = song.artist,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = glassColors.subTextColor
                            )
                        }
                        IconButton(
                            onClick = { onRemoveQueueItem(index) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove",
                                tint = glassColors.subTextColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
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
                            String.format("Remaining: %02d:%02d", mins, secs)
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
                            Icons.Rounded.QueueMusic,
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
            .glassmorphic(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = glassColors.backgroundColor,
                borderColor = glassColors.borderColor
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
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
                    text = formatTime(totalDuration),
                    fontSize = 11.sp,
                    color = glassColors.subTextColor,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val safeMillis = maxOf(0L, millis)
    val hours = TimeUnit.MILLISECONDS.toHours(safeMillis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMillis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMillis) % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}