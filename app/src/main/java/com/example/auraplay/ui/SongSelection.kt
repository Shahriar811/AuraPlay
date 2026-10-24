package com.example.auraplay.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import com.example.auraplay.data.Song

@Stable
internal class SongSelectionState(active: Boolean = false, ids: Set<Long> = emptySet()) {
    var active by mutableStateOf(active)
        private set
    var ids by mutableStateOf(ids)
        private set

    fun start() { active = true }
    fun toggle(id: Long) {
        active = true
        ids = if (id in ids) ids - id else ids + id
    }
    fun toggleAll(songs: List<Song>) {
        active = true
        val shown = songs.mapTo(linkedSetOf()) { it.id }
        ids = if (shown.isNotEmpty() && ids.containsAll(shown)) ids - shown else ids + shown
    }
    fun clear() { ids = emptySet(); active = false }

    companion object {
        val Saver = listSaver<SongSelectionState, Long>(
            save = { listOf(if (it.active) 1L else 0L) + it.ids },
            restore = { SongSelectionState(it.first() == 1L, it.drop(1).toSet()) }
        )
    }
}

/** Preserve selection order across searches and skip tracks removed since they were selected. */
internal fun selectedSongs(ids: Set<Long>, library: List<Song>): List<Song> {
    val byId = library.associateBy { it.id }
    return ids.mapNotNull(byId::get)
}
