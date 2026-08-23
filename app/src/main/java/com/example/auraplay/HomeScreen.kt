package com.example.auraplay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    val glassColors = glassCardColors()

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
        containerColor = Color.Transparent, // Allow background to show
        topBar = {
            Column(modifier = Modifier.background(Color.Transparent)) {
                TopAppBar(
                    title = { 
                        Text(
                            "AuraPlay", 
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = glassColors.textColor
                        ) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = glassColors.textColor
                    ),
                    actions = {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Sort, 
                                contentDescription = "Sort",
                                tint = glassColors.textColor
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Title") },
                                leadingIcon = { Icon(Icons.Rounded.Title, contentDescription = null) },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.TITLE)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist") },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.ARTIST)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date Added") },
                                leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                                onClick = {
                                    viewModel.changeSortOrder(SortOrder.DATE_ADDED)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glassmorphic(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = glassColors.backgroundColor,
                            borderColor = glassColors.borderColor
                        )
                ) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        onSearch = {},
                        active = false,
                        onActiveChange = {},
                        placeholder = { 
                            Text(
                                "Search songs or artists",
                                color = glassColors.subTextColor
                            ) 
                        },
                        leadingIcon = { 
                            Icon(
                                Icons.Rounded.Search, 
                                contentDescription = null,
                                tint = glassColors.subTextColor
                            ) 
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                    Icon(
                                        Icons.Rounded.Close, 
                                        contentDescription = "Clear",
                                        tint = glassColors.subTextColor
                                    )
                                }
                            }
                        },
                        colors = SearchBarDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {}
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = if (searchQuery.isNotEmpty()) Icons.Rounded.SearchOff else Icons.Rounded.MusicOff,
                            contentDescription = null,
                            tint = glassColors.textColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No songs found for '$searchQuery'" else "No music found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = glassColors.subTextColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = songs,
                        key = { it.id },
                        contentType = { "song" }
                    ) { song ->
                        var showSongMenu by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (showSongMenu) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f), label = ""
                        )

                        SongListItem(
                            song = song,
                            onSongSelected = { onPlaySong(song) },
                            modifier = Modifier.scale(scale),
                            trailingContent = {
                                Box {
                                    IconButton(
                                        onClick = { showSongMenu = true },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.MoreVert, 
                                            contentDescription = "More options",
                                            tint = glassColors.subTextColor
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showSongMenu,
                                        onDismissRequest = { showSongMenu = false },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Add to playlist") },
                                            leadingIcon = { 
                                                Icon(
                                                    Icons.Rounded.PlaylistAdd, 
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                songToAddToPlaylist = song
                                                showSongMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (song.isFavorite) "Unfavorite" else "Favorite") },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = if (song.isFavorite) 
                                                        Icons.Rounded.Favorite 
                                                    else 
                                                        Icons.Rounded.FavoriteBorder,
                                                    contentDescription = null,
                                                    tint = if (song.isFavorite) 
                                                        glassColors.accentColor
                                                    else 
                                                        glassColors.textColor
                                                )
                                            },
                                            onClick = {
                                                viewModel.toggleFavorite(song)
                                                showSongMenu = false
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
                                                viewModel.deleteSong(song)
                                                showSongMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
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
    val glassColors = glassCardColors()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Add to Playlist",
                fontWeight = FontWeight.Bold,
                color = glassColors.textColor
            ) 
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists) { playlist ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassmorphic(
                                shape = RoundedCornerShape(12.dp),
                                backgroundColor = glassColors.backgroundColor.copy(alpha = 0.2f),
                                borderColor = glassColors.borderColor
                            )
                            .clickable { onAdd(playlist) }
                    ) {
                        Text(
                            text = playlist.name,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = glassColors.textColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = glassColors.accentColor) 
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = glassColors.backgroundColor,
        modifier = Modifier.border(1.dp, glassColors.borderColor, RoundedCornerShape(24.dp))
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
    val playPauseIconScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f), label = ""
    )
    val glassColors = glassCardColors()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .glassmorphic(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = glassColors.backgroundColor,
                borderColor = glassColors.borderColor
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song.albumArtUri.isNullOrBlank() || song.albumArtUri == "null") {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassColors.accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Generic Music Icon",
                        tint = glassColors.accentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = song.albumArtUri,
                        error = painterResource(id = R.drawable.ic_launcher_foreground)
                    ),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = glassColors.textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist, 
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = glassColors.subTextColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(44.dp)
                    .scale(playPauseIconScale),
                shape = CircleShape,
                color = glassColors.accentColor,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    onSongSelected: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val glassColors = glassCardColors()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glassmorphic(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = glassColors.backgroundColor,
                borderColor = glassColors.borderColor
            )
            .clickable(onClick = onSongSelected)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song.albumArtUri.isNullOrBlank() || song.albumArtUri == "null") {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(glassColors.accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = "Generic Music Icon",
                        tint = glassColors.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Image(
                    painter = rememberAsyncImagePainter(
                        model = song.albumArtUri,
                        error = painterResource(id = R.drawable.ic_launcher_foreground)
                    ),
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title, 
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = glassColors.textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.artist, 
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    color = glassColors.subTextColor
                )
            }
            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingContent()
            }
        }
    }
}