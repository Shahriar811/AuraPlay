package com.example.auraplay

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.example.auraplay.features.AudioEngine
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioQueueOrderTest {
    @Test fun unshuffledQueueKeepsItsNaturalOrder() {
        assertEquals(listOf(0, 1, 2, 3), AudioEngine.order(player(4, listOf(2, 0, 3, 1), shuffle = false)))
        assertTrue(AudioEngine.order(player(0, emptyList())).isEmpty())
    }

    @Test fun largeShuffledQueuePreservesEveryQueuePosition() {
        val sequence = (9_999 downTo 0).toList()
        assertEquals(sequence, AudioEngine.order(player(sequence.size, sequence)))
    }

    @Test fun repeatedTimelinePositionDoesNotLoopOrLoseQueueItems() {
        assertEquals(listOf(2, 0, 1, 3), AudioEngine.order(player(4, listOf(2, 0, 2))))
    }

    @Test fun incompleteTimelineAppendsUnvisitedPositionsSafely() {
        assertEquals(listOf(2, 0, 1, 3), AudioEngine.order(player(4, listOf(2))))
        assertEquals(listOf(0, 1, 2), AudioEngine.order(player(3, listOf(99))))
    }

    private fun player(count: Int, sequence: List<Int>, shuffle: Boolean = true): ExoPlayer {
        val next = sequence.zipWithNext().toMap()
        val timeline = object : Timeline() {
            override fun getWindowCount(): Int = count
            override fun getPeriodCount(): Int = count
            override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = sequence.firstOrNull() ?: C.INDEX_UNSET
            override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
                assertEquals(Player.REPEAT_MODE_OFF, repeatMode)
                assertTrue(shuffleModeEnabled)
                return next[windowIndex] ?: C.INDEX_UNSET
            }
            override fun getIndexOfPeriod(uid: Any): Int = (uid as? Int)?.takeIf { it in 0 until count } ?: C.INDEX_UNSET
            override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
                throw UnsupportedOperationException("Queue ordering does not need window metadata")
            override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
                throw UnsupportedOperationException("Queue ordering does not need period metadata")
        }
        return Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, _ ->
            when (method.name) {
                "getCurrentTimeline" -> timeline
                "getShuffleModeEnabled" -> shuffle
                "getMediaItemCount" -> count
                else -> throw UnsupportedOperationException(method.name)
            }
        } as ExoPlayer
    }
}
