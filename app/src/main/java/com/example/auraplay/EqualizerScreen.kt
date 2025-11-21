package com.example.auraplay.ui

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.auraplay.EqualizerManager
import com.example.auraplay.EqualizerState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    equalizerManager: EqualizerManager?
) {
    val equalizerState by equalizerManager?.equalizerState?.collectAsState() ?: remember { mutableStateOf(EqualizerState()) }
    val isEnabled = equalizerState.isEnabled

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Equalizer",
                        fontWeight = FontWeight.Bold,
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
                    IconButton(onClick = { equalizerManager?.reset() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Reset",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Enable/Disable Switch
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Equalizer",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    if (isEnabled) "Enabled" else "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { equalizerManager?.setEnabled(it) }
                        )
                    }
                }
            }

            if (isEnabled && equalizerManager != null) {
                item {
                    // Frequency Bands
                    FrequencyBandsSection(equalizerManager, equalizerState)
                }
                
                item {
                    // Bass Boost
                    BassBoostSection(equalizerManager, equalizerState.bassBoost)
                }
                
                item {
                    // Virtualizer
                    VirtualizerSection(equalizerManager, equalizerState.virtualizer)
                }
                
                item {
                    // Reverb Presets
                    ReverbSection(equalizerManager, equalizerState.reverbPreset)
                }
            } else if (!isEnabled) {
                item {
                    // Placeholder when disabled
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Text(
                                "Enable equalizer to customize audio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FrequencyBandsSection(
    equalizerManager: EqualizerManager,
    state: EqualizerState
) {
    val numBands = equalizerManager.getNumberOfBands()
    val bandLevelRange = equalizerManager.getBandLevelRange()
    val minLevel = bandLevelRange?.get(0) ?: -1500
    val maxLevel = bandLevelRange?.get(1) ?: 1500

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Frequency Bands",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            
            // Frequency band sliders with scale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Vertical scale (dB labels)
                Column(
                    modifier = Modifier
                        .width(40.dp)
                        .height(240.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "+15",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                    Text(
                        "0",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                    Text(
                        "-15",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                
                // Frequency band sliders
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (band in 0 until numBands.coerceAtMost(5)) {
                        FrequencyBandSlider(
                            band = band,
                            level = state.bandLevels.getOrElse(band) { 0 },
                            minLevel = minLevel,
                            maxLevel = maxLevel,
                            centerFreq = equalizerManager.getCenterFreq(band),
                            onLevelChange = { equalizerManager.setBandLevel(band, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FrequencyBandSlider(
    band: Int,
    level: Int,
    minLevel: Int,
    maxLevel: Int,
    centerFreq: Int,
    onLevelChange: (Int) -> Unit
) {
    val density = LocalDensity.current
    var currentLevel by remember(level) { mutableStateOf(level) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStartY by remember { mutableStateOf(0f) }
    
    // Update currentLevel when level changes externally
    LaunchedEffect(level) {
        if (!isDragging) {
            currentLevel = level
        }
    }
    
    // Animated thumb position for smooth movement
    val normalizedLevel = ((currentLevel - minLevel).toFloat() / (maxLevel - minLevel)).coerceIn(0f, 1f)
    val animatedNormalized by animateFloatAsState(
        targetValue = normalizedLevel,
        animationSpec = if (isDragging) tween(0) else spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "thumbPosition"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(50.dp)
    ) {
        // Frequency label at top
        Text(
            text = formatFrequency(centerFreq),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Vertical slider with neumorphic design
        BoxWithConstraints(
            modifier = Modifier
                .height(240.dp)
                .width(32.dp)
        ) {
            val thumbPosition = maxHeight * (1f - animatedNormalized)
            
            // Background track with neumorphic style
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
            )
            
            // Tick marks on the track
            for (i in 0..10) {
                val tickY = maxHeight * (i / 10f)
                Box(
                    modifier = Modifier
                        .offset(y = tickY - 1.dp)
                        .width(6.dp)
                        .height(2.dp)
                        .background(
                            if (i == 5) 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                )
            }
            
            // Active track (from bottom to thumb) with smooth animation
            val activeHeight = maxHeight * animatedNormalized
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(activeHeight)
                    .width(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isDragging) 0.9f else 0.8f
                        )
                    )
            )
            
            // Thumb with neumorphic design and smooth animation
            val thumbScale by animateFloatAsState(
                targetValue = if (isDragging) 1.15f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "thumbScale"
            )
            
            Box(
                modifier = Modifier
                    .offset(y = thumbPosition - 14.dp)
                    .size(28.dp)
                    .scale(thumbScale)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isDragging) 
                            MaterialTheme.colorScheme.surface
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                    .shadow(
                        elevation = if (isDragging) 12.dp else 6.dp,
                        shape = RoundedCornerShape(14.dp),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Inner indicator line
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            
            // Interactive area - handles both tap and drag
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                with(density) {
                                    // Update immediately on drag start
                                    val startY = offset.y.coerceIn(0f, maxHeight.toPx())
                                    val newNormalized = 1f - (startY / maxHeight.toPx())
                                    val newLevel = (minLevel + (maxLevel - minLevel) * newNormalized).toInt()
                                    currentLevel = newLevel.coerceIn(minLevel, maxLevel)
                                    onLevelChange(currentLevel)
                                    dragStartY = startY
                                }
                            },
                            onDragEnd = { 
                                isDragging = false
                                onLevelChange(currentLevel)
                            },
                            onDrag = { change, dragAmount ->
                                with(density) {
                                    val newY = (dragStartY + dragAmount.y).coerceIn(0f, maxHeight.toPx())
                                    val newNormalized = 1f - (newY / maxHeight.toPx())
                                    val newLevel = (minLevel + (maxLevel - minLevel) * newNormalized).toInt()
                                    currentLevel = newLevel.coerceIn(minLevel, maxLevel)
                                    onLevelChange(currentLevel)
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                with(density) {
                                    val tapY = tapOffset.y.coerceIn(0f, maxHeight.toPx())
                                    val newNormalized = 1f - (tapY / maxHeight.toPx())
                                    val newLevel = (minLevel + (maxLevel - minLevel) * newNormalized).toInt()
                                    currentLevel = newLevel.coerceIn(minLevel, maxLevel)
                                    onLevelChange(currentLevel)
                                }
                            }
                        )
                    }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Level value with animation
        val displayValue = (currentLevel / 100)
        Text(
            text = "${if (displayValue >= 0) "+" else ""}$displayValue",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp
        )
    }
}

@Composable
fun BassBoostSection(
    equalizerManager: EqualizerManager,
    bassBoost: Int
) {
    var currentBassBoost by remember(bassBoost) { mutableStateOf(bassBoost) }
    var isDragging by remember { mutableStateOf(false) }
    val isBassBoostEnabled = currentBassBoost > 0
    
    // Update currentBassBoost when bassBoost changes externally
    LaunchedEffect(bassBoost) {
        if (!isDragging) {
            currentBassBoost = bassBoost
        }
    }
    
    // Animated value for smooth transitions
    val animatedValue by animateFloatAsState(
        targetValue = currentBassBoost.toFloat(),
        animationSpec = if (isDragging) tween(0) else spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "bassBoost"
    )
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with toggle button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Bass Boost",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.weight(1f))
                
                // Toggle button to enable/disable bass boost
                Surface(
                    modifier = Modifier
                        .clickable {
                            if (isBassBoostEnabled) {
                                // Turn off
                                currentBassBoost = 0
                                equalizerManager.setBassBoost(0)
                            } else {
                                // Turn on with default value (50%)
                                currentBassBoost = 500
                                equalizerManager.setBassBoost(500)
                            }
                        }
                        .shadow(
                            elevation = if (isBassBoostEnabled) 4.dp else 2.dp,
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = if (isBassBoostEnabled) 
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else 
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (isBassBoostEnabled) "ON" else "OFF",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isBassBoostEnabled) 
                                MaterialTheme.colorScheme.primary
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Slider (only show when enabled)
            if (isBassBoostEnabled) {
                // Display percentage
                Text(
                    "${(currentBassBoost / 10)}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                // Custom slider with neumorphic design
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    val normalizedValue = (animatedValue / 1000f).coerceIn(0f, 1f)
                    val thumbPosition = maxWidth * normalizedValue
                    
                    // Background track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                    )
                    
                    // Active track
                    Box(
                        modifier = Modifier
                            .width(thumbPosition)
                            .height(8.dp)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (isDragging) 0.9f else 0.8f
                                )
                            )
                    )
                    
                    // Thumb with neumorphic design
                    val thumbScale by animateFloatAsState(
                        targetValue = if (isDragging) 1.2f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                        label = "thumbScale"
                    )
                    
                    Box(
                        modifier = Modifier
                            .offset(x = thumbPosition - 16.dp)
                            .size(32.dp)
                            .scale(thumbScale)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isDragging) 
                                    MaterialTheme.colorScheme.surface
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                            .shadow(
                                elevation = if (isDragging) 12.dp else 6.dp,
                                shape = RoundedCornerShape(16.dp),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Inner indicator
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    
                    // Interactive area
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        with(density) {
                                            val startX = offset.x.coerceIn(0f, maxWidth.toPx())
                                            val newValue = (startX / maxWidth.toPx() * 1000f).toInt()
                                            currentBassBoost = newValue.coerceIn(0, 1000)
                                            equalizerManager.setBassBoost(currentBassBoost)
                                        }
                                    },
                                    onDragEnd = { 
                                        isDragging = false
                                        equalizerManager.setBassBoost(currentBassBoost)
                                    },
                                    onDrag = { change, dragAmount ->
                                        with(density) {
                                            val newX = (thumbPosition.toPx() + dragAmount.x).coerceIn(0f, maxWidth.toPx())
                                            val newValue = (newX / maxWidth.toPx() * 1000f).toInt()
                                            currentBassBoost = newValue.coerceIn(0, 1000)
                                            equalizerManager.setBassBoost(currentBassBoost)
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { tapOffset ->
                                        with(density) {
                                            val tapX = tapOffset.x.coerceIn(0f, maxWidth.toPx())
                                            val newValue = (tapX / maxWidth.toPx() * 1000f).toInt()
                                            currentBassBoost = newValue.coerceIn(0, 1000)
                                            equalizerManager.setBassBoost(currentBassBoost)
                                        }
                                    }
                                )
                            }
                    )
                }
                
                // Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Off", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        "100%", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Placeholder when disabled
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Tap 'ON' to enable Bass Boost",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun VirtualizerSection(
    equalizerManager: EqualizerManager,
    virtualizer: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.SurroundSound,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Virtualizer",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Slider(
                value = virtualizer.toFloat(),
                onValueChange = { equalizerManager.setVirtualizer(it.toInt()) },
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Off", style = MaterialTheme.typography.labelSmall)
                Text("${(virtualizer / 10)}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ReverbSection(
    equalizerManager: EqualizerManager,
    currentPreset: Int
) {
    val reverbPresets = listOf(
        "None" to android.media.audiofx.PresetReverb.PRESET_NONE.toInt(),
        "Small Room" to android.media.audiofx.PresetReverb.PRESET_SMALLROOM.toInt(),
        "Medium Room" to android.media.audiofx.PresetReverb.PRESET_MEDIUMROOM.toInt(),
        "Large Room" to android.media.audiofx.PresetReverb.PRESET_LARGEROOM.toInt(),
        "Medium Hall" to android.media.audiofx.PresetReverb.PRESET_MEDIUMHALL.toInt(),
        "Large Hall" to android.media.audiofx.PresetReverb.PRESET_LARGEHALL.toInt(),
        "Plate" to android.media.audiofx.PresetReverb.PRESET_PLATE.toInt()
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Waves,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Reverb",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            reverbPresets.forEach { (name, preset) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentPreset == preset,
                        onClick = { equalizerManager.setReverbPreset(preset) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

fun formatFrequency(hz: Int): String {
    return when {
        hz >= 1000 -> "${(hz / 1000f).toString().take(3)}kHz"
        else -> "${hz}Hz"
    }
}

