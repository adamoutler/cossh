package com.adamoutler.ssh.sftp.transfer

import com.adamoutler.ssh.sftp.model.SftpTransferState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SftpTransferManagerTest {

    @Test
    fun `startTransfer tracks progress to completion`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val manager = SftpTransferManager(scope)

        val transferId = manager.startTransfer(
            filename = "backup.tar.gz",
            remotePath = "/var/backup.tar.gz",
            isUpload = false,
        ) { onProgress ->
            onProgress(SftpTransferState.Transferring(500, 1000, 50, 50f))
            onProgress(SftpTransferState.Completed(1000))
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val item = manager.transfers.value[transferId]
        assertNotNull(item)
        assertEquals("backup.tar.gz", item?.filename)
        assertEquals("/var/backup.tar.gz", item?.remotePath)
        assertEquals(false, item?.isUpload)
        assertTrue(item?.state is SftpTransferState.Completed)
        assertEquals(1000L, (item?.state as SftpTransferState.Completed).bytesTransferred)
    }

    @Test
    fun `startTransfer catches exceptions and sets failed state`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val manager = SftpTransferManager(scope)

        val transferId = manager.startTransfer(
            filename = "data.db",
            remotePath = "/var/data.db",
            isUpload = true,
        ) { _ ->
            throw IOException("Network connection dropped")
        }

        testDispatcher.scheduler.advanceUntilIdle()

        val item = manager.transfers.value[transferId]
        assertNotNull(item)
        assertTrue(item?.state is SftpTransferState.Failed)
        assertEquals("Network connection dropped", (item?.state as SftpTransferState.Failed).throwable.message)
    }

    @Test
    fun `cancelTransfer aborts job and marks transfer as cancelled`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val manager = SftpTransferManager(scope)

        val transferId = manager.startTransfer(
            filename = "large.iso",
            remotePath = "/var/large.iso",
            isUpload = false,
        ) { onProgress ->
            onProgress(SftpTransferState.Transferring(100, 10000, 10, 1f))
            delay(5000)
            onProgress(SftpTransferState.Completed(10000))
        }

        manager.cancelTransfer(transferId)
        testDispatcher.scheduler.advanceUntilIdle()

        val item = manager.transfers.value[transferId]
        assertNotNull(item)
        assertTrue(item?.state is SftpTransferState.Failed)
        assertTrue((item?.state as SftpTransferState.Failed).throwable is java.util.concurrent.CancellationException)
    }

    @Test
    fun `removeTransfer removes item from transfers map`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val manager = SftpTransferManager(scope)

        val transferId = manager.startTransfer(
            filename = "log.txt",
            remotePath = "/var/log.txt",
            isUpload = false,
        ) { onProgress ->
            onProgress(SftpTransferState.Completed(100))
        }

        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(manager.transfers.value[transferId])

        manager.removeTransfer(transferId)
        assertNull(manager.transfers.value[transferId])
    }
}
