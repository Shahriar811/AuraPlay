package com.example.auraplay

import com.example.auraplay.data.Song
import com.example.auraplay.features.*
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class FeatureRegressionTest {
    private val now = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
    private val day = 86_400_000L
    private fun song(id: Long) = Song(id, "Song $id", "Artist", duration = 60_000, data = "/Music/$id.mp3", albumArtUri = null)
    private fun event(id: String, song: Long, start: Long, duration: Long, artist: String = "Artist") = ListeningEvent(id, song, "Song $song", artist, start, duration)

    @Test fun resumeRetainsDuplicateQueueOccurrencesAndPosition() {
        val restored = restoreQueue(listOf(1, 2, 1), 2, 12345, listOf(song(1), song(2)))!!
        assertEquals(listOf(1L, 2L, 1L), restored.songs.map { it.id })
        assertEquals(2, restored.index); assertEquals(12345L, restored.position)
    }
    @Test fun resumeSkipsDeletedCurrentTrackAndStartsNextAtZero() {
        val restored = restoreQueue(listOf(1, 99, 2), 1, 50000, listOf(song(1), song(2)))!!
        assertEquals(1, restored.index); assertEquals(2L, restored.songs[restored.index].id); assertEquals(0L, restored.position)
        assertNull(restoreQueue(listOf(99), 0, 0, listOf(song(1))))
    }
    @Test fun resumeClampsPositionToDuration() {
        assertEquals(60000L, restoreQueue(listOf(1), 0, Long.MAX_VALUE, listOf(song(1)))!!.position)
        assertEquals(0L, restoreQueue(listOf(1), 0, -500, listOf(song(1)))!!.position)
    }
    @Test fun resumePreservesShuffleOrderWhenTracksDisappear() {
        val restored = restoreQueue(listOf(1, 99, 2, 1), 2, 250, listOf(song(1), song(2)), listOf(3, 1, 0, 2))!!
        assertEquals(listOf(2, 0, 1), restored.order)
        assertEquals(listOf(0, 1), restoreQueue(listOf(1, 2), 0, 0, listOf(song(1), song(2)), listOf(0, 0))!!.order)
    }
    @Test fun folderSelectionUsesPathBoundariesAndExclusionWins() {
        val settings = FeatureSettings(selectedFoldersOnly = true, includedFolders = setOf("/Music"), excludedFolders = setOf("/Music/Voice"))
        assertTrue(folderAllowed("/Music/Albums/A", settings)); assertTrue(folderAllowed("/Music", settings))
        assertFalse(folderAllowed("/Music2", settings)); assertFalse(folderAllowed("/Music/Voice/Recordings", settings))
        assertFalse(folderAllowed("/Music", settings.copy(includedFolders = emptySet())))
        assertTrue(pathContains("/Music/", "/Music/Albums")); assertTrue(pathContains("/", "/Music"))
    }
    @Test fun allFolderModeStillHonorsExclusions() {
        assertTrue(folderAllowed("/Downloads", FeatureSettings()))
        assertFalse(folderAllowed("/Downloads/A", FeatureSettings(excludedFolders = setOf("/Downloads"))))
    }
    @Test fun smartPlaylistsUseRealAddedAndLastPlayedDates() {
        val songs = listOf(song(1).copy(dateAdded = (now - day) / 1000, isFavorite = true),
            song(2).copy(dateAdded = (now - 40 * day) / 1000, isFavorite = true, lastPlayedTimestamp = now - day, playCount = 1),
            song(3).copy(isFavorite = true, lastPlayedTimestamp = now - 40 * day, playCount = 2))
        assertEquals(listOf(1L), smartSongs(SmartPlaylist.RECENTLY_ADDED, songs, emptyList(), now).map { it.id })
        assertEquals(listOf(1L), smartSongs(SmartPlaylist.NEVER_PLAYED, songs, emptyList(), now).map { it.id })
        assertEquals(listOf(1L, 3L), smartSongs(SmartPlaylist.FORGOTTEN_FAVORITES, songs, emptyList(), now).map { it.id })
    }
    @Test fun weeklyFavoritesRankListeningTimeAndIgnoreOldAndMissingTracks() {
        val events = listOf(event("a", 1, now - day, 1000), event("b", 2, now - day, 4000), event("c", 1, now - 8 * day, 9000), event("d", 99, now, 99999))
        assertEquals(listOf(2L, 1L), smartSongs(SmartPlaylist.WEEKLY_FAVORITES, listOf(song(1), song(2)), events, now).map { it.id })
    }
    @Test fun listeningSummaryUsesLocalDayAndCalendarMonth() {
        val time = Instant.parse("2026-10-01T00:30:00Z").toEpochMilli()
        val events = listOf(event("a", 1, time - 2 * 3600000, 120000), event("b", 2, time - 1000, 60000, "Other"))
        val utc = summarizeListening(events, time, ZoneId.of("UTC"))
        assertEquals(60000L, utc.todayMs); assertEquals(180000L, utc.weekMs); assertEquals(60000L, utc.monthMs)
        assertEquals(7, utc.daily.size); assertEquals("Artist", utc.artists.first().first)
        val dhaka = summarizeListening(events, time, ZoneId.of("Asia/Dhaka"))
        assertEquals(180000L, dhaka.todayMs); assertEquals(180000L, dhaka.monthMs)
    }
    @Test fun listeningSummaryRejectsFutureOrNegativeEntries() {
        val summary = summarizeListening(listOf(event("a", 1, now + 1, 9999), event("b", 2, now, -1)), now)
        assertEquals(0L, summary.weekMs); assertTrue(summary.artists.isEmpty())
    }
    @Test fun listeningIntervalsSplitAcrossMidnightAndDst() {
        val start = Instant.parse("2026-10-18T23:59:30Z").toEpochMilli()
        val parts = listeningSlices(start, 90000, ZoneId.of("UTC"))
        assertEquals(listOf(30000L, 60000L), parts.map { it.durationMs })
        val dstStart = Instant.parse("2026-03-08T05:00:00Z").toEpochMilli()
        val dstParts = listeningSlices(dstStart, 25 * 3600000, ZoneId.of("America/New_York"))
        assertEquals(23 * 3600000L, dstParts.first().durationMs)
        assertEquals(25 * 3600000L, dstParts.sumOf { it.durationMs })
        assertTrue(listeningSlices(start, 0).isEmpty())
    }
    @Test fun replayGainUsesTagsLimitsAndPeakProtection() {
        assertEquals(1.0, replayGainMultiplier(null, null), 0.0)
        assertEquals(1.0, replayGainMultiplier(Double.NaN, null), 0.0)
        assertEquals(0.501187, replayGainMultiplier(-6.0, null), 0.000001)
        assertEquals(1.25, replayGainMultiplier(6.0, 0.8), 0.000001)
        assertTrue(replayGainMultiplier(1000.0, null) < 8.0)
    }
    @Test fun pcmGainNeverWrapsAtFullScale() {
        assertEquals(32767.toShort(), scalePcm16(30000, 2.0))
        assertEquals((-32768).toShort(), scalePcm16(-30000, 2.0))
        assertEquals(500.toShort(), scalePcm16(1000, 0.5))
    }
    @Test fun backupMatcherRejectsBlankAndAmbiguousPaths() {
        val matcher = SongMatcher(listOf(song(1).copy(data = ""), song(2).copy(data = "")))
        assertNull(matcher.match("", "Unavailable", "Artist", 60000))
        val duplicates = SongMatcher(listOf(song(1).copy(title = "Same"), song(2).copy(title = "Same")))
        assertNull(duplicates.match("/Other/unknown.mp3", "Same", "Artist", 60000))
    }
    @Test fun backupMatcherFindsMovedFilesEvenWithAppTitleEdits() {
        val matcher = SongMatcher(listOf(song(7).copy(data = "/New/album/song.mp3")))
        assertEquals(7L, matcher.match("/Old/album/song.mp3", "Edited title", "Changed artist", 60000)!!.id)
        assertNull(matcher.match("/Old/album/song.mp3", "Edited title", "Changed artist", 30000))
    }
    @Test fun backupMatcherNeverReliesOnContentUriIds() {
        val matcher = SongMatcher(listOf(song(1).copy(data = "content://media/external/audio/media/10")))
        assertNull(matcher.match("content://media/external/audio/media/10", "Different file", "Other artist", 60000))
        assertEquals(1L, matcher.match("content://media/external/audio/media/999", "Song 1", "Artist", 60000)!!.id)
    }
}
