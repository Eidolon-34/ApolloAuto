package com.apolloauto.player.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apolloauto.player.ApolloApp
import com.apolloauto.player.data.MusicSource
import com.apolloauto.player.data.MusicSourceKind
import com.apolloauto.player.theme.LocalBodyFont
import com.apolloauto.player.theme.LocalHudPalette
import com.apolloauto.player.ui.hud.ApolloStylePanel
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

@Composable
fun LibrarySettingsPanel() {
    val app = ApolloApp.instance
    val context = LocalContext.current
    val p = LocalHudPalette.current
    val body = LocalBodyFont.current
    val scope = rememberCoroutineScope()
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var localBusy by remember { mutableStateOf(false) }
    var pickerPending by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf("") }
    val scanning by app.libraryStore.scanning.collectAsState()
    val scanStatus by app.libraryStore.status.collectAsState()
    val busy = localBusy || scanning

    fun scan(label: String, action: suspend () -> Unit) {
        if (localBusy || scanning) {
            notice = "A scan is already running. Wait for it to finish or cancel this screen’s scan."
            return
        }
        localBusy = true
        notice = label
        activeJob = scope.launch {
            try {
                runLibraryAction(action,
                    onFailure = { error ->
                        Log.e("ApolloLibraryUI", "Library action failed", error)
                        notice = if (error is SecurityException)
                            "Storage access was denied. Reconnect the USB and choose its music folder again."
                        else "Library action failed (${error.javaClass.simpleName}). Your music files have not been deleted. Try reconnecting the USB and selecting its folder again."
                        app.libraryStore.setStatus(notice)
                    },
                    onCancelled = { notice = "Scan cancelled. Music files were not changed." }
                )
            } finally {
                localBusy = false
            }
        }
    }

    fun launchPicker(launch: () -> Unit) {
        if (busy || pickerPending) return
        notice = "Opening Android’s folder picker. Select the USB music folder, then Use this folder. If that screen is blank, press Back; Apollo has not started scanning yet."
        pickerPending = true
        launchLibraryFolderPicker(launch) { error ->
            pickerPending = false
            Log.e("ApolloLibraryUI", "Folder picker launch failed", error)
            notice = if (error is ActivityNotFoundException)
                "This radio has no Android folder picker available. Enable or install the manufacturer’s Files/Documents app before adding a USB folder."
            else "Android could not open its folder picker (${error.javaClass.simpleName}). No scan was started."
        }
    }

    val sources by app.musicSourceStore.sources.collectAsState()
    val errors by app.musicSourceStore.scanErrors.collectAsState()
    val interfaceStyle by app.themeStore.interfaceStyle.collectAsState()
    val Panel: @Composable (String, @Composable () -> Unit) -> Unit = { title, content ->
        ApolloStylePanel(title = title, interfaceStyle = interfaceStyle) { content() }
    }

    val openPhoneFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        pickerPending = false
        if (uri == null) {
            notice = "Folder selection cancelled. No scan was started."
            return@rememberLauncherForActivityResult
        }
        scan("Folder selected. Requesting access and scanning…") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.libraryScanCoordinator.addPhoneFolder(uri, uri.lastPathSegment)
            notice = app.libraryStore.status.value
        }
    }

    val openAddSource = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        pickerPending = false
        if (uri == null) {
            notice = "Folder selection cancelled. No scan was started."
            return@rememberLauncherForActivityResult
        }
        scan("Folder selected. Requesting access and scanning…") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            app.libraryScanCoordinator.addUsbOrExternal(uri, uri.lastPathSegment)
            notice = app.libraryStore.status.value
        }
    }

    Panel("APOLLO // STORAGE MATRIX") {
        Text(
            "CHANGE PHONE FOLDER and ADD SOURCE open Android’s folder picker; RESCAN reads sources already linked. Removing a source never deletes music files.",
            color = p.dim,
            fontFamily = body,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceActionChip("CHANGE PHONE FOLDER", Modifier.weight(1f), enabled = !busy && !pickerPending) { launchPicker { openPhoneFolder.launch(null) } }
            SourceActionChip("+ ADD SOURCE", Modifier.weight(1f), enabled = !busy && !pickerPending) { launchPicker { openAddSource.launch(null) } }
        }
        Spacer(Modifier.height(8.dp))
        SourceActionChip("RESCAN ALL", Modifier.fillMaxWidth(), enabled = !busy && !pickerPending) {
            scan("Starting library scan…") {
                app.libraryScanCoordinator.scanAllEnabled()
                notice = app.libraryStore.status.value
            }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(scanStatus, color = p.soft, fontFamily = body, fontSize = 12.sp)
            if (localBusy) SourceActionChip("CANCEL SCAN") { activeJob?.cancel() }
        }
        if (notice.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(notice, color = p.soft, fontFamily = body, fontSize = 12.sp)
        }
    }

    sources.forEach { source ->
        Panel(source.kindLabel) {
            SourceCard(
                source = source,
                error = errors[source.id] ?: source.lastError,
                enabled = !busy && !pickerPending,
                onRescan = {
                    scan("Scanning ${source.label}…") {
                        app.libraryScanCoordinator.scanOne(source)
                        notice = app.libraryStore.status.value
                    }
                },
                onToggle = {
                    val enable = !source.enabled
                    app.musicSourceStore.setEnabled(source.id, enable)
                    if (enable) {
                        scan("Scanning ${source.label}…") {
                            app.libraryScanCoordinator.scanOne(source.copy(enabled = true))
                            notice = app.libraryStore.status.value
                        }
                    }
                },
                onPreferred = { app.musicSourceStore.setPreferred(source.id) },
                onRemove = {
                    if (source.id != com.apolloauto.player.data.MusicSourceStore.PHONE_MEDIASTORE_ID) {
                        app.musicSourceStore.removeSource(source.id)
                    } else {
                        app.musicSourceStore.setEnabled(source.id, false)
                    }
                }
            )
        }
    }
}

@Composable
private fun SourceCard(
    source: MusicSource,
    error: String?,
    enabled: Boolean,
    onRescan: () -> Unit,
    onToggle: () -> Unit,
    onPreferred: () -> Unit,
    onRemove: () -> Unit
) {
    val p = LocalHudPalette.current
    val body = LocalBodyFont.current
    val last = if (source.lastScanMs > 0) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(source.lastScanMs))
    } else "Never"
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(source.label, color = p.soft, fontFamily = body, fontSize = 16.sp)
                Text(
                    "${source.trackCount} tracks · ${source.playlistCount} playlists",
                    color = p.dim,
                    fontFamily = body,
                    fontSize = 12.sp
                )
            }
            Text(
                when {
                    !source.enabled -> "OFF"
                    source.connected -> "ONLINE"
                    else -> "OFFLINE"
                },
                color = when {
                    !source.enabled -> p.dim
                    source.connected -> p.accent
                    else -> p.danger
                },
                fontFamily = body,
                fontSize = 11.sp
            )
        }
        Text("Last scan: $last", color = p.dim, fontFamily = body, fontSize = 11.sp)
        if (source.preferred) {
            Text("PREFERRED SOURCE", color = p.secondary, fontFamily = body, fontSize = 10.sp)
        }
        if (!error.isNullOrBlank()) {
            Text("Error: $error", color = p.danger, fontFamily = body, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceActionChip(if (source.enabled) "DISABLE" else "ENABLE", enabled = enabled) { onToggle() }
            SourceActionChip("RESCAN", enabled = enabled) { onRescan() }
            if (!source.preferred) SourceActionChip("DEFAULT", enabled = enabled) { onPreferred() }
            if (source.kind != MusicSourceKind.PHONE_MEDIASTORE) {
                SourceActionChip("REMOVE", enabled = enabled) { onRemove() }
            }
        }
        Text(
            "Remove drops Apollo’s index only — files stay on storage.",
            color = p.dim,
            fontFamily = body,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun SourceActionChip(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val p = LocalHudPalette.current
    val body = LocalBodyFont.current
    Text(
        label,
        color = p.voidBlack,
        fontFamily = body,
        fontSize = 11.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (enabled) p.primary else p.dim)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}
