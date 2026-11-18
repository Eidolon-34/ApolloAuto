package com.apolloauto.player.data

/** Source-qualified, case-preserving folder identity. USB roots may have identical names. */
data class FolderIdentity(val sourceId: String, val path: String) {
    fun contains(other: FolderIdentity, includeSubfolders: Boolean): Boolean =
        sourceId == other.sourceId && (path == other.path ||
            (includeSubfolders && (path.isEmpty() || other.path.startsWith("$path/"))))

    fun ancestors(): List<FolderIdentity> {
        val result = mutableListOf(this)
        var parent = path
        while (parent.isNotEmpty()) {
            parent = parent.substringBeforeLast('/', "")
            result.add(copy(path = parent))
        }
        return result
    }

    companion object {
        fun from(sourceId: String, relativePath: String, fallbackFolder: String): FolderIdentity {
            val normalized = relativePath.replace('\\', '/').trimStart('/')
            val parent = if (normalized.isNotBlank()) normalized.substringBeforeLast('/', "")
                else fallbackFolder.replace('\\', '/').trim('/')
            return FolderIdentity(sourceId, parent)
        }
    }
}

fun Track.folderIdentity(): FolderIdentity = FolderIdentity.from(sourceId, relativePath, folder)
