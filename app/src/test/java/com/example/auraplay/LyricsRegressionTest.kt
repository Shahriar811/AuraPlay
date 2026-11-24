package com.example.auraplay

import com.example.auraplay.data.Song
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class LyricsRegressionTest {
    @Test fun metadataIsNotRenderedAsLyricAtTimeZero() {
        val lyrics = LyricsManager.parseLrcContent("[ar:Artist]\n[offset:100]\n[00:01.50]Hello")
        assertEquals(listOf(LyricLine(1600, "Hello")), lyrics.lines)
    }

    @Test fun multipleTimestampsAndDifferentFractionLengthsAreParsed() {
        val lyrics = LyricsManager.parseLrcContent("[00:02.1][00:04.125]Again\n[00:03.12]Between")
        assertEquals(listOf(2100L, 3120L, 4125L), lyrics.lines.map { it.timeMs })
    }

    @Test fun emptyTimedLineClearsPreviousLyric() {
        val lyrics = LyricsManager.parseLrcContent("[00:00.00]Hello\n[00:02.00]\n[00:05.00]Next")
        assertEquals("", lyrics.lines[LyricsManager.getActiveLyricIndex(lyrics.lines, 3000)].text)
    }

    @Test fun activeIndexHandlesSeeksAndDuplicateTimes() {
        val lines = listOf(LyricLine(1000, "A"), LyricLine(1000, "B"), LyricLine(3000, "C"))
        assertEquals(-1, LyricsManager.getActiveLyricIndex(lines, 999))
        assertEquals(1, LyricsManager.getActiveLyricIndex(lines, 1000))
        assertEquals(2, LyricsManager.getActiveLyricIndex(lines, Long.MAX_VALUE))
        assertEquals(-1, LyricsManager.getActiveLyricIndex(emptyList(), 0))
    }

    @Test fun plainLyricsArePreservedWithoutMetadata() {
        val lyrics = LyricsManager.parseLrcContent("[ti:Title]\nFirst line\nSecond line")
        assertFalse(lyrics.isSynced)
        assertEquals("First line\nSecond line", lyrics.plainText)
    }

    @Test fun negativeOffsetDoesNotProduceNegativeTime() {
        val lyrics = LyricsManager.parseLrcContent("[offset:-2000]\n[00:01.00]Start")
        assertEquals(0L, lyrics.lines.single().timeMs)
    }

    @Test fun providerNullIsMissingAndPlainBanglaIsPreserved() {
        assertNull(lyricText("null"))
        assertEquals("[Chorus]\nআমার গান", LyricsManager.parseLrcContent("\uFEFF[ti:Song]\n[Chorus]\nআমার গান").plainText)
    }

    @Test fun bilingualTagsAndDownloadNamesProduceAlternateSearchTerms() {
        val song = Song(1, "আমার গান (Amar Gaan)", "Unknown Artist", albumArtUri = null,
            duration = 180_000, data = "/Music/01. Artist - Amar Gaan [320kbps].mp3")
        val terms = lyricSearchTerms(song)
        assertTrue(LyricSearchTerms("আমার গান", "") in terms)
        assertTrue(LyricSearchTerms("Amar Gaan", "Artist") in terms)
    }

    @Test fun downloadedVersionsRequireMatchingArtistsAndOnlyUseReliableTimings() {
        val terms = LyricSearchTerms("আমার গান", "My Artist")
        val lyrics = LyricsManager.parseLrcContent("[0:02.00]আমার গান")
        assertNull(rankLyricMatch(terms, 180_000, "আমার গান", "Another Artist", 180_000, lyrics))
        assertTrue(rankLyricMatch(terms, 180_000, "আমার গান", "My Artist", 181_000, lyrics)!!.lyrics.isSynced)
        val slower = rankLyricMatch(terms, 180_000, "আমার গান", "My Artist", 189_000, lyrics)!!.lyrics
        assertFalse(slower.isSynced)
        assertEquals("আমার গান", slower.plainText)
    }

    @Test fun embeddedAndImportedUnicodeLyricsDecodeWithoutChangingTheText() {
        val bangla = "আমার গান"
        val frame = byteArrayOf(3, 'b'.code.toByte(), 'e'.code.toByte(), 'n'.code.toByte(), 0) + bangla.toByteArray()
        assertEquals(bangla, decodeId3Lyrics(frame))
        assertEquals(bangla, readLyricDocument(ByteArrayInputStream(bangla.toByteArray(Charsets.UTF_16))))
        assertNull(readLyricDocument(ByteArrayInputStream(byteArrayOf(40, 0, 16))))
    }
}
