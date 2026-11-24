package com.example.auraplay

import android.content.Context
import android.net.Uri
import com.example.auraplay.data.PlaylistDao
import com.example.auraplay.data.Song
import com.example.auraplay.data.SongLyricsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
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

internal val SongLyrics.hasText: Boolean
    get() = lyricText(plainText) != null || lines.any { lyricText(it.text) != null }

private fun readLimited(input: InputStream, limit: Int): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return output.toByteArray()
        if (output.size() + read > limit) return null
        output.write(buffer, 0, read)
    }
}

internal fun readLyricDocument(input: InputStream): String? {
    val bytes = readLimited(input, 512_000) ?: return null
    val hasUtf16Bom = bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
        (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))
    val charset = if (hasUtf16Bom) Charsets.UTF_16 else Charsets.UTF_8
    val text = runCatching { charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
    return text?.trimStart('\uFEFF')?.takeUnless { value ->
        value.any { it.isISOControl() && it !in "\r\n\t" }
    }
}

object LyricsManager {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val inlineTimestamps = Regex("<\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?>")
    private val metadataLine = Regex("^\\[[a-z]{1,12}:.*]$", RegexOption.IGNORE_CASE)

    internal suspend fun getLyricsForSong(
        context: Context,
        song: Song,
        playlistDao: PlaylistDao,
        forceRefresh: Boolean = false,
        search: LyricSearchTerms? = null
    ): SongLyrics = withContext(Dispatchers.IO) {
        val cached = playlistDao.getLyricsForSong(song.id)?.let { record ->
            val parsed = lyricText(record.syncedLyrics)?.let(::parseLrcContent)
            val lyrics = parsed?.takeIf { it.isSynced && it.hasText }
                ?: SongLyrics(plainText = lyricText(record.plainLyrics).orEmpty())
            lyrics.copy(source = record.source).takeIf { it.hasText }
        }
        if (cached?.source == "IMPORTED" && search == null && !forceRefresh) return@withContext cached

        val local = loadLocalLrcFile(song.data)
        val refreshOnline = forceRefresh || search != null
        if (!refreshOnline) {
            local?.takeIf { it.isSynced }?.let { return@withContext it }
            cached?.takeIf { it.isSynced }?.let { return@withContext it }
        }
        val embedded = readEmbeddedLyrics(context, song)
        if (!refreshOnline) {
            embedded?.takeIf { it.isSynced }?.let { return@withContext it }
            cached?.let { return@withContext local ?: embedded ?: it }
        }

        val online = fetchFromLrclib(song, search)
        if (online != null) {
            if (online.isSynced || (local == null && embedded == null) || refreshOnline) {
                saveLyrics(song.id, online, playlistDao)
                return@withContext online
            }
        }
        local ?: embedded ?: cached ?: online ?: SongLyrics()
    }

    suspend fun importLyrics(context: Context, songId: Long, uri: Uri, playlistDao: PlaylistDao): SongLyrics =
        withContext(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.use(::readLyricDocument)
            val lyrics = text?.let(::parseLrcContent)?.takeIf { it.hasText }
                ?.copy(source = "IMPORTED") ?: error("Choose a readable .lrc or .txt lyrics file.")
            saveLyrics(songId, lyrics, playlistDao)
            lyrics
        }

    private suspend fun saveLyrics(songId: Long, lyrics: SongLyrics, playlistDao: PlaylistDao) {
        playlistDao.insertLyrics(SongLyricsEntity(songId, lyrics.isSynced, lyrics.rawLrc, lyrics.plainText, lyrics.source))
    }

    private fun loadLocalLrcFile(filePath: String): SongLyrics? {
        val audioFile = File(filePath.removePrefix("file://"))
        val folder = audioFile.parentFile ?: return null
        for (extension in listOf("lrc", "LRC")) {
            val file = File(folder, "${audioFile.nameWithoutExtension}.$extension")
            val lyrics = runCatching { file.inputStream().use(::readLyricDocument)?.let(::parseLrcContent) }.getOrNull()
            if (lyrics?.hasText == true) return lyrics.copy(source = "LOCAL")
        }
        return null
    }

    private suspend fun fetchFromLrclib(song: Song, search: LyricSearchTerms?): SongLyrics? {
        val terms = if (search != null) listOf(search) else lyricSearchTerms(song).take(4)
        val requested = mutableSetOf<Map<String, String>>()
        var best: LyricMatch? = null
        val first = terms.firstOrNull() ?: return null
        if (search == null && first.artist.isNotBlank() && song.duration > 0L &&
            song.album.isNotBlank() && !song.album.equals("Unknown Album", true)) {
            val exact = request("/api/get-cached", mapOf(
                "track_name" to first.title, "artist_name" to first.artist,
                "album_name" to song.album, "duration" to (song.duration / 1000).toString()
            ))
            if (exact == null) return null
            val exactLyrics = runCatching { JSONObject(exact).toLyrics() }.getOrNull()
            if (exactLyrics?.isSynced == true) return exactLyrics
            if (exactLyrics != null) best = LyricMatch(exactLyrics, 50_000L)
        }
        for (term in terms) {
            val titleQuery = mapOf("track_name" to term.title)
            val queries = if (term.artist.isBlank()) listOf(titleQuery)
                else listOf(titleQuery + ("artist_name" to term.artist), titleQuery)
            for (query in queries) {
                if (!requested.add(query)) continue
                val response = request("/api/search", query) ?: return best?.lyrics
                val matches = runCatching { JSONArray(response) }.getOrNull() ?: continue
                for (index in 0 until matches.length()) {
                    val item = matches.optJSONObject(index) ?: continue
                    val lyrics = item.toLyrics() ?: continue
                    val duration = item.optDouble("duration").takeIf { it.isFinite() && it > 0 }
                        ?.let { (it * 1000).toLong() }
                    val match = rankLyricMatch(term, song.duration, item.optString("trackName"),
                        item.optString("artistName"), duration, lyrics) ?: continue
                    if (match.score > (best?.score ?: Long.MIN_VALUE)) best = match
                }
                if (best?.lyrics?.isSynced == true) return best.lyrics
                if (requested.size >= 5) return best?.lyrics
            }
        }
        return best?.lyrics
    }

    private fun JSONObject.toLyrics(): SongLyrics? {
        if (optBoolean("instrumental")) return null
        val synced = lyricText(optString("syncedLyrics"))
        val plain = lyricText(optString("plainLyrics"))
        val parsed = synced?.let(::parseLrcContent)
        if (parsed?.isSynced == true && parsed.hasText) {
            return parsed.copy(plainText = plain ?: parsed.plainText, source = "LRCLIB")
        }
        return (plain ?: parsed?.plainText?.let(::lyricText))?.let { SongLyrics(plainText = it, source = "LRCLIB") }
    }

    // Empty means no match. Null means unavailable or rate limited, so avoid fanning out more requests.
    private suspend fun request(path: String, query: Map<String, String>): String? {
        currentCoroutineContext().ensureActive()
        val encoded = query.entries.joinToString("&") { (name, value) -> "$name=" + URLEncoder.encode(value, "UTF-8") }
        return runCatching {
            val connection = URL("https://lrclib.net$path?$encoded").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 3500
                connection.readTimeout = 4500
                connection.setRequestProperty("User-Agent", "AuraPlay/1.1.2 (https://github.com/Shahriar811/AuraPlay)")
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> connection.inputStream.use { readLimited(it, 2_000_000)?.toString(Charsets.UTF_8) }
                    HttpURLConnection.HTTP_NOT_FOUND -> ""
                    else -> null
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    fun parseLrcContent(content: String): SongLyrics {
        val lines = mutableListOf<LyricLine>()
        val offset = Regex("\\[offset:([+-]?\\d+)\\]", RegexOption.IGNORE_CASE)
            .find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        content.trimStart('\uFEFF').lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
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
                val text = line.substring(lastEnd).replace(inlineTimestamps, "").trim()
                timestamps.forEach { lines.add(LyricLine(it, text)) }
            }
        }
        val sorted = lines.sortedBy { it.timeMs }
        return SongLyrics(isSynced = sorted.isNotEmpty(), lines = sorted,
            plainText = if (sorted.isNotEmpty()) sorted.joinToString("\n") { it.text }
                else content.trimStart('\uFEFF').lineSequence().filterNot { metadataLine.matches(it.trim()) }.joinToString("\n").trim(),
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
