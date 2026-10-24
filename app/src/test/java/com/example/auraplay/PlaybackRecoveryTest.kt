package com.example.auraplay

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.example.auraplay.features.PlaybackRecovery
import com.example.auraplay.features.playOrRetry
import com.example.auraplay.features.seekToNextOrRetry
import com.example.auraplay.features.seekToPreviousOrRetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PlaybackRecoveryTest {
    @Test fun nextAndPreviousRestartAnEngineStoppedByAnError() {
        val playback = TestPlayer()
        playback.player.seekToNextOrRetry()
        assertEquals(1, playback.index)
        assertEquals(1, playback.prepareCount)
        assertTrue(playback.playWhenReady)
        assertEquals(Player.STATE_BUFFERING, playback.state)

        playback.fail()
        playback.player.seekToPreviousOrRetry()
        assertEquals(0, playback.index)
        assertEquals(2, playback.prepareCount)
        assertTrue(playback.playWhenReady)
    }

    @Test fun skippingWhilePausedKeepsPlaybackPaused() {
        val playback = TestPlayer().apply { playWhenReady = false }
        playback.player.seekToNextOrRetry()
        assertEquals(1, playback.index)
        assertEquals(1, playback.prepareCount)
        assertFalse(playback.playWhenReady)
        playback.player.playOrRetry()
        assertTrue(playback.playWhenReady)
    }

    @Test fun emptyQueueAndReadyPlayerDoNotPrepareAgain() {
        val empty = TestPlayer().apply { count = 0 }
        empty.player.seekToNextOrRetry()
        assertEquals(0, empty.prepareCount)
        val ready = TestPlayer().apply { state = Player.STATE_READY; error = null }
        ready.player.seekToNextOrRetry()
        assertEquals(0, ready.prepareCount)
    }

    @Test fun temporaryFailureRetriesOnceThenReportsTheError() = runBlocking {
        val playback = TestPlayer()
        val failures = mutableListOf<PlaybackException>()
        val recovery = PlaybackRecovery(playback.player, this, failures::add)
        recovery.onPlayerError(playback.error!!)
        awaitRetries()
        assertEquals(1, playback.prepareCount)
        assertTrue(failures.isEmpty())
        playback.fail()
        recovery.onPlayerError(playback.error!!)
        awaitRetries()
        assertEquals(1, playback.prepareCount)
        assertEquals(1, failures.size)
    }

    @Test fun permissionAndUnsupportedFileFailuresAreNotRetried() = runBlocking {
        for (code in listOf(PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED)) {
            val playback = TestPlayer().apply { fail(code) }
            val failures = mutableListOf<PlaybackException>()
            PlaybackRecovery(playback.player, this, failures::add).onPlayerError(playback.error!!)
            awaitRetries()
            assertEquals(0, playback.prepareCount)
            assertEquals(1, failures.size)
        }
    }

    @Test fun pauseOrChangingTracksCancelsAPendingRetry() = runBlocking {
        for (pause in listOf(true, false)) {
            val playback = TestPlayer()
            val recovery = PlaybackRecovery(playback.player, this) { fail("Unexpected terminal error") }
            recovery.onPlayerError(playback.error!!)
            if (pause) {
                playback.playWhenReady = false
                recovery.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            } else {
                playback.index = 1
                playback.state = Player.STATE_READY
                playback.error = null
                recovery.onMediaItemTransition(playback.player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
            }
            awaitRetries()
            assertEquals(0, playback.prepareCount)
        }
    }

    @Test fun aNewQueueItemGetsItsOwnRetryAndReleaseCancelsWork() = runBlocking {
        val playback = TestPlayer()
        val recovery = PlaybackRecovery(playback.player, this) { fail("Unexpected terminal error") }
        recovery.onPlayerError(playback.error!!)
        awaitRetries()
        playback.index = 1
        recovery.onMediaItemTransition(playback.player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        playback.fail()
        recovery.onPlayerError(playback.error!!)
        awaitRetries()
        assertEquals(2, playback.prepareCount)
        playback.index = 0
        recovery.onMediaItemTransition(playback.player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        playback.fail()
        recovery.onPlayerError(playback.error!!)
        recovery.release()
        awaitRetries()
        assertEquals(2, playback.prepareCount)
    }

    private suspend fun CoroutineScope.awaitRetries() { coroutineContext[Job]!!.children.toList().joinAll() }

    /** Simulates ExoPlayer's documented error state: seek changes the item but leaves it idle. */
    private class TestPlayer {
        val items = listOf(MediaItem.Builder().setMediaId("a").build(), MediaItem.Builder().setMediaId("b").build())
        var index = 0
        var count = items.size
        var state = Player.STATE_IDLE
        var playWhenReady = true
        var error: PlaybackException? = TestPlaybackException(PlaybackException.ERROR_CODE_UNSPECIFIED)
        var prepareCount = 0
        fun fail(code: Int = PlaybackException.ERROR_CODE_UNSPECIFIED) {
            state = Player.STATE_IDLE
            error = TestPlaybackException(code)
        }

        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getPlaybackState" -> state
                "getPlayWhenReady" -> playWhenReady
                "getPlayerError" -> error
                "getMediaItemCount" -> count
                "getCurrentMediaItem" -> items.getOrNull(index).takeIf { count > 0 }
                "seekToNext" -> { if (count > 0) index = (index + 1).coerceAtMost(count - 1); null }
                "seekToPrevious" -> { index = (index - 1).coerceAtLeast(0); null }
                "prepare" -> { prepareCount++; state = Player.STATE_BUFFERING; error = null; null }
                "play" -> { playWhenReady = true; null }
                "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; null }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as Player
    }

    private class TestPlaybackException(code: Int) : PlaybackException("Playback failure", null, code, 0L)
}
