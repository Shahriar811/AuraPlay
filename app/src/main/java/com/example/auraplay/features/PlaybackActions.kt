package com.example.auraplay.features

import androidx.media3.common.Player

/** An error leaves ExoPlayer idle; play() alone cannot restart its renderers. */
fun Player.playOrRetry() {
    if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
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
