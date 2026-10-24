package com.example.auraplay.features

import com.example.auraplay.ui.MotionIconButton as IconButton
import com.example.auraplay.ui.MotionButton as Button
import com.example.auraplay.ui.MotionFilledTonalButton as FilledTonalButton
import com.example.auraplay.ui.MotionOutlinedButton as OutlinedButton
import com.example.auraplay.ui.MotionFilterChip as FilterChip

import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.auraplay.MainViewModel
import com.example.auraplay.SettingsToggleCard
import com.example.auraplay.data.Song
import com.example.auraplay.ui.*
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeaturePage(title: String, nav: NavController, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(containerColor = Color.Transparent,
        topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) }, navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)) }, content = content)
}

@Composable
fun PlaybackOptionsScreen(nav: NavController, vm: MainViewModel) {
    val settings by vm.featureSettings.collectAsStateWithLifecycle()
    var seconds by remember(settings.crossfadeSeconds) { mutableFloatStateOf(settings.crossfadeSeconds.toFloat()) }
    val colors = glassCardColors()
    FeaturePage("Playback options", nav) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { SettingsToggleCard(Icons.Rounded.Restore, "Resume after restart", "Remember your last song, queue and position. Playback stays paused until you press Play.",
                settings.resumePlayback, { enabled -> vm.changeFeatures { it.copy(resumePlayback = enabled) } }, colors) }
            item {
                Card { Column(Modifier.padding(16.dp)) {
                    Text("Crossfade", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (seconds.roundToInt() == 0) "Off" else "${seconds.roundToInt()} seconds", color = colors.accentColor)
                    Text("Overlap the end of one song with the beginning of the next. End-of-track sleep timers take priority.", color = colors.subTextColor)
                    Slider(value = seconds, onValueChange = { seconds = it }, valueRange = 0f..12f, steps = 11,
                        onValueChangeFinished = { vm.changeFeatures { it.copy(crossfadeSeconds = seconds.roundToInt()) } })
                } }
            }
            item {
                Card { Column(Modifier.padding(16.dp)) {
                    Text("ReplayGain", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Use embedded loudness tags for more consistent volume. Untagged songs keep their original level.", color = colors.subTextColor)
                    listOf("OFF" to "Off", "TRACK" to "Track gain", "ALBUM" to "Album gain").forEach { (mode, label) ->
                        Row(Modifier.fillMaxWidth().clickable { vm.changeFeatures { it.copy(replayGain = mode) } }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(settings.replayGain == mode, { vm.changeFeatures { it.copy(replayGain = mode) } })
                            Text(label)
                        }
                    }
                } }
            }
        }
    }
}

@Composable
fun FolderSettingsScreen(nav: NavController, vm: MainViewModel) {
    val settings by vm.featureSettings.collectAsStateWithLifecycle()
    val folders by vm.allFolders.collectAsStateWithLifecycle()
    val colors = glassCardColors()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (uri.authority != "com.android.externalstorage.documents") vm.showFeatureMessage("Choose a folder on this device.")
            else try {
                val id = DocumentsContract.getTreeDocumentId(uri)
                val volume = id.substringBefore(':')
                val base = if (volume.equals("primary", true)) Environment.getExternalStorageDirectory().absolutePath else "/storage/$volume"
                val path = File(base, id.substringAfter(':', "")).absolutePath.trimEnd('/')
                vm.changeFeatures { it.copy(selectedFoldersOnly = true, includedFolders = it.includedFolders + path, excludedFolders = it.excludedFolders - path) }
            } catch (_: Exception) { vm.showFeatureMessage("That folder could not be selected.") }
        }
    }
    val entries = remember(folders, settings) {
        (folders.map { it.folderPath } + settings.includedFolders + settings.excludedFolders).distinct().sorted()
    }
    FeaturePage("Music folders", nav) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SettingsToggleCard(Icons.Rounded.Folder, "Only selected folders", "Include selected folders and their subfolders. Hidden folders are always excluded.",
                settings.selectedFoldersOnly, { enabled -> vm.changeFeatures { it.copy(selectedFoldersOnly = enabled) } }, colors) }
            item { OutlinedButton(onClick = { picker.launch(null) }) { Icon(Icons.Rounded.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text("Choose a folder") } }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Folder", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Include", Modifier.width(64.dp)); Text("Hide", Modifier.width(48.dp))
                }
                if (entries.isEmpty()) Text("Folders appear after scanning your music library.", color = colors.subTextColor)
                if (settings.selectedFoldersOnly && settings.includedFolders.isEmpty()) Text("Select a folder to show music in your library.", color = colors.accentColor)
            }
            items(entries, key = { it }) { path ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(path.substringAfterLast('/').ifBlank { path }, fontWeight = FontWeight.SemiBold)
                        Text(path, color = colors.subTextColor, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Checkbox(path in settings.includedFolders, { checked -> vm.changeFeatures {
                        it.copy(includedFolders = if (checked) it.includedFolders + path else it.includedFolders - path)
                    } }, modifier = Modifier.width(64.dp))
                    Checkbox(path in settings.excludedFolders, { checked -> vm.changeFeatures {
                        it.copy(excludedFolders = if (checked) it.excludedFolders + path else it.excludedFolders - path)
                    } }, modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

@Composable
fun BackupScreen(nav: NavController, vm: MainViewModel) {
    val busy by vm.featureBusy.collectAsStateWithLifecycle()
    val scanning by vm.isScanning.collectAsStateWithLifecycle()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportBackup) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importBackup) }
    FeaturePage("Backup & restore", nav) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(Icons.Rounded.Backup, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Text("Keep your library yours", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Back up playlists, liked songs, bookmarks, listening history, settings and song edits. Audio files stay on your device.")
            Text("Restore merges saved items with this library. Songs are matched by file location or song information; unavailable tracks are skipped.", color = glassCardColors().subTextColor)
            Button(onClick = { exporter.launch("AuraPlay-backup-${LocalDate.now()}.zip") }, enabled = !busy && !scanning, modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
            OutlinedButton(onClick = { importer.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy && !scanning, modifier = Modifier.fillMaxWidth()) { Text("Restore backup") }
            if (scanning) Text("Finishing the music scan before backup or restore…")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun SmartPlaylistsScreen(nav: NavController, vm: MainViewModel, onPlay: (List<Song>, Song) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(SmartPlaylist.RECENTLY_ADDED.name) }
    val mode = SmartPlaylist.valueOf(selected)
    val tracks by remember(mode) { vm.smartPlaylist(mode) }.collectAsStateWithLifecycle(initialValue = emptyList())
    FeaturePage("Smart playlists", nav) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SmartPlaylist.entries) { item -> FilterChip(item == mode, { selected = item.name }, label = { Text(item.title) }) }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(mode.description); Text("${tracks.size} songs", color = glassCardColors().subTextColor) }
                FilledTonalButton(onClick = { tracks.firstOrNull()?.let { onPlay(tracks, it) } }, enabled = tracks.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text("Play") }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (tracks.isEmpty()) item { Text("This playlist will fill as you add and listen to music.", Modifier.padding(24.dp)) }
                items(tracks, key = { it.id }) { song -> SongListItem(song, onSongSelected = { onPlay(tracks, song) }, trailingContent = { FeatureSongMenu(song, nav, vm) }) }
            }
        }
    }
}

@Composable
fun BookmarksScreen(nav: NavController, vm: MainViewModel, onPlay: (Song, Long) -> Unit) {
    val marks by vm.bookmarks.collectAsStateWithLifecycle()
    val library by vm.librarySongs.collectAsStateWithLifecycle()
    val byId = remember(library) { library.associateBy { it.id } }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(marks, byId, query) { marks.filter { mark -> byId[mark.songId]?.let {
        query.isBlank() || it.title.contains(query, true) || mark.label.contains(query, true)
    } == true } }
    FeaturePage("Audio bookmarks", nav) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search bookmarks") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                if (filtered.isEmpty()) item { Text("Save a timestamp from the player menu to find it here.") }
                items(filtered, key = { it.bookmarkId }) { mark ->
                    val song = byId[mark.songId]!!
                    ListItem(headlineContent = { Text(mark.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${song.title} · ${formatTime(mark.positionMs)}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(Icons.Rounded.Bookmark, null) },
                        trailingContent = { IconButton(onClick = { vm.deleteBookmark(mark) }) { Icon(Icons.Rounded.DeleteOutline, "Delete bookmark") } },
                        modifier = Modifier.clickable { onPlay(song, mark.positionMs) })
                }
            }
        }
    }
}

@Composable
fun FeatureSongMenu(song: Song, nav: NavController, vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Song actions") }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = { vm.enqueue(song, true); open = false })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { vm.enqueue(song, false); open = false })
            DropdownMenuItem(text = { Text("Edit song information") }, onClick = { nav.navigate("song_edit/${song.id}"); open = false })
        }
    }
}
