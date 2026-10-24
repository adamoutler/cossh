package com.adamoutler.ssh.sftp.saf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.adamoutler.ssh.sftp.SftpClientManager
import com.adamoutler.ssh.sftp.model.SftpTransferState
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Storage Access Framework (SAF) IO abstraction for streaming and unit testing.
 */
interface SafIoHandler {
    fun openOutputStream(uri: Uri, mode: String): OutputStream?
    fun openInputStream(uri: Uri): InputStream?
}

/**
 * Default implementation delegating directly to Android's [ContentResolver].
 */
class ContentResolverSafIoHandler(private val contentResolver: ContentResolver) : SafIoHandler {
    override fun openOutputStream(uri: Uri, mode: String): OutputStream? = contentResolver.openOutputStream(uri, mode)
    override fun openInputStream(uri: Uri): InputStream? = contentResolver.openInputStream(uri)
}

/**
 * Storage Access Framework (SAF) Scoped Storage Bridge.
 * Streams remote SFTP files directly into Android ContentResolver URIs and vice versa,
 * with atomic cache staging and immediate cancellation cleanup.
 */
class SftpSafBridge(
    private val context: Context,
    private val sftpManager: SftpClientManager,
) {
    private val TAG = "SftpSafBridge"

    /**
     * Downloads a remote SFTP file directly to a user-selected SAF [targetUri] (DocumentFile / Content URI).
     * Streams first to an internal cache staging file `.cossh_part_<uuid>`, then commits to [targetUri].
     */
    suspend fun downloadToSafUri(
        remotePath: String,
        targetUri: Uri,
        ioHandler: SafIoHandler = ContentResolverSafIoHandler(context.contentResolver),
        onProgress: ((SftpTransferState) -> Unit)? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val stagingDir = File(context.cacheDir, "sftp_staging").apply { if (!exists()) mkdirs() }
        val stagingFile = File(stagingDir, ".cossh_part_${UUID.randomUUID()}")

        try {
            // Step 1: Download to staging file with progress tracking
            sftpManager.downloadFile(
                remotePath = remotePath,
                destinationFile = stagingFile,
                stagingDir = stagingDir,
                onProgress = onProgress,
            )

            // Step 2: Stream staging file into target SAF Content URI
            ioHandler.openOutputStream(targetUri, "wt")?.use { outStream ->
                stagingFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: throw java.io.IOException("Failed to open output stream for SAF URI: $targetUri")

            AppLog.i(TAG, "Successfully committed SFTP download from $remotePath to SAF URI: $targetUri")
            true
        } catch (e: Exception) {
            AppLog.w(TAG, "SAF download failed for $remotePath: ${e.message}")
            onProgress?.invoke(SftpTransferState.Failed(e))
            throw e
        } finally {
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
        }
    }

    suspend fun downloadToSafUri(
        remotePath: String,
        targetUri: Uri,
        contentResolver: ContentResolver,
        onProgress: ((SftpTransferState) -> Unit)? = null,
    ): Boolean = downloadToSafUri(remotePath, targetUri, ContentResolverSafIoHandler(contentResolver), onProgress)

    /**
     * Uploads a local file backed by a SAF [sourceUri] to a remote SFTP path.
     */
    suspend fun uploadFromSafUri(
        sourceUri: Uri,
        remotePath: String,
        ioHandler: SafIoHandler = ContentResolverSafIoHandler(context.contentResolver),
        onProgress: ((SftpTransferState) -> Unit)? = null,
    ): Unit = withContext(Dispatchers.IO) {
        val stream = ioHandler.openInputStream(sourceUri)
            ?: throw java.io.FileNotFoundException("Could not open input stream for SAF URI: $sourceUri")

        // Stage into cache file for streaming upload
        val stagingDir = File(context.cacheDir, "sftp_upload_staging").apply { if (!exists()) mkdirs() }
        val stagingFile = File(stagingDir, "upload_${UUID.randomUUID()}")

        try {
            stream.use { input ->
                FileOutputStream(stagingFile).use { output ->
                    input.copyTo(output)
                }
            }

            sftpManager.uploadFile(
                sourceFile = stagingFile,
                remotePath = remotePath,
                onProgress = onProgress,
            )
            AppLog.i(TAG, "Successfully uploaded SAF URI $sourceUri to remote $remotePath")
        } finally {
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
        }
    }

    suspend fun uploadFromSafUri(
        sourceUri: Uri,
        remotePath: String,
        contentResolver: ContentResolver,
        onProgress: ((SftpTransferState) -> Unit)? = null,
    ): Unit = uploadFromSafUri(sourceUri, remotePath, ContentResolverSafIoHandler(contentResolver), onProgress)
}
