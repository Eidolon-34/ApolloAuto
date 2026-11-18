package com.apolloauto.player.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LibraryActionGuardTest {
    @Test fun actionFailureIsReportedRatherThanThrown() = runBlocking {
        var reported: Exception? = null
        runLibraryAction({ throw SecurityException("denied") }, { reported = it }, { fail("not cancelled") })
        assertTrue(reported is SecurityException)
    }
    @Test fun cancellationIsReportedAndRethrown() = runBlocking {
        var cancelled = false
        try {
            runLibraryAction({ throw CancellationException("stop") }, { fail("not a failure") }, { cancelled = true })
            fail("cancellation must propagate")
        } catch (_: CancellationException) {
            assertTrue(cancelled)
        }
    }
    @Test fun successfulActionDoesNotReportFailure() = runBlocking {
        var ran = false
        runLibraryAction({ ran = true }, { fail("unexpected failure") }, { fail("unexpected cancel") })
        assertTrue(ran)
    }
    @Test fun pickerLaunchErrorIsReported() {
        var reported: Exception? = null
        launchLibraryFolderPicker({ throw IllegalStateException("no activity") }, { reported = it })
        assertTrue(reported is IllegalStateException)
    }
    @Test fun pickerLaunchRunsOnce() {
        var count = 0
        launchLibraryFolderPicker({ count++ }, { fail("unexpected failure") })
        assertEquals(1, count)
    }
}
