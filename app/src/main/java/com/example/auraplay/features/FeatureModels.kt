package com.example.auraplay.features

import androidx.room.*
import com.example.auraplay.data.Song
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow

data class FeatureSettings(
    val resumePlayback: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val replayGain: String = "OFF",
    val selectedFoldersOnly: Boolean = false,
    val includedFolders: Set<String> = emptySet(),
    val excludedFolders: Set<String> = emptySet()
)

@Entity(foreignKeys = [ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)])
data class SongEdit(@PrimaryKey val songId: Long, val title: String, val artist: String, val album: String, val artwork: String?)

@Entity(indices = [Index(value = ["songId", "positionMs"], unique = true)],
    foreignKeys = [ForeignKey(entity = Song::class, parentColumns = ["id"], childColumns = ["songId"], onDelete = ForeignKey.CASCADE)])
data class AudioBookmark(@PrimaryKey(autoGenerate = true) val bookmarkId: Long = 0, val songId: Long,
    val positionMs: Long, val label: String, val createdAt: Long = System.currentTimeMillis())

@Entity(indices = [Index("startedAt"), Index("songId")])
data class ListeningEvent(@PrimaryKey val eventId: String, val songId: Long, val title: String,
    val artist: String, val startedAt: Long, val durationMs: Long)

enum class SmartPlaylist(val title: String, val description: String) {
    RECENTLY_ADDED("Recently added", "Music added in the last 30 days"),
    NEVER_PLAYED("Unplayed discoveries", "Songs you haven't listened to yet"),
    FORGOTTEN_FAVORITES("Forgotten favorites", "Liked songs not played in the last 30 days"),
    WEEKLY_FAVORITES("This week's favorites", "Your most listened-to songs in the last 7 days")
}

fun pathContains(folder: String, path: String): Boolean {
    val base = folder.trimEnd('/')
    return path == base || path.startsWith("$base/")
}

fun folderAllowed(path: String, settings: FeatureSettings): Boolean =
    (!settings.selectedFoldersOnly || settings.includedFolders.any { pathContains(it, path) }) &&
        settings.excludedFolders.none { pathContains(it, path) }

fun smartSongs(mode: SmartPlaylist, songs: List<Song>, events: List<ListeningEvent>, now: Long): List<Song> {
    val month = now - 30L * 86_400_000
    return when (mode) {
        SmartPlaylist.RECENTLY_ADDED -> songs.filter { it.dateAdded * 1000 >= month }.sortedByDescending { it.dateAdded }
        SmartPlaylist.NEVER_PLAYED -> songs.filter { it.playCount == 0 }.sortedBy { it.title.lowercase() }
        SmartPlaylist.FORGOTTEN_FAVORITES -> songs.filter { it.isFavorite && it.lastPlayedTimestamp < month }.sortedBy { it.lastPlayedTimestamp }
        SmartPlaylist.WEEKLY_FAVORITES -> {
            val totals = events.filter { it.startedAt in (now - 7L * 86_400_000)..now && it.durationMs > 0 }.groupBy { it.songId }
                .mapValues { (_, entries) -> entries.sumOf { it.durationMs } }
            songs.filter { it.id in totals }.sortedByDescending { totals[it.id] }
        }
    }
}

data class ListeningSummary(val todayMs: Long, val weekMs: Long, val monthMs: Long,
    val daily: List<Pair<LocalDate, Long>>, val artists: List<Pair<String, Long>>)

fun summarizeListening(events: List<ListeningEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault()): ListeningSummary {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val valid = events.filter { it.startedAt in 0..now && it.durationMs > 0 }
    val byDay = valid.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .mapValues { (_, entries) -> entries.sumOf { it.durationMs } }
    val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
    val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return ListeningSummary(byDay[today] ?: 0, valid.filter { it.startedAt >= weekStart }.sumOf { it.durationMs },
        valid.filter { it.startedAt >= monthStart }.sumOf { it.durationMs },
        (6L downTo 0).map { today.minusDays(it).let { day -> day to (byDay[day] ?: 0) } },
        valid.filter { it.startedAt >= weekStart }.groupBy { it.artist }
            .map { (artist, entries) -> artist to entries.sumOf { it.durationMs } }.sortedByDescending { it.second }.take(10))
}

data class ListeningSlice(val startedAt: Long, val durationMs: Long)

/** Split real playback at local midnight, including daylight-saving transitions. */
fun listeningSlices(start: Long, duration: Long, zone: ZoneId = ZoneId.systemDefault()): List<ListeningSlice> {
    if (duration <= 0) return emptyList()
    var cursor = start
    val end = start + duration
    val result = mutableListOf<ListeningSlice>()
    while (cursor < end) {
        val midnight = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val until = minOf(end, midnight)
        result.add(ListeningSlice(cursor, until - cursor)); cursor = until
    }
    return result
}

fun replayGainMultiplier(gainDb: Double?, peak: Double?): Double {
    if (gainDb == null || !gainDb.isFinite()) return 1.0
    val gain = 10.0.pow(gainDb.coerceIn(-30.0, 18.0) / 20.0)
    return if (peak != null && peak.isFinite() && peak > 0) minOf(gain, 1.0 / peak) else gain
}

fun scalePcm16(sample: Short, gain: Double): Short = (sample * gain).toInt().coerceIn(-32768, 32767).toShort()
