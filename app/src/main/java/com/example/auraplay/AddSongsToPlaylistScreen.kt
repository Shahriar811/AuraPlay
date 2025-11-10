package com.example.auraplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.auraplay.MainViewModel
import com.example.auraplay.data.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistScreen(
    playlistId: Long,
    navController: NavController,
    viewModel: MainViewModel
) {
    val allSongs by viewModel.songs.collectAsState()
    val playlistWithSongs by viewModel.getPlaylistWithSongs(playlistId).collectAsState(initial = null)
    val existingSongIds = remember(playlistWithSongs) {
        playlistWithSongs?.songs?.map { it.id }?.toSet() ?: emptySet()
    }

    val selectedSongIds = remember { mutableStateMapOf<Long, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Songs") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val idsToAdd = selectedSongIds.filter { it.value }.keys.toList()
                        viewModel.addMultipleSongsToPlaylist(playlistId, idsToAdd)
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Done, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(allSongs) { song ->
                val isAlreadyInPlaylist = existingSongIds.contains(song.id)
                val isSelected = selectedSongIds[song.id] ?: false

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !isAlreadyInPlaylist) {
                        if (!isAlreadyInPlaylist) {
                            selectedSongIds[song.id] = !isSelected
                        }
                    }
                ) {
                    Checkbox(
                        checked = isAlreadyInPlaylist || isSelected,
                        onCheckedChange = {
                            if (!isAlreadyInPlaylist) {
                                selectedSongIds[song.id] = it
                            }
                        },
                        enabled = !isAlreadyInPlaylist
                    )
                    // Using the existing SongListItem composable for consistent UI
                    SongListItem(song = song, onSongSelected = {
                        if (!isAlreadyInPlaylist) {
                            selectedSongIds[song.id] = !isSelected
                        }
                    })
                }
            }
        }
    }
}