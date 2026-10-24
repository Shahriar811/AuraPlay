package com.example.auraplay.features

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Restarts a stopped engine without changing the queue or overriding a user's pause. */
class PlaybackRecovery(
    private val player: Player,
    private val scope: CoroutineScope,
    private val onFailure: (PlaybackException) -> Unit
) : Player.Listener {
    private var retryJob: Job? = null
    private var attemptedItem: MediaItem? = null

    override fun onPlayerError(error: PlaybackException) {
        retryJob?.cancel()
        val item = player.currentMediaItem
        if (item == null || !player.playWhenReady || attemptedItem == item || !canRetry(error)) {
            onFailure(error)
            return
        }
        attemptedItem = item
        retryJob = scope.launch {
            // Let the failed renderer finish stopping before preparing it again.
            delay(250)
            if (player.currentMediaItem == item && player.playWhenReady &&
                player.playbackState == Player.STATE_IDLE && player.playerError != null) {
                player.prepare()
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        retryJob?.cancel()
        if (attemptedItem != mediaItem || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) attemptedItem = null
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            retryJob?.cancel()
            // MediaSession/notification/headset seeks need the same recovery as app controls.
            player.prepareAfterSeek()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) retryJob?.cancel()
    }

    fun release() { retryJob?.cancel() }

    private fun canRetry(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_UNSPECIFIED,
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> true
        else -> false
    }
}
