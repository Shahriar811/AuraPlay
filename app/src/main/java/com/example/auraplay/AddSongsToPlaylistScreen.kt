package com.example.auraplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                title = { 
                    Text(
                        "Add Songs",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack, 
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val idsToAdd = selectedSongIds.filter { it.value }.keys.toList()
                            viewModel.addMultipleSongsToPlaylist(playlistId, idsToAdd)
                            navController.popBackStack()
                        }
                    ) {
                        Icon(
                            Icons.Rounded.Done, 
                            contentDescription = "Save",
                            tint = MaterialTheme.colorScheme.primary
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
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(allSongs) { song ->
                val isAlreadyInPlaylist = existingSongIds.contains(song.id)
                val isSelected = selectedSongIds[song.id] ?: false

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isAlreadyInPlaylist) {
                            if (!isAlreadyInPlaylist) {
                                selectedSongIds[song.id] = !isSelected
                            }
                        }
                        .padding(horizontal = 8.dp)
                ) {
                    Checkbox(
                        checked = isAlreadyInPlaylist || isSelected,
                        onCheckedChange = {
                            if (!isAlreadyInPlaylist) {
                                selectedSongIds[song.id] = it
                            }
                        },
                        enabled = !isAlreadyInPlaylist,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    SongListItem(
                        song = song, 
                        onSongSelected = {
                            if (!isAlreadyInPlaylist) {
                                selectedSongIds[song.id] = !isSelected
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}