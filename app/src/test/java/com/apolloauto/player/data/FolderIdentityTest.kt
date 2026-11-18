package com.apolloauto.player.data

import org.junit.Assert.*
import org.junit.Test

class FolderIdentityTest {
    @Test fun sameLeafFoldersAreDistinct() {
        assertNotEquals(FolderIdentity.from("usb", "Artist A/Mix/song.mp3", "Mix"),
            FolderIdentity.from("usb", "Artist B/Mix/song.mp3", "Mix"))
    }
    @Test fun sourceRootsStayDistinct() {
        assertNotEquals(FolderIdentity.from("usb1", "song.mp3", "USB"),
            FolderIdentity.from("usb2", "song.mp3", "USB"))
    }
    @Test fun subtreeUsesSegmentBoundaryAndSource() {
        val folder = FolderIdentity("usb", "Artist")
        assertTrue(folder.contains(FolderIdentity("usb", "Artist/Album"), true))
        assertFalse(folder.contains(FolderIdentity("usb", "Artist/Album"), false))
        assertFalse(folder.contains(FolderIdentity("usb", "Artist Two"), true))
        assertFalse(folder.contains(FolderIdentity("other", "Artist"), true))
    }
    @Test fun rootAndAncestorsSupportLooseSongsAndNestedAlbums() {
        assertEquals("", FolderIdentity.from("usb", "loose.mp3", "USB").path)
        assertEquals(listOf("Artist/Album", "Artist", ""), FolderIdentity("usb", "Artist/Album").ancestors().map { it.path })
        assertTrue(FolderIdentity("usb", "").contains(FolderIdentity("usb", "Artist/Album"), true))
    }
}
