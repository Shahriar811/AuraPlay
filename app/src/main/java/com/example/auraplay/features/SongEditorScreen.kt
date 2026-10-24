package com.example.auraplay.features

import com.example.auraplay.ui.MotionButton as Button
import com.example.auraplay.ui.MotionOutlinedButton as OutlinedButton
import com.example.auraplay.ui.MotionTextButton as TextButton

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.auraplay.MainViewModel

@Composable
fun SongEditorScreen(id: Long, nav: NavController, vm: MainViewModel) {
    val song by remember(id) { vm.getSongById(id) }.collectAsStateWithLifecycle(initialValue = null)
    val busy by vm.featureBusy.collectAsStateWithLifecycle()
    FeaturePage("Song information", nav) { padding ->
        val track = song
        if (track == null) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Song unavailable") }; return@FeaturePage }
        var title by rememberSaveable(id, track.title) { mutableStateOf(track.title) }
        var artist by rememberSaveable(id, track.artist) { mutableStateOf(track.artist) }
        var album by rememberSaveable(id, track.album) { mutableStateOf(track.album) }
        var cover by rememberSaveable(id) { mutableStateOf<String?>(null) }
        var confirmReset by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> cover = uri?.toString() ?: cover }
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(cover ?: track.albumArtUri, "Album cover", contentScale = ContentScale.Crop, modifier = Modifier.size(180.dp))
            OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !busy) { Text("Choose album cover") }
            OutlinedTextField(title, { title = it.take(2000) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(artist, { artist = it.take(2000) }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            OutlinedTextField(album, { album = it.take(2000) }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Text("Edits and covers are saved in AuraPlay and included in your backups. Your audio file stays unchanged.", style = MaterialTheme.typography.bodySmall)
            Text(track.data, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Button(onClick = { vm.saveSongInfo(track, title, artist, album, cover?.let(Uri::parse)) { nav.popBackStack() } }, enabled = !busy && title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
            TextButton(onClick = { confirmReset = true }, enabled = !busy) { Text("Restore original song information") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Restore original information?") },
            text = { Text("Your title, artist, album and cover edits for this song will be removed. The library will be rescanned.") },
            confirmButton = { TextButton(onClick = { vm.resetSongInfo(track); cover = null; confirmReset = false; nav.popBackStack() }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
    }
}
