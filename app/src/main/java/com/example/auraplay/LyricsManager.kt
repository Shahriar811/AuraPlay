package com.example.auraplay

import android.content.Context
import com.example.auraplay.data.PlaylistDao
import com.example.auraplay.data.Song
import com.example.auraplay.data.SongLyricsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class SongLyrics(
    val isSynced: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
    val plainText: String = "",
    val source: String = "NONE" // "LOCAL", "CACHED", "LRCLIB", "NONE"
)

object LyricsManager {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{2,3}))?\\]")

    suspend fun getLyricsForSong(
        song: Song,
        playlistDao: PlaylistDao,
        forceRefresh: Boolean = false
    ): SongLyrics = withContext(Dispatchers.IO) {
        // 1. Check local file storage (.lrc)
        if (!forceRefresh) {
            val localLrc = loadLocalLrcFile(song.data)
            if (localLrc != null) {
                return@withContext localLrc.copy(source = "LOCAL")
            }

            // 2. Check Room Database cache
            val cached = playlistDao.getLyricsForSong(song.id)
            if (cached != null) {
                return@withContext if (cached.isSynced && cached.syncedLyrics.isNotBlank()) {
                    parseLrcContent(cached.syncedLyrics).copy(source = cached.source)
                } else {
                    SongLyrics(
                        isSynced = false,
                        lines = emptyList(),
                        plainText = cached.plainLyrics,
                        source = cached.source
                    )
                }
            }
        }

        // 3. Fetch from LRCLIB Online API
        val onlineResult = fetchFromLrclib(song.title, song.artist, song.duration)
        if (onlineResult != null) {
            // Cache into Room DB
            val entity = SongLyricsEntity(
                songId = song.id,
                isSynced = onlineResult.isSynced,
                syncedLyrics = if (onlineResult.isSynced) onlineResult.plainText else "",
                plainLyrics = onlineResult.plainText,
                source = "LRCLIB"
            )
            playlistDao.insertLyrics(entity)
            return@withContext onlineResult.copy(source = "LRCLIB")
        }

        SongLyrics(source = "NONE")
    }

    private fun loadLocalLrcFile(filePath: String): SongLyrics? {
        if (filePath.isBlank()) return null
        try {
            val audioFile = File(filePath)
            val parentDir = audioFile.parentFile
            val baseName = audioFile.nameWithoutExtension

            if (parentDir != null && parentDir.exists()) {
                val lrcFile = File(parentDir, "$baseName.lrc")
                if (lrcFile.exists() && lrcFile.canRead()) {
                    return parseLrcContent(lrcFile.readText())
                }
                val altLrcFile = File(parentDir, "$baseName.LRC")
                if (altLrcFile.exists() && altLrcFile.canRead()) {
                    return parseLrcContent(altLrcFile.readText())
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
        return null
    }

    private fun sanitizeQuery(text: String): String {
        return text
            .replace(Regex("(?i)\\.mp3|\\.m4a|\\.flac|\\.wav|\\.aac"), "")
            .replace(Regex("(?i)\\(official\\s*(music)?\\s*video\\)"), "")
            .replace(Regex("(?i)\\[official\\s*(music)?\\s*video\\]"), "")
            .replace(Regex("(?i)\\(lyrics?\\)"), "")
            .replace(Regex("(?i)\\[lyrics?\\]"), "")
            .replace(Regex("(?i)\\(audio\\)"), "")
            .replace(Regex("(?i)\\[audio\\]"), "")
            .replace(Regex("(?i)\\bft\\.?\\s+.*|\\bfeat\\.?\\s+.*"), "")
            .trim()
    }

    private fun fetchFromLrclib(rawTitle: String, rawArtist: String, durationMs: Long): SongLyrics? {
        val cleanTitle = sanitizeQuery(rawTitle)
        val cleanArtist = if (rawArtist.contains("unknown", ignoreCase = true)) "" else sanitizeQuery(rawArtist)
        val durationSec = if (durationMs > 0) durationMs / 1000 else 0

        // Attempt 1: Exact Match /api/get
        try {
            val queryUrl = buildString {
                append("https://lrclib.net/api/get?track_name=")
                append(URLEncoder.encode(cleanTitle, "UTF-8"))
                if (cleanArtist.isNotBlank()) {
                    append("&artist_name=")
                    append(URLEncoder.encode(cleanArtist, "UTF-8"))
                }
                if (durationSec > 0) {
                    append("&duration=")
                    append(durationSec)
                }
            }

            val response = executeHttpGet(queryUrl)
            if (response != null) {
                val json = JSONObject(response)
                val synced = json.optString("syncedLyrics", "")
                val plain = json.optString("plainLyrics", "")

                if (synced.isNotBlank()) {
                    return parseLrcContent(synced)
                } else if (plain.isNotBlank()) {
                    return SongLyrics(isSynced = false, lines = emptyList(), plainText = plain)
                }
            }
        } catch (e: Exception) {
            // Try search fallback
        }

        // Attempt 2: Search Match /api/search
        try {
            val searchQuery = "$cleanTitle $cleanArtist".trim()
            val searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(searchQuery, "UTF-8")
            val response = executeHttpGet(searchUrl)
            if (response != null) {
                val array = JSONArray(response)
                if (array.length() > 0) {
                    for (i in 0 until minOf(array.length(), 3)) {
                        val item = array.getJSONObject(i)
                        val synced = item.optString("syncedLyrics", "")
                        val plain = item.optString("plainLyrics", "")

                        if (synced.isNotBlank()) {
                            return parseLrcContent(synced)
                        } else if (plain.isNotBlank() && i == 0) {
                            return SongLyrics(isSynced = false, lines = emptyList(), plainText = plain)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // No online lyrics found
        }

        return null
    }

    private fun executeHttpGet(urlString: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", "AuraPlay-Android-MusicPlayer/2.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                sb.toString()
            } else null
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
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
                    if (lyricText.isNotBlank()) {
                        for (ts in timestamps) {
                            lines.add(LyricLine(timeMs = ts, text = lyricText))
                        }
                    }
                } else if (!trimmed.startsWith("[ti:") && !trimmed.startsWith("[ar:") && !trimmed.startsWith("[al:") && !trimmed.startsWith("[by:")) {
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
