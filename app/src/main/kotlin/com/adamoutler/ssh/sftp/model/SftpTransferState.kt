package com.adamoutler.ssh.sftp.model

/**
 * Reactive transfer state representing the lifecycle of an active SFTP file download or upload.
 */
sealed interface SftpTransferState {
    data object Idle : SftpTransferState

    data class Transferring(
        val bytesTransferred: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val percent: Float,
    ) : SftpTransferState

    data class Completed(
        val bytesTransferred: Long,
    ) : SftpTransferState

    data class Failed(
        val throwable: Throwable,
    ) : SftpTransferState
}
