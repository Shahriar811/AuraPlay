package com.example.auraplay

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class SongLyrics(
    val isSynced: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
    val plainText: String = ""
)

object LyricsManager {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]")

    suspend fun loadLyricsForSong(filePath: String): SongLyrics = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext SongLyrics()

        try {
            // Check for .lrc file in the same folder with the same name
            val audioFile = File(filePath)
            val parentDir = audioFile.parentFile
            val baseName = audioFile.nameWithoutExtension

            if (parentDir != null && parentDir.exists()) {
                val lrcFile = File(parentDir, "$baseName.lrc")
                if (lrcFile.exists() && lrcFile.canRead()) {
                    val content = lrcFile.readText()
                    return@withContext parseLrcContent(content)
                }

                // Also check case-insensitive .LRC
                val altLrcFile = File(parentDir, "$baseName.LRC")
                if (altLrcFile.exists() && altLrcFile.canRead()) {
                    val content = altLrcFile.readText()
                    return@withContext parseLrcContent(content)
                }
            }
        } catch (e: Exception) {
            // Handle error
        }

        SongLyrics()
    }

    fun parseLrcContent(content: String): SongLyrics {
        val lines = mutableListOf<LyricLine>()
        var isSynced = false

        content.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val matcher = TIME_TAG_PATTERN.matcher(trimmed)
                var foundTag = false
                var lastEnd = 0
                val timestamps = mutableListOf<Long>()

                while (matcher.find()) {
                    foundTag = true
                    val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                    val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                    val millisStr = matcher.group(3) ?: "00"
                    val millis = if (millisStr.length == 2) millisStr.toLong() * 10 else millisStr.toLong()
                    val totalMs = (minutes * 60 + seconds) * 1000 + millis
                    timestamps.add(totalMs)
                    lastEnd = matcher.end()
                }

                if (foundTag) {
                    isSynced = true
                    val lyricText = trimmed.substring(lastEnd).trim()
                    for (ts in timestamps) {
                        lines.add(LyricLine(timeMs = ts, text = lyricText))
                    }
                } else if (!trimmed.startsWith("[ti:") && !trimmed.startsWith("[ar:") && !trimmed.startsWith("[al:")) {
                    // Plain text line
                    lines.add(LyricLine(timeMs = 0L, text = trimmed))
                }
            }
        }

        val sortedLines = lines.sortedBy { it.timeMs }
        return SongLyrics(
            isSynced = isSynced,
            lines = sortedLines,
            plainText = if (isSynced) sortedLines.joinToString("\n") { it.text } else content
        )
    }

    fun getActiveLyricIndex(lyrics: List<LyricLine>, currentPositionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        var activeIndex = -1
        for (i in lyrics.indices) {
            if (lyrics[i].timeMs <= currentPositionMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }
}
