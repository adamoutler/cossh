package com.adamoutler.ssh.ui.screens

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.KnownHostRecord
import com.adamoutler.ssh.crypto.KnownHostsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class KnownHostsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var knownHostsFile: File
    private lateinit var manager: KnownHostsManager
    private lateinit var viewModel: KnownHostsViewModel

    private val ed25519Key = "AAAAC3NzaC1lZDI1NTE5AAAAIGo4wN/jWvjZ4mN+0pB4B9wT7Hq9q2r1x8kL8p8kL8p8"
    private val rsaKey = "AAAAB3NzaC1yc2EAAAADAQABAAABAQC3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3r3"
    private val ecdsaKey = "AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBN5y9x..."

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        knownHostsFile = tempFolder.newFile("ssh_known_hosts")
        val lines = listOf(
            "192.168.1.100 ssh-ed25519 $ed25519Key",
            "bastion.cloud.org ssh-rsa $rsaKey",
            "[internal.node]:2222 ecdsa-sha2-nistp256 $ecdsaKey",
        )
        knownHostsFile.writeText(lines.joinToString("\n") + "\n")
        manager = KnownHostsManager(knownHostsFile)
        viewModel = KnownHostsViewModel(app, manager)
    }

    @Test
    fun testInitialState_loadsEntriesAndCalculatesCounts() {
        val state = viewModel.uiState.value
        assertEquals(3, state.entries.size)
        assertEquals(3, state.plaintextCount)
        assertEquals(1, state.countEd25519)
        assertEquals(1, state.countRsa)
        assertEquals(1, state.countEcdsa)
        assertEquals(3, state.filteredEntries.size)
        assertFalse(state.isMultiSelectMode)
    }

    @Test
    fun testSearchQueryFilter() {
        viewModel.onSearchQueryChange("bastion")
        val state = viewModel.uiState.value
        assertEquals("bastion", state.searchQuery)
        assertEquals(1, state.filteredEntries.size)
        assertEquals("bastion.cloud.org", state.filteredEntries[0].rawHostPattern)

        viewModel.onSearchQueryChange("2222")
        assertEquals(1, viewModel.uiState.value.filteredEntries.size)

        viewModel.onSearchQueryChange("")
        assertEquals(3, viewModel.uiState.value.filteredEntries.size)
    }

    @Test
    fun testAlgorithmFilter() {
        viewModel.onAlgorithmFilterSelect(KeyAlgorithmFilter.ED25519)
        var state = viewModel.uiState.value
        assertEquals(1, state.filteredEntries.size)
        assertEquals("ssh-ed25519", state.filteredEntries[0].keyType)

        viewModel.onAlgorithmFilterSelect(KeyAlgorithmFilter.RSA)
        state = viewModel.uiState.value
        assertEquals(1, state.filteredEntries.size)
        assertEquals("ssh-rsa", state.filteredEntries[0].keyType)

        viewModel.onAlgorithmFilterSelect(KeyAlgorithmFilter.ECDSA)
        state = viewModel.uiState.value
        assertEquals(1, state.filteredEntries.size)
        assertEquals("ecdsa-sha2-nistp256", state.filteredEntries[0].keyType)

        viewModel.onAlgorithmFilterSelect(KeyAlgorithmFilter.ALL)
        assertEquals(3, viewModel.uiState.value.filteredEntries.size)
    }

    @Test
    fun testMultiSelectAndBulkDelete() {
        viewModel.setMultiSelectMode(true)
        assertTrue(viewModel.uiState.value.isMultiSelectMode)

        val id1 = viewModel.uiState.value.entries[0].id
        val id2 = viewModel.uiState.value.entries[1].id

        viewModel.toggleEntrySelection(id1)
        assertEquals(setOf(id1), viewModel.uiState.value.selectedEntryIds)

        viewModel.toggleEntrySelection(id2)
        assertEquals(setOf(id1, id2), viewModel.uiState.value.selectedEntryIds)

        // Untoggle id1
        viewModel.toggleEntrySelection(id1)
        assertEquals(setOf(id2), viewModel.uiState.value.selectedEntryIds)

        // Select all
        viewModel.toggleSelectAll()
        assertEquals(3, viewModel.uiState.value.selectedEntryIds.size)

        // Bulk delete dialog
        viewModel.setShowBulkDeleteDialog(true)
        assertTrue(viewModel.uiState.value.showBulkDeleteDialog)

        viewModel.executeBulkDelete()
        assertFalse(viewModel.uiState.value.showBulkDeleteDialog)
        assertFalse(viewModel.uiState.value.isMultiSelectMode)
        assertTrue(viewModel.uiState.value.selectedEntryIds.isEmpty())
        assertEquals(0, viewModel.uiState.value.entries.size)
        assertNotNull(viewModel.uiState.value.statusMessage)
    }

    @Test
    fun testSingleDelete() {
        val entry = viewModel.uiState.value.entries[0]
        viewModel.confirmSingleDelete(entry)
        assertEquals(entry, viewModel.uiState.value.entryPendingDelete)

        viewModel.executeSingleDelete()
        assertNull(viewModel.uiState.value.entryPendingDelete)
        assertEquals(2, viewModel.uiState.value.entries.size)
        assertEquals("Host key removed.", viewModel.uiState.value.statusMessage)

        viewModel.clearStatusMessage()
        assertNull(viewModel.uiState.value.statusMessage)
    }

    @Test
    fun testExecuteHashAll() {
        viewModel.setShowHashAllDialog(true)
        assertTrue(viewModel.uiState.value.showHashAllDialog)

        viewModel.executeHashAll()
        assertFalse(viewModel.uiState.value.showHashAllDialog)
        assertEquals(0, viewModel.uiState.value.plaintextCount)
        assertEquals(3, viewModel.uiState.value.entries.size)
        assertTrue(viewModel.uiState.value.entries.all { it.isHashed })
        assertTrue(viewModel.uiState.value.statusMessage!!.contains("Hashed 3"))
    }

    @Test
    fun testCopyFingerprintToClipboard() {
        val fingerprint = "SHA256:testFingerprintSample"
        viewModel.copyFingerprintToClipboard(fingerprint)

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(fingerprint, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        assertTrue(viewModel.uiState.value.statusMessage!!.contains("Fingerprint copied"))
    }
}
