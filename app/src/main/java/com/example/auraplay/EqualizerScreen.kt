package com.example.auraplay.ui

import com.example.auraplay.ui.MotionIconButton as IconButton
import com.example.auraplay.ui.MotionTextButton as TextButton

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val equalizerState by equalizerManager?.equalizerState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(EqualizerState()) }
    val isEnabled = equalizerState.isEnabled
    val glassColors = glassCardColors()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Equalizer",
                        fontWeight = FontWeight.Bold,
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
                    IconButton(onClick = { equalizerManager?.reset() }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Reset all equalizer settings",
                            tint = glassColors.textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
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
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = glassColors.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Equalizer",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = glassColors.textColor
                                )
                                Text(
                                    if (isEnabled) "Enabled" else "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = glassColors.subTextColor
                                )
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { equalizerManager?.setEnabled(it) },
                            enabled = equalizerManager != null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = glassColors.accentColor,
                                checkedTrackColor = glassColors.accentColor.copy(alpha = 0.5f)
                            )
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
                                tint = glassColors.textColor.copy(alpha = 0.2f)
                            )
                            Text(
                                if (equalizerManager == null) "Start playback to connect the equalizer" else "Enable equalizer to customize audio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glassColors.subTextColor
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
    val glassColors = glassCardColors()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphic(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = glassColors.backgroundColor,
                borderColor = glassColors.borderColor
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EffectSectionHeader("Frequency Bands", Icons.Rounded.Equalizer,
                state.bandLevels.any { it != 0 }, equalizerManager::resetBands)

            // Frequency band sliders with scale
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Vertical scale (dB labels)
                Column(
                    modifier = Modifier
                        .width(32.dp)
                        .height(220.dp)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "+15",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.subTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Text(
                        "0",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Text(
                        "-15",
                        style = MaterialTheme.typography.labelSmall,
                        color = glassColors.subTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
                
                // Frequency band sliders
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (band in 0 until numBands) {
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
    val latestLevelChange by rememberUpdatedState(onLevelChange)
    var currentLevel by remember { mutableIntStateOf(level) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderHeight by remember { mutableFloatStateOf(0f) }
    
    // Update currentLevel when level changes externally
    LaunchedEffect(level) {
        if (!isDragging) {
            currentLevel = level
        }
    }
    
    // Smooth indicator and thumb position
    val normalizedLevel = ((currentLevel - minLevel).toFloat() / (maxLevel - minLevel).coerceAtLeast(1)).coerceIn(0f, 1f)
    
    val thumbScale by animateFloatAsState(
        targetValue = if (isDragging) 1.25f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
        label = "thumbScale"
    )
    
    val glassColors = glassCardColors()
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(55.dp)
    ) {
        // Frequency label at top
        Text(
            text = formatFrequency(centerFreq),
            style = MaterialTheme.typography.labelSmall,
            color = glassColors.textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        // Vertical slider with premium glassmorphic style
        Box(
            modifier = Modifier
                .height(220.dp)
                .width(36.dp)
                .onGloballyPositioned { coordinates ->
                    sliderHeight = coordinates.size.height.toFloat()
                }
                .pointerInput(minLevel, maxLevel) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            if (sliderHeight > 0f) {
                                val tapY = offset.y.coerceIn(0f, sliderHeight)
                                val fraction = (1f - (tapY / sliderHeight)).coerceIn(0f, 1f)
                                val newLevel = (minLevel + (maxLevel - minLevel) * fraction).toInt()
                                currentLevel = newLevel.coerceIn(minLevel, maxLevel)
                                latestLevelChange(currentLevel)
                            }
                        },
                        onDragEnd = { 
                            isDragging = false
                            latestLevelChange(currentLevel)
                        },
                        onDragCancel = {
                            isDragging = false
                            latestLevelChange(currentLevel)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (sliderHeight > 0f) {
                                val currentY = change.position.y.coerceIn(0f, sliderHeight)
                                val fraction = (1f - (currentY / sliderHeight)).coerceIn(0f, 1f)
                                val newLevel = (minLevel + (maxLevel - minLevel) * fraction).toInt()
                                currentLevel = newLevel.coerceIn(minLevel, maxLevel)
                                latestLevelChange(currentLevel)
                            }
                        }
                    )
                }
                .pointerInput(minLevel, maxLevel) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            if (sliderHeight > 0f) {
                                val tapY = tapOffset.y.coerceIn(0f, sliderHeight)
                                val fraction = (1f - (tapY / sliderHeight)).coerceIn(0f, 1f)
                                val newLevel = (minLevel + (maxLevel - minLevel) * fraction).toInt()
                                currentLevel = newLevel.coerceIn(minLevel, maxLevel)
                                latestLevelChange(currentLevel)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Background track (Glass canal)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(glassColors.textColor.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(5.dp))
            )
            
            // Active track (gradient growth from bottom)
            val activeHeight = 220.dp * normalizedLevel
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(activeHeight)
                    .width(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                glassColors.accentColor.copy(alpha = 0.95f),
                                glassColors.accentColor.copy(alpha = 0.4f)
                            )
                        )
                    )
            )
            
            // Thumb
            val thumbPosition = 220.dp * (1f - normalizedLevel)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbPosition - 18.dp)
                    .size(36.dp)
                    .scale(thumbScale)
                    .shadow(
                        elevation = if (isDragging) 12.dp else 4.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = glassColors.accentColor,
                        spotColor = glassColors.accentColor
                    )
                    .background(Color.White, CircleShape)
                    .border(2.dp, glassColors.accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Inner indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(glassColors.accentColor, CircleShape)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Level value label
        val displayValue = (currentLevel / 100)
        Text(
            text = "${if (displayValue >= 0) "+" else ""}$displayValue",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = glassColors.textColor,
            fontSize = 12.sp
        )
        TextButton(onClick = { latestLevelChange(0) }, enabled = currentLevel != 0,
            contentPadding = PaddingValues(0.dp), modifier = Modifier.width(55.dp)) {
            Icon(Icons.Rounded.Refresh, "Reset ${formatFrequency(centerFreq)} band", modifier = Modifier.size(14.dp))
            Text("Reset", fontSize = 10.sp)
        }
    }
}

@Composable
fun BassBoostSection(equalizerManager: EqualizerManager, bassBoost: Int) {
    EffectStrengthSection("Bass Boost", Icons.Rounded.VolumeUp, bassBoost,
        equalizerManager::setBassBoost, { equalizerManager.setBassBoost(0) })
}

@Composable
fun VirtualizerSection(equalizerManager: EqualizerManager, virtualizer: Int) {
    EffectStrengthSection("Virtualizer", Icons.Rounded.SurroundSound, virtualizer,
        equalizerManager::setVirtualizer, { equalizerManager.setVirtualizer(0) })
}

@Composable
private fun EffectStrengthSection(title: String, icon: ImageVector, value: Int, onChange: (Int) -> Unit, onReset: () -> Unit) {
    val colors = glassCardColors()
    Column(
        modifier = Modifier.fillMaxWidth().glassmorphic(
            shape = RoundedCornerShape(20.dp), backgroundColor = colors.backgroundColor, borderColor = colors.borderColor
        ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EffectSectionHeader(title, icon, value != 0, onReset)
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) }, valueRange = 0f..1000f,
            colors = SliderDefaults.colors(thumbColor = colors.accentColor, activeTrackColor = colors.accentColor,
                inactiveTrackColor = colors.textColor.copy(alpha = 0.08f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Off", style = MaterialTheme.typography.labelSmall, color = colors.subTextColor)
            Text("${value / 10}%", style = MaterialTheme.typography.labelSmall, color = colors.subTextColor)
        }
    }
}

@Composable
private fun EffectSectionHeader(title: String, icon: ImageVector, canReset: Boolean, onReset: () -> Unit) {
    val colors = glassCardColors()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.accentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium, color = colors.textColor)
        TextButton(onClick = onReset, enabled = canReset) {
            Icon(Icons.Rounded.Refresh, "Reset $title", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Reset")
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
    val glassColors = glassCardColors()

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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EffectSectionHeader("Reverb", Icons.Rounded.Waves,
                currentPreset != android.media.audiofx.PresetReverb.PRESET_NONE.toInt(),
                { equalizerManager.setReverbPreset(android.media.audiofx.PresetReverb.PRESET_NONE.toInt()) })
            reverbPresets.forEach { (name, preset) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { equalizerManager.setReverbPreset(preset) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentPreset == preset,
                        onClick = { equalizerManager.setReverbPreset(preset) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = glassColors.accentColor,
                            unselectedColor = glassColors.subTextColor
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassColors.textColor
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

