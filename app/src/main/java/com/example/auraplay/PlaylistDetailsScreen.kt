package com.example.auraplay.ui

import com.example.auraplay.ui.MotionIconButton as IconButton
import com.example.auraplay.ui.MotionFloatingActionButton as FloatingActionButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.auraplay.MainViewModel
import com.example.auraplay.data.Song
import com.example.auraplay.data.filterBySongQuery
import com.example.auraplay.features.FeatureSongMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailsScreen(
    playlistId: Long,
    navController: NavController,
    viewModel: MainViewModel,
    onSongSelected: (Song) -> Unit
) {
    val playlistWithSongs by remember(playlistId) { viewModel.getPlaylistWithSongs(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    val playlistData = playlistWithSongs
    val songs = playlistData?.songs.orEmpty()
    val glassColors = glassCardColors()
    var showSearch by rememberSaveable(playlistId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(playlistId) { mutableStateOf("") }
    val visibleSongs = remember(songs, searchQuery) { songs.filterBySongQuery(searchQuery.trim()) }
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            playlistData?.playlist?.name ?: "Playlist",
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
                        if (!showSearch) {
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = "Search playlist",
                                    tint = glassColors.textColor
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
                if (showSearch) {
                    LibrarySearchField(
                        query = searchQuery,
                        onQueryChange = { query ->
                            searchQuery = query
                            listState.requestScrollToItem(0)
                        },
                        placeholder = "Search songs in playlist",
                        onClose = {
                            if (searchQuery.isNotBlank()) listState.requestScrollToItem(0)
                            searchQuery = ""
                            showSearch = false
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("add_songs/$playlistId") },
                shape = CircleShape,
                containerColor = glassColors.accentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = "Add Songs",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .consumeWindowInsets(padding)
            .imePadding()
        if (playlistData == null || visibleSongs.isEmpty()) {
            val emptyPlaylist = songs.isEmpty()
            Box(
                modifier = contentModifier,
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (emptyPlaylist) Icons.Rounded.PlaylistPlay else Icons.Rounded.Search,
                        contentDescription = null,
                        tint = glassColors.textColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        if (emptyPlaylist) "No songs in this playlist yet" else "No matching songs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = glassColors.subTextColor
                    )
                    Text(
                        if (emptyPlaylist) "Tap the '+' button below to add songs" else "Try another title, artist, or album.",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassColors.subTextColor.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = contentModifier,
                contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = visibleSongs,
                    key = { it.id },
                    contentType = { "song" }
                ) { song ->
                    SongListItem(
                        song = song,
                        onSongSelected = { onSongSelected(song) },
                        trailingContent = {
                            Row {
                                FeatureSongMenu(song, viewModel)
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
                        }
                    )
                }
            }
        }
    }
}
