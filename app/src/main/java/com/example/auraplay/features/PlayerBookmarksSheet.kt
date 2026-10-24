package com.example.auraplay.features

import com.example.auraplay.ui.MotionIconButton as IconButton
import com.example.auraplay.ui.MotionButton as Button

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auraplay.MainViewModel
import com.example.auraplay.data.Song
import com.example.auraplay.ui.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerBookmarksSheet(song: Song, position: Long, vm: MainViewModel, onDismiss: () -> Unit, onSeek: (Long) -> Unit) {
    val marks by remember(song.id) { vm.bookmarksForSong(song.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val capturedPosition = remember(song.id) { position.coerceAtLeast(0) }
    var label by rememberSaveable(song.id) { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Audio bookmarks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlinedTextField(label, { label = it.take(120); saved = false }, singleLine = true, label = { Text("Name this moment") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.addBookmark(song.id, capturedPosition, label.ifBlank { "Moment at ${formatTime(capturedPosition)}" }); saved = true },
                enabled = !saved && marks.none { it.positionMs == capturedPosition }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.BookmarkAdd, null); Spacer(Modifier.width(8.dp)); Text(if (saved) "Saved" else "Save at ${formatTime(capturedPosition)}")
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(marks, key = { it.bookmarkId }) { mark ->
                    ListItem(headlineContent = { Text(mark.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(formatTime(mark.positionMs)) },
                        trailingContent = { IconButton(onClick = { vm.deleteBookmark(mark) }) { Icon(Icons.Rounded.DeleteOutline, "Delete bookmark") } },
                        modifier = Modifier.clickable { onSeek(mark.positionMs.coerceAtMost(song.duration.coerceAtLeast(0))) })
                }
            }
        }
    }
}
