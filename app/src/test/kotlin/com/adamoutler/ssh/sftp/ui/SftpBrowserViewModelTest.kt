package com.adamoutler.ssh.sftp.ui

import com.adamoutler.ssh.sftp.FakeSftpClientAdapter
import com.adamoutler.ssh.sftp.SftpClientManager
import com.adamoutler.ssh.sftp.model.SftpFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SftpBrowserViewModelTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var fakeAdapter: FakeSftpClientAdapter
    private lateinit var sftpManager: SftpClientManager
    private lateinit var viewModel: SftpBrowserViewModel

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        fakeAdapter = FakeSftpClientAdapter()
        sftpManager = SftpClientManager(fakeAdapter, testDispatcher)

        fakeAdapter.addVirtualDirectory("/var")
        fakeAdapter.addVirtualDirectory("/var/log")
        fakeAdapter.addVirtualFile("/var/log/syslog", "log line 1\nlog line 2".toByteArray(Charsets.UTF_8))
        fakeAdapter.addVirtualFile("/var/log/auth.log", "auth success".toByteArray(Charsets.UTF_8))

        viewModel = SftpBrowserViewModel(sftpManager)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buildBreadcrumbs generates correct path hierarchy`() {
        val rootCrumbs = SftpBrowserViewModel.buildBreadcrumbs("/")
        assertEquals(1, rootCrumbs.size)
        assertEquals("root", rootCrumbs[0].name)
        assertEquals("/", rootCrumbs[0].fullPath)

        val deepCrumbs = SftpBrowserViewModel.buildBreadcrumbs("/var/log/nginx")
        assertEquals(4, deepCrumbs.size)
        assertEquals("root", deepCrumbs[0].name)
        assertEquals("/", deepCrumbs[0].fullPath)
        assertEquals("var", deepCrumbs[1].name)
        assertEquals("/var", deepCrumbs[1].fullPath)
        assertEquals("log", deepCrumbs[2].name)
        assertEquals("/var/log", deepCrumbs[2].fullPath)
        assertEquals("nginx", deepCrumbs[3].name)
        assertEquals("/var/log/nginx", deepCrumbs[3].fullPath)
    }

    @Test
    fun `loadDirectory navigates and loads child files`() = runTest(testDispatcher) {
        viewModel.loadDirectory("/var/log")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/var/log", state.currentPath)
        assertEquals(2, state.items.size)
        assertTrue(state.items.any { it.name == "syslog" })
        assertTrue(state.items.any { it.name == "auth.log" })
    }

    @Test
    fun `updateSearchQuery filters items dynamically`() = runTest(testDispatcher) {
        viewModel.loadDirectory("/var/log")
        advanceUntilIdle()

        viewModel.updateSearchQuery("auth")
        val state = viewModel.uiState.value
        assertEquals(1, state.filteredItems.size)
        assertEquals("auth.log", state.filteredItems[0].name)

        viewModel.updateSearchQuery("")
        assertEquals(2, viewModel.uiState.value.filteredItems.size)
    }

    @Test
    fun `applySortAndFilter orders files and honors foldersFirst`() {
        val folder = SftpFileItem("zebra_folder", "/zebra_folder", 4096, isDirectory = true, isRegularFile = false, isSymlink = false, permissions = "drwxr-xr-x", octalPermissions = 0x1ED, modifiedTimeEpochMs = 100)
        val fileA = SftpFileItem("apple.txt", "/apple.txt", 1000, isDirectory = false, isRegularFile = true, isSymlink = false, permissions = "-rw-r--r--", octalPermissions = 0x1A4, modifiedTimeEpochMs = 200)
        val fileB = SftpFileItem("banana.txt", "/banana.txt", 5000, isDirectory = false, isRegularFile = true, isSymlink = false, permissions = "-rw-r--r--", octalPermissions = 0x1A4, modifiedTimeEpochMs = 300)

        val items = listOf(fileB, folder, fileA)

        // Folders first: zebra_folder must come before files even though 'a' comes before 'z'
        val sortedName = SftpBrowserViewModel.applySortAndFilter(items, "", SftpSortMode.NAME_ASC, foldersFirst = true)
        assertEquals("zebra_folder", sortedName[0].name)
        assertEquals("apple.txt", sortedName[1].name)
        assertEquals("banana.txt", sortedName[2].name)

        // Size desc without folders first
        val sortedSize = SftpBrowserViewModel.applySortAndFilter(items, "", SftpSortMode.SIZE_DESC, foldersFirst = false)
        assertEquals("banana.txt", sortedSize[0].name)
        assertEquals("zebra_folder", sortedSize[1].name)
        assertEquals("apple.txt", sortedSize[2].name)
    }

    @Test
    fun `multi-selection toggles, selects all, and clears correctly`() = runTest(testDispatcher) {
        viewModel.loadDirectory("/var/log")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isMultiSelectMode)

        viewModel.toggleItemSelection("/var/log/syslog")
        assertTrue(viewModel.uiState.value.isMultiSelectMode)
        assertEquals(setOf("/var/log/syslog"), viewModel.uiState.value.selectedPaths)

        viewModel.selectAll()
        assertEquals(2, viewModel.uiState.value.selectedPaths.size)

        viewModel.clearSelection()
        assertFalse(viewModel.uiState.value.isMultiSelectMode)
        assertTrue(viewModel.uiState.value.selectedPaths.isEmpty())
    }

    @Test
    fun `openPreview respects 2MB memory guard`() = runTest(testDispatcher) {
        val smallFile = SftpFileItem("syslog", "/var/log/syslog", 1024, isDirectory = false, isRegularFile = true, isSymlink = false, permissions = "-rw-r--r--", octalPermissions = 0x1A4, modifiedTimeEpochMs = 100)
        viewModel.openPreview(smallFile)
        advanceUntilIdle()

        assertEquals("log line 1\nlog line 2", viewModel.uiState.value.previewContent)
        assertFalse(viewModel.uiState.value.isPreviewLoading)

        val hugeFile = SftpFileItem("big.iso", "/var/log/big.iso", 10 * 1024 * 1024L, isDirectory = false, isRegularFile = true, isSymlink = false, permissions = "-rw-r--r--", octalPermissions = 0x1A4, modifiedTimeEpochMs = 100)
        viewModel.openPreview(hugeFile)
        advanceUntilIdle()

        // Content must be null because it was blocked by the guard before loading
        assertNull(viewModel.uiState.value.previewContent)
        assertFalse(viewModel.uiState.value.isPreviewLoading)
    }

    @Test
    fun `applyPermissions updates octal permissions on target item`() = runTest(testDispatcher) {
        val target = SftpFileItem("syslog", "/var/log/syslog", 1024, isDirectory = false, isRegularFile = true, isSymlink = false, permissions = "-rw-r--r--", octalPermissions = 0x1A4, modifiedTimeEpochMs = 100)
        viewModel.openChmodDialog(target)
        assertNotNull(viewModel.uiState.value.activeChmodItem)

        viewModel.applyPermissions(0x1FF) // 0777
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeChmodItem)
        val updatedStat = sftpManager.stat("/var/log/syslog")
        assertEquals(0x1FF, updatedStat?.octalPermissions)
    }
}
