package com.example.auraplay.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(playlistWithSongs?.playlist?.name ?: "Playlist") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("add_songs/$playlistId") }) {
                Icon(Icons.Default.Add, contentDescription = "Add Songs")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            playlistWithSongs?.let { playlistData ->
                items(playlistData.songs) { song ->
                    SongListItem(
                        song = song,
                        onSongSelected = { onSongSelected(song) },
                        trailingContent = {
                            IconButton(onClick = {
                                viewModel.removeSongFromPlaylist(song, playlistData.playlist)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove from playlist")
                            }
                        }
                    )
                }
            }
        }
    }
}