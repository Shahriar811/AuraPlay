package com.example.auraplay

import android.app.Application
import android.content.ContentUris
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
        loadSongsFromMediaStore() // Renamed function
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

    // Renamed from loadSongs to be clearer
    private fun loadSongsFromMediaStore() {
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

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)


                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)
                    val artist = cursor.getString(artistColumn)
                    val albumId = cursor.getLong(albumIdColumn)
                    val duration = cursor.getLong(durationColumn)
                    val data = cursor.getString(dataColumn)
                    val albumArtUri = ContentUris.withAppendedId(
                        android.net.Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    // Note: isFavorite defaults to false. The insert conflict strategy
                    // in the DAO will ignore this if the song already exists,
                    // preserving its favorite status.
                    songsList.add(Song(id, title, artist, albumArtUri, duration, data))
                }
            }
            // No longer updating _allSongs.value, just caching in DB.
            // The UI Flow will update automatically.
            playlistDao.insertSongs(songsList) // Cache songs in DB
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
        playlistDao.deleteSong(song)
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