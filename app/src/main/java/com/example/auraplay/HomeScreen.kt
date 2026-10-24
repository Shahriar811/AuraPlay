package com.example.auraplay.ui

import com.example.auraplay.ui.MotionIconButton as IconButton
import com.example.auraplay.ui.MotionButton as Button
import com.example.auraplay.ui.MotionTextButton as TextButton
import com.example.auraplay.ui.MotionFilterChip as FilterChip

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.example.auraplay.MainViewModel
import com.example.auraplay.R
import com.example.auraplay.SortOrder
import com.example.auraplay.data.*
import com.example.auraplay.service.MusicService
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activePlayingSongId by remember {
        MusicService.playerState.map { if (it.isPlaying) it.currentSong?.id else null }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSearch by rememberSaveable { mutableStateOf(searchQuery.isNotBlank()) }
    var showSortMenu by remember { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }
    var collection by remember { mutableStateOf<LibraryCollection?>(null) }
    val selection = rememberSaveable(saver = SongSelectionState.Saver) { SongSelectionState() }
    val scope = rememberCoroutineScope()
    var preparingSelection by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = selection.active) { selection.clear() }
    LaunchedEffect(selection.active) { if (selection.active) { focus.clearFocus(); keyboard?.hide() } }
    val colors = glassCardColors()
    val listState = rememberLazyListState()
    LaunchedEffect(showSearch) { if (showSearch) listState.scrollToItem(0) }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")
    // Subscribe only to the visible collection; grouping runs off the UI thread.
    val albums = if (selectedTab == 1) {
        val entries by viewModel.albums.collectAsStateWithLifecycle()
        remember(entries) { entries.chunked(2) }
    } else emptyList()
    val artists = if (selectedTab == 2) {
        val entries by viewModel.artists.collectAsStateWithLifecycle()
        entries
    } else emptyList()
    val folders = if (selectedTab == 3) {
        val entries by viewModel.folders.collectAsStateWithLifecycle()
        entries
    } else emptyList()
    val now = LocalDateTime.now()
    val greeting = when (now.hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }

    songToAdd?.let { song ->
        AddToPlaylistDialog(playlists, { songToAdd = null }) {
            viewModel.addSongToPlaylist(song, it)
            songToAdd = null
        }
    }
    collection?.let { selected ->
        // Resolve from the latest library so deletions never leave a stale playable collection.
        val collectionSongs by remember(selected) {
            viewModel.getCollectionSongs(selected)
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        CollectionSongsDialog(
            title = selected.name,
            subtitle = "${collectionSongs.size} songs",
            artworkUri = collectionSongs.firstOrNull()?.albumArtUri,
            songs = collectionSongs,
            onDismiss = { collection = null },
            onPlaySong = { onPlaySongList(collectionSongs, it) },
            onPlayAll = { collectionSongs.firstOrNull()?.let { onPlaySongList(collectionSongs, it) } }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                if (selection.active) {
                    val allShownSelected = songs.isNotEmpty() && songs.all { it.id in selection.ids }
                    Column(Modifier.fillMaxWidth().background(colors.backgroundColor).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = selection::clear) { Icon(Icons.Rounded.Close, "Cancel song selection") }
                            Text("${selection.ids.size} selected", modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge, color = colors.textColor,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { showSearch = !showSearch; if (!showSearch) viewModel.onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Search, "Search library", tint = colors.textColor)
                            }
                            TextButton(onClick = { selection.toggleAll(songs) }, enabled = songs.isNotEmpty()) {
                                Text(if (allShownSelected) "Deselect all" else "Select all")
                            }
                        }
                        Button(onClick = {
                            val ids = selection.ids
                            val library = viewModel.visibleLibrary.value
                            preparingSelection = true
                            scope.launch {
                                try {
                                    val tracks = withContext(Dispatchers.Default) { selectedSongs(ids, library) }
                                    if (selection.active && selection.ids == ids) {
                                        if (tracks.isNotEmpty()) {
                                            selection.clear()
                                            onPlaySongList(tracks, tracks.first())
                                        } else {
                                            selection.clear()
                                            viewModel.showFeatureMessage("The selected songs are no longer available.")
                                        }
                                    }
                                } finally { preparingSelection = false }
                            }
                        }, enabled = selection.ids.isNotEmpty() && !preparingSelection,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play selected (${selection.ids.size})")
                        }
                    }
                } else Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(Brush.horizontalGradient(listOf(colors.accentColor.copy(alpha = 0.13f), colors.backgroundColor)))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Home", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-1).sp, color = colors.textColor, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) viewModel.onSearchQueryChange("") }) {
                        Icon(Icons.Rounded.Search, "Search library", tint = colors.textColor)
                    }
                    IconButton(onClick = { viewModel.refreshSongs() }, enabled = !isScanning) {
                        if (isScanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.accentColor)
                        else Icon(Icons.Rounded.Refresh, "Refresh library", tint = colors.textColor)
                    }
                    IconButton(onClick = { navController.navigate("settings") },
                        modifier = Modifier.background(colors.accentColor.copy(alpha = 0.16f), CircleShape)) {
                        Icon(Icons.Rounded.PersonOutline, "Settings", tint = colors.accentColor)
                    }
                }
                if (showSearch) LibrarySearchField(searchQuery, viewModel::onSearchQueryChange) {
                    viewModel.onSearchQueryChange("")
                    showSearch = false
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("library-list"),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (searchQuery.isBlank() && !showSearch) {
                item(key = "greeting") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(greeting, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.textColor)
                        Spacer(Modifier.height(4.dp))
                        Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                            color = colors.subTextColor, fontSize = 14.sp)
                    }
                }
                item(key = "daily-mix") {
                    DailyMixCard(songs, onPlay = {
                        val mix = songs.shuffled()
                        mix.firstOrNull()?.let { onPlaySongList(mix, it) }
                    })
                }
                item(key = "quick-access") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(26.dp)).background(colors.backgroundColor).padding(16.dp)) {
                        Text("Quick access", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = colors.textColor)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickAccessCard("Liked Songs", "Your collection", Icons.Rounded.Favorite,
                                colors.accentColor, Modifier.weight(1f)) { navController.navigate("favorites") }
                            QuickAccessCard("On repeat", "Your listening", Icons.Rounded.BarChart,
                                MaterialTheme.colorScheme.tertiary, Modifier.weight(1f)) { navController.navigate("stats") }
                        }
                    }
                }
                if (recentlyPlayed.isNotEmpty()) item(key = "recent") {
                    Column {
                        Text("Jump back in", color = colors.textColor, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recentlyPlayed.take(8), key = { it.id }) { song ->
                                RecentSongCard(song) { onPlaySongList(recentlyPlayed, song) }
                            }
                        }
                    }
                }
            }
            item(key = "library-header") {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your library", color = colors.textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("${songs.size} songs on this device", color = colors.subTextColor, fontSize = 12.sp)
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Rounded.Sort, "Sort library", tint = colors.subTextColor) }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(text = { Text(when (order) {
                                    SortOrder.TITLE -> "Title"
                                    SortOrder.ARTIST -> "Artist"
                                    SortOrder.DATE_ADDED -> "Recently added"
                                }) }, onClick = { viewModel.changeSortOrder(order); showSortMenu = false })
                            }
                        }
                    }
                    if (selectedTab == 0) IconButton(onClick = selection::start, enabled = songs.isNotEmpty()) {
                        Icon(Icons.Rounded.Checklist, "Select songs", tint = colors.subTextColor)
                    }
                }
            }
            item(key = "library-tabs") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tabs.size) { index ->
                        FilterChip(selected = selectedTab == index, onClick = { selectedTab = index; if (index != 0) selection.clear() },
                            label = { Text(tabs[index]) }, shape = RoundedCornerShape(20.dp))
                    }
                }
            }
            scanError?.let { error -> item(key = "scan-error") {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp))
            } }
            if (songs.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.MusicNote, null, tint = colors.accentColor, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(if (isScanning) "Finding your music…" else if (searchQuery.isNotBlank()) "No matching music" else "Your music lives here",
                        color = colors.textColor, fontWeight = FontWeight.Bold)
                    Text(if (searchQuery.isNotBlank()) "Try another title, album, or artist." else "Add audio to your device, then refresh your library.",
                        color = colors.subTextColor, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            when (selectedTab) {
                0 -> items(songs, key = { "song-${it.id}" }, contentType = { "song" }) { song ->
                    var showMenu by remember { mutableStateOf(false) }
                    SongListItem(song, activePlayingSongId == song.id,
                        { if (selection.active) selection.toggle(song.id) else onPlaySong(song) },
                        modifier = Modifier.testTag("song-${song.id}"), glassColors = colors,
                        onLongClick = { selection.toggle(song.id) },
                        selected = if (selection.active) song.id in selection.ids else null,
                        trailingContent = {
                            if (selection.active) {
                                Checkbox(checked = song.id in selection.ids, onCheckedChange = null)
                            } else Box {
                                // Lazy rows use the standard ripple; no per-row press-animation job/layer.
                                androidx.compose.material3.IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, "Song options", tint = colors.subTextColor) }
                                if (showMenu) DropdownMenu(expanded = true, onDismissRequest = { showMenu = false }) {
                                    DropdownMenuItem(text = { Text("Play next") }, onClick = { viewModel.enqueue(song, true); showMenu = false })
                                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = { viewModel.enqueue(song, false); showMenu = false })
                                    DropdownMenuItem(text = { Text("Edit song information") }, onClick = { navController.navigate("song_edit/${song.id}"); showMenu = false })
                                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { songToAdd = song; showMenu = false })
                                    DropdownMenuItem(text = { Text(if (song.isFavorite) "Remove from liked songs" else "Like song") },
                                        onClick = { viewModel.toggleFavorite(song); showMenu = false })
                                    DropdownMenuItem(text = { Text("Delete from device") }, onClick = { viewModel.deleteSong(song); showMenu = false })
                                }
                            }
                        })
                }
                1 -> {
                    val albumRows = albums
                    items(albumRows.size, contentType = { "album-row" }) { index ->
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            albumRows[index].forEach { album -> Box(Modifier.weight(1f)) {
                                AlbumGridCard(album) { collection = LibraryCollection("album", album.album, album.albumId.toString()) }
                            } }
                            if (albumRows[index].size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                2 -> items(artists, key = { "artist-${it.artist}" }, contentType = { "artist" }) { artist ->
                    ArtistListCard(artist) { collection = LibraryCollection("artist", artist.artist, artist.artist) }
                }
                3 -> items(folders, key = { "folder-${it.folderPath}" }, contentType = { "folder" }) { folder ->
                    FolderListCard(folder) { collection = LibraryCollection("folder", folder.folderName, folder.folderPath) }
                }
            }
        }
    }
}

@Composable
private fun DailyMixCard(songs: List<Song>, onPlay: () -> Unit) {
    val colors = glassCardColors()
    val artwork = songs.firstOrNull()?.albumArtUri
    val context = LocalContext.current
    val request = remember(artwork, context) {
        ImageRequest.Builder(context).data(artwork).size(720, 360).crossfade(false).build()
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 204.dp)
        .clip(RoundedCornerShape(28.dp)).background(colors.backgroundColor)) {
        if (artwork != null) AsyncImage(model = request, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF384634).copy(alpha = 0.70f), Color(0xFF141B13).copy(alpha = 0.97f)))))
        Column(Modifier.padding(20.dp)) {
            Text("✦  MADE FOR YOU", color = Color(0xFFD6E7CD), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp, modifier = Modifier.background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp))
            Spacer(Modifier.height(16.dp))
            Text("Daily mix", fontSize = 29.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFF0F2EB))
            Text("A fresh shuffle of the music you love.", color = Color(0xFFD2D8CD), fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Button(onClick = onPlay, enabled = songs.isNotEmpty(), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC4E6BB), contentColor = Color(0xFF20351F))) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Play", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun QuickAccessCard(title: String, subtitle: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    val colors = glassCardColors()
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(tint.copy(alpha = 0.16f)).clickable(onClick = onClick).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(tint.copy(alpha = 0.15f), tint.copy(alpha = 0.40f)))), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = colors.textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = colors.subTextColor, fontSize = 11.sp, maxLines = 1)
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
                        painter = rememberAsyncImagePainter(model = ImageRequest.Builder(LocalContext.current).data(artworkUri).size(120).crossfade(false).build()),
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
                    items(songs, key = { it.id }) { song ->
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
        shape = RoundedCornerShape(32.dp),
        containerColor = glassColors.backgroundColor,
        modifier = Modifier.border(1.dp, glassColors.borderColor, RoundedCornerShape(32.dp))
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
            if (playlists.isEmpty()) Text("Create a playlist in the Playlists tab to add songs.", color = glassColors.subTextColor)
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists, key = { it.playlistId }) { playlist ->
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
        shape = RoundedCornerShape(32.dp),
        containerColor = glassColors.backgroundColor,
        modifier = Modifier.border(1.dp, glassColors.borderColor, RoundedCornerShape(32.dp))
    )
}

@Composable
fun HomeMiniPlayer(
    navController: NavController,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onPrevious: (() -> Unit)? = null
) {
    val snapshot by remember {
        MusicService.playerState.miniPlayerSnapshots()
    }.collectAsStateWithLifecycle(initialValue = MiniPlayerSnapshot())
    val direction = rememberTrackDirection(snapshot.trackKey, snapshot.playbackOrder)
    snapshot.song?.let { song ->
        MiniPlayer(song = song, isPlaying = snapshot.isPlaying,
            onPlayPause = onPlayPause, onNext = onNext, onPrevious = onPrevious,
            onClick = { navController.navigate("player") { launchSingleTop = true } }, modifier = modifier,
            trackKey = snapshot.trackKey ?: song.id, trackDirection = direction)
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trackKey: Any = song.id,
    trackDirection: Int = 1,
    onPrevious: (() -> Unit)? = null
) {
    val darkTheme = com.example.auraplay.ui.theme.LocalThemeIsDark.current
    val glassColors = glassCardColors()
    val accent = glassColors.accentColor
    val latestNext by rememberUpdatedState(onNext)
    val latestPrevious by rememberUpdatedState(onPrevious)

    val miniPlayerBg = if (darkTheme) {
        Color(0xFF20251E).copy(alpha = 0.90f)
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
                Color(0xFFD0D8CC).copy(alpha = 0.75f)
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
                shape = RoundedCornerShape(32.dp),
                clip = false,
                ambientColor = if (darkTheme) Color.Black.copy(alpha = 0.45f) else accent.copy(alpha = 0.20f),
                spotColor = if (darkTheme) Color.Black.copy(alpha = 0.55f) else Color(0xFF20351F).copy(alpha = 0.22f)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        miniPlayerBg,
                        if (darkTheme) Color(0xFF171B15).copy(alpha = 0.94f) else Color(0xFFF0F4ED).copy(alpha = 0.94f)
                    )
                )
            )
            .border(
                width = 1.5.dp,
                brush = miniPlayerBorder,
                shape = RoundedCornerShape(32.dp)
            )
            .clip(RoundedCornerShape(32.dp))
            .pointerInput(trackKey, onNext != null, onPrevious != null) {
                if (onNext == null && onPrevious == null) return@pointerInput
                var drag = 0f
                val threshold = 56.dp.toPx()
                // Wait for horizontal touch slop so taps and vertical scrolling stay independent.
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragCancel = { drag = 0f },
                    onDragEnd = {
                        if (drag <= -threshold) latestNext?.invoke()
                        else if (drag >= threshold) latestPrevious?.invoke()
                        drag = 0f
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    }
                )
            }
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedTrackContent(song, Modifier.weight(1f), trackKey, trackDirection) { displayedSong ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        // The transition owns the fade; thumbnail requests remain bounded and cached.
                        if (displayedSong.albumArtUri.isNullOrBlank() || displayedSong.albumArtUri == "null") {
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
                            val context = LocalContext.current
                            val request = remember(displayedSong.albumArtUri) {
                                ImageRequest.Builder(context).data(displayedSong.albumArtUri).crossfade(false).size(150, 150)
                                    .error(R.drawable.ic_music_placeholder).build()
                            }
                            AsyncImage(
                                model = request,
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
                                text = displayedSong.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 15.sp,
                                color = if (darkTheme) Color(0xFFF8FAFC) else Color(0xFF20351F)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = displayedSong.artist,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (darkTheme) Color(0xFFB6BDB0) else Color(0xFF53604F).copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Play / Pause Button with tactile spring bounce and bounded circular ripple
                Box(
                    modifier = Modifier
                        .size(48.dp)
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
                    AnimatedControlIcon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Next track button on Mini Player (UX enhancement)
                if (onNext != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
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

            MiniPlayerProgress(accent, darkTheme)
        }
    }
}

@Composable
private fun MiniPlayerProgress(accent: Color, darkTheme: Boolean) {
    val state = MusicService.playerState.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxWidth().height(2.dp).background(accent.copy(alpha = 0.12f)).drawBehind {
        val playback = state.value
        val progress = if (playback.totalDuration > 0) (playback.currentPosition.toFloat() / playback.totalDuration).coerceIn(0f, 1f) else 0f
        drawRect(accent, size = androidx.compose.ui.geometry.Size(size.width * progress, size.height))
    })
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean = false,
    onSongSelected: () -> Unit,
    modifier: Modifier = Modifier,
    glassColors: GlassCardColors = glassCardColors(),
    trailingContent: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(SongItemShape)
            .then(if (isPlaying || selected == true) Modifier
                .background(glassColors.accentColor.copy(alpha = 0.12f))
                .border(1.dp, glassColors.accentColor.copy(alpha = 0.25f), SongItemShape)
            else Modifier)
            .semantics { if (selected != null) this.selected = selected }
            .then(if (onLongClick != null) Modifier.combinedClickable(
                onClick = onSongSelected, onLongClick = onLongClick, onLongClickLabel = "Select song",
                role = if (selected != null) Role.Checkbox else null
            ) else Modifier.clickable(onClick = onSongSelected))
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
                            .error(R.drawable.ic_music_placeholder)
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
