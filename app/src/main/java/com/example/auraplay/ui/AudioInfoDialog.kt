package com.example.auraplay.ui

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.auraplay.data.Song
import com.example.auraplay.features.audioUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

@Composable
internal fun AudioInfoDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info by produceState<AudioFileInfo?>(null, song.id, song.data) {
        value = withContext(Dispatchers.IO) { readAudioFileInfo(context, song) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Audio info", style = MaterialTheme.typography.headlineSmall) },
        text = {
            val fileInfo = info
            if (fileInfo == null) {
                CircularProgressIndicator()
            } else {
                SelectionContainer {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AudioInfoField("File name", fileInfo.name, valueStyle = MaterialTheme.typography.titleSmall)
                        AudioInfoField("Location", fileInfo.location, valueStyle = MaterialTheme.typography.bodySmall,
                            secondary = true)
                        AudioInfoField("File type", fileInfo.type)
                        AudioInfoField("Bitrate", fileInfo.bitrate?.let { bitrate ->
                            val estimated = if (fileInfo.estimatedBitrate) " (estimated)" else ""
                            "${(bitrate / 1000.0).roundToLong()} kbps$estimated"
                        })
                        fileInfo.codec?.let { AudioInfoField("Audio codec", it) }
                        fileInfo.size?.let { AudioInfoField("Size", Formatter.formatFileSize(context, it)) }
                        fileInfo.duration?.let { AudioInfoField("Duration", formatTime(it)) }
                        fileInfo.sampleRate?.let { AudioInfoField("Sample rate", "$it Hz") }
                        fileInfo.channels?.let { AudioInfoField("Channels", it.toString()) }
                    }
                }
            }
        },
        confirmButton = { MotionTextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
private fun AudioInfoField(
    label: String,
    value: String?,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    secondary: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value ?: "Unavailable", style = valueStyle,
            color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

private data class AudioFileInfo(
    val name: String,
    val location: String,
    val type: String?,
    val size: Long?,
    val duration: Long?,
    val bitrate: Long?,
    val estimatedBitrate: Boolean,
    val codec: String?,
    val sampleRate: Int?,
    val channels: Int?
)

@Suppress("DEPRECATION")
private fun readAudioFileInfo(context: Context, song: Song): AudioFileInfo {
    val uri = song.audioUri()
    val resolver = context.contentResolver
    val path = song.data.takeIf { it.startsWith("/") } ?: uri.path?.takeIf { uri.scheme == "file" }
    val file = path?.let(::File)
    var name = file?.name
    var location = path ?: uri.toString()
    var mime: String? = null
    var size: Long? = null

    if (uri.scheme == "content") runCatching {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATA)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0) ?: name
                size = if (cursor.isNull(1)) null else cursor.getLong(1).takeIf { it > 0 }
                mime = cursor.getString(2)
                location = cursor.getString(3)?.takeIf(String::isNotBlank) ?: location
            }
        }
    }
    if (size == null) size = runCatching { file?.length()?.takeIf { it > 0 } }.getOrNull()
    var duration = song.duration.takeIf { it > 0 }
    var bitrate: Long? = null
    var codec: String? = null
    var sampleRate: Int? = null
    var channels: Int? = null

    // The audio stream reports codec, channel count and sample rate on older Android versions too.
    runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val streamType = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!streamType.startsWith("audio/")) continue
                codec = streamType
                sampleRate = format.positiveInt(MediaFormat.KEY_SAMPLE_RATE)
                channels = format.positiveInt(MediaFormat.KEY_CHANNEL_COUNT)
                bitrate = format.positiveInt(MediaFormat.KEY_BIT_RATE)?.toLong()
                break
            }
        } finally {
            extractor.release()
        }
    }
    runCatching {
        val metadata = MediaMetadataRetriever()
        try {
            metadata.setDataSource(context, uri)
            mime = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: mime
            duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0 } ?: duration
            bitrate = bitrate ?: metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()?.takeIf { it > 0 }
        } finally {
            metadata.release()
        }
    }
    val bytes = size
    val milliseconds = duration
    val estimated = if (bitrate == null && bytes != null && milliseconds != null) {
        (bytes.toDouble() * 8000 / milliseconds).roundToLong().takeIf { it > 0 }
    } else null
    val fileName = name ?: song.title
    val extension = fileName.substringAfterLast('.', "").takeIf { it.isNotBlank() && it.length <= 8 }
    return AudioFileInfo(fileName, location, extension?.uppercase(Locale.ROOT) ?: mime,
        size, duration, bitrate ?: estimated, estimated != null, codec, sampleRate, channels)
}

private fun MediaFormat.positiveInt(key: String): Int? =
    if (containsKey(key)) getInteger(key).takeIf { it > 0 } else null
