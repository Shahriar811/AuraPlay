package com.example.auraplay.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.auraplay.MainViewModel
import com.example.auraplay.R
import com.example.auraplay.SortOrder
import com.example.auraplay.data.*
import com.example.auraplay.service.MusicService
import com.example.auraplay.service.PlayerState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val SongItemShape = RoundedCornerShape(16.dp)
private val SongThumbShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel,
    onPlaySong: (Song) -> Unit,
    onPlaySongList: (List<Song>, Song) -> Unit,
    onTogglePlayPause: () -> Unit = { MusicService.togglePlayPause() },
    onNextSong: (() -> Unit)? = { MusicService.seekToNext() }
) {
    val songs by viewModel.songs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()

    // Decoupled active playing song ID: Prevents LazyColumn from recomposing on position ticks
    val activePlayingSongId by remember {
        MusicService.playerState
            .map { if (it.isPlaying) it.currentSong?.id else null }
            .distinctUntilChanged()
    }.collectAsState(initial = null)

    val searchQuery by viewModel.searchQuery.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")

    val glassColors = glassCardColors()

    // State for Drill-Down / Collection Dialog
    var selectedAlbumForDialog by remember { mutableStateOf<AlbumItem?>(null) }
    var selectedArtistForDialog by remember { mutableStateOf<ArtistItem?>(null) }
    var selectedFolderForDialog by remember { mutableStateOf<FolderItem?>(null) }

    // State for the "Add to Playlist" dialog
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    val playlists by viewModel.playlists.collectAsState()

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

    // Drill-down dialogs
    selectedAlbumForDialog?.let { albumItem ->
        val albumSongs by viewModel.getSongsByAlbum(albumItem.album).collectAsState(initial = emptyList())
        CollectionSongsDialog(
            title = albumItem.album,
            subtitle = "${albumItem.artist} • ${albumSongs.size} tracks",
            artworkUri = albumItem.albumArtUri,
            songs = albumSongs,
            onDismiss = { selectedAlbumForDialog = null },
            onPlaySong = { s -> onPlaySongList(albumSongs, s) },
            onPlayAll = { if (albumSongs.isNotEmpty()) onPlaySongList(albumSongs, albumSongs.first()) }
        )
    }

    selectedArtistForDialog?.let { artistItem ->
        val artistSongs by viewModel.getSongsByArtist(artistItem.artist).collectAsState(initial = emptyList())
        CollectionSongsDialog(
            title = artistItem.artist,
            subtitle = "${artistSongs.size} tracks • ${artistItem.albumCount} albums",
            artworkUri = artistSongs.firstOrNull()?.albumArtUri,
            songs = artistSongs,
            onDismiss = { selectedArtistForDialog = null },
            onPlaySong = { s -> onPlaySongList(artistSongs, s) },
            onPlayAll = { if (artistSongs.isNotEmpty()) onPlaySongList(artistSongs, artistSongs.first()) }
        )
    }

    selectedFolderForDialog?.let { folderItem ->
        val folderSongs by viewModel.getSongsByFolder(folderItem.folderPath).collectAsState(initial = emptyList())
        CollectionSongsDialog(
            title = folderItem.folderName,
            subtitle = folderItem.folderPath,
            artworkUri = folderSongs.firstOrNull()?.albumArtUri,
            songs = folderSongs,
            onDismiss = { selectedFolderForDialog = null },
            onPlaySong = { s -> onPlaySongList(folderSongs, s) },
            onPlayAll = { if (folderSongs.isNotEmpty()) onPlaySongList(folderSongs, folderSongs.first()) }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(modifier = Modifier.background(Color.Transparent)) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "AuraPlay",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = glassColors.textColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(glassColors.accentColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${songs.size} tracks",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = glassColors.accentColor
                                )
                            }
                        }
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

                // Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
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
                                "Search songs, albums, or artists",
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

                // Sliding Tabs Pill Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = glassColors.accentColor,
                    edgePadding = 16.dp,
                    divider = {},
                    indicator = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        val count = when (index) {
                            0 -> songs.size
                            1 -> albums.size
                            2 -> artists.size
                            3 -> folders.size
                            else -> 0
                        }

                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp, top = 6.dp, bottom = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) glassColors.accentColor else glassColors.backgroundColor.copy(alpha = 0.3f)
                                )
                                .clickable { selectedTab = index }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$title ($count)",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (isSelected) Color.White else glassColors.textColor
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> {
                    // Songs Tab with "Jump Back In"
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (recentlyPlayed.isNotEmpty() && searchQuery.isBlank()) {
                            item {
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text(
                                        text = "⚡ Recently Played",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = glassColors.textColor,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(recentlyPlayed.take(8)) { song ->
                                            RecentSongCard(
                                                song = song,
                                                onClick = { onPlaySong(song) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (songs.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(300.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicOff,
                                            contentDescription = null,
                                            tint = glassColors.textColor.copy(alpha = 0.3f),
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = if (searchQuery.isNotEmpty()) "No songs found" else "No music found",
                                            color = glassColors.subTextColor
                                        )
                                    }
                                }
                            }
                        } else {
                            items(
                                items = songs,
                                key = { it.id },
                                contentType = { "song" }
                            ) { song ->
                                var showSongMenu by remember { mutableStateOf(false) }

                                SongListItem(
                                    song = song,
                                    isPlaying = activePlayingSongId == song.id,
                                    onSongSelected = { onPlaySong(song) },
                                    glassColors = glassColors,
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
                                            if (showSongMenu) {
                                                DropdownMenu(
                                                    expanded = true,
                                                    onDismissRequest = { showSongMenu = false },
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text("Add to playlist") },
                                                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                                                        onClick = {
                                                            songToAddToPlaylist = song
                                                            showSongMenu = false
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (song.isFavorite) "Unfavorite" else "Favorite") },
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                                contentDescription = null,
                                                                tint = if (song.isFavorite) glassColors.accentColor else glassColors.textColor
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
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Albums Grid Tab
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albums) { albumItem ->
                            AlbumGridCard(
                                album = albumItem,
                                onClick = { selectedAlbumForDialog = albumItem }
                            )
                        }
                    }
                }
                2 -> {
                    // Artists Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(artists) { artistItem ->
                            ArtistListCard(
                                artist = artistItem,
                                onClick = { selectedArtistForDialog = artistItem }
                            )
                        }
                    }
                }
                3 -> {
                    // Folders Tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(folders) { folderItem ->
                            FolderListCard(
                                folder = folderItem,
                                onClick = { selectedFolderForDialog = folderItem }
                            )
                        }
                    }
                }
            }

            // Mini Player Bar (Isolated Flow collection: prevents HomeScreen from recomposing)
            HomeMiniPlayer(
                navController = navController,
                onPlayPause = onTogglePlayPause,
                onNext = onNextSong,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun RecentSongCard(song: Song, onClick: () -> Unit) {
    val glassColors = glassCardColors()
    Box(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(glassColors.backgroundColor)
            .border(1.dp, glassColors.borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column {
            if (song.albumArtUri.isNullOrBlank() || song.albumArtUri == "null") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(114.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(glassColors.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = glassColors.accentColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                val context = LocalContext.current
                val imageRequest = remember(song.albumArtUri) {
                    ImageRequest.Builder(context)
                        .data(song.albumArtUri)
                        .crossfade(false)
                        .size(180, 180)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(114.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = song.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = glassColors.textColor
            )
            Text(
                text = song.artist,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = glassColors.subTextColor
            )
        }
    }
}

@Composable
fun AlbumGridCard(album: AlbumItem, onClick: () -> Unit) {
    val glassColors = glassCardColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(glassColors.backgroundColor)
            .border(1.dp, glassColors.borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Column {
            if (album.albumArtUri.isNullOrBlank() || album.albumArtUri == "null") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(glassColors.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Album,
                        contentDescription = null,
                        tint = glassColors.accentColor,
                        modifier = Modifier.size(44.dp)
                    )
                }
            } else {
                val context = LocalContext.current
                val imageRequest = remember(album.albumArtUri) {
                    ImageRequest.Builder(context)
                        .data(album.albumArtUri)
                        .crossfade(false)
                        .size(240, 240)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = album.album,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = glassColors.textColor
            )
            Text(
                text = "${album.artist} • ${album.songCount} songs",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = glassColors.subTextColor
            )
        }
    }
}

@Composable
fun ArtistListCard(artist: ArtistItem, onClick: () -> Unit) {
    val glassColors = glassCardColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(glassColors.backgroundColor)
            .border(1.dp, glassColors.borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(glassColors.accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = glassColors.accentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.artist,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = glassColors.textColor
                )
                Text(
                    text = "${artist.songCount} songs • ${artist.albumCount} albums",
                    fontSize = 12.sp,
                    color = glassColors.subTextColor
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = glassColors.subTextColor
            )
        }
    }
}

@Composable
fun FolderListCard(folder: FolderItem, onClick: () -> Unit) {
    val glassColors = glassCardColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(glassColors.backgroundColor)
            .border(1.dp, glassColors.borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glassColors.accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = glassColors.accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.folderName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = glassColors.textColor
                )
                Text(
                    text = "${folder.songCount} songs • ${folder.folderPath}",
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = glassColors.subTextColor
                )
            }
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = "Play Folder",
                tint = glassColors.accentColor,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun CollectionSongsDialog(
    title: String,
    subtitle: String,
    artworkUri: String?,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAll: () -> Unit
) {
    val glassColors = glassCardColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!artworkUri.isNullOrBlank() && artworkUri != "null") {
                    Image(
                        painter = rememberAsyncImagePainter(model = artworkUri),
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = glassColors.textColor
                    )
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = glassColors.subTextColor
                    )
                }
            }
        },
        text = {
            Column {
                Button(
                    onClick = {
                        onPlayAll()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = glassColors.accentColor)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All (${songs.size})", fontWeight = FontWeight.Bold)
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(songs) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(glassColors.backgroundColor.copy(alpha = 0.2f))
                                .clickable {
                                    onPlaySong(song)
                                    onDismiss()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = glassColors.textColor
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = glassColors.subTextColor
                                )
                            }
                            Icon(
                                Icons.Rounded.PlayCircleOutline,
                                contentDescription = null,
                                tint = glassColors.accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = glassColors.accentColor)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = glassColors.backgroundColor,
        modifier = Modifier.border(1.dp, glassColors.borderColor, RoundedCornerShape(24.dp))
    )
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
                            .clip(RoundedCornerShape(12.dp))
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
fun HomeMiniPlayer(
    navController: NavController,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val playerState by MusicService.playerState.collectAsState()
    playerState.currentSong?.let { song ->
        MiniPlayer(
            song = song,
            isPlaying = playerState.isPlaying,
            currentPosition = playerState.currentPosition,
            totalDuration = playerState.totalDuration,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onClick = { navController.navigate("player") },
            modifier = modifier
        )
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    currentPosition: Long = 0L,
    totalDuration: Long = 0L,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkTheme = com.example.auraplay.ui.theme.LocalThemeIsDark.current
    val glassColors = glassCardColors()
    val accent = glassColors.accentColor

    val miniPlayerBg = if (darkTheme) {
        Color(0xFF140F28).copy(alpha = 0.90f)
    } else {
        Color.White.copy(alpha = 0.94f)
    }

    val miniPlayerBorder = if (darkTheme) {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.25f),
                accent.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White,
                accent.copy(alpha = 0.50f),
                Color(0xFFDDD6FE).copy(alpha = 0.75f)
            )
        )
    }

    val playPauseIconScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "miniPlayScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                ambientColor = if (darkTheme) Color.Black.copy(alpha = 0.45f) else accent.copy(alpha = 0.20f),
                spotColor = if (darkTheme) Color.Black.copy(alpha = 0.55f) else Color(0xFF1E1035).copy(alpha = 0.22f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        miniPlayerBg,
                        if (darkTheme) Color(0xFF0D0A1C).copy(alpha = 0.94f) else Color(0xFFF5EFFF).copy(alpha = 0.94f)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = miniPlayerBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art with downsampled caching
                if (song.albumArtUri.isNullOrBlank() || song.albumArtUri == "null") {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent.copy(alpha = 0.15f))
                            .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.15f else 0.5f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = "Generic Music Icon",
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUri)
                            .crossfade(true)
                            .size(150, 150)
                            .error(R.drawable.ic_launcher_foreground)
                            .build(),
                        contentDescription = "Album Art",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color.White.copy(alpha = if (darkTheme) 0.15f else 0.5f), RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Song Title & Artist
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = song.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                        color = if (darkTheme) Color(0xFFF8FAFC) else Color(0xFF1E1035)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.artist,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (darkTheme) Color(0xFF94A3B8) else Color(0xFF6B21A8).copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Play / Pause Button with tactile spring bounce and bounded circular ripple
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(playPauseIconScale)
                        .shadow(6.dp, CircleShape, ambientColor = accent, spotColor = accent)
                        .background(accent, CircleShape)
                        .bouncingClickable(
                            shape = CircleShape,
                            scaleDown = 0.88f,
                            onClick = onPlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                }

                // Next track button on Mini Player (UX enhancement)
                if (onNext != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .bouncingClickable(
                                shape = CircleShape,
                                scaleDown = 0.88f,
                                onClick = onNext
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next Track",
                            tint = if (darkTheme) Color(0xFFE2E8F0) else accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Sleek GPU-Drawn Progress Track Indicator (zero layout / measure passes)
            val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(accent.copy(alpha = if (darkTheme) 0.14f else 0.10f))
                    .drawBehind {
                        if (progress > 0f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(accent, accent.copy(alpha = 0.85f))
                                ),
                                size = androidx.compose.ui.geometry.Size(size.width * progress, size.height)
                            )
                        }
                    }
            )
        }
    }
}

/**
 * Animated dancing equalizer waveform for active track
 */
@Composable
fun MiniLiveEqualizer(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "miniEq")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.90f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.20f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(510, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = h1
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = h2
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    scaleY = h3
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
    }
}

@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean = false,
    onSongSelected: () -> Unit,
    modifier: Modifier = Modifier,
    glassColors: GlassCardColors = glassCardColors(),
    trailingContent: (@Composable () -> Unit)? = null
) {
    val itemBg = if (isPlaying) glassColors.accentColor.copy(alpha = 0.16f) else glassColors.backgroundColor
    val itemBorder = if (isPlaying) glassColors.accentColor.copy(alpha = 0.55f) else glassColors.borderColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(SongItemShape)
            .background(itemBg)
            .border(1.dp, itemBorder, SongItemShape)
            .clickable(onClick = onSongSelected)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art Thumbnail with downsampling & zero-crossfade on scroll
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(SongThumbShape)
            ) {
                if (song.albumArtUri.isNullOrBlank() || song.albumArtUri == "null") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(glassColors.accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlaying) {
                            MiniLiveEqualizer(color = glassColors.accentColor)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Generic Music Icon",
                                tint = glassColors.accentColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    val context = LocalContext.current
                    val imageRequest = remember(song.albumArtUri) {
                        ImageRequest.Builder(context)
                            .data(song.albumArtUri)
                            .crossfade(false) // Instant appearance without CPU/GPU alpha blend overhead
                            .size(120, 120) // Exact downsampling
                            .error(R.drawable.ic_launcher_foreground)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            MiniLiveEqualizer(color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = song.title,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isPlaying) glassColors.accentColor else glassColors.textColor
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${song.artist} • ${song.album}",
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