package com.example.auraplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val glassColors = glassCardColors()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Add Songs",
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
                actions = {
                    val selectedCount = selectedSongIds.count { it.value }
                    if (selectedCount > 0) {
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
                                tint = glassColors.accentColor
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = allSongs,
                key = { it.id },
                contentType = { "song" }
            ) { song ->
                val isAlreadyInPlaylist = existingSongIds.contains(song.id)
                val isSelected = selectedSongIds[song.id] ?: false

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassmorphic(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = if (isSelected) glassColors.accentColor.copy(alpha = 0.15f) else glassColors.backgroundColor,
                            borderColor = if (isSelected) glassColors.accentColor.copy(alpha = 0.4f) else glassColors.borderColor
                        )
                        .clickable(enabled = !isAlreadyInPlaylist) {
                            selectedSongIds[song.id] = !isSelected
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 12.dp)
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
                                checkedColor = glassColors.accentColor,
                                uncheckedColor = glassColors.subTextColor,
                                disabledCheckedColor = glassColors.accentColor.copy(alpha = 0.5f)
                            )
                        )
                        SongListItem(
                            song = song, 
                            onSongSelected = {
                                if (!isAlreadyInPlaylist) {
                                    selectedSongIds[song.id] = !isSelected
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(0.dp)
                        )
                    }
                }
            }
        }
    }
}