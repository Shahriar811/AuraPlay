package com.example.auraplay.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Artwork and lyrics share one viewport. Playback controls occupy their own stable area. */
@Composable
internal fun FixedPlayerLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    presentation: @Composable () -> Unit,
    controls: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val compactPortrait = maxHeight < 520.dp || LocalDensity.current.fontScale > 1.3f
        val compactArtworkHeight = minOf(maxWidth, 240.dp)
        Column(Modifier.fillMaxSize()) {
            header()
            if (landscape) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { presentation() }
                    // Compact/landscape screens can scroll the controls independently.
                    Box(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { controls() }
                }
            } else if (compactPortrait) {
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().height(compactArtworkHeight)) { presentation() }
                    controls()
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) { presentation() }
                controls()
            }
        }
    }
}

/** One line per field keeps the playback deck the same height for every track. */
@Composable
internal fun PlayerTrackDetails(title: String, artist: String, modifier: Modifier = Modifier) {
    val colors = glassCardColors()
    Column(modifier) {
        Text(title, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.6).sp, color = colors.textColor,
            minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(artist, fontSize = 16.sp, lineHeight = 24.sp, color = colors.subTextColor,
            minLines = 1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
