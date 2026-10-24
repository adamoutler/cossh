package com.adamoutler.ssh.sftp.saf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.sftp.FakeSftpClientAdapter
import com.adamoutler.ssh.sftp.SftpClientManager
import com.adamoutler.ssh.sftp.model.SftpTransferState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SftpSafBridgeTest {

    private lateinit var context: Context
    private lateinit var fakeAdapter: FakeSftpClientAdapter
    private lateinit var sftpManager: SftpClientManager
    private lateinit var safBridge: SftpSafBridge

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeAdapter = FakeSftpClientAdapter()
        sftpManager = SftpClientManager(fakeAdapter)
        safBridge = SftpSafBridge(context, sftpManager)

        fakeAdapter.addVirtualDirectory("/var/backups")
        fakeAdapter.addVirtualFile("/var/backups/db.sql", "CREATE TABLE users (id INT);".toByteArray(Charsets.UTF_8))
    }

    @Test
    fun `downloadToSafUri streams remote content to target URI via atomic staging`() = runTest {
        val targetUri = Uri.parse("content://com.android.providers.downloads.documents/document/raw%3A123")
        val destinationOutput = ByteArrayOutputStream()

        val mockHandler = object : SafIoHandler {
            override fun openOutputStream(uri: Uri, mode: String): OutputStream = destinationOutput
            override fun openInputStream(uri: Uri): InputStream? = null
        }

        val progressUpdates = mutableListOf<SftpTransferState>()
        val success = safBridge.downloadToSafUri(
            remotePath = "/var/backups/db.sql",
            targetUri = targetUri,
            ioHandler = mockHandler,
            onProgress = { progressUpdates.add(it) },
        )

        assertTrue(success)
        assertEquals("CREATE TABLE users (id INT);", destinationOutput.toString(Charsets.UTF_8.name()))

        // Verify staging directory does not hold lingering .cossh_part_ files
        val stagingDir = File(context.cacheDir, "sftp_staging")
        val stagingFiles = stagingDir.listFiles { _, name -> name.startsWith(".cossh_part_") }
        assertTrue(stagingFiles?.isEmpty() ?: true)
    }

    @Test
    fun `downloadToSafUri cleans up staging file if ContentResolver throws IOException`() = runTest {
        val targetUri = Uri.parse("content://invalid/uri")

        val failingHandler = object : SafIoHandler {
            override fun openOutputStream(uri: Uri, mode: String): OutputStream = throw java.io.IOException("Permission denied on storage provider")
            override fun openInputStream(uri: Uri): InputStream? = null
        }

        try {
            safBridge.downloadToSafUri(
                remotePath = "/var/backups/db.sql",
                targetUri = targetUri,
                ioHandler = failingHandler,
            )
            fail("Expected IOException")
        } catch (_: java.io.IOException) {}

        val stagingDir = File(context.cacheDir, "sftp_staging")
        val stagingFiles = stagingDir.listFiles { _, name -> name.startsWith(".cossh_part_") }
        assertTrue("Staging file must be cleaned up on failure", stagingFiles?.isEmpty() ?: true)
    }

    @Test
    fun `uploadFromSafUri streams bytes from ContentResolver to remote SFTP destination`() = runTest {
        val sourceUri = Uri.parse("content://com.example.provider/files/config.json")
        val sourceContent = "{\"port\": 2222, \"env\": \"prod\"}".toByteArray(Charsets.UTF_8)

        val mockHandler = object : SafIoHandler {
            override fun openOutputStream(uri: Uri, mode: String): OutputStream? = null
            override fun openInputStream(uri: Uri): InputStream = ByteArrayInputStream(sourceContent)
        }

        safBridge.uploadFromSafUri(
            sourceUri = sourceUri,
            remotePath = "/var/backups/config.json",
            ioHandler = mockHandler,
        )

        val remoteStat = sftpManager.stat("/var/backups/config.json")
        assertEquals(sourceContent.size.toLong(), remoteStat?.size)
        assertEquals(String(sourceContent), sftpManager.readFileContent("/var/backups/config.json"))
    }

    @Test
    fun `ContentResolverSafIoHandler delegates to ContentResolver`() {
        val resolver = context.contentResolver
        val handler = ContentResolverSafIoHandler(resolver)
        val dummyUri = Uri.parse("content://dummy/test")
        try {
            handler.openInputStream(dummyUri)
        } catch (_: Exception) {}
        try {
            handler.openOutputStream(dummyUri, "w")
        } catch (_: Exception) {}
    }
}
