package com.apolloauto.player.data

/** Keep the completed library visible until a successful final scan reconciles removals. */
internal fun <T, K> mergeScanPreview(previous: List<T>, discovered: List<T>, key: (T) -> K): List<T> =
    (previous + discovered).associateBy(key).values.toList()
