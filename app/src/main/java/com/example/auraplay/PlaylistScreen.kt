package com.example.auraplay.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.example.auraplay.data.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(navController: NavController, viewModel: MainViewModel) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val glassColors = glassCardColors()

    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        containerColor = Color.Transparent, // Allow background to show
        topBar = { 
            TopAppBar(
                title = { 
                    Text(
                        "Playlists",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = glassColors.textColor
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            ) 
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = CircleShape,
                containerColor = glassColors.accentColor,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 12.dp
                ),
                modifier = Modifier.padding(bottom = 80.dp) // Offset above bottom floating dock
            ) {
                Icon(
                    Icons.Rounded.Add, 
                    contentDescription = "Create Playlist",
                    tint = Color.White
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = playlists,
                key = { it.playlistId },
                contentType = { "playlist" }
            ) { playlist ->
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
                title = { 
                    Text(
                        "Delete Playlist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = glassColors.textColor
                    ) 
                },
                text = { 
                    Text(
                        "Are you sure you want to delete '${playlist.name}'?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = glassColors.textColor
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deletePlaylist(playlist)
                            playlistToDelete = null
                        }
                    ) { 
                        Text(
                            "Delete",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        ) 
                    }
                },
                dismissButton = {
                    TextButton(onClick = { playlistToDelete = null }) { 
                        Text("Cancel", color = glassColors.accentColor) 
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = glassColors.backgroundColor,
                modifier = Modifier.border(1.dp, glassColors.borderColor, RoundedCornerShape(24.dp))
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
    val glassColors = glassCardColors()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphic(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = glassColors.backgroundColor,
                borderColor = glassColors.borderColor
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .glassmorphic(
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = glassColors.accentColor.copy(alpha = 0.2f),
                        borderColor = glassColors.accentColor.copy(alpha = 0.4f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = glassColors.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name, 
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge,
                    color = glassColors.textColor
                )
            }
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Rounded.MoreVert, 
                    contentDescription = "More options",
                    tint = glassColors.subTextColor
                )
            }
        }
    }
    
    DropdownMenu(
        expanded = showMenu, 
        onDismissRequest = { showMenu = false },
        shape = RoundedCornerShape(12.dp)
    ) {
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
            onClick = {
                onRename(playlist)
                showMenu = false
            }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            leadingIcon = { 
                Icon(
                    Icons.Rounded.Delete, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                ) 
            },
            onClick = {
                onDelete(playlist)
                showMenu = false
            }
        )
    }
}

@Composable
fun CreateRenameDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val glassColors = glassCardColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = glassColors.textColor
            ) 
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Playlist Name") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = glassColors.accentColor,
                    unfocusedBorderColor = glassColors.borderColor,
                    focusedLabelColor = glassColors.accentColor,
                    unfocusedLabelColor = glassColors.subTextColor
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text)
                    }
                }
            ) { 
                Text("Confirm", color = glassColors.accentColor, fontWeight = FontWeight.Bold) 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = glassColors.subTextColor) 
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = glassColors.backgroundColor,
        modifier = Modifier.border(1.dp, glassColors.borderColor, RoundedCornerShape(24.dp))
    )
}
