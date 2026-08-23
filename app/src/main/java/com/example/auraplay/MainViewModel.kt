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
import com.example.auraplay.data.Playlist
import com.example.auraplay.data.PlaylistDao
import com.example.auraplay.data.PlaylistSongCrossRef
import com.example.auraplay.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortOrder {
    TITLE, ARTIST, DATE_ADDED
}

class MainViewModel(
    application: Application,
    private val playlistDao: PlaylistDao,
    private val settingsDataStore: SettingsDataStore // 1. Add SettingsDataStore
) : AndroidViewModel(application) {

    private val _deletePendingIntent = MutableSharedFlow<PendingIntent>()
    val deletePendingIntent = _deletePendingIntent.asSharedFlow()

    var pendingDeleteSong: Song? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_ADDED)
    val sortOrder = _sortOrder.asStateFlow()

    // 2. Remove the old MutableStateFlow for darkTheme
    // private val _darkTheme = MutableStateFlow(true)
    // val darkTheme = _darkTheme.asStateFlow()

    // 2. Read the theme directly from DataStore and expose it as a StateFlow
    val darkTheme: StateFlow<Boolean> = settingsDataStore.darkThemeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Default to true while DataStore is loading
        )

    val songs: StateFlow<List<Song>> =
        combine(playlistDao.getAllSongs(), _searchQuery, _sortOrder) { songsFromDb, query, order ->
            val filteredSongs = if (query.isBlank()) {
                songsFromDb
            } else {
                songsFromDb.filter {
                    it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true)
                }
            }
            when (order) {
                SortOrder.TITLE -> filteredSongs.sortedBy { it.title }
                SortOrder.ARTIST -> filteredSongs.sortedBy { it.artist }
                SortOrder.DATE_ADDED -> filteredSongs.sortedByDescending { it.id }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Playlist states
    val playlists = playlistDao.getAllPlaylists().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Favorite songs state
    val favoriteSongs = playlistDao.getFavoriteSongs().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getPlaylistWithSongs(playlistId: Long) = playlistDao.getPlaylistWithSongs(playlistId)

    // Get a single song by ID
    fun getSongById(songId: Long) = playlistDao.getSongById(songId)

    val showPlaylistDialog = mutableStateOf<Song?>(null)

    init {
        refreshSongs()
    }

    // 3. Update toggleTheme to save the new value to DataStore
    fun toggleTheme() {
        viewModelScope.launch {
            // Save the *opposite* of the current value
            settingsDataStore.saveThemePreference(!darkTheme.value)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun changeSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun refreshSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            val songsList = mutableListOf<Song>()
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
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
                    val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = if (idColumn >= 0) cursor.getLong(idColumn) else continue
                        val title = (if (titleColumn >= 0) cursor.getString(titleColumn) else null) ?: "Unknown Title"
                        val artist = (if (artistColumn >= 0) cursor.getString(artistColumn) else null) ?: "Unknown Artist"
                        val albumId = if (albumIdColumn >= 0) cursor.getLong(albumIdColumn) else -1L
                        val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                        val data = (if (dataColumn >= 0) cursor.getString(dataColumn) else null) ?: ""
                        val albumArtUri = if (albumId >= 0) {
                            ContentUris.withAppendedId(
                                android.net.Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()
                        } else null

                        songsList.add(Song(id, title, artist, albumArtUri, duration, data))
                    }
                }

                // Reconcile with Room DB
                val existingSongs = playlistDao.getAllSongsList()
                val favMap = existingSongs.associate { it.id to it.isFavorite }
                val currentMediaStoreIds = songsList.map { it.id }.toSet()

                // Delete orphaned songs no longer on storage
                val deletedIds = existingSongs.map { it.id }.filterNot { currentMediaStoreIds.contains(it) }
                if (deletedIds.isNotEmpty()) {
                    playlistDao.deleteSongsByIds(deletedIds)
                }

                // Upsert current songs while preserving favorite state
                val reconciledSongs = songsList.map { song ->
                    val isFav = favMap[song.id] ?: false
                    song.copy(isFavorite = isFav)
                }

                for (song in reconciledSongs) {
                    if (favMap.containsKey(song.id)) {
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

    // --- New/Updated Playlist & Favorite Management ---

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
                // For Android 11+
                val pendingIntent = MediaStore.createDeleteRequest(resolver, listOf(songUri))
                _deletePendingIntent.emit(pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10
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
                // For Android 9 and below
                resolver.delete(songUri, null, null)
                playlistDao.deleteSong(song)
                pendingDeleteSong = null
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "Error deleting song from storage", e)
            // Fallback: at least remove from Room DB
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

// 1. Update the Factory to accept SettingsDataStore
class MainViewModelFactory(
    private val application: Application,
    private val playlistDao: PlaylistDao,
    private val settingsDataStore: SettingsDataStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // 2. Pass it to the ViewModel's constructor
            return MainViewModel(application, playlistDao, settingsDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}