package com.example.auraplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
fun PlaylistDetailsScreen(
    playlistId: Long,
    navController: NavController,
    viewModel: MainViewModel,
    onSongSelected: (Song) -> Unit
) {
    val playlistWithSongs by viewModel.getPlaylistWithSongs(playlistId).collectAsState(initial = null)
    val glassColors = glassCardColors()

    Scaffold(
        containerColor = Color.Transparent, // Allow background to show
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        playlistWithSongs?.playlist?.name ?: "Playlist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = glassColors.textColor
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack, 
                            contentDescription = "Back",
                            tint = glassColors.textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_songs/$playlistId") },
                shape = CircleShape,
                containerColor = glassColors.accentColor,
                modifier = Modifier.padding(bottom = 80.dp) // Offset above bottom floating dock
            ) {
                Icon(
                    Icons.Rounded.Add, 
                    contentDescription = "Add Songs",
                    tint = Color.White
                )
            }
        }
    ) { padding ->
        val songs = playlistWithSongs?.songs ?: emptyList()
        if (songs.isEmpty()) {
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
                        imageVector = Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = glassColors.textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "No songs in this playlist yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = glassColors.subTextColor
                    )
                    Text(
                        "Tap the '+' button below to add songs",
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                playlistWithSongs?.let { playlistData ->
                    items(
                        items = playlistData.songs,
                        key = { it.id },
                        contentType = { "song" }
                    ) { song ->
                        SongListItem(
                            song = song,
                            onSongSelected = { onSongSelected(song) },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        viewModel.removeSongFromPlaylist(song, playlistData.playlist)
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete, 
                                        contentDescription = "Remove from playlist",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}