package com.example.auraplay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.auraplay.ui.glassCardColors
import com.example.auraplay.ui.glassmorphic
import com.example.auraplay.ui.resolveAccentColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    val darkTheme by viewModel.darkTheme.collectAsState()
    val accentTheme by viewModel.accentTheme.collectAsState()
    val isPureBlack by viewModel.pureBlack.collectAsState()
    val filterShortAudio by viewModel.filterShortAudio.collectAsState()

    val currentAccent = resolveAccentColor(accentTheme)
    val glassColors = glassCardColors(
        customAccent = currentAccent,
        isPureBlack = isPureBlack
    )

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
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
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Theme Section
            SettingsCategoryTitle("Appearance & Theme", glassColors.subTextColor)

            // Dark Theme Switch
            SettingsToggleCard(
                icon = Icons.Rounded.DarkMode,
                title = "Dark Theme",
                subtitle = "Switch between light and dark mode",
                isChecked = darkTheme,
                onCheckedChange = { viewModel.toggleTheme() },
                glassColors = glassColors
            )

            // Pure Black AMOLED mode
            if (darkTheme) {
                SettingsToggleCard(
                    icon = Icons.Rounded.Contrast,
                    title = "Pure Black (AMOLED)",
                    subtitle = "Deep true blacks for OLED battery savings",
                    isChecked = isPureBlack,
                    onCheckedChange = { viewModel.setPureBlack(it) },
                    glassColors = glassColors
                )
            }

            // Accent Palette Theme Picker
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassmorphic(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = glassColors.backgroundColor,
                        borderColor = glassColors.borderColor
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .glassmorphic(
                                    shape = RoundedCornerShape(12.dp),
                                    backgroundColor = glassColors.accentColor.copy(alpha = 0.15f),
                                    borderColor = glassColors.accentColor.copy(alpha = 0.35f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = glassColors.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                "Aura Accent Palette",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = glassColors.textColor
                            )
                            Text(
                                "Choose your signature aesthetic aura",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassColors.subTextColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val palettes = listOf(
                        "DYNAMIC" to ("Dynamic Aura" to Color(0xFFC084FC)),
                        "PURPLE" to ("Amethyst" to Color(0xFFC084FC)),
                        "CYAN" to ("Cyber Cyan" to Color(0xFF38BDF8)),
                        "SUNSET" to ("Sunset Coral" to Color(0xFFFB923C)),
                        "EMERALD" to ("Emerald" to Color(0xFF34D399)),
                        "GOLD" to ("Golden Aura" to Color(0xFFFBBF24))
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(palettes) { (key, info) ->
                            val (name, color) = info
                            val isSelected = accentTheme.equals(key, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) color.copy(alpha = 0.25f) else glassColors.backgroundColor.copy(alpha = 0.3f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) color else glassColors.borderColor,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { viewModel.setAccentTheme(key) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) color else glassColors.textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Audio & Playback Section
            SettingsCategoryTitle("Audio & Playback", glassColors.subTextColor)

            // Equalizer Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassmorphic(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = glassColors.backgroundColor,
                        borderColor = glassColors.borderColor
                    )
                    .clickable { navController.navigate("equalizer") }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .glassmorphic(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = glassColors.accentColor.copy(alpha = 0.15f),
                                borderColor = glassColors.accentColor.copy(alpha = 0.35f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = glassColors.accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Equalizer & Sound Effects",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = glassColors.textColor
                        )
                        Text(
                            "Bass Boost, Virtualizer & 5-Band EQ",
                            style = MaterialTheme.typography.bodySmall,
                            color = glassColors.subTextColor
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = glassColors.subTextColor
                    )
                }
            }

            // Filter Short Audio Clips (< 30s)
            SettingsToggleCard(
                icon = Icons.Rounded.FilterAlt,
                title = "Filter Short Audio Clips",
                subtitle = "Exclude voice notes and ringtones (< 30 seconds)",
                isChecked = filterShortAudio,
                onCheckedChange = { viewModel.setFilterShortAudio(it) },
                glassColors = glassColors
            )

            // Library Rescan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassmorphic(
                        shape = RoundedCornerShape(20.dp),
                        backgroundColor = glassColors.backgroundColor,
                        borderColor = glassColors.borderColor
                    )
                    .clickable { viewModel.refreshSongs() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .glassmorphic(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = glassColors.accentColor.copy(alpha = 0.15f),
                                borderColor = glassColors.accentColor.copy(alpha = 0.35f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = glassColors.accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Rescan Music Library",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = glassColors.textColor
                        )
                        Text(
                            "Scan device storage for newly added audio files",
                            style = MaterialTheme.typography.bodySmall,
                            color = glassColors.subTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Credits Section
            CreditsSection(glassColors)

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsCategoryTitle(title: String, color: Color) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

@Composable
fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    glassColors: com.example.auraplay.ui.GlassCardColors
) {
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
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .glassmorphic(
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = glassColors.accentColor.copy(alpha = 0.15f),
                        borderColor = glassColors.accentColor.copy(alpha = 0.35f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = glassColors.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = glassColors.textColor
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = glassColors.subTextColor
                )
            }
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = glassColors.accentColor,
                    checkedTrackColor = glassColors.accentColor.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun CreditsSection(glassColors: com.example.auraplay.ui.GlassCardColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphic(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = glassColors.backgroundColor,
                borderColor = glassColors.borderColor
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .glassmorphic(
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = glassColors.accentColor.copy(alpha = 0.15f),
                            borderColor = glassColors.accentColor.copy(alpha = 0.35f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = glassColors.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "AuraPlay Music",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = glassColors.textColor
                    )
                    Text(
                        "Version 2.0 • Ultra Edition",
                        fontSize = 12.sp,
                        color = glassColors.subTextColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Developer: Md. Shahriar Hossain",
                style = MaterialTheme.typography.bodyMedium,
                color = glassColors.textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Mail: mdshahriarhossain08@gmail.com",
                style = MaterialTheme.typography.bodyMedium,
                color = glassColors.subTextColor
            )
        }
    }
}