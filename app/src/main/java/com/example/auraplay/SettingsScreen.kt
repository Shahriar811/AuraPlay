package com.example.auraplay

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import com.example.auraplay.ui.glassCardColors
import com.example.auraplay.ui.glassmorphic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    val darkTheme by viewModel.darkTheme.collectAsState()
    val glassColors = glassCardColors()

    Scaffold(
        containerColor = Color.Transparent, // Allow background to show
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dark Theme Section
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
                            .size(48.dp)
                            .glassmorphic(
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = glassColors.accentColor.copy(alpha = 0.15f),
                                borderColor = glassColors.accentColor.copy(alpha = 0.35f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = glassColors.accentColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Dark Theme",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = glassColors.textColor
                        )
                        Text(
                            "Switch between light and dark mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = glassColors.subTextColor
                        )
                    }
                    Switch(
                        checked = darkTheme, 
                        onCheckedChange = { viewModel.toggleTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = glassColors.accentColor,
                            checkedTrackColor = glassColors.accentColor.copy(alpha = 0.5f)
                        )
                    )
                }
            }
            
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
                            .size(48.dp)
                            .glassmorphic(
                                shape = RoundedCornerShape(14.dp),
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
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Equalizer",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            color = glassColors.textColor
                        )
                        Text(
                            "Customize audio with equalizer",
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
            
            Spacer(modifier = Modifier.height(8.dp))
            CreditsSection()
        }
    }
}

@Composable
fun CreditsSection() {
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
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .glassmorphic(
                            shape = RoundedCornerShape(14.dp),
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
                Text(
                    "Credits", 
                    fontSize = 18.sp, 
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = glassColors.textColor
                )
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