package com.apolloauto.player.data
import org.junit.Assert.*
import org.junit.Test
class AutoScanPolicyTest {
    @Test fun firstRunIsDue() { assertTrue(AutoScanPolicy.isDue(0, 100)) }
    @Test fun restartDoesNotRescanRecentLibrary() { assertFalse(AutoScanPolicy.isDue(1000, 2000)) }
    @Test fun scheduledCheckBecomesDue() { assertTrue(AutoScanPolicy.isDue(1000, 1000 + AutoScanPolicy.INTERVAL_MS)) }
    @Test fun clockRollbackDoesNotSuppressScanningForever() { assertTrue(AutoScanPolicy.isDue(2000, 1000)) }
}
