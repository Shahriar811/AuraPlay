package com.example.auraplay

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.flac.VorbisComment
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.example.auraplay.data.Song
import com.example.auraplay.features.audioUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal suspend fun readEmbeddedLyrics(context: Context, song: Song): SongLyrics? = try {
    withTimeoutOrNull(2500L) {
        val future = MetadataRetriever.retrieveMetadata(context, MediaItem.fromUri(song.audioUri()))
        val tracks = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { future.cancel(false) }
            future.addListener({
                if (continuation.isActive) {
                    try { continuation.resume(future.get()) }
                    catch (error: Exception) { continuation.resumeWithException(error) }
                }
            }, { it.run() })
        }
        var plain: SongLyrics? = null
        for (groupIndex in 0 until tracks.length) {
            val group = tracks[groupIndex]
            for (trackIndex in 0 until group.length) {
                val metadata = group.getFormat(trackIndex).metadata ?: continue
                for (index in 0 until metadata.length()) {
                    val text = embeddedLyricText(metadata[index]) ?: continue
                    val lyrics = LyricsManager.parseLrcContent(text).copy(source = "EMBEDDED")
                    if (lyrics.isSynced && lyrics.hasText) return@withTimeoutOrNull lyrics
                    if (lyrics.hasText && plain == null) plain = lyrics
                }
            }
        }
        plain
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun embeddedLyricText(entry: Metadata.Entry): String? = when (entry) {
    is BinaryFrame -> if (entry.id == "USLT" || entry.id == "ULT") decodeId3Lyrics(entry.data) else null
    is VorbisComment -> entry.value.takeIf { isLyricsTag(entry.key) }
    is TextInformationFrame -> entry.values.firstOrNull()?.takeIf { isLyricsTag(entry.description.orEmpty()) || isLyricsTag(entry.id) }
    is InternalFrame -> entry.text.takeIf { isLyricsTag(entry.description) }
    else -> null
}

private fun isLyricsTag(name: String): Boolean = name.equals("LYRICS", true) ||
    name.equals("UNSYNCEDLYRICS", true) || name.equals("SYNCEDLYRICS", true) || name == "©lyr"

internal fun decodeId3Lyrics(data: ByteArray): String? {
    if (data.size < 5) return null
    val encoding = when (data[0].toInt()) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> return null
    }
    val delimiter = if (data[0].toInt() in 1..2) 2 else 1
    // Encoding byte, three-letter language, then a terminated description before the lyrics.
    for (index in 4..data.size - delimiter step delimiter) {
        if (data[index] == 0.toByte() && (delimiter == 1 || data[index + 1] == 0.toByte())) {
            return String(data, index + delimiter, data.size - index - delimiter, encoding).trimEnd('\u0000')
        }
    }
    return null
}
