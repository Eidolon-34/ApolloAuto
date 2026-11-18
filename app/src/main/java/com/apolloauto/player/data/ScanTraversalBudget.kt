package com.apolloauto.player.data

/** Fails closed before an unusual provider tree can exhaust the radio heap or stack. */
internal class ScanTraversalBudget(
    private val maxEntries: Int = 100_000,
    private val maxDirectories: Int = 8_192,
    private val maxDepth: Int = 128
) {
    private var entries = 0
    private val directories = HashSet<String>()
    fun enterDirectory(identity: String, depth: Int) {
        check(depth <= maxDepth) { "Folder nesting too deep; select a smaller music folder" }
        check(directories.add(identity)) { "Repeated folder detected; previous library preserved" }
        check(directories.size <= maxDirectories) { "Too many folders; select a smaller music folder" }
    }
    fun visitEntry() {
        check(++entries <= maxEntries) { "Library too large; select a smaller music folder" }
    }
}
