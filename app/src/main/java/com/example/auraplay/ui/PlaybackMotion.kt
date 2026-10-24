package com.example.auraplay.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.auraplay.data.Song
import com.example.auraplay.service.PlayerState

internal const val PlaybackMotionDuration = 340

/** Queue occurrence keys also distinguish consecutive copies of the same song. */
internal fun PlayerState.trackMotionKey(): Any? = queueKeys.getOrNull(currentQueueIndex) ?: currentSong?.id

@Composable
internal fun rememberTrackDirection(trackKey: Any?, playbackOrder: List<Any>): Int {
    val history = remember { TrackMotionHistory(trackKey, playbackOrder) }
    val direction = remember(trackKey) {
        val before = history.order.indexOf(history.key)
        val after = history.order.indexOf(trackKey)
        when {
            before < 0 || after < 0 || before == after -> 1
            after == (before + 1) % history.order.size -> 1
            after == (before - 1 + history.order.size) % history.order.size -> -1
            after < before -> -1
            else -> 1
        }
    }
    SideEffect { history.key = trackKey; history.order = playbackOrder }
    return direction
}

private class TrackMotionHistory(var key: Any?, var order: List<Any>)
private data class TrackVisual(val song: Song, val key: Any)

/** Only song identity starts a transition; progress and metadata edits do not. */
@Composable
internal fun AnimatedTrackContent(
    song: Song,
    modifier: Modifier = Modifier,
    trackKey: Any = song.id,
    direction: Int = 1,
    content: @Composable (Song) -> Unit
) {
    AnimatedContent(
        targetState = TrackVisual(song, trackKey),
        modifier = modifier.clipToBounds(),
        contentKey = { it.key },
        transitionSpec = {
            (slideInHorizontally(tween(PlaybackMotionDuration, easing = FastOutSlowInEasing)) { it / 5 * direction } +
                fadeIn(tween(PlaybackMotionDuration, easing = FastOutSlowInEasing))) togetherWith
                (slideOutHorizontally(tween(PlaybackMotionDuration, easing = FastOutSlowInEasing)) { -it / 5 * direction } +
                    fadeOut(tween(220))) using null
        },
        label = "track-change"
    ) { visual -> content(visual.song) }
}

/** Keep the accessible label stable while the old and new glyphs overlap. */
@Composable
internal fun AnimatedControlIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val color by animateColorAsState(tint, tween(200), label = "control-tint")
    Box(
        modifier.size(24.dp).then(if (contentDescription == null) Modifier else Modifier.semantics {
            this.contentDescription = contentDescription
        }),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = imageVector,
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 40)) + scaleIn(tween(240), initialScale = 0.65f)) togetherWith
                    (fadeOut(tween(100)) + scaleOut(tween(160), targetScale = 0.65f)) using null
            },
            label = "control-icon"
        ) { icon -> Icon(icon, null, tint = color) }
    }
}
