package com.apolloauto.player.data

import org.junit.Test

class ScanSafetyTest {
    @Test fun supportsLargeNormalTree() {
        val budget = ScanTraversalBudget()
        repeat(1000) { budget.enterDirectory("folder$it", it % 5) }
        repeat(50_000) { budget.visitEntry() }
    }
    @Test(expected = IllegalStateException::class)
    fun rejectsRepeatedFolder() {
        val budget = ScanTraversalBudget()
        budget.enterDirectory("same", 0)
        budget.enterDirectory("same", 1)
    }
    @Test(expected = IllegalStateException::class)
    fun rejectsExcessiveDepth() {
        ScanTraversalBudget(maxDepth = 2).enterDirectory("deep", 3)
    }
    @Test(expected = IllegalStateException::class)
    fun rejectsExcessiveEntries() {
        val budget = ScanTraversalBudget(maxEntries = 2)
        repeat(3) { budget.visitEntry() }
    }
    @Test(expected = IllegalStateException::class)
    fun rejectsExcessiveDirectories() {
        val budget = ScanTraversalBudget(maxDirectories = 2)
        repeat(3) { budget.enterDirectory("folder$it", 0) }
    }
}
