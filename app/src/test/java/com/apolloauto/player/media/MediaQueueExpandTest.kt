package com.apolloauto.player.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaQueueExpandTest {
    private fun c(
        id: String,
        album: String = "Album",
        artist: String = "Artist",
        folder: String = "Music"
    ) = MediaQueueExpand.Candidate(id, album, artist, folder)

    @Test
    fun expandsToAlbumMates() {
        val catalog = listOf(c("a1"), c("a2"), c("b1", album = "Other"))
        val expanded = MediaQueueExpand.expandIds("a2", catalog)
        assertEquals(listOf("a1", "a2"), expanded.ids)
        assertEquals(1, expanded.startIndex)
    }

    @Test
    fun fallsBackToFolder() {
        val catalog = listOf(
            c("x", album = "Unknown Album", folder = "Mix"),
            c("y", album = "Unknown Album", folder = "Mix"),
            c("z", album = "Unknown Album", folder = "Other")
        )
        val expanded = MediaQueueExpand.expandIds("x", catalog)
        assertEquals(listOf("x", "y"), expanded.ids)
    }

    @Test
    fun unknownIdReturnsEmpty() {
        assertTrue(MediaQueueExpand.expandIds("missing", emptyList()).ids.isEmpty())
    }
    @Test
    fun folderFallbackUsesSourceAndFullPath() {
        val catalog = listOf(
            MediaQueueExpand.Candidate("a", "Unknown Album", "", "Mix", "usb1", "A/Mix/a.mp3"),
            MediaQueueExpand.Candidate("b", "Unknown Album", "", "Mix", "usb1", "A/Mix/b.mp3"),
            MediaQueueExpand.Candidate("c", "Unknown Album", "", "Mix", "usb1", "B/Mix/c.mp3"),
            MediaQueueExpand.Candidate("d", "Unknown Album", "", "Mix", "usb2", "A/Mix/d.mp3")
        )
        assertEquals(listOf("a", "b"), MediaQueueExpand.expandIds("a", catalog).ids)
    }
}
