package com.adamoutler.ssh.sftp

import com.adamoutler.ssh.sftp.model.SftpFileItem
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Fail-fast [SftpClientAdapter] representing a disconnected state.
 * Throws [IOException] on every operation when an active SSH session is required but absent.
 */
class DisconnectedSftpClientAdapter(
    val message: String = "SFTP client is not connected: active SSH session required",
) : SftpClientAdapter {

    override suspend fun list(remotePath: String): List<SftpFileItem> {
        throw IOException(message)
    }

    override suspend fun makeDirectory(remotePath: String) {
        throw IOException(message)
    }

    override suspend fun remove(remotePath: String, isDirectory: Boolean) {
        throw IOException(message)
    }

    override suspend fun rename(oldPath: String, newPath: String) {
        throw IOException(message)
    }

    override suspend fun setPermissions(remotePath: String, permissionsOctal: Int) {
        throw IOException(message)
    }

    override suspend fun openInputStream(remotePath: String): InputStream {
        throw IOException(message)
    }

    override suspend fun openOutputStream(remotePath: String, append: Boolean): OutputStream {
        throw IOException(message)
    }

    override suspend fun stat(remotePath: String): SftpFileItem? {
        throw IOException(message)
    }

    override fun close() {
        throw IOException(message)
    }
}
