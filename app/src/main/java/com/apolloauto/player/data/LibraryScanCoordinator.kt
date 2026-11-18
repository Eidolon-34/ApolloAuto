package com.apolloauto.player.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

/**
 * Scans all enabled music sources without blocking playback.
 * MediaStore phone library + SAF roots (phone folders / USB / SD).
 */
class LibraryScanCoordinator(
    private val context: Context,
    private val sourceStore: MusicSourceStore,
    private val deviceScanner: DeviceMusicScanner,
    private val safScanner: SafLibraryScanner,
    private val libraryStore: LibraryStore,
    private val m3uService: M3uPlaylistService,
    private val playlistStore: PlaylistStore,
    private val artEnricher: AlbumArtEnricher
) {
    private val scanMutex = Mutex()

    suspend fun scanAllEnabled(autoImportDiscoveredM3u: Boolean = true) = runScan {
        scanAllLocked(autoImportDiscoveredM3u)
    }

    /** Coalesce automatic requests; manual refresh remains available. */
    suspend fun scanDue() {
        if (scanMutex.isLocked || sourceStore.sources.value.none {
            it.enabled && AutoScanPolicy.isDue(it.lastScanMs, System.currentTimeMillis())
        }) return
        runScan { scanAllLocked(autoImportDiscoveredM3u = true, onlyDue = true) }
    }

    /**
     * Callers usually launch from Compose's Main scope. Keep catalog merges, cache writes,
     * provider access and source setup off that thread, and never let an ordinary storage
     * exception terminate the activity. Cancellation still belongs to the caller.
     */
    private suspend fun <T> runScan(action: suspend () -> T): T? = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            libraryStore.setScanning(true)
            try {
                action()
            } catch (cancelled: CancellationException) {
                libraryStore.setStatus("Scan cancelled — previous library retained")
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Library operation failed", error)
                libraryStore.setStatus(
                    "Library operation failed: ${error.message ?: "storage unavailable"}. " +
                        "Reconnect the drive or choose the folder again."
                )
                null
            } finally {
                libraryStore.setScanning(false)
            }
        }
    }

    private suspend fun scanAllLocked(autoImportDiscoveredM3u: Boolean, onlyDue: Boolean = false) {
        val sources = sourceStore.sources.value.filter { it.enabled && (!onlyDue || AutoScanPolicy.isDue(it.lastScanMs, System.currentTimeMillis())) }
            // MediaStore first so tracks appear quickly while SAF trees walk.
            .sortedBy { if (it.kind == MusicSourceKind.PHONE_MEDIASTORE) 0 else 1 }
        if (onlyDue && sources.isEmpty()) return
        if (sources.isEmpty()) {
            libraryStore.setScanning(false)
            libraryStore.setStatus("No music sources enabled — open Settings → Library")
            Log.w(TAG, "scanAllEnabled: no enabled sources")
            return
        }
        libraryStore.setScanning(true)
        libraryStore.setStatus("Scanning ${sources.size} source(s)…")
        Log.i(TAG, "scanAllEnabled: ${sources.joinToString { "${it.kind}/${it.label}" }}")
        try {
            for (source in sources) {
                try {
                    scanOneLocked(source, autoImportDiscoveredM3u)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Source unavailable; continuing other sources", error)
                } finally {
                    sourceStore.clearScanPreview(source.id)
                }
            }
            val total = libraryStore.tracks.value.size
            if (total == 0) {
                libraryStore.setStatus(
                    "Scan finished — 0 tracks. Re-select your music folder in Settings → Library " +
                        "(SAF permission is required after reinstall)."
                )
            }
        } finally {
            libraryStore.setScanning(false)
        }
        // Folder covers only during SAF walk — fill embedded art afterward.
        enrichBounded()
    }

    companion object {
        private const val TAG = "ApolloLibScan"
    }

    suspend fun scanOne(source: MusicSource, autoImportDiscoveredM3u: Boolean = true) = runScan {
        try { scanOneLocked(source, autoImportDiscoveredM3u) }
        finally { sourceStore.clearScanPreview(source.id) }
    }

    private suspend fun scanOneLocked(source: MusicSource, autoImportDiscoveredM3u: Boolean = true) {
        coroutineContext.ensureActive()
        when (source.kind) {
            MusicSourceKind.PHONE_MEDIASTORE -> {
                val tracks = try {
                    deviceScanner.scanPhoneLibrary(publishDirectly = false) { batch ->
                        sourceStore.previewScan(source.id, batch)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    sourceStore.recordScanFailure(
                        source.id, error.message ?: "Phone provider unavailable", connected = false
                    )
                    throw error
                }
                if (tracks.isEmpty() && sourceStore.tracksForSource(source.id).isNotEmpty()) {
                    // Permission blip / MediaStore hiccup — keep last good phone catalog.
                    sourceStore.recordScanFailure(
                        source.id,
                        "Phone scan returned empty — keeping previous library",
                        connected = true
                    )
                } else {
                    sourceStore.recordScanResult(source.id, tracks, emptyList())
                }
            }
            MusicSourceKind.PHONE_FOLDER,
            MusicSourceKind.USB_SSD,
            MusicSourceKind.SD_CARD,
            MusicSourceKind.OTHER -> {
                val outcome = safScanner.scanSource(source)
                if (autoImportDiscoveredM3u && outcome.error == null) {
                    importDiscovered(outcome.playlists)
                }
            }
        }
    }

    /**
     * User-selected phone music folder becomes the phone library.
     * Disables MediaStore (ringtones/notifications/etc.) and other phone folders
     * so the catalog only shows files under this tree.
     */
    suspend fun addPhoneFolder(treeUri: Uri, displayName: String?): MusicSource? = runScan {
        val kind = MusicSourceStore.inferKindFromDisplayName(displayName)
        // Prefer PHONE_FOLDER when SAF root looks like internal storage.
        val resolvedKind = when (kind) {
            MusicSourceKind.USB_SSD, MusicSourceKind.SD_CARD -> kind
            else -> MusicSourceKind.PHONE_FOLDER
        }
        val source = sourceStore.upsertSafSource(
            treeUri = treeUri,
            kind = resolvedKind,
            label = displayName?.ifBlank { null } ?: MusicSourceStore.defaultLabel(resolvedKind),
            makePreferred = true
        )
        takePersistablePermission(treeUri)
        sourceStore.useExclusivePhoneFolder(source.id)
        scanOneLocked(source)
        enrichBounded()
        if (sourceStore.scanErrors.value[source.id] == null) {
            libraryStore.setStatus("Phone folder active — ${source.label}.")
        }
        source
    }

    suspend fun addUsbOrExternal(treeUri: Uri, displayName: String?): MusicSource? = runScan {
        val kind = MusicSourceStore.inferKindFromDisplayName(displayName).let {
            if (it == MusicSourceKind.PHONE_FOLDER) MusicSourceKind.USB_SSD else it
        }
        val source = sourceStore.upsertSafSource(
            treeUri = treeUri,
            kind = kind,
            label = displayName?.ifBlank { null } ?: MusicSourceStore.defaultLabel(kind),
            makePreferred = false
        )
        takePersistablePermission(treeUri)
        scanOneLocked(source)
        enrichBounded()
        source
    }

    private suspend fun importDiscovered(files: List<DiscoveredPlaylistFile>) = withContext(Dispatchers.IO) {
        val catalog = libraryStore.tracks.value
        for (file in files) {
            coroutineContext.ensureActive()
            try {
                val dir = file.relativePath.substringBeforeLast('/', missingDelimiterValue = "")
                val preview = m3uService.preview(file.uri, catalog.filter { it.sourceId == file.sourceId }, dir)
                m3uService.importConfirmed(preview)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Skipping unreadable playlist: ${file.name}", error)
            }
        }
    }

    private suspend fun enrichBounded() {
        try {
            artEnricher.enrichMissing(maxTracks = 24)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "art enrich failed", error)
        }
    }

    private fun takePersistablePermission(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
}
