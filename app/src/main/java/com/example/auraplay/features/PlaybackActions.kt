package com.example.auraplay.features

import androidx.media3.common.Player

internal val Player.isPlayingOrBuffering: Boolean
    get() = isPlaying || (playWhenReady && playbackState == Player.STATE_BUFFERING)

fun Player.togglePlayPauseOrRetry() {
    if (isPlayingOrBuffering) pause() else playOrRetry()
}

/** An error leaves ExoPlayer idle; play() alone cannot restart its renderers. */
fun Player.playOrRetry() {
    when (playbackState) {
        Player.STATE_IDLE -> if (mediaItemCount > 0) prepare()
        Player.STATE_ENDED -> if (mediaItemCount > 0) seekToDefaultPosition()
    }
    play()
}

/** Seeking changes the selected song even when an error has stopped the renderers. */
fun Player.prepareAfterSeek() {
    if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
}

fun Player.seekToNextOrRetry() {
    seekToNext()
    prepareAfterSeek()
}

fun Player.seekToPreviousOrRetry() {
    seekToPrevious()
    prepareAfterSeek()
}

fun Player.seekToPreviousTrackOrRetry() {
    seekToPreviousMediaItem()
    prepareAfterSeek()
}
