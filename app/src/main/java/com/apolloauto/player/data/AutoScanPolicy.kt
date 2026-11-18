package com.apolloauto.player.data

/** Directory timestamps are not reliable on every USB provider; use a bounded cadence. */
internal object AutoScanPolicy {
    const val INTERVAL_MS = 15 * 60 * 1000L
    fun isDue(lastScanMs: Long, nowMs: Long): Boolean =
        lastScanMs <= 0 || nowMs < lastScanMs || nowMs - lastScanMs >= INTERVAL_MS
}
