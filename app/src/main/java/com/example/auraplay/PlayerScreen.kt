package com.example.auraplay.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.auraplay.MainViewModel
import com.example.auraplay.service.PlayerState
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
    val songId = playerState.currentSong?.id
    val songFromDb by viewModel.getSongById(songId ?: 0L).collectAsState(initial = null)
    val song = songFromDb ?: playerState.currentSong
    val glassColors = glassCardColors()

    // Breathing Glow Pulse for Album Art on GPU Layer
    val infiniteTransition = rememberInfiniteTransition(label = "breathingPulse")
    val pulseScaleState = infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val pulseAlphaState = infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    Scaffold(
        containerColor = Color.Transparent, // Allow background to show
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
                    IconButton(onClick = { navController.navigate("equalizer") }) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = glassColors.textColor
                        )
                    }
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            song?.let { s ->
                val hasAlbumArt = !s.albumArtUri.isNullOrBlank() && s.albumArtUri != "null"
                
                // Album Art container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Glowing Breathing Aura ONLY when there is an album image and player is playing
                    if (playerState.isPlaying && hasAlbumArt) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize(0.9f)
                                .graphicsLayer {
                                    scaleX = pulseScaleState.value
                                    scaleY = pulseScaleState.value
                                    alpha = pulseAlphaState.value
                                }
                                .shadow(
                                    elevation = 28.dp,
                                    shape = RoundedCornerShape(36.dp),
                                    clip = false,
                                    ambientColor = glassColors.accentColor,
                                    spotColor = glassColors.accentColor
                                )
                                .background(
                                    glassColors.accentColor,
                                    RoundedCornerShape(36.dp)
                                )
                        )
                    }

                    // Frosted Glass Album Card overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .glassmorphic(
                                shape = RoundedCornerShape(36.dp),
                                backgroundColor = glassColors.backgroundColor,
                                borderColor = glassColors.borderColor
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!hasAlbumArt) {
                            // Static generic image icon - NO animation
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(28.dp))
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
                            val albumArtModel = remember(s.albumArtUri) { s.albumArtUri }
                            AsyncImage(
                                model = albumArtModel,
                                contentDescription = "Album Art",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(28.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Song Info panel as an elegant glass deck
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassmorphic(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = glassColors.backgroundColor,
                            borderColor = glassColors.borderColor
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = s.title, 
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            color = glassColors.textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = s.artist, 
                            fontSize = 14.sp, 
                            color = glassColors.subTextColor,
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Progress Slider panel isolated from root recomposition
                PlayerProgressSlider(
                    currentPosition = playerState.currentPosition,
                    totalDuration = playerState.totalDuration,
                    onSeek = onSeek,
                    glassColors = glassColors
                )

                // Glass Controls Panel
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
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle Button
                        Box(
                            modifier = Modifier
                                .size(44.dp)
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
                                .size(52.dp)
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

                        // Play/Pause Button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(onClick = onPlayPause)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = CircleShape,
                                    clip = false,
                                    ambientColor = glassColors.accentColor,
                                    spotColor = glassColors.accentColor
                                )
                                .background(glassColors.accentColor, CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(36.dp),
                                tint = Color.White
                            )
                        }

                        // Next Button
                        Box(
                            modifier = Modifier
                                .size(52.dp)
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
                                .size(44.dp)
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// 🎚️ Isolated Player Progress Slider Component (Zero Root Recompositions during Drag)
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
                .padding(16.dp)
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