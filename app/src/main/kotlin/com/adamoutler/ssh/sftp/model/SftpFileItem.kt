package com.adamoutler.ssh.sftp.model

/**
 * Metadata representation of a remote SFTP file or directory entry.
 */
data class SftpFileItem(
    val name: String,
    val fullPath: String,
    val size: Long,
    val isDirectory: Boolean,
    val isRegularFile: Boolean,
    val isSymlink: Boolean,
    val permissions: String,
    val octalPermissions: Int,
    val modifiedTimeEpochMs: Long,
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "—"
            if (size < 1024) return "$size B"
            val kb = size / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.1f MB".format(mb)
            val gb = mb / 1024.0
            return "%.1f GB".format(gb)
        }
}
