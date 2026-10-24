package com.adamoutler.ssh.sftp

import com.adamoutler.ssh.sftp.model.SftpFileItem
import java.io.InputStream
import java.io.OutputStream

/**
 * Transport abstraction contract for remote SFTP file and directory operations.
 * Enables zero-overhead in-memory unit testing alongside wire-level SSHJ production execution.
 */
interface SftpClientAdapter : AutoCloseable {
    suspend fun list(remotePath: String): List<SftpFileItem>
    suspend fun makeDirectory(remotePath: String)
    suspend fun remove(remotePath: String, isDirectory: Boolean)
    suspend fun rename(oldPath: String, newPath: String)
    suspend fun setPermissions(remotePath: String, permissionsOctal: Int)
    suspend fun openInputStream(remotePath: String): InputStream
    suspend fun openOutputStream(remotePath: String, append: Boolean = false): OutputStream
    suspend fun stat(remotePath: String): SftpFileItem?
}
