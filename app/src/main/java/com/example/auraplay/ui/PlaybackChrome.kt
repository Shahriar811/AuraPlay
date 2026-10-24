package com.example.auraplay.ui

import com.example.auraplay.data.Song
import com.example.auraplay.service.PlayerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal data class MiniPlayerSnapshot(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val trackKey: Any? = null,
    val playbackOrder: List<Any> = emptyList()
)

/** Ignore position ticks BEFORE traversing a potentially library-sized queue. */
internal fun Flow<PlayerState>.miniPlayerSnapshots(): Flow<MiniPlayerSnapshot> =
    distinctUntilChanged { previous, current ->
        previous.currentSong == current.currentSong && previous.isPlaying == current.isPlaying &&
            previous.currentQueueIndex == current.currentQueueIndex &&
            previous.queueKeys === current.queueKeys && previous.queueOrder === current.queueOrder
    }.map { state ->
        MiniPlayerSnapshot(state.currentSong, state.isPlaying, state.trackMotionKey(),
            state.queueOrder.mapNotNull { state.queueKeys.getOrNull(it) })
    }.distinctUntilChanged()
