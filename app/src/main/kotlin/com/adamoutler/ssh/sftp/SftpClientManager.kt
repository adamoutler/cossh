package com.adamoutler.ssh.sftp

import com.adamoutler.ssh.sftp.model.SftpFileItem
import com.adamoutler.ssh.sftp.model.SftpTransferState
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Hardened SFTP Client Manager providing secure remote filesystem operations:
 * - Strict POSIX path canonicalization and directory traversal guards.
 * - Atomic download staging (`.cossh_part_`) with immediate cleanup on cancellation or network abort.
 * - Reactive progress calculation and transfer metrics.
 * - Bounded text inspection with a strict 2MB memory safety ceiling.
 */
class SftpClientManager(
    val adapter: SftpClientAdapter,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {

    companion object {
        private const val TAG = "SftpClientManager"
    }

    suspend fun listDirectory(path: String): List<SftpFileItem> = withContext(ioDispatcher) {
        val canonical = SftpPathValidator.canonicalize(path)
        adapter.list(canonical)
    }

    suspend fun createDirectory(path: String): Unit = withContext(ioDispatcher) {
        val canonical = SftpPathValidator.canonicalize(path)
        adapter.makeDirectory(canonical)
    }

    suspend fun delete(path: String, isDirectory: Boolean): Unit = withContext(ioDispatcher) {
        val canonical = SftpPathValidator.canonicalize(path)
        adapter.remove(canonical, isDirectory)
    }

    suspend fun rename(oldPath: String, newPath: String): Unit = withContext(ioDispatcher) {
        val canonicalOld = SftpPathValidator.canonicalize(oldPath)
        val canonicalNew = SftpPathValidator.canonicalize(newPath)
        adapter.rename(canonicalOld, canonicalNew)
    }

    suspend fun setPermissions(path: String, octalPermissions: Int): Unit = withContext(ioDispatcher) {
        val canonical = SftpPathValidator.canonicalize(path)
        adapter.setPermissions(canonical, octalPermissions)
    }

    suspend fun stat(path: String): SftpFileItem? = withContext(ioDispatcher) {
        val canonical = SftpPathValidator.canonicalize(path)
        adapter.stat(canonical)
    }

    /**
     * Reads text content from a remote file up to [maxBytes] (default 2MB safety guard).
     * Throws [IllegalStateException] if remote file exceeds [maxBytes].
     */
    suspend fun readFileContent(
        remotePath: String,
        maxBytes: Long = 2 * 1024 * 1024L,
    ): String = withContext(ioDispatcher) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        val fileStat = stat(canonical)
        if (fileStat != null && fileStat.size > maxBytes) {
            throw IllegalStateException("File size (${fileStat.formattedSize}) exceeds maximum preview limit (2MB).")
        }

        adapter.openInputStream(canonical).use { input ->
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            var totalRead = 0L

            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                totalRead += bytesRead
                if (totalRead > maxBytes) {
                    throw IllegalStateException("File stream exceeded maximum preview limit (2MB).")
                }
                output.write(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    /**
     * Downloads [remotePath] to [destinationFile] using atomic staging:
     * 1. Streams bytes to a private staging file `.cossh_part_<uuid>`.
     * 2. Emits real-time speed, bytes, and percentage progress updates.
     * 3. Upon 100% completion, executes atomic move/rename to [destinationFile].
     * 4. Guarantees immediate cleanup of partial staging file on cancellation or failure.
     */
    suspend fun downloadFile(
        remotePath: String,
        destinationFile: File,
        stagingDir: File = destinationFile.parentFile ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp"),
        onProgress: ((SftpTransferState) -> Unit)? = null,
    ): File = withContext(ioDispatcher) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        val totalBytes = stat(canonical)?.size ?: -1L

        if (!stagingDir.exists()) {
            stagingDir.mkdirs()
        }

        val stagingFile = File(stagingDir, ".cossh_part_${UUID.randomUUID()}")
        var isSuccess = false

        onProgress?.invoke(SftpTransferState.Transferring(0, totalBytes, 0, 0f))

        try {
            adapter.openInputStream(canonical).use { input ->
                FileOutputStream(stagingFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var transferred = 0L
                    var lastTime = System.currentTimeMillis()
                    var bytesSinceLastSample = 0L

                    var read = input.read(buffer)
                    while (read != -1) {
                        ensureActive()
                        output.write(buffer, 0, read)
                        transferred += read
                        bytesSinceLastSample += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 250) {
                            val currentSpeed = (bytesSinceLastSample * 1000L) / elapsed.coerceAtLeast(1)
                            val percent = if (totalBytes > 0) (transferred.toFloat() / totalBytes) * 100f else 0f
                            onProgress?.invoke(SftpTransferState.Transferring(transferred, totalBytes, currentSpeed, percent))
                            lastTime = now
                            bytesSinceLastSample = 0L
                        }

                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }

            // Commit atomic rename
            try {
                Files.move(stagingFile.toPath(), destinationFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: Exception) {
                Files.move(stagingFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            isSuccess = true
            onProgress?.invoke(SftpTransferState.Completed(destinationFile.length()))
            AppLog.i(TAG, "Successfully downloaded $canonical to ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")
            destinationFile
        } catch (e: Exception) {
            onProgress?.invoke(SftpTransferState.Failed(e))
            AppLog.w(TAG, "Download failed for $canonical: ${e.message}")
            throw e
        } finally {
            if (!isSuccess && stagingFile.exists()) {
                stagingFile.delete()
                AppLog.d(TAG, "Cleaned up orphaned staging file: ${stagingFile.name}")
            }
        }
    }

    /**
     * Uploads local [sourceFile] to remote [remotePath] with progress tracking.
     */
    suspend fun uploadFile(
        sourceFile: File,
        remotePath: String,
        onProgress: ((SftpTransferState) -> Unit)? = null,
    ): Unit = withContext(ioDispatcher) {
        val canonical = SftpPathValidator.canonicalize(remotePath)
        val totalBytes = sourceFile.length()

        onProgress?.invoke(SftpTransferState.Transferring(0, totalBytes, 0, 0f))

        try {
            FileInputStream(sourceFile).use { input ->
                adapter.openOutputStream(canonical, append = false).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var transferred = 0L
                    var lastTime = System.currentTimeMillis()
                    var bytesSinceLastSample = 0L

                    var read = input.read(buffer)
                    while (read != -1) {
                        ensureActive()
                        output.write(buffer, 0, read)
                        transferred += read
                        bytesSinceLastSample += read

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastTime
                        if (elapsed >= 250) {
                            val currentSpeed = (bytesSinceLastSample * 1000L) / elapsed.coerceAtLeast(1)
                            val percent = if (totalBytes > 0) (transferred.toFloat() / totalBytes) * 100f else 0f
                            onProgress?.invoke(SftpTransferState.Transferring(transferred, totalBytes, currentSpeed, percent))
                            lastTime = now
                            bytesSinceLastSample = 0L
                        }

                        read = input.read(buffer)
                    }
                    output.flush()
                }
            }

            onProgress?.invoke(SftpTransferState.Completed(totalBytes))
            AppLog.i(TAG, "Successfully uploaded ${sourceFile.name} to $canonical ($totalBytes bytes)")
        } catch (e: Exception) {
            onProgress?.invoke(SftpTransferState.Failed(e))
            AppLog.w(TAG, "Upload failed for $canonical: ${e.message}")
            throw e
        }
    }

    override fun close() {
        adapter.close()
    }
}
