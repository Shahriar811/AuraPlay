package com.example.auraplay.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class AlbumItem(
    val album: String,
    val albumId: Long,
    val artist: String,
    val albumArtUri: String?,
    val songCount: Int
)

data class ArtistItem(
    val artist: String,
    val songCount: Int,
    val albumCount: Int
)

data class FolderItem(
    val folderPath: String,
    val folderName: String,
    val songCount: Int
)

@Dao
interface PlaylistDao {
    // Playlist operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlist ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Transaction
    @Query("SELECT * FROM playlist WHERE playlistId = :playlistId")
    fun getPlaylistWithSongs(playlistId: Long): Flow<PlaylistWithSongs?>

    // Song operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Query("SELECT * FROM Song")
    suspend fun getAllSongsList(): List<Song>

    @Query("SELECT * FROM Song")
    fun getAllSongs(): Flow<List<Song>> // Get all songs as a Flow

    @Query("SELECT * FROM Song WHERE id = :songId")
    fun getSongById(songId: Long): Flow<Song?> // Get a single song by ID

    @Query("SELECT * FROM Song WHERE isFavorite = 1")
    fun getFavoriteSongs(): Flow<List<Song>> // Get all favorite songs

    @Query("UPDATE Song SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun setFavorite(songId: Long, isFavorite: Boolean) // Set favorite status

    @Query("DELETE FROM Song WHERE id IN (:songIds)")
    suspend fun deleteSongsByIds(songIds: List<Long>)

    @Delete
    suspend fun deleteSong(song: Song) // Delete a song from database

    // Stats & History Queries
    @Query("SELECT * FROM Song WHERE lastPlayedTimestamp > 0 ORDER BY lastPlayedTimestamp DESC LIMIT 30")
    fun getRecentlyPlayedSongs(): Flow<List<Song>>

    @Query("SELECT * FROM Song WHERE playCount > 0 ORDER BY playCount DESC LIMIT 30")
    fun getMostPlayedSongs(): Flow<List<Song>>

    @Query("UPDATE Song SET playCount = playCount + 1, lastPlayedTimestamp = :timestamp WHERE id = :songId")
    suspend fun recordSongPlayed(songId: Long, timestamp: Long)

    @Query("SELECT * FROM Song WHERE album = :album ORDER BY title ASC")
    fun getSongsByAlbum(album: String): Flow<List<Song>>

    @Query("SELECT * FROM Song WHERE artist = :artist ORDER BY title ASC")
    fun getSongsByArtist(artist: String): Flow<List<Song>>

    @Query("SELECT * FROM Song WHERE folderPath = :folderPath ORDER BY title ASC")
    fun getSongsByFolder(folderPath: String): Flow<List<Song>>

    // Lyrics operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: SongLyricsEntity)

    @Query("SELECT * FROM SongLyricsEntity WHERE songId = :songId")
    suspend fun getLyricsForSong(songId: Long): SongLyricsEntity?

    @Query("DELETE FROM SongLyricsEntity WHERE songId = :songId")
    suspend fun deleteLyricsForSong(songId: Long)

    // Grouping queries
    @Query("SELECT album, albumId, artist, albumArtUri, COUNT(*) as songCount FROM Song WHERE album != '' GROUP BY album ORDER BY album ASC")
    fun getAllAlbums(): Flow<List<AlbumItem>>

    @Query("SELECT artist, COUNT(*) as songCount, COUNT(DISTINCT album) as albumCount FROM Song WHERE artist != '' GROUP BY artist ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<ArtistItem>>

    // Playlist-Song relationship operations
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongsToPlaylist(crossRefs: List<PlaylistSongCrossRef>)

    @Delete
    suspend fun removeSongFromPlaylist(crossRef: PlaylistSongCrossRef)
}