package com.apolloauto.player.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import android.provider.DocumentsContract
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Recursive SAF tree scanner for phone folders, USB SSD, and SD card roots.
 * Discovers audio files and M3U/M3U8 playlists. Does not delete files.
 *
 * Publishes lightweight progress only; the last good catalog stays intact until completion.
 * Embedded album-art extraction is deferred (folder covers only during scan) —
 * MediaMetadataRetriever per file was making large libraries appear stuck at 0 songs.
 */
class SafLibraryScanner(
    private val context: Context,
    private val sourceStore: MusicSourceStore,
    private val libraryStore: LibraryStore
) {
    suspend fun scanSource(source: MusicSource): ScanOutcome = withContext(Dispatchers.IO) {
        val uriStr = source.treeUri
            ?: return@withContext ScanOutcome(emptyList(), emptyList(), "Missing folder URI")
        libraryStore.setScanning(true)
        libraryStore.setStatus("Scanning ${source.label}…")
        try {
            val treeUri = Uri.parse(uriStr)
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root == null || !root.exists() || !root.canRead()) {
                val msg = "Cannot open folder — re-select it in Settings → Library"
                Log.e(TAG, "open failed source=${source.id} uri=$uriStr exists=${root?.exists()} canRead=${root?.canRead()}")
                sourceStore.recordScanFailure(source.id, msg, connected = false)
                libraryStore.setStatus(msg)
                return@withContext ScanOutcome(emptyList(), emptyList(), msg, connected = false)
            }
            Log.i(TAG, "start source=${source.id} label=${source.label} name=${root.name}")
            libraryStore.setStatus("Scanning ${source.label}… (folder open)")
            val tracks = ArrayList<Track>(512)
            val playlists = ArrayList<DiscoveredPlaylistFile>(32)
            var lastProgressMs = 0L
            walk(root.name ?: source.label, root, "", source, tracks, playlists, ScanTraversalBudget(), 0) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastProgressMs >= 1_000L) {
                    lastProgressMs = now
                    sourceStore.previewScan(source.id, tracks.toList())
                    libraryStore.setStatus("Scanning ${source.label}… ${tracks.size} tracks so far")
                }
            }
            coroutineContext.ensureActive()
            check(root.exists() && root.canRead()) { "Folder disconnected during scan; reconnect and retry" }
            check(tracks.isNotEmpty() || sourceStore.tracksForSource(source.id).isEmpty()) {
                "Scan returned no music; previous library preserved. Reconnect or re-select the folder"
            }
            sourceStore.recordScanResult(source.id, tracks, playlists, error = null, connected = true)
            libraryStore.setStatus(
                "${tracks.size} tracks · ${playlists.size} playlists · ${source.label}"
            )
            Log.i(TAG, "done source=${source.id} tracks=${tracks.size} playlists=${playlists.size}")
            ScanOutcome(tracks, playlists)
        } catch (cancelled: CancellationException) {
            libraryStore.setStatus("Scan cancelled — keeping previous library")
            throw cancelled
        } catch (t: Exception) {
            val msg = t.message ?: "scan failed"
            Log.e(TAG, "scan failed source=${source.id}", t)
            sourceStore.recordScanFailure(source.id, msg, connected = false)
            libraryStore.setStatus("Scan failed: $msg (keeping previous library)")
            ScanOutcome(emptyList(), emptyList(), msg, connected = false)
        } finally {
            sourceStore.clearScanPreview(source.id)
            libraryStore.setScanning(false)
        }
    }

    private suspend fun walk(
        rootLabel: String,
        dir: DocumentFile,
        rel: String,
        source: MusicSource,
        tracks: MutableList<Track>,
        playlists: MutableList<DiscoveredPlaylistFile>,
        budget: ScanTraversalBudget,
        depth: Int,
        onProgress: () -> Unit
    ) {
        coroutineContext.ensureActive()
        budget.enterDirectory(dir.uri.toString(), depth)
        // DocumentFile.listFiles silently converts provider errors into empty directories.
        // Query directly so disconnects/permission failures cannot commit a truncated catalog.
        val children = listChildren(dir, budget)
        val lyricsByBase = HashMap<String, Uri>(8)
        var folderCover: Uri? = null
        for (child in children) {
            coroutineContext.ensureActive()
            val name = child.name ?: continue
            if (!child.isFile || name.startsWith(".")) continue
            if (LyricsLoader.isLyricsFile(name)) {
                val base = name.substringBeforeLast('.').lowercase(Locale.US)
                lyricsByBase[base] = child.uri
            } else if (folderCover == null && AlbumArtResolver.isCoverFileName(name)) {
                folderCover = child.uri
            }
        }
        for (child in children.sortedBy { it.isDirectory }) {
            coroutineContext.ensureActive()
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            val childRel = if (rel.isEmpty()) name else "$rel/$name"
            if (child.isDirectory) {
                walk(rootLabel, child.document, childRel, source, tracks, playlists, budget, depth + 1, onProgress)
            } else if (child.isFile) {
                when {
                    isAudio(name) -> {
                        val folderLabel = rel.substringAfterLast('/', missingDelimiterValue = rootLabel)
                            .ifBlank { rootLabel }
                        val (title, artist, album) = FilenameMetadataParser.resolve(
                            embeddedTitle = null,
                            embeddedArtist = null,
                            displayName = name,
                            folderFallback = folderLabel
                        )
                        val base = name.substringBeforeLast('.').lowercase(Locale.US)
                        val trackId = "saf:${source.id}:${child.uri}"
                        // Folder cover only during scan — embedded art is too slow for large trees.
                        tracks += Track(
                            id = trackId,
                            title = title,
                            artist = artist,
                            album = album,
                            uri = child.uri,
                            folder = folderLabel,
                            artworkUri = folderCover,
                            sourceId = source.id,
                            sourceKind = source.kind,
                            relativePath = childRel.replace('\\', '/'),
                            lyricsUri = lyricsByBase[base],
                            available = true,
                            dateAddedMs = child.modified.coerceAtLeast(0L)
                        )
                        onProgress()
                    }
                    isPlaylist(name) -> {
                        playlists += DiscoveredPlaylistFile(
                            sourceId = source.id,
                            name = name.substringBeforeLast('.'),
                            uri = child.uri,
                            relativePath = childRel.replace('\\', '/')
                        )
                    }
                }
            }
        }
    }

    private suspend fun listChildren(dir: DocumentFile, budget: ScanTraversalBudget): List<ScanEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            dir.uri, DocumentsContract.getDocumentId(dir.uri)
        )
        val cursor = context.contentResolver.query(
            childrenUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ), null, null, null
        ) ?: error("Cannot read folder — reconnect the USB drive and retry")
        return cursor.use {
            val children = ArrayList<ScanEntry>()
            while (it.moveToNext()) {
                coroutineContext.ensureActive()
                budget.visitEntry()
                val uri = DocumentsContract.buildDocumentUriUsingTree(dir.uri, it.getString(0))
                val document = DocumentFile.fromSingleUri(context, uri)
                    ?: error("Cannot read folder entry")
                children += ScanEntry(document, it.getString(1), it.getString(2), it.getLong(3))
            }
            children
        }
    }

    private data class ScanEntry(
        val document: DocumentFile,
        val name: String?,
        val mime: String?,
        val modified: Long
    ) {
        val uri: Uri get() = document.uri
        val isDirectory: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
        val isFile: Boolean get() = !isDirectory && !mime.isNullOrEmpty()
    }

    data class ScanOutcome(
        val tracks: List<Track>,
        val playlists: List<DiscoveredPlaylistFile>,
        val error: String? = null,
        val connected: Boolean = true
    )

    companion object {
        private const val TAG = "ApolloSafScan"

        private val AUDIO_EXT = listOf(
            ".mp3", ".flac", ".m4a", ".aac", ".wav", ".ogg", ".opus", ".wma", ".aiff", ".alac"
        )

        fun isAudio(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val lower = name.lowercase(Locale.US)
            return AUDIO_EXT.any { lower.endsWith(it) }
        }

        fun isPlaylist(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val lower = name.lowercase(Locale.US)
            return lower.endsWith(".m3u") || lower.endsWith(".m3u8")
        }
    }
}
