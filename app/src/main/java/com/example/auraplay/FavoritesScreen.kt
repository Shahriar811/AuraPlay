package com.example.auraplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.auraplay.MainViewModel
import com.example.auraplay.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    navController: NavController,
    viewModel: MainViewModel,
    onSongSelected: (Song) -> Unit
) {
    val favoriteSongs by viewModel.favoriteSongs.collectAsState()
    val glassColors = glassCardColors()

    Scaffold(
        containerColor = Color.Transparent, // Allow background to show
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Favorite Songs",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = glassColors.textColor
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        if (favoriteSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Column(
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = glassColors.textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "No favorite songs yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = glassColors.subTextColor
                    )
                    Text(
                        "Tap the heart icon on any song to add it here",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassColors.subTextColor.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = favoriteSongs,
                    key = { it.id },
                    contentType = { "song" }
                ) { song ->
                    SongListItem(
                        song = song,
                        onSongSelected = { onSongSelected(song) },
                        trailingContent = {
                            IconButton(
                                onClick = { viewModel.toggleFavorite(song) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = "Remove from Favorites",
                                    tint = glassColors.accentColor
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}