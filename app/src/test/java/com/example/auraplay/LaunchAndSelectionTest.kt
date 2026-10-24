package com.example.auraplay

import com.example.auraplay.data.Song
import com.example.auraplay.service.PlayerState
import com.example.auraplay.ui.SongSelectionState
import com.example.auraplay.ui.miniPlayerSnapshots
import com.example.auraplay.ui.selectedSongs
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LaunchAndSelectionTest {
    private fun song(id: Long) = Song(id, "Track $id", "Artist", albumArtUri = null,
        duration = 60_000, data = "/Music/$id.mp3")

    @Test fun progressTicksDoNotTraverseALargeQueueAgain() = runBlocking {
        val order = CountingList((0 until 10_000).toList())
        val initial = PlayerState(currentSong = song(1), queueKeys = (0 until 10_000).map { "key-$it" }, queueOrder = order)
        val snapshots = flow {
            emit(initial)
            repeat(100) { emit(initial.copy(currentPosition = it * 250L)) }
        }.miniPlayerSnapshots().toList()
        assertEquals(1, snapshots.size)
        assertEquals(10_000, snapshots.single().playbackOrder.size)
        assertEquals("Only one queue traversal is allowed, regardless of progress ticks", 10_000, order.reads)
    }

    @Test fun chromeStillUpdatesForPlaybackShuffleMetadataAndDuplicateOccurrences() = runBlocking {
        val initial = PlayerState(currentSong = song(1), queueKeys = listOf("copy-1", "copy-2"), queueOrder = listOf(0, 1))
        val playing = initial.copy(isPlaying = true)
        val secondCopy = playing.copy(currentQueueIndex = 1)
        val shuffled = secondCopy.copy(queueOrder = listOf(1, 0))
        val renamed = shuffled.copy(currentSong = song(1).copy(title = "Updated title"))
        val snapshots = flow {
            emit(initial); emit(playing); emit(secondCopy); emit(shuffled); emit(renamed)
        }.miniPlayerSnapshots().toList()
        assertEquals(5, snapshots.size)
        assertTrue(snapshots[1].isPlaying)
        assertEquals("copy-2", snapshots[2].trackKey)
        assertEquals(listOf("copy-2", "copy-1"), snapshots[3].playbackOrder)
        assertEquals("Updated title", snapshots[4].song?.title)
    }

    @Test fun changedVersionGenerationOrStorageRequiresRescan() {
        val saved = "external_primary:2:v1:100;sdcard:2:v2:20"
        assertTrue(canReuseLibraryScan(saved, saved, true))
        listOf(null, "", "external_primary:2:v1:101;sdcard:2:v2:20",
            "external_primary:2:v3:100;sdcard:2:v2:20", "external_primary:2:v1:100").forEach {
            assertFalse(canReuseLibraryScan(it, saved, true))
        }
        assertFalse("A restored setting cannot stand in for an empty database", canReuseLibraryScan(saved, saved, false))
    }

    @Test fun selectAllOnlyChangesTheCurrentSearchAndKeepsOtherSelections() {
        val selection = SongSelectionState()
        selection.toggle(3)
        selection.toggleAll(listOf(song(1), song(2)))
        assertEquals(setOf(1L, 2L, 3L), selection.ids)
        selection.toggleAll(listOf(song(1), song(2)))
        assertEquals(setOf(3L), selection.ids)
        selection.toggle(3)
        assertTrue(selection.active)
        assertTrue(selection.ids.isEmpty())
        selection.clear()
        assertFalse(selection.active)
    }

    @Test fun playbackContainsOnlySelectedAvailableSongsInSelectionOrder() {
        val selection = SongSelectionState()
        selection.toggle(3); selection.toggle(1); selection.toggle(2); selection.toggle(2)
        assertEquals(listOf(3L, 1L), selectedSongs(selection.ids, listOf(song(1), song(2), song(3), song(4))).map { it.id })
        assertEquals(listOf(1L), selectedSongs(selection.ids, listOf(song(1), song(2), song(4))).map { it.id })
        selection.clear()
        assertTrue(selectedSongs(selection.ids, listOf(song(1))).isEmpty())
    }

    private class CountingList<T>(private val contents: List<T>) : AbstractList<T>() {
        var reads = 0
        override val size: Int get() = contents.size
        override fun get(index: Int): T { reads++; return contents[index] }
    }
}
