package com.example.auraplay.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.auraplay.MainViewModel
import com.example.auraplay.R
import com.example.auraplay.SortOrder
import com.example.auraplay.data.Playlist
import com.example.auraplay.data.Song
import com.example.auraplay.service.PlayerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel,
    playerState: PlayerState,
    onPlaySong: (Song) -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    // State for the "Add to Playlist" dialog
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    val playlists by viewModel.playlists.collectAsState()

    // Show dialog when a song is selected
    songToAddToPlaylist?.let { song ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { songToAddToPlaylist = null },
            onAdd = { playlist ->
                viewModel.addSongToPlaylist(song, playlist)
                songToAddToPlaylist = null
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("AuraPlay") },
                    actions = {
                        // Simplified TopAppBar
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Title") },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.TITLE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist") },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.ARTIST)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date Added") },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.DATE_ADDED)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                )
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onSearch = {},
                    active = false,
                    onActiveChange = {},
                    placeholder = { Text("Search songs or artists") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {}
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(songs) { song ->
                    var showSongMenu by remember { mutableStateOf(false) } // Each item has its own menu state

                    SongListItem(
                        song = song,
                        onSongSelected = { onPlaySong(song) },
                        // Add the context menu
                        trailingContent = {
                            Box {
                                IconButton(onClick = { showSongMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                }
                                DropdownMenu(
                                    expanded = showSongMenu,
                                    onDismissRequest = { showSongMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add to playlist") },
                                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null)},
                                        onClick = {
                                            songToAddToPlaylist = song
                                            showSongMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (song.isFavorite) "Unfavorite" else "Favorite") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            viewModel.toggleFavorite(song)
                                            showSongMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }

            playerState.currentSong?.let { song ->
                MiniPlayer(
                    song = song,
                    isPlaying = playerState.isPlaying,
                    onPlayPause = onTogglePlayPause,
                    onClick = { navController.navigate("player") },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAdd: (Playlist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            LazyColumn {
                items(playlists) { playlist ->
                    Text(
                        text = playlist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(playlist) }
                            .padding(16.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = song.albumArtUri,
                    error = painterResource(id = R.drawable.ic_launcher_foreground)
                ),
                contentDescription = "Album Art",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    onSongSelected: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSongSelected)
            .padding(horizontal = 16.dp, vertical = 12.dp), // Increased vertical padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art removed for a cleaner list, as requested by the UI design
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}