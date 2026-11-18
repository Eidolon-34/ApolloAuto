package com.apolloauto.player.ui

import kotlinx.coroutines.CancellationException

/** UI boundary: ordinary scan failures must not escape a launch and close the activity. */
internal suspend fun runLibraryAction(
    action: suspend () -> Unit,
    onFailure: (Exception) -> Unit,
    onCancelled: () -> Unit
) {
    try {
        action()
    } catch (cancelled: CancellationException) {
        onCancelled()
        throw cancelled
    } catch (error: Exception) {
        onFailure(error)
    }
}

/** Launch errors (including a missing DocumentsUI handler) stay inside Apollo. */
internal fun launchLibraryFolderPicker(launch: () -> Unit, onFailure: (Exception) -> Unit) {
    try {
        launch()
    } catch (error: Exception) {
        onFailure(error)
    }
}
