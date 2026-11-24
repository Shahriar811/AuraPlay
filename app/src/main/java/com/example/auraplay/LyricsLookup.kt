package com.example.auraplay

import com.example.auraplay.data.Song
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal data class LyricSearchTerms(val title: String, val artist: String)
internal data class LyricMatch(val lyrics: SongLyrics, val score: Long)

internal fun lyricText(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }

private val trackNumber = Regex("^\\d{1,3}(?:[.)]\\s*|\\s*[-_]\\s*|\\s+)")
private val fileSuffix = Regex("(?i)\\.(mp3|m4a|flac|wav|aac|ogg|opus|wma|aiff|mp4)$")
private val uploadQualifier = Regex("(?i)[\\[(]\\s*(?:official(?:\\s+music)?\\s+(?:video|audio)|lyrics?|audio|visuali[sz]er|\\d+\\s*kbps)\\s*[)\\]]")
private val artistSeparator = Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring|&|and)\\s+|[,;/]")
private val normalizedSeparator = Regex("[^\\p{L}\\p{M}\\p{N}]+")
private val bengali = Regex("[\u0980-\u09FF]")

private fun cleanTag(value: String): String = value.replace(fileSuffix, "").replace(uploadQualifier, "").trim()
private fun lookupKey(value: String): String = Normalizer.normalize(cleanTag(value), Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace("\u200C", "").replace("\u200D", "").replace(normalizedSeparator, " ").trim()

private fun usefulTag(value: String): String = cleanTag(value).takeUnless {
    lookupKey(it) in setOf("unknown", "unknown artist", "unknown title", "unknown album", "various artists")
}.orEmpty()

internal fun lyricSearchTerms(song: Song): List<LyricSearchTerms> {
    val title = usefulTag(song.title)
    val artist = usefulTag(song.artist)
    val fileTitle = song.data.takeUnless { it.startsWith("content:") }
        ?.substringAfterLast('/')?.substringAfterLast('\\')?.replace(trackNumber, "")?.let(::cleanTag).orEmpty()
    val parts = fileTitle.split(" - ", limit = 2)
    val fileTerms = if (parts.size == 2) {
        val reverse = title.isNotBlank() && lookupKey(parts[0]) == lookupKey(title) ||
            artist.isNotBlank() && lookupKey(parts[1]) == lookupKey(artist)
        if (reverse) LyricSearchTerms(parts[0], parts[1]) else LyricSearchTerms(parts[1], parts[0])
    } else LyricSearchTerms(fileTitle, artist)
    val mixedTitle = title.split('(', '[', '|').map { it.trim(' ', ')', ']') }.filter(String::isNotBlank)
    val aliases = if (mixedTitle.any(bengali::containsMatchIn) && mixedTitle.any { !bengali.containsMatchIn(it) }) {
        mixedTitle.map { LyricSearchTerms(it, artist) }
    } else emptyList()
    return (listOf(LyricSearchTerms(title, artist)) + aliases + fileTerms)
        .filter { it.title.isNotBlank() && it.title.length <= 200 && it.artist.length <= 200 }
        .distinctBy { lookupKey(it.title) to lookupKey(it.artist) }
}

internal fun rankLyricMatch(
    terms: LyricSearchTerms, songDuration: Long, title: String, artist: String, duration: Long?, lyrics: SongLyrics
): LyricMatch? {
    if (!lyrics.hasText || lookupKey(terms.title) != lookupKey(title)) return null
    val expectedArtist = lookupKey(terms.artist)
    val candidateArtist = lookupKey(artist)
    val artistMatches = expectedArtist.isNotEmpty() && (expectedArtist == candidateArtist ||
        terms.artist.split(artistSeparator).map(::lookupKey).intersect(artist.split(artistSeparator).map(::lookupKey).toSet())
            .any(String::isNotEmpty))
    val gap = if (duration != null && songDuration > 0L) abs(duration - songDuration) else null
    if (expectedArtist.isNotEmpty() && !artistMatches) return null
    if (expectedArtist.isEmpty() && (gap == null || gap > 3_000L)) return null
    if (gap != null && gap > 30_000L) return null
    val reliableTimings = lyrics.isSynced && (gap == null || gap <= 4_000L)
    val result = if (lyrics.isSynced && !reliableTimings) {
        SongLyrics(plainText = lyrics.plainText.ifBlank { lyrics.lines.joinToString("\n") { it.text } }, source = lyrics.source)
    } else lyrics
    val score = (if (artistMatches) 50_000 else 0) + (if (reliableTimings) 25_000 else 0) - (gap ?: 3_000L)
    return LyricMatch(result, score)
}
