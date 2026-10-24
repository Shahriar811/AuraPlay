package com.example.auraplay

import com.example.auraplay.features.*
import android.net.Uri
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.util.Locale
import android.app.Application
import android.app.PendingIntent
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auraplay.data.*
import com.example.auraplay.service.MusicService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class SortOrder {
    TITLE, ARTIST, DATE_ADDED
}

class MainViewModel(
    application: Application,
    private val playlistDao: PlaylistDao,
    private val settingsDataStore: SettingsDataStore,
    val featureDao: FeatureDao = (application as AuraPlayApplication).database.featureDao()
) : AndroidViewModel(application) {

    private val deleteIntentChannel = Channel<PendingIntent>(Channel.BUFFERED)
    val deletePendingIntent = deleteIntentChannel.receiveAsFlow()
    private val legacyDeleteChannel = Channel<Unit>(Channel.BUFFERED)
    val legacyDeletePermission = legacyDeleteChannel.receiveAsFlow()

    @Volatile private var pendingDeleteSong: Song? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder = _sortOrder.asStateFlow()

    private val _currentLyrics = MutableStateFlow(SongLyrics())
    val currentLyrics = _currentLyrics.asStateFlow()

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading = _isLyricsLoading.asStateFlow()

    private var loadLyricsJob: Job? = null
    private var lyricsRequest = 0

    init {
        // Automatically load synced lyrics whenever active song changes
        viewModelScope.launch {
            MusicService.playerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collectLatest { id ->
                    if (id != null) {
                        val song = playlistDao.getSongById(id).first() ?: MusicService.playerState.value.currentSong
                        loadLyrics(song, forceRefresh = false)
                    } else {
                        loadLyrics(null)
                    }
                }
        }
    }

    // Settings Flows
    val darkTheme: StateFlow<Boolean> = settingsDataStore.darkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val accentTheme: StateFlow<String> = settingsDataStore.accentThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MINT")

    val pureBlack: StateFlow<Boolean> = settingsDataStore.pureBlackFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filterShortAudio: StateFlow<Boolean> = settingsDataStore.filterShortAudioFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playbackSpeed: StateFlow<Float> = settingsDataStore.playbackSpeedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val visualizerEnabled = settingsDataStore.visualizerEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setVisualizerEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.saveVisualizerEnabled(enabled)
    }

    val sleepTimerState = MusicService.sleepTimerState

    // A single Room observer feeds every library view, instead of six simultaneous scans.
    val librarySongs = playlistDao.getAllSongs().distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val featureSettings = settingsDataStore.featureSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeatureSettings())
    val listeningEvents = featureDao.listeningEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bookmarks = featureDao.bookmarks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val visibleLibrary = combine(librarySongs, featureSettings, filterShortAudio) { library, features, short ->
        library.filter { folderAllowed(it.folderPath, features) && (!short || it.duration >= 30_000L) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val queueRequests = Channel<Pair<Song, Boolean>>(Channel.BUFFERED)
    private val _featureMessage = MutableStateFlow<String?>(null)
    val featureMessage = _featureMessage.asStateFlow()
    private val _featureBusy = MutableStateFlow(false)
    val featureBusy = _featureBusy.asStateFlow()

    val allFolders = librarySongs.map(::groupFolders).flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun smartPlaylist(mode: SmartPlaylist): Flow<List<Song>> = combine(visibleLibrary, listeningEvents) { library, events ->
        smartSongs(mode, library, events, System.currentTimeMillis())
    }.flowOn(Dispatchers.Default)
    fun showFeatureMessage(message: String) { _featureMessage.value = message }
    fun clearFeatureMessage() { _featureMessage.value = null }
    fun changeFeatures(change: (FeatureSettings) -> FeatureSettings) = viewModelScope.launch {
        settingsDataStore.updateFeatures(change)
    }
    fun enqueue(song: Song, next: Boolean) = viewModelScope.launch { queueRequests.send(song to next) }
    fun bookmarksForSong(id: Long) = featureDao.bookmarksForSong(id)
    fun addBookmark(songId: Long, position: Long, label: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val song = playlistDao.getSongById(songId).first() ?: error("This song is no longer in your library.")
            featureDao.addBookmark(AudioBookmark(songId = songId, positionMs = position.coerceIn(0, song.duration.coerceAtLeast(0)), label = label.trim().take(120).ifBlank { "Bookmark" }))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _featureMessage.value = "Could not save the bookmark. Check that the song is still available." }
    }
    fun deleteBookmark(bookmark: AudioBookmark) = viewModelScope.launch(Dispatchers.IO) { featureDao.deleteBookmark(bookmark) }
    fun saveSongInfo(song: Song, title: String, artist: String, album: String, cover: Uri?, onSaved: () -> Unit) = viewModelScope.launch {
        _featureBusy.value = true
        try {
            withContext(Dispatchers.IO) {
                val artwork = cover?.let { CoverStore.save(getApplication(), it) } ?: song.albumArtUri
                val edit = SongEdit(song.id, title.trim().take(2000).ifBlank { song.title }, artist.trim().take(2000).ifBlank { "Unknown Artist" }, album.trim().take(2000).ifBlank { "Unknown Album" }, artwork)
                featureDao.saveEdit(edit)
                playlistDao.updateMetadata(song.id, edit.title, edit.artist, edit.album, song.albumId, artwork, song.duration, song.data, song.folderPath, song.dateAdded)
            }
            playlistDao.getSongById(song.id).first()?.let { MusicService.refreshMetadata(it) }
            onSaved()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _featureMessage.value = error.message ?: "Could not save song information." }
        finally { _featureBusy.value = false }
    }
    fun resetSongInfo(song: Song) = viewModelScope.launch(Dispatchers.IO) { featureDao.deleteEdit(song.id); refreshSongs() }
    fun exportBackup(uri: Uri) = runFeatureOperation {
        BackupManager(getApplication(), settingsDataStore).export(uri)
        "Backup saved."
    }
    fun importBackup(uri: Uri) = runFeatureOperation {
        val result = BackupManager(getApplication(), settingsDataStore).restore(uri)
        val equalizer = settingsDataStore.equalizerFlow.first()
        withContext(Dispatchers.Main) { MusicService.applyEqualizer(equalizer) }
        result
    }
    private fun runFeatureOperation(action: suspend () -> String) = viewModelScope.launch {
        if (_featureBusy.value) return@launch
        _featureBusy.value = true
        try { _featureMessage.value = withContext(Dispatchers.IO) { action() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { _featureMessage.value = error.message ?: "The operation could not be completed." }
        finally { _featureBusy.value = false }
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError = _scanError.asStateFlow()

    val songs: StateFlow<List<Song>> =
        combine(visibleLibrary, _searchQuery, _sortOrder) { songsFromDb, query, order ->
            val filteredSongs = if (query.isBlank()) {
                songsFromDb
            } else {
                songsFromDb.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true) ||
                            it.album.contains(query, ignoreCase = true)
                }
            }
            when (order) {
                SortOrder.TITLE -> filteredSongs.sortedBy { it.title.lowercase(Locale.ROOT) }
                SortOrder.ARTIST -> filteredSongs.sortedBy { it.artist.lowercase(Locale.ROOT) }
                SortOrder.DATE_ADDED -> filteredSongs.sortedWith(compareByDescending<Song> { it.dateAdded }.thenByDescending { it.id })
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Library collections
    val playlists = playlistDao.getAllPlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val albums = songs.map(::groupAlbums).flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val artists = songs.map(::groupArtists).flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folders = songs.map(::groupFolders).flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val favoriteSongs = visibleLibrary.map { tracks -> tracks.filter { it.isFavorite }.sortedBy { it.title.lowercase(Locale.ROOT) } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentlyPlayed = visibleLibrary.map { tracks -> tracks.filter { it.lastPlayedTimestamp > 0 }.sortedByDescending { it.lastPlayedTimestamp }.take(30) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mostPlayed = visibleLibrary.map { tracks -> tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(30) }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getCollectionSongs(collection: LibraryCollection): Flow<List<Song>> = combine(when (collection.type) {
        "album" -> playlistDao.getSongsByAlbumId(collection.key.toLong(), collection.name)
        "artist" -> playlistDao.getSongsByArtist(collection.key)
        else -> playlistDao.getSongsByFolder(collection.key)
    }, filterShortAudio, featureSettings) { tracks, filter, features -> tracks.filter { (!filter || it.duration >= 30_000L) && folderAllowed(it.folderPath, features) } }

    fun getPlaylistWithSongs(playlistId: Long) = combine(playlistDao.getPlaylistWithSongs(playlistId), filterShortAudio, featureSettings) { list, short, features ->
        list?.copy(songs = list.songs.filter { (!short || it.duration >= 30_000L) && folderAllowed(it.folderPath, features) })
    }.flowOn(Dispatchers.Default)
    fun getSongById(songId: Long) = playlistDao.getSongById(songId)
    fun getSongsByAlbum(album: String) = playlistDao.getSongsByAlbum(album)
    fun getSongsByArtist(artist: String) = playlistDao.getSongsByArtist(artist)
    fun getSongsByFolder(folderPath: String) = playlistDao.getSongsByFolder(folderPath)

    init {
        viewModelScope.launch {
            combine(librarySongs, settingsDataStore.playbackSnapshotFlow.distinctUntilChanged(), featureSettings) { library, json, features ->
                if (MusicService.getInstance() != null) null
                else {
                    val snapshot = if (features.resumePlayback) PlaybackSnapshot.decode(json) else null
                    resumePreviewState(snapshot?.let { restoreQueue(it.ids, it.index, it.position, library, it.order) }, snapshot)
                }
            }.flowOn(Dispatchers.Default).collect { preview ->
                preview?.let(MusicService::publishResumePreview)
            }
        }
    }

    private var refreshJob: Job? = null
    @Volatile private var libraryScanned = false

    fun ensureLibraryLoaded() {
        if (!libraryScanned) refreshSongs(force = false)
    }

    fun toggleTheme() {
        viewModelScope.launch {
            settingsDataStore.saveThemePreference(!darkTheme.value)
        }
    }

    fun setAccentTheme(theme: String) {
        viewModelScope.launch {
            settingsDataStore.saveAccentTheme(theme)
        }
    }

    fun setPureBlack(isPureBlack: Boolean) {
        viewModelScope.launch {
            settingsDataStore.savePureBlack(isPureBlack)
        }
    }

    fun setFilterShortAudio(filter: Boolean) {
        viewModelScope.launch {
            settingsDataStore.saveFilterShortAudio(filter)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        viewModelScope.launch {
            settingsDataStore.savePlaybackSpeed(speed)
            MusicService.setPlaybackSpeed(speed)
        }
    }

    fun startSleepTimer(minutes: Int) {
        MusicService.startSleepTimer(minutes)
    }

    fun startSleepTimerEndOfTrack() {
        MusicService.startSleepTimerEndOfTrack()
    }

    fun cancelSleepTimer() {
        MusicService.cancelSleepTimer()
    }

    fun playQueueItem(index: Int) {
        MusicService.playQueueItem(index)
    }

    fun removeQueueItem(index: Int) {
        MusicService.removeQueueItem(index)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun changeSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun loadLyrics(song: Song?, forceRefresh: Boolean = false) {
        val request = ++lyricsRequest
        loadLyricsJob?.cancel()
        _currentLyrics.value = SongLyrics()
        _isLyricsLoading.value = false
        if (song == null) return
        loadLyricsJob = viewModelScope.launch {
            _isLyricsLoading.value = true
            try {
                _currentLyrics.value = LyricsManager.getLyricsForSong(song, playlistDao, forceRefresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("MainViewModel", "Could not load lyrics", error)
            } finally {
                if (request == lyricsRequest) _isLyricsLoading.value = false
            }
        }
    }

    fun refreshSongs(force: Boolean = true) {
        if (refreshJob?.isActive == true) return
        val context = getApplication<Application>().applicationContext
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            _scanError.value = null
            val songsList = mutableListOf<Song>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_ADDED
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            try {
                val startStamp = mediaStoreScanStamp(context)
                if (!force && canReuseLibraryScan(startStamp, settingsDataStore.libraryScanStampFlow.first(), playlistDao.hasCachedSongs())) {
                    libraryScanned = true
                    return@launch
                }
                _isScanning.value = true
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                    val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val dateAddedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                    while (cursor.moveToNext()) {
                        ensureActive()
                        val id = if (idColumn >= 0) cursor.getLong(idColumn) else continue
                        val title = (if (titleColumn >= 0) cursor.getString(titleColumn) else null) ?: "Unknown Title"
                        val artist = (if (artistColumn >= 0) cursor.getString(artistColumn) else null) ?: "Unknown Artist"
                        val album = (if (albumColumn >= 0) cursor.getString(albumColumn) else null) ?: "Unknown Album"
                        val albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else -1L
                        val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                        val data = (if (dataColumn >= 0) cursor.getString(dataColumn) else null) ?: ""
                        
                        val folderPath = try {
                            if (data.isNotBlank()) File(data).parent ?: "" else ""
                        } catch (e: Exception) { "" }

                        val albumArtUri = if (albumId >= 0) {
                            ContentUris.withAppendedId(
                                android.net.Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()
                        } else null

                        songsList.add(
                            Song(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                albumId = albumId,
                                albumArtUri = albumArtUri,
                                duration = duration,
                                data = data,
                                folderPath = folderPath,
                                dateAdded = if (dateAddedColumn >= 0) cursor.getLong(dateAddedColumn) else 0L
                            )
                        )
                    }
                } ?: throw IllegalStateException("MediaStore query returned no cursor")

                ensureActive()
                // Reconcile with Room DB atomically without invalidation thrashing
                val existingSongs = playlistDao.getAllSongsList()
                val existingMap = existingSongs.associateBy { it.id }
                val currentMediaStoreIds = songsList.map { it.id }.toSet()

                // Delete orphaned songs no longer on storage
                val deletedIds = existingSongs.map { it.id }.filterNot { currentMediaStoreIds.contains(it) }

                val toInsert = mutableListOf<Song>()
                val toUpdate = mutableListOf<Song>()

                for (song in songsList) {
                    val existing = existingMap[song.id]
                    if (existing == null) {
                        toInsert.add(song)
                    } else {
                        val metadataChanged = existing.title != song.title ||
                                existing.artist != song.artist ||
                                existing.album != song.album ||
                                existing.albumId != song.albumId ||
                                existing.albumArtUri != song.albumArtUri ||
                                existing.duration != song.duration ||
                                existing.data != song.data ||
                                existing.folderPath != song.folderPath || existing.dateAdded != song.dateAdded

                        if (metadataChanged) {
                            val merged = song.copy(
                                isFavorite = existing.isFavorite,
                                playCount = existing.playCount,
                                lastPlayedTimestamp = existing.lastPlayedTimestamp
                            )
                            toUpdate.add(merged)
                        }
                    }
                }

                if (deletedIds.isNotEmpty() || toInsert.isNotEmpty() || toUpdate.isNotEmpty()) {
                    playlistDao.syncSongs(toInsert = toInsert, toUpdate = toUpdate, toDelete = deletedIds)
                }
                // Never bless a partial scan if storage changed while the cursor was being read.
                val endStamp = mediaStoreScanStamp(context)
                try {
                    settingsDataStore.saveLibraryScanStamp(startStamp?.takeIf { it == endStamp }.orEmpty())
                } catch (error: java.io.IOException) {
                    android.util.Log.w("MainViewModel", "Library cache marker could not be saved", error)
                }
                libraryScanned = true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _scanError.value = "Could not refresh your music. Check audio permission and try again."
                android.util.Log.e("MainViewModel", "Error refreshing songs from MediaStore", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    // --- Playlist & Favorite Management ---

    fun toggleFavorite(song: Song) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.toggleFavorite(song.id)
    }

    fun createPlaylist(name: String) = viewModelScope.launch(Dispatchers.IO) {
        if (name.isNotBlank()) playlistDao.insertPlaylist(Playlist(name = name.trim()))
    }

    fun deletePlaylist(playlist: Playlist) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    fun renamePlaylist(playlist: Playlist, newName: String) = viewModelScope.launch(Dispatchers.IO) {
        if (newName.isNotBlank()) playlistDao.updatePlaylist(playlist.copy(name = newName.trim()))
    }

    fun addSongToPlaylist(song: Song, playlist: Playlist) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.addSongToPlaylist(PlaylistSongCrossRef(playlist.playlistId, song.id))
    }

    fun addMultipleSongsToPlaylist(playlistId: Long, songIds: List<Long>) = viewModelScope.launch(Dispatchers.IO) {
        val crossRefs = songIds.map { songId ->
            PlaylistSongCrossRef(playlistId = playlistId, id = songId)
        }
        playlistDao.addSongsToPlaylist(crossRefs)
    }

    fun removeSongFromPlaylist(song: Song, playlist: Playlist) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.removeSongFromPlaylist(PlaylistSongCrossRef(playlist.playlistId, song.id))
    }

    fun deleteSong(song: Song) = viewModelScope.launch(Dispatchers.IO) {
        if (pendingDeleteSong != null) return@launch
        pendingDeleteSong = song
        val context = getApplication<Application>().applicationContext
        val resolver = context.contentResolver
        val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
        try {
            if (Build.VERSION.SDK_INT <= 28 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                legacyDeleteChannel.send(Unit)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                deleteIntentChannel.send(MediaStore.createDeleteRequest(resolver, listOf(songUri)))
            } else {
                try {
                    resolver.delete(songUri, null, null)
                    playlistDao.deleteSongWithLyrics(song)
                    pendingDeleteSong = null
                } catch (error: SecurityException) {
                    if (Build.VERSION.SDK_INT >= 29 && error is android.app.RecoverableSecurityException) {
                        deleteIntentChannel.send(error.userAction.actionIntent)
                    } else throw error
                }
            }
        } catch (cancelled: CancellationException) {
            pendingDeleteSong = null
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.e("MainViewModel", "Error deleting song from storage", error)
            _scanError.value = "The song could not be deleted. It is still in your library."
            pendingDeleteSong = null
        }
    }

    fun continueLegacyDelete(granted: Boolean) {
        val song = pendingDeleteSong
        pendingDeleteSong = null
        if (granted && song != null) deleteSong(song)
        else _scanError.value = "Storage permission is needed to delete this song."
    }

    fun confirmPendingDelete() = viewModelScope.launch(Dispatchers.IO) {
        val song = pendingDeleteSong ?: return@launch
        try {
            // Android 10 grants consent to retry; Android 11+ performs the deletion itself.
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                getApplication<Application>().contentResolver.delete(uri, null, null)
            }
            playlistDao.deleteSongWithLyrics(song)
        } catch (error: Exception) {
            _scanError.value = "Could not finish deleting this song. Refresh your library and try again."
        } finally { pendingDeleteSong = null }
    }

    fun cancelPendingDelete() {
        pendingDeleteSong = null
    }

}

class MainViewModelFactory(
    private val application: Application,
    private val playlistDao: PlaylistDao,
    private val settingsDataStore: SettingsDataStore,
    private val featureDao: FeatureDao? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, playlistDao, settingsDataStore, featureDao ?: (application as AuraPlayApplication).database.featureDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
