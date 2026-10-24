package com.example.auraplay

import com.example.auraplay.data.*
import org.junit.Assert.*
import org.junit.Test

class LibraryRegressionTest {
    private fun track(id: Long, albumId: Long, album: String = "Greatest Hits", folder: String = "/Music") =
        Song(id, "Song $id", "Artist", album, albumId, null, 60_000, "/Music/$id.mp3", folder)

    @Test fun albumsWithSameTitleRemainSeparate() {
        val albums = groupAlbums(listOf(track(1, 10), track(2, 20), track(3, 10)))
        assertEquals(2, albums.size)
        assertEquals(2, albums.first { it.albumId == 10L }.songCount)
    }

    @Test fun artistAlbumCountUsesAlbumIdentity() {
        assertEquals(2, groupArtists(listOf(track(1, 10), track(2, 20))).single().albumCount)
    }

    @Test fun foldersKeepDistinctPathsAndIgnoreMissingPath() {
        val folders = groupFolders(listOf(track(1, 10, folder = "/Music/A"), track(2, 10, folder = "/Downloads/A"), track(3, 10, folder = "")))
        assertEquals(2, folders.size)
        assertEquals(setOf("/Music/A", "/Downloads/A"), folders.map { it.folderPath }.toSet())
    }

    @Test fun emptyLibraryProducesEmptyCollections() {
        assertTrue(groupAlbums(emptyList()).isEmpty())
        assertTrue(groupArtists(emptyList()).isEmpty())
        assertTrue(groupFolders(emptyList()).isEmpty())
    }
}
