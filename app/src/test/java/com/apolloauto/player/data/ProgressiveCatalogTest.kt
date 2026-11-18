package com.apolloauto.player.data
import org.junit.Assert.*
import org.junit.Test
class ProgressiveCatalogTest {
    private data class Item(val id: String, val title: String)
    @Test fun partialScanNeverRemovesCachedSongs() {
        val old = listOf(Item("a", "A"), Item("b", "B"))
        assertEquals(old, mergeScanPreview(old, listOf(old[0])) { it.id })
    }
    @Test fun newSongsAppearBeforeScanCompletes() {
        val old = listOf(Item("a", "A"))
        assertEquals(listOf("a", "b"), mergeScanPreview(old, listOf(Item("b", "B"))) { it.id }.map { it.id })
    }
    @Test fun repeatedBatchesDoNotDuplicateTracks() {
        val song = Item("a", "A")
        assertEquals(listOf(song), mergeScanPreview(listOf(song), listOf(song, song)) { it.id })
    }
    @Test fun updatedMetadataWinsWithoutReorderingCachedSongs() {
        val old = listOf(Item("a", "A"), Item("b", "B"))
        assertEquals(listOf(Item("a", "New"), old[1]), mergeScanPreview(old, listOf(Item("a", "New"))) { it.id })
        assertEquals("A", old[0].title)
    }
}
