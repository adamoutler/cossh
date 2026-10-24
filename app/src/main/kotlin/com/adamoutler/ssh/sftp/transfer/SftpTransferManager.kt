package com.adamoutler.ssh.sftp.transfer

import com.adamoutler.ssh.sftp.model.SftpTransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Representation of an active or recent SFTP transfer item.
 */
data class SftpTransferItem(
    val id: String = UUID.randomUUID().toString(),
    val filename: String,
    val remotePath: String,
    val isUpload: Boolean,
    val state: SftpTransferState = SftpTransferState.Idle,
)

/**
 * Central state machine managing active SFTP transfers with progress flow and cancellation support.
 */
class SftpTransferManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val _transfers = MutableStateFlow<Map<String, SftpTransferItem>>(emptyMap())
    val transfers: StateFlow<Map<String, SftpTransferItem>> = _transfers.asStateFlow()

    private val transferJobs = mutableMapOf<String, Job>()

    fun startTransfer(
        filename: String,
        remotePath: String,
        isUpload: Boolean,
        block: suspend (onProgress: (SftpTransferState) -> Unit) -> Unit,
    ): String {
        val transferId = UUID.randomUUID().toString()
        val item = SftpTransferItem(
            id = transferId,
            filename = filename,
            remotePath = remotePath,
            isUpload = isUpload,
            state = SftpTransferState.Idle,
        )

        _transfers.update { it + (transferId to item) }

        val job = scope.launch {
            try {
                block { progress ->
                    _transfers.update { current ->
                        val existing = current[transferId]
                        if (existing != null) {
                            current + (transferId to existing.copy(state = progress))
                        } else {
                            current
                        }
                    }
                }
            } catch (e: Exception) {
                _transfers.update { current ->
                    val existing = current[transferId]
                    if (existing != null) {
                        current + (transferId to existing.copy(state = SftpTransferState.Failed(e)))
                    } else {
                        current
                    }
                }
            }
        }

        synchronized(transferJobs) {
            transferJobs[transferId] = job
        }

        return transferId
    }

    fun cancelTransfer(transferId: String) {
        synchronized(transferJobs) {
            transferJobs.remove(transferId)?.cancel()
        }
        _transfers.update { current ->
            val existing = current[transferId]
            if (existing != null) {
                current + (transferId to existing.copy(state = SftpTransferState.Failed(java.util.concurrent.CancellationException("Transfer cancelled by user"))))
            } else {
                current
            }
        }
    }

    fun removeTransfer(transferId: String) {
        synchronized(transferJobs) {
            transferJobs.remove(transferId)?.cancel()
        }
        _transfers.update { it - transferId }
    }

    fun clearFinished() {
        _transfers.update { current ->
            current.filterValues { it.state !is SftpTransferState.Completed && it.state !is SftpTransferState.Failed }
        }
    }
}
