package com.example.auraplay.data

import java.util.Locale

data class LibraryCollection(val type: String, val name: String, val key: String)

fun groupAlbums(songs: List<Song>): List<AlbumItem> = songs.groupBy { it.albumId to it.album }
    .map { (_, tracks) ->
        val first = tracks.first()
        AlbumItem(first.album, first.albumId, first.artist, first.albumArtUri, tracks.size)
    }.sortedBy { it.album.lowercase(Locale.ROOT) }

fun groupArtists(songs: List<Song>): List<ArtistItem> = songs.groupBy { it.artist }
    .map { (artist, tracks) -> ArtistItem(artist, tracks.size, tracks.map { it.albumId to it.album }.distinct().size) }
    .sortedBy { it.artist.lowercase(Locale.ROOT) }

fun groupFolders(songs: List<Song>): List<FolderItem> = songs.filter { it.folderPath.isNotBlank() }
    .groupBy { it.folderPath }
    .map { (path, tracks) -> FolderItem(path, path.trimEnd('/').substringAfterLast('/'), tracks.size) }
    .sortedBy { it.folderName.lowercase(Locale.ROOT) }
