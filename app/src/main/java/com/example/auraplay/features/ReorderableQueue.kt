package com.example.auraplay.ui

import com.example.auraplay.ui.MotionIconButton as IconButton

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.auraplay.data.Song
import com.example.auraplay.service.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class QueueRow(val key: String, val song: Song)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(playerState: PlayerState, onDismiss: () -> Unit, onPlayQueueItem: (Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit, onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> }) {
    val source = remember(playerState.queue, playerState.queueKeys, playerState.queueOrder) {
        val order = playerState.queueOrder.takeIf { it.size == playerState.queue.size && it.toSet() == playerState.queue.indices.toSet() } ?: playerState.queue.indices.toList()
        order.map { i -> val song = playerState.queue[i]
        QueueRow(playerState.queueKeys.getOrElse(i) { "$i-${song.id}" }, song)
    } }
    var rows by remember { mutableStateOf(source) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var fromIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var sourceKeys by remember { mutableStateOf(emptyList<String>()) }
    val latestSource by rememberUpdatedState(source)
    val latestMove by rememberUpdatedState(onMoveQueueItem)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val stride = with(LocalDensity.current) { 78.dp.toPx() }
    val colors = glassCardColors()
    LaunchedEffect(source) { if (dragging == null) rows = source }
    fun finish(commit: Boolean) {
        val to = rows.indexOfFirst { it.key == dragging }
        if (commit && sourceKeys == latestSource.map { it.key } && fromIndex >= 0 && to >= 0 && fromIndex != to) latestMove(fromIndex, to)
        else rows = latestSource
        dragging = null; dragOffset = 0f; scrollJob?.cancel()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Queue · ${rows.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Hold the handle to reorder. Tap a song to play it.", color = colors.subTextColor, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    val active = row.key == playerState.queueKeys.getOrNull(playerState.currentQueueIndex)
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().height(72.dp).testTag("queue-row-${row.key}").semantics { selected = active }
                        .zIndex(if (dragging == row.key) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging == row.key) dragOffset else 0f }
                        .background(if (active || dragging == row.key) MaterialTheme.colorScheme.primaryContainer else colors.backgroundColor, MaterialTheme.shapes.medium)
                        .clickable(enabled = dragging == null) { onPlayQueueItem(index) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DragHandle, "Hold to reorder", tint = colors.subTextColor,
                            modifier = Modifier.size(44.dp).padding(10.dp).pointerInput(row.key, stride) {
                                detectDragGesturesAfterLongPress(onDragStart = {
                                    dragging = row.key; fromIndex = rows.indexOfFirst { it.key == row.key }; dragOffset = 0f; sourceKeys = latestSource.map { it.key }
                                }, onDragEnd = { finish(true) }, onDragCancel = { finish(false) }, onDrag = { change, delta ->
                                    change.consume()
                                    dragOffset += delta.y
                                    val current = rows.indexOfFirst { it.key == row.key }
                                    val target = when { dragOffset > stride / 2 -> current + 1; dragOffset < -stride / 2 -> current - 1; else -> current }
                                    if (target in rows.indices && current >= 0 && target != current) {
                                        rows = rows.toMutableList().apply { add(target, removeAt(current)) }
                                        dragOffset -= (target - current) * stride
                                        val visible = listState.layoutInfo.visibleItemsInfo
                                        if (target >= (visible.lastOrNull()?.index ?: 0) || target <= (visible.firstOrNull()?.index ?: 0)) {
                                            scrollJob?.cancel(); scrollJob = scope.launch { listState.animateScrollToItem((target - if (delta.y < 0) 0 else 3).coerceAtLeast(0)) }
                                        }
                                    }
                                })
                            })
                        Column(Modifier.weight(1f)) {
                            Text(row.song.title, color = if (active) colors.accentColor else colors.textColor,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(row.song.artist, style = MaterialTheme.typography.bodySmall, color = colors.subTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box {
                            IconButton(onClick = { menu = true }, enabled = dragging == null) { Icon(Icons.Rounded.MoreVert, "Queue actions") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Move up") }, enabled = index > 0, onClick = { onMoveQueueItem(index, index - 1); menu = false })
                                DropdownMenuItem(text = { Text("Move down") }, enabled = index < rows.lastIndex, onClick = { onMoveQueueItem(index, index + 1); menu = false })
                                DropdownMenuItem(text = { Text("Remove from queue") }, onClick = { onRemoveQueueItem(index); menu = false })
                            }
                        }
                    }
                }
            }
        }
    }
}
