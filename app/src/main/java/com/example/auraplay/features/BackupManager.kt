package com.example.auraplay.features

import android.net.Uri
import androidx.room.withTransaction
import com.example.auraplay.AuraPlayApplication
import com.example.auraplay.SettingsDataStore
import com.example.auraplay.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Portable application-data backup. Audio files are referenced, never copied or overwritten. */
class BackupManager(private val app: AuraPlayApplication, private val settings: SettingsDataStore, private val db: AppDatabase = app.database) {
    private val songs get() = db.playlistDao()
    private val features get() = db.featureDao()

    private fun ref(song: Song) = JSONObject().put("path", song.data).put("title", song.title)
        .put("artist", song.artist).put("duration", song.duration)

    suspend fun export(uri: Uri) {
        val covers = linkedMapOf<String, File>()
        val manifest = db.withTransaction {
            val library = songs.getAllSongsList().associateBy { it.id }
            JSONObject().apply {
                put("app", "AuraPlay"); put("version", 1); put("created", System.currentTimeMillis())
                put("libraryStats", JSONArray(library.values.filter { it.playCount > 0 || it.lastPlayedTimestamp > 0 }.map { track ->
                    JSONObject().put("song", ref(track)).put("plays", track.playCount).put("lastPlayed", track.lastPlayedTimestamp)
                }))
                put("favorites", JSONArray(library.values.filter { it.isFavorite }.map(::ref)))
                put("playlists", JSONArray(songs.playlistList().map { playlist ->
                    JSONObject().put("name", playlist.name).put("songs", JSONArray(songs.playlistSongList(playlist.playlistId).map(::ref)))
                }))
                put("edits", JSONArray(features.edits().mapNotNull { edit -> library[edit.songId]?.let { track ->
                    var cover = ""
                    edit.artwork?.let { value ->
                        val parsed = Uri.parse(value)
                        if (parsed.scheme == "file") {
                            val file = File(parsed.path.orEmpty())
                            val directory = File(app.filesDir, "covers").canonicalPath + File.separator
                            if (file.isFile && file.canonicalPath.startsWith(directory) && file.length() <= 2_000_000) {
                                cover = "artwork/${file.name}"
                                covers[cover] = file
                            }
                        }
                    }
                    JSONObject().put("song", ref(track)).put("title", edit.title).put("artist", edit.artist)
                        .put("album", edit.album).put("cover", cover)
                } }))
                put("bookmarks", JSONArray(features.bookmarkList().mapNotNull { bookmark -> library[bookmark.songId]?.let {
                    JSONObject().put("song", ref(it)).put("position", bookmark.positionMs).put("label", bookmark.label).put("created", bookmark.createdAt)
                } }))
                put("history", JSONArray(features.eventList().map { event ->
                    JSONObject().put("id", event.eventId).put("song", library[event.songId]?.let(::ref))
                        .put("title", event.title).put("artist", event.artist).put("started", event.startedAt).put("duration", event.durationMs)
                }))
            }
        }
        manifest.put("settings", settings.exportSettings())
        manifest.put("widgetStyle", app.getSharedPreferences("widget_styles", 0).getString("default", "COMPACT"))
        val bytes = manifest.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 64 * 1024 * 1024) { "This backup is too large to export." }
        val output = app.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the backup destination.")
        output.use { stream -> ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(ZipEntry("library.json")); zip.write(bytes); zip.closeEntry()
            covers.forEach { (name, file) -> zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry() }
        } }
    }

    suspend fun restore(uri: Uri): String {
        var manifest: JSONObject? = null
        val coverData = mutableMapOf<String, File>()
        val staging = File(app.cacheDir, "backup-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
        var total = 0L
        val input = app.contentResolver.openInputStream(uri) ?: error("Could not read the selected backup.")
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            var count = 0
            while (entry != null) {
                require(++count <= 10_000) { "The backup contains too many files." }
                require(entry.name == "library.json" || entry.name.matches(Regex("artwork/[0-9a-f]{64}\\.jpg"))) { "Unrecognized backup contents." }
                val limit = if (entry.name == "library.json") 64 * 1024 * 1024 else 2_000_000
                val data = ByteArrayOutputStream().use { out ->
                    val buffer = ByteArray(8192)
                    var length = zip.read(buffer)
                    while (length >= 0) {
                        total += length
                        require(total <= 256L * 1024 * 1024 && out.size() + length <= limit) { "The backup exceeds the supported size." }
                        out.write(buffer, 0, length); length = zip.read(buffer)
                    }
                    out.toByteArray()
                }
                if (entry.name == "library.json") { require(manifest == null); manifest = JSONObject(data.toString(Charsets.UTF_8)) }
                else coverData[entry.name] = File(staging, entry.name.substringAfterLast('/')).apply { writeBytes(data) }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
        val root = manifest ?: error("The backup has no library manifest.")
        require(root.optString("app") == "AuraPlay" && root.optInt("version") == 1) { "Unsupported AuraPlay backup version." }
        fun objects(name: String, limit: Int): List<JSONObject> {
            val array = root.optJSONArray(name) ?: return emptyList()
            require(array.length() <= limit) { "The backup contains too many $name entries." }
            return (0 until array.length()).map { array.getJSONObject(it) }
        }
        val statistics = objects("libraryStats", 50_000)
        val favorites = objects("favorites", 50_000)
        val playlists = objects("playlists", 5_000)
        val edits = objects("edits", 50_000)
        val bookmarks = objects("bookmarks", 100_000)
        val history = objects("history", 200_000)
        playlists.forEach { require(it.getString("name").isNotBlank() && it.getJSONArray("songs").length() <= 50_000) }
        edits.forEach { require(it.getString("title").isNotBlank() && it.getString("title").length <= 2000) }
        val library = songs.getAllSongsList()
        val matcher = SongMatcher(library)
        val missing = mutableSetOf<String>()
        fun match(j: JSONObject?): Song? {
            if (j == null) return null
            val path = j.optString("path")
            val song = matcher.match(path, j.optString("title"), j.optString("artist"), j.optLong("duration"))
            if (song == null) missing.add(path.ifBlank { j.toString() })
            return song
        }
        val covers = coverData.mapValues { (_, data) -> CoverStore.storeBytes(app, data.readBytes()) }
        var likes = 0
        var marks = 0
        db.withTransaction {
            statistics.forEach { j -> match(j.optJSONObject("song"))?.let {
                songs.restoreSongStats(it.id, j.optInt("plays").coerceIn(0, 100_000_000), j.optLong("lastPlayed").coerceIn(0, System.currentTimeMillis()))
            } }
            favorites.forEach { j -> match(j)?.let { songs.setFavorite(it.id, true); likes++ } }
            val existing = songs.playlistList().groupBy { it.name.lowercase(Locale.ROOT) }
            val matchedPlaylists = mutableSetOf<Long>()
            playlists.forEach { j ->
                val name = j.getString("name").trim().take(200)
                val playlist = existing[name.lowercase(Locale.ROOT)]?.firstOrNull { it.playlistId !in matchedPlaylists }
                    ?: Playlist(songs.insertPlaylist(Playlist(name = name)), name)
                matchedPlaylists.add(playlist.playlistId)
                val array = j.getJSONArray("songs")
                val refs = (0 until array.length()).mapNotNull { i -> match(array.getJSONObject(i))?.let { PlaylistSongCrossRef(playlist.playlistId, it.id) } }
                songs.addSongsToPlaylist(refs)
            }
            edits.forEach { j -> match(j.optJSONObject("song"))?.let { song ->
                val edit = SongEdit(song.id, j.getString("title").take(2000), j.optString("artist", song.artist).take(2000),
                    j.optString("album", song.album).take(2000), covers[j.optString("cover")] ?: song.albumArtUri)
                features.saveEdit(edit)
                songs.updateMetadata(song.id, edit.title, edit.artist, edit.album, song.albumId, edit.artwork, song.duration, song.data, song.folderPath, song.dateAdded)
            } }
            bookmarks.forEach { j -> match(j.optJSONObject("song"))?.let { song ->
                features.addBookmark(AudioBookmark(songId = song.id, positionMs = j.optLong("position").coerceIn(0, song.duration.coerceAtLeast(0)),
                    label = j.optString("label", "Bookmark").take(120), createdAt = j.optLong("created").coerceAtLeast(0)))
                marks++
            } }
            history.forEach { j ->
                val track = match(j.optJSONObject("song"))
                val id = j.optString("id").take(100)
                if (id.isNotBlank()) features.recordEvent(ListeningEvent(id, track?.id ?: -1, j.optString("title").take(2000), j.optString("artist").take(2000),
                    j.optLong("started").coerceAtLeast(0), j.optLong("duration").coerceIn(0, 7L * 86_400_000)))
            }
        }
        root.optJSONObject("settings")?.let { settings.importSettings(it) }
        val widget = root.optString("widgetStyle", "COMPACT").takeIf { it in listOf("COMPACT", "STANDARD", "ARTWORK") } ?: "COMPACT"
        com.example.auraplay.widget.WidgetStyles.setDefault(app, com.example.auraplay.widget.WidgetStyle.valueOf(widget))
        return "Restored ${playlists.size} playlists, $likes liked songs and $marks bookmarks." + if (missing.isEmpty()) "" else " ${missing.size} unavailable tracks were skipped."
        } finally { staging.deleteRecursively() }
    }
}
