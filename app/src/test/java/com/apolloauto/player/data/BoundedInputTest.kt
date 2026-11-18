package com.apolloauto.player.data

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BoundedInputTest {
    @Test fun acceptsExactLimit() { assertArrayEquals(byteArrayOf(1,2,3), byteArrayOf(1,2,3).inputStream().readBoundedBytes(3)) }
    @Test fun acceptsEmpty() { assertArrayEquals(byteArrayOf(), byteArrayOf().inputStream().readBoundedBytes(3)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsOversize() { byteArrayOf(1,2,3,4).inputStream().readBoundedBytes(3) }
}
