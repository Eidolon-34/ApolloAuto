package com.apolloauto.player.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reject oversized text before parsers allocate an unbounded catalog or lyrics document. */
internal fun InputStream.readBoundedBytes(limit: Int): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream(minOf(limit, 8192))
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count < 0) return output.toByteArray()
        if (count == 0) continue
        require(output.size() + count <= limit) { "Text file exceeds the supported size limit" }
        output.write(buffer, 0, count)
    }
}
