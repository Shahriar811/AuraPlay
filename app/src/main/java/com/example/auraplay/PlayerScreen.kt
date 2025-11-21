package com.example.auraplay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    viewModel: MainViewModel, // Added ViewModel
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeatMode: () -> Unit
) {
    // --- Performance Fix for Slider ---
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isUserSeeking by remember { mutableStateOf(false) }
    // Remember playing state when seeking starts to prevent icon flicker
    var cachedPlayingState by remember { mutableStateOf(playerState.isPlaying) }

    // Update cached playing state only when not seeking
    LaunchedEffect(playerState.isPlaying, isUserSeeking) {
        if (!isUserSeeking) {
            cachedPlayingState = playerState.isPlaying
        }
    }

    LaunchedEffect(playerState.currentPosition, isUserSeeking) {
        if (!isUserSeeking) {
            sliderPosition = playerState.currentPosition.toFloat()
        }
    }
    // --- End Performance Fix ---
    
    // Use stable playing state during seeking to prevent icon flicker
    // This ensures the icon doesn't change while the user is dragging the slider
    val stablePlayingState = if (isUserSeeking) {
        cachedPlayingState // Use cached state during seeking
    } else {
        playerState.isPlaying // Use actual state when not seeking
    }

    // Get the full song object (including favorite status) from the DB
    val songId = playerState.currentSong?.id
    val songFromDb by viewModel.getSongById(songId ?: 0L).collectAsState(initial = null)
    val song = songFromDb ?: playerState.currentSong // Use DB version if available

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Now Playing",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack, 
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("equalizer") }) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Equalizer",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    songFromDb?.let { s ->
                        IconButton(onClick = { viewModel.toggleFavorite(s) }) {
                            Icon(
                                imageVector = if (s.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (s.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
                Spacer(modifier = Modifier.height(8.dp))
                
                // Album Art with smooth rounded corners
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(32.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    AsyncImage(
                        model = s.albumArtUri,
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(32.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // Song Title and Artist
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    modifier = Modifier.padding(vertical = 20.dp)
                ) {
                    Text(
                        text = s.title, 
                        fontSize = 22.sp, 
                        fontWeight = FontWeight.Bold,
                        maxLines = 2, 
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = s.artist, 
                        fontSize = 16.sp, 
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Progress Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            // Capture playing state when seeking starts
                            if (!isUserSeeking) {
                                cachedPlayingState = playerState.isPlaying
                                isUserSeeking = true
                            }
                            sliderPosition = it
                        },
                        onValueChangeFinished = {
                            isUserSeeking = false
                            onSeek(sliderPosition)
                        },
                        valueRange = 0f..playerState.totalDuration.toFloat().coerceAtLeast(0f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
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
                            text = formatTime(sliderPosition.toLong()), 
                            fontSize = 12.sp, 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            text = formatTime(playerState.totalDuration), 
                            fontSize = 12.sp, 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Player Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Button
                    Surface(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (playerState.isShuffleOn) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
                        else 
                            Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playerState.isShuffleOn) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Previous Button
                    Surface(
                        onClick = onPrevious,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious, 
                                contentDescription = "Previous",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Play/Pause Button
                    val playPauseScale by animateFloatAsState(
                        targetValue = if (stablePlayingState) 1f else 1f,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f), label = ""
                    )
                    Surface(
                        onClick = onPlayPause,
                        modifier = Modifier
                            .size(80.dp)
                            .scale(playPauseScale),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        tonalElevation = 4.dp,
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (stablePlayingState) 
                                    Icons.Rounded.Pause 
                                else 
                                    Icons.Rounded.PlayArrow,
                                contentDescription = "Play/Pause",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    // Next Button
                    Surface(
                        onClick = onNext,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext, 
                                contentDescription = "Next",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Repeat Button
                    val (repeatIcon, repeatTint) = when (playerState.repeatMode) {
                        1 -> Icons.Rounded.RepeatOne to MaterialTheme.colorScheme.primary
                        2 -> Icons.Rounded.Repeat to MaterialTheme.colorScheme.primary
                        else -> Icons.Rounded.Repeat to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                    Surface(
                        onClick = onToggleRepeatMode,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = if (playerState.repeatMode != 0) 
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) 
                        else 
                            Color.Transparent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = repeatIcon, 
                                contentDescription = "Repeat", 
                                tint = repeatTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

fun formatTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}