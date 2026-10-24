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
    val rawLrc: String = "",
    val source: String = "NONE" // "LOCAL", "CACHED", "LRCLIB", "NONE"
)

object LyricsManager {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")

    suspend fun getLyricsForSong(
        song: Song,
        playlistDao: PlaylistDao,
        forceRefresh: Boolean = false
    ): SongLyrics = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            // 1. Check local file storage (.lrc)
            val localLrc = loadLocalLrcFile(song.data)
            if (localLrc != null && localLrc.isSynced && localLrc.lines.isNotEmpty()) {
                return@withContext localLrc.copy(source = "LOCAL")
            }

            // 2. Check Room Database cache for valid synced lyrics
            val cached = playlistDao.getLyricsForSong(song.id)
            if (cached != null) {
                if (cached.isSynced && cached.syncedLyrics.isNotBlank()) {
                    val parsed = parseLrcContent(cached.syncedLyrics)
                    if (parsed.isSynced && parsed.lines.isNotEmpty()) return@withContext parsed.copy(source = cached.source)
                }
                if (cached.plainLyrics.isNotBlank()) return@withContext SongLyrics(plainText = cached.plainLyrics, source = cached.source)
            }
        }

        // 3. Fetch from LRCLIB Online API (auto-retrieves synced karaoke lyrics without requiring manual refresh)
        val onlineResult = fetchFromLrclib(song.title, song.artist, song.duration)
        if (onlineResult != null) {
            // Cache into Room DB
            val entity = SongLyricsEntity(
                songId = song.id,
                isSynced = onlineResult.isSynced,
                syncedLyrics = if (onlineResult.isSynced) onlineResult.rawLrc else "",
                plainLyrics = onlineResult.plainText,
                source = "LRCLIB"
            )
            playlistDao.insertLyrics(entity)
            return@withContext onlineResult.copy(source = "LRCLIB")
        }

        // 4. Fallback: If online search returns nothing (e.g. offline), use cached plain lyrics or local file
        val cachedFallback = playlistDao.getLyricsForSong(song.id)
        if (cachedFallback?.isSynced == true && cachedFallback.syncedLyrics.isNotBlank()) {
            val parsed = parseLrcContent(cachedFallback.syncedLyrics)
            if (parsed.isSynced) return@withContext parsed.copy(source = cachedFallback.source)
        }
        if (cachedFallback != null && cachedFallback.plainLyrics.isNotBlank()) {
            return@withContext SongLyrics(
                isSynced = false,
                lines = emptyList(),
                plainText = cachedFallback.plainLyrics,
                rawLrc = "",
                source = cachedFallback.source
            )
        }
        val localLrcFallback = loadLocalLrcFile(song.data)
        if (localLrcFallback != null) {
            return@withContext localLrcFallback.copy(source = "LOCAL")
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
            .replace(Regex("(?i)\\.mp3|\\.m4a|\\.flac|\\.wav|\\.aac|\\.ogg"), "")
            .replace(Regex("(?i)\\(official\\s*(music)?\\s*video\\)"), "")
            .replace(Regex("(?i)\\[official\\s*(music)?\\s*video\\]"), "")
            .replace(Regex("(?i)\\(lyrics?\\)"), "")
            .replace(Regex("(?i)\\[lyrics?\\]"), "")
            .replace(Regex("(?i)\\(audio\\)"), "")
            .replace(Regex("(?i)\\[audio\\]"), "")
            .replace(Regex("(?i)\\(visualizer\\)"), "")
            .replace(Regex("(?i)\\(from\\s+.*\\)"), "")
            .replace(Regex("(?i)\\[from\\s+.*\\]"), "")
            .replace(Regex("(?i)\\b(ft\\.?|feat\\.?)\\s+.*"), "")
            .replace(Regex("(?i)\\s*•\\s*.*"), "")
            .trim()
    }

    private fun fetchFromLrclib(rawTitle: String, rawArtist: String, durationMs: Long): SongLyrics? {
        val cleanTitle = sanitizeQuery(rawTitle)
        val cleanArtist = if (rawArtist.contains("unknown", ignoreCase = true)) "" else sanitizeQuery(rawArtist)
        val primaryArtist = cleanArtist.split(",", "&", "/", ";").firstOrNull()?.trim() ?: cleanArtist
        val durationSec = if (durationMs > 0) durationMs / 1000 else 0

        // Attempt 1: Exact Match /api/get with cleanTitle & primaryArtist
        try {
            val queryUrl = buildString {
                append("https://lrclib.net/api/get?track_name=")
                append(URLEncoder.encode(cleanTitle, "UTF-8"))
                if (primaryArtist.isNotBlank()) {
                    append("&artist_name=")
                    append(URLEncoder.encode(primaryArtist, "UTF-8"))
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
                    return SongLyrics(isSynced = false, lines = emptyList(), plainText = plain, rawLrc = "")
                }
            }
        } catch (e: Exception) {
            // Fallback to search
        }

        // Attempt 2: Search Match with cleanTitle and primaryArtist
        try {
            val searchQuery = "$cleanTitle $primaryArtist".trim()
            val searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(searchQuery, "UTF-8")
            val response = executeHttpGet(searchUrl)
            if (response != null) {
                val array = JSONArray(response)
                var fallbackPlain: String? = null
                for (i in 0 until minOf(array.length(), 5)) {
                    val item = array.getJSONObject(i)
                    val synced = item.optString("syncedLyrics", "")
                    val plain = item.optString("plainLyrics", "")

                    if (synced.isNotBlank()) {
                        return parseLrcContent(synced)
                    } else if (plain.isNotBlank() && fallbackPlain == null) {
                        fallbackPlain = plain
                    }
                }
                if (fallbackPlain != null) {
                    return SongLyrics(isSynced = false, lines = emptyList(), plainText = fallbackPlain, rawLrc = "")
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        // Attempt 3: Search Match with cleanTitle only
        try {
            val searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(cleanTitle, "UTF-8")
            val response = executeHttpGet(searchUrl)
            if (response != null) {
                val array = JSONArray(response)
                var fallbackPlain: String? = null
                for (i in 0 until minOf(array.length(), 5)) {
                    val item = array.getJSONObject(i)
                    val synced = item.optString("syncedLyrics", "")
                    val plain = item.optString("plainLyrics", "")

                    if (synced.isNotBlank()) {
                        return parseLrcContent(synced)
                    } else if (plain.isNotBlank() && fallbackPlain == null) {
                        fallbackPlain = plain
                    }
                }
                if (fallbackPlain != null) {
                    return SongLyrics(isSynced = false, lines = emptyList(), plainText = fallbackPlain, rawLrc = "")
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
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
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
        val offset = Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)
            .find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        content.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
            val matcher = TIME_TAG_PATTERN.matcher(line)
            val timestamps = mutableListOf<Long>()
            var lastEnd = 0
            while (matcher.find()) {
                val minutes = matcher.group(1)!!.toLong()
                val seconds = matcher.group(2)!!.toLong()
                val fraction = matcher.group(3).orEmpty().padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                timestamps.add(((minutes * 60 + seconds) * 1000 + fraction + offset).coerceAtLeast(0L))
                lastEnd = matcher.end()
            }
            if (timestamps.isNotEmpty()) {
                // Keep empty timestamped lines: they clear the previous lyric during instrumentals.
                val text = line.substring(lastEnd).trim()
                timestamps.forEach { lines.add(LyricLine(it, text)) }
            }
        }
        val sorted = lines.sortedBy { it.timeMs }
        return SongLyrics(isSynced = sorted.isNotEmpty(), lines = sorted,
            plainText = if (sorted.isNotEmpty()) sorted.joinToString("\n") { it.text }
                else content.lineSequence().filterNot { it.trim().startsWith("[") }.joinToString("\n").trim(),
            rawLrc = if (sorted.isNotEmpty()) content else "")
    }

    fun getActiveLyricIndex(lyrics: List<LyricLine>, currentPositionMs: Long): Int {
        var low = 0
        var high = lyrics.lastIndex
        var active = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lyrics[middle].timeMs <= currentPositionMs) { active = middle; low = middle + 1 }
            else high = middle - 1
        }
        return active
    }
}
