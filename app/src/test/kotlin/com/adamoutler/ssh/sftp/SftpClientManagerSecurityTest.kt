package com.adamoutler.ssh.sftp

import com.adamoutler.ssh.sftp.model.SftpTransferState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class SftpClientManagerSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fakeAdapter: FakeSftpClientAdapter
    private lateinit var sftpManager: SftpClientManager

    @Before
    fun setUp() {
        fakeAdapter = FakeSftpClientAdapter()
        sftpManager = SftpClientManager(fakeAdapter)

        // Seed virtual filesystem
        fakeAdapter.addVirtualDirectory("/home/user")
        fakeAdapter.addVirtualFile("/home/user/notes.txt", "Hello CoSSH SFTP!".toByteArray(Charsets.UTF_8))
        fakeAdapter.addVirtualFile("/home/user/script.sh", "#!/bin/sh\necho ok".toByteArray(Charsets.UTF_8), permsOctal = 0x1ED) // 0755
    }

    @Test
    fun `listDirectory returns canonical file items and metadata`() = runTest {
        val items = sftpManager.listDirectory("/home/user/../user")
        assertEquals(2, items.size)

        val notes = items.find { it.name == "notes.txt" }
        assertNotNull(notes)
        assertEquals("/home/user/notes.txt", notes?.fullPath)
        assertEquals("Hello CoSSH SFTP!".length.toLong(), notes?.size)
        assertFalse(notes?.isDirectory ?: true)

        val script = items.find { it.name == "script.sh" }
        assertNotNull(script)
        assertEquals(0x1ED, script?.octalPermissions)
    }

    @Test
    fun `createDirectory and delete manage directories cleanly`() = runTest {
        sftpManager.createDirectory("/home/user/new_folder")
        val stat = sftpManager.stat("/home/user/new_folder")
        assertNotNull(stat)
        assertTrue(stat?.isDirectory ?: false)

        sftpManager.delete("/home/user/new_folder", isDirectory = true)
        val statAfter = sftpManager.stat("/home/user/new_folder")
        assertNull(statAfter)
    }

    @Test
    fun `rename changes remote path atomically`() = runTest {
        sftpManager.rename("/home/user/notes.txt", "/home/user/notes_renamed.txt")
        assertNull(sftpManager.stat("/home/user/notes.txt"))
        assertNotNull(sftpManager.stat("/home/user/notes_renamed.txt"))
    }

    @Test
    fun `setPermissions updates octal permissions`() = runTest {
        sftpManager.setPermissions("/home/user/notes.txt", 0x1ED) // 0755
        val stat = sftpManager.stat("/home/user/notes.txt")
        assertEquals(0x1ED, stat?.octalPermissions)
    }

    @Test
    fun `readFileContent returns text for files under 2MB`() = runTest {
        val content = sftpManager.readFileContent("/home/user/notes.txt")
        assertEquals("Hello CoSSH SFTP!", content)
    }

    @Test
    fun `readFileContent throws IllegalStateException when file exceeds 2MB safety guard`() = runTest {
        // Create 2.1 MB virtual file
        val bigData = ByteArray(2 * 1024 * 1024 + 100)
        fakeAdapter.addVirtualFile("/home/user/big.log", bigData)

        try {
            sftpManager.readFileContent("/home/user/big.log")
            fail("Expected IllegalStateException for file exceeding 2MB")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("exceeds maximum preview limit") ?: false)
        }
    }

    @Test
    fun `downloadFile writes destination file via atomic staging and cleans up staging`() = runTest {
        val destFile = File(tempFolder.root, "downloaded_notes.txt")
        val stagingDir = File(tempFolder.root, "staging")
        val progressStates = mutableListOf<SftpTransferState>()

        val result = sftpManager.downloadFile(
            remotePath = "/home/user/notes.txt",
            destinationFile = destFile,
            stagingDir = stagingDir,
            onProgress = { progressStates.add(it) },
        )

        assertTrue(result.exists())
        assertEquals("Hello CoSSH SFTP!", result.readText())

        // Verify staging directory contains NO lingering .cossh_part_ files
        val lingeringStaging = stagingDir.listFiles { _, name -> name.startsWith(".cossh_part_") }
        assertTrue("No staging files should linger after completion", lingeringStaging?.isEmpty() ?: true)

        // Verify progress updates
        assertTrue(progressStates.any { it is SftpTransferState.Completed })
    }

    @Test
    fun `downloadFile immediately deletes partial staging file upon error`() = runTest {
        val failingAdapter = object : SftpClientAdapter by fakeAdapter {
            override suspend fun openInputStream(remotePath: String): InputStream = object : InputStream() {
                override fun read(): Int = throw java.io.IOException("Simulated network drop")
            }
        }
        val failingManager = SftpClientManager(failingAdapter)
        val destFile = File(tempFolder.root, "aborted.txt")
        val stagingDir = File(tempFolder.root, "staging_err")

        try {
            failingManager.downloadFile(
                remotePath = "/home/user/notes.txt",
                destinationFile = destFile,
                stagingDir = stagingDir,
            )
            fail("Expected IOException")
        } catch (_: java.io.IOException) {
            // Expected
        }

        assertFalse(destFile.exists())
        val lingeringStaging = stagingDir.listFiles { _, name -> name.startsWith(".cossh_part_") }
        assertTrue("Orphaned staging files must be cleaned up on failure", lingeringStaging?.isEmpty() ?: true)
    }

    @Test
    fun `uploadFile roundtrip transfers local file to remote destination`() = runTest {
        val localUpload = tempFolder.newFile("upload_me.txt")
        localUpload.writeText("Uploaded data content from Android client")

        sftpManager.uploadFile(
            sourceFile = localUpload,
            remotePath = "/home/user/uploaded.txt",
        )

        val stat = sftpManager.stat("/home/user/uploaded.txt")
        assertNotNull(stat)
        assertEquals(localUpload.length(), stat?.size)
        assertEquals("Uploaded data content from Android client", sftpManager.readFileContent("/home/user/uploaded.txt"))
    }
}
