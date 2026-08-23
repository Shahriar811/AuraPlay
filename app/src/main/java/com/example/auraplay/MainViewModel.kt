package com.example.auraplay

import android.app.Application
import android.app.PendingIntent
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auraplay.data.*
import com.example.auraplay.service.MusicService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

enum class SortOrder {
    TITLE, ARTIST, DATE_ADDED
}

class MainViewModel(
    application: Application,
    private val playlistDao: PlaylistDao,
    private val settingsDataStore: SettingsDataStore
) : AndroidViewModel(application) {

    private val _deletePendingIntent = MutableSharedFlow<PendingIntent>()
    val deletePendingIntent = _deletePendingIntent.asSharedFlow()

    var pendingDeleteSong: Song? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder = _sortOrder.asStateFlow()

    private val _currentLyrics = MutableStateFlow(SongLyrics())
    val currentLyrics = _currentLyrics.asStateFlow()

    // Settings Flows
    val darkTheme: StateFlow<Boolean> = settingsDataStore.darkThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val accentTheme: StateFlow<String> = settingsDataStore.accentThemeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DYNAMIC")

    val pureBlack: StateFlow<Boolean> = settingsDataStore.pureBlackFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filterShortAudio: StateFlow<Boolean> = settingsDataStore.filterShortAudioFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val playbackSpeed: StateFlow<Float> = settingsDataStore.playbackSpeedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val sleepTimerState = MusicService.sleepTimerState

    val songs: StateFlow<List<Song>> =
        combine(playlistDao.getAllSongs(), _searchQuery, _sortOrder, filterShortAudio) { songsFromDb, query, order, filterShort ->
            val baseSongs = if (filterShort) {
                songsFromDb.filter { it.duration >= 30_000L }
            } else {
                songsFromDb
            }

            val filteredSongs = if (query.isBlank()) {
                baseSongs
            } else {
                baseSongs.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true) ||
                            it.album.contains(query, ignoreCase = true)
                }
            }
            when (order) {
                SortOrder.TITLE -> filteredSongs.sortedBy { it.title }
                SortOrder.ARTIST -> filteredSongs.sortedBy { it.artist }
                SortOrder.DATE_ADDED -> filteredSongs.sortedByDescending { it.id }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Library collections
    val playlists = playlistDao.getAllPlaylists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val favoriteSongs = playlistDao.getFavoriteSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val albums = playlistDao.getAllAlbums().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val artists = playlistDao.getAllArtists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val recentlyPlayed = playlistDao.getRecentlyPlayedSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val mostPlayed = playlistDao.getMostPlayedSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Folders computed flow
    val folders: StateFlow<List<FolderItem>> = playlistDao.getAllSongs().map { allSongs ->
        allSongs.groupBy { it.folderPath }.map { (path, songList) ->
            val folderName = if (path.isNotBlank()) File(path).name else "Root / Storage"
            FolderItem(
                folderPath = path,
                folderName = folderName,
                songCount = songList.size
            )
        }.filter { it.folderPath.isNotBlank() }.sortedBy { it.folderName }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getPlaylistWithSongs(playlistId: Long) = playlistDao.getPlaylistWithSongs(playlistId)
    fun getSongById(songId: Long) = playlistDao.getSongById(songId)
    fun getSongsByAlbum(album: String) = playlistDao.getSongsByAlbum(album)
    fun getSongsByArtist(artist: String) = playlistDao.getSongsByArtist(artist)
    fun getSongsByFolder(folderPath: String) = playlistDao.getSongsByFolder(folderPath)

    val showPlaylistDialog = mutableStateOf<Song?>(null)

    init {
        refreshSongs()
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

    private val _isLyricsLoading = MutableStateFlow(false)
    val isLyricsLoading = _isLyricsLoading.asStateFlow()

    fun loadLyrics(song: Song?, forceRefresh: Boolean = false) {
        if (song == null) return
        viewModelScope.launch {
            _isLyricsLoading.value = true
            val lyrics = LyricsManager.getLyricsForSong(song, playlistDao, forceRefresh)
            _currentLyrics.value = lyrics
            _isLyricsLoading.value = false
        }
    }

    fun refreshSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val songsList = mutableListOf<Song>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            val context = getApplication<Application>().applicationContext

            try {
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

                    while (cursor.moveToNext()) {
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
                                folderPath = folderPath
                            )
                        )
                    }
                }

                // Reconcile with Room DB
                val existingSongs = playlistDao.getAllSongsList()
                val existingMap = existingSongs.associateBy { it.id }
                val currentMediaStoreIds = songsList.map { it.id }.toSet()

                // Delete orphaned songs no longer on storage
                val deletedIds = existingSongs.map { it.id }.filterNot { currentMediaStoreIds.contains(it) }
                if (deletedIds.isNotEmpty()) {
                    playlistDao.deleteSongsByIds(deletedIds)
                }

                // Upsert current songs while preserving favorite state, playCount, and lastPlayedTimestamp
                val reconciledSongs = songsList.map { song ->
                    val existing = existingMap[song.id]
                    song.copy(
                        isFavorite = existing?.isFavorite ?: false,
                        playCount = existing?.playCount ?: 0,
                        lastPlayedTimestamp = existing?.lastPlayedTimestamp ?: 0L
                    )
                }

                for (song in reconciledSongs) {
                    if (existingMap.containsKey(song.id)) {
                        playlistDao.updateSong(song)
                    } else {
                        playlistDao.insertSongs(listOf(song))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error refreshing songs from MediaStore", e)
            }
        }
    }

    // --- Playlist & Favorite Management ---

    fun toggleFavorite(song: Song) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.setFavorite(song.id, !song.isFavorite)
    }

    fun createPlaylist(name: String) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.insertPlaylist(Playlist(name = name))
    }

    fun deletePlaylist(playlist: Playlist) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    fun renamePlaylist(playlist: Playlist, newName: String) = viewModelScope.launch(Dispatchers.IO) {
        playlistDao.updatePlaylist(playlist.copy(name = newName))
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
        val context = getApplication<Application>().applicationContext
        val resolver = context.contentResolver
        val songUri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            song.id
        )
        pendingDeleteSong = song

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(resolver, listOf(songUri))
                _deletePendingIntent.emit(pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    resolver.delete(songUri, null, null)
                    playlistDao.deleteSong(song)
                    pendingDeleteSong = null
                } catch (securityException: SecurityException) {
                    val recoverableSecurityException = securityException as? android.app.RecoverableSecurityException
                    recoverableSecurityException?.userAction?.actionIntent?.let {
                        _deletePendingIntent.emit(it)
                    }
                }
            } else {
                resolver.delete(songUri, null, null)
                playlistDao.deleteSong(song)
                pendingDeleteSong = null
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error deleting song from storage", e)
            playlistDao.deleteSong(song)
            pendingDeleteSong = null
        }
    }

    fun confirmPendingDelete() = viewModelScope.launch(Dispatchers.IO) {
        pendingDeleteSong?.let { song ->
            playlistDao.deleteSong(song)
            pendingDeleteSong = null
        }
    }

    fun cancelPendingDelete() {
        pendingDeleteSong = null
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val playlistDao: PlaylistDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, playlistDao, settingsDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}