package com.example.auraplay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.auraplay.MainViewModel
import com.example.auraplay.data.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(navController: NavController, viewModel: MainViewModel) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }


    Scaffold(
        topBar = { TopAppBar(title = { Text("Playlists") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create Playlist")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(playlists) { playlist ->
                PlaylistItem(
                    playlist = playlist,
                    onClick = { navController.navigate("playlist_details/${playlist.playlistId}") },
                    onRename = { playlistToRename = it },
                    onDelete = { playlistToDelete = it }
                )
            }
        }

        if (showCreateDialog) {
            CreateRenameDialog(
                title = "New Playlist",
                onDismiss = { showCreateDialog = false },
                onConfirm = { name ->
                    viewModel.createPlaylist(name)
                    showCreateDialog = false
                }
            )
        }

        playlistToRename?.let { playlist ->
            CreateRenameDialog(
                title = "Rename Playlist",
                initialValue = playlist.name,
                onDismiss = { playlistToRename = null },
                onConfirm = { newName ->
                    viewModel.renamePlaylist(playlist, newName)
                    playlistToRename = null
                }
            )
        }

        playlistToDelete?.let { playlist ->
            AlertDialog(
                onDismissRequest = { playlistToDelete = null },
                title = { Text("Delete Playlist") },
                text = { Text("Are you sure you want to delete '${playlist.name}'?")},
                confirmButton = {
                    Button(onClick = {
                        viewModel.deletePlaylist(playlist)
                        playlistToDelete = null
                    }) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { playlistToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun PlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(playlist.name, fontWeight = FontWeight.SemiBold) },
        modifier = Modifier.clickable(onClick = onClick),
        trailingContent = {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        onRename(playlist)
                        showMenu = false
                    })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        onDelete(playlist)
                        showMenu = false
                    })
                }
            }
        }
    )
}

@Composable
fun CreateRenameDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Playlist Name") }
            )
        },
        confirmButton = {
            Button(onClick = {
                if (text.isNotBlank()) {
                    onConfirm(text)
                }
            }) { Text("Confirm") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
