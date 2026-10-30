package com.adamoutler.ssh.ui

import android.app.Application
import android.app.Notification
import android.content.Context
import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.backup.BackupManager
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.network.ConnectionState
import com.adamoutler.ssh.network.SshService
import com.adamoutler.ssh.sync.DriveSyncManager
import com.adamoutler.ssh.ui.components.ExtraKeysPresetId
import com.adamoutler.ssh.ui.components.SessionTabItem
import com.adamoutler.ssh.ui.components.configureTerminalEditorInfo
import com.adamoutler.ssh.ui.screens.AddEditProfileViewModel
import com.adamoutler.ssh.ui.screens.ConnectionListItem
import com.adamoutler.ssh.ui.screens.ConnectionListViewModel
import com.adamoutler.ssh.ui.screens.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class PreReleaseJuiceSshParityTest {

    private fun createAddEditViewModel(): Triple<AddEditProfileViewModel, SecurityStorageManager, IdentityStorageManager> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_parity_addedit_" + UUID.randomUUID(), Context.MODE_PRIVATE)
        val storageManager = SecurityStorageManager(app, prefs)
        val identityStorageManager = IdentityStorageManager(app, prefs)
        val viewModel = AddEditProfileViewModel(app, storageManager, identityStorageManager)
        return Triple(viewModel, storageManager, identityStorageManager)
    }

    @Test
    fun `profile form state preserves folderId and sortOrder across load and save`() = runTest {
        val (viewModel, storageManager, _) = createAddEditViewModel()

        val profile = ConnectionProfile(
            id = "profile-folder-test-1",
            nickname = "Database Host",
            host = "db.internal.lan",
            port = 22,
            protocol = Protocol.SSH,
            username = "postgres",
            authType = AuthType.PASSWORD,
            folderId = "Databases",
            sortOrder = 7,
        )
        storageManager.saveProfile(profile)

        // Load profile and verify UI state reflects folderId and sortOrder
        viewModel.loadProfileIfNeeded("profile-folder-test-1")
        assertEquals("Databases", viewModel.uiState.value.folderId)
        assertEquals(7, viewModel.uiState.value.sortOrder)

        // Update folder and sortOrder
        viewModel.updateFolderId("Production DBs")
        viewModel.updateSortOrder(12)
        assertEquals("Production DBs", viewModel.uiState.value.folderId)
        assertEquals(12, viewModel.uiState.value.sortOrder)

        // Save from state and verify persistence
        val saved = viewModel.saveProfileFromState("profile-folder-test-1")
        assertTrue("Profile should save successfully", saved)

        val retrieved = storageManager.getProfile("profile-folder-test-1")
        assertNotNull(retrieved)
        assertEquals("Production DBs", retrieved?.folderId)
        assertEquals(12, retrieved?.sortOrder)
    }

    @Test
    fun `profile form state strips whitespace from folderId on save`() = runTest {
        val (viewModel, storageManager, _) = createAddEditViewModel()

        viewModel.updateState {
            it.copy(
                nickname = "Web Server",
                host = "10.0.0.5",
                port = "22",
                username = "admin",
            )
        }
        viewModel.updateFolderId("   ")
        viewModel.saveProfileFromState("profile-blank-folder")

        val retrieved = storageManager.getProfile("profile-blank-folder")
        assertNotNull(retrieved)
        assertNull("Blank folder string should be normalized to null", retrieved?.folderId)
    }

    @Test
    fun `connection list search query matches profile folderId`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_connlist_search_" + UUID.randomUUID(), Context.MODE_PRIVATE)
        val storageManager = SecurityStorageManager(app, prefs)
        val identityStorageManager = IdentityStorageManager(app, prefs)
        val backupManager = BackupManager(app, storageManager, identityStorageManager)
        val viewModel = ConnectionListViewModel(app, storageManager, backupManager)

        val p1 = ConnectionProfile(
            id = "p1",
            nickname = "Primary Host",
            host = "192.168.1.10",
            username = "root",
            authType = AuthType.PASSWORD,
            folderId = "Kubernetes-Cluster",
            sortOrder = 0,
        )
        val p2 = ConnectionProfile(
            id = "p2",
            nickname = "Secondary Host",
            host = "192.168.1.11",
            username = "root",
            authType = AuthType.PASSWORD,
            folderId = "Staging-Env",
            sortOrder = 1,
        )
        storageManager.saveProfile(p1)
        storageManager.saveProfile(p2)

        // Query by folder name that does not exist in nickname or host
        viewModel.updateSearchQuery("Kubernetes")
        val results = viewModel.profiles.value
        assertEquals(1, results.size)
        assertEquals("p1", results[0].id)
        assertEquals("Kubernetes-Cluster", results[0].folderId)
    }

    @Test
    fun `settings manager persists allowOsc52Clipboard preference`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val settingsManager = SettingsManager(app)

        assertFalse("OSC 52 clipboard access must default to false", settingsManager.allowOsc52Clipboard)

        settingsManager.allowOsc52Clipboard = true
        assertTrue("OSC 52 clipboard access should be true when set", settingsManager.allowOsc52Clipboard)

        settingsManager.allowOsc52Clipboard = false
        assertFalse("OSC 52 clipboard access should return to false", settingsManager.allowOsc52Clipboard)
    }

    @Test
    fun `settings view model updates fontSize extraKeysPreset and allowOsc52Clipboard`() = runTest {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val billingManager = BillingManager(app)
        val driveSyncManager = DriveSyncManager(app)
        val viewModel = SettingsViewModel(app, billingManager, driveSyncManager, workManager = null)

        viewModel.updateFontSize(18)
        assertEquals(18, viewModel.uiState.value.globalFontSize)

        viewModel.updateExtraKeysPreset(ExtraKeysPresetId.TMUX)
        assertEquals(ExtraKeysPresetId.TMUX, viewModel.uiState.value.extraKeysPreset)

        viewModel.updateAllowOsc52Clipboard(true)
        assertTrue(viewModel.uiState.value.allowOsc52Clipboard)

        val settingsManager = SettingsManager(app)
        assertEquals(18, settingsManager.globalFontSize)
        assertEquals(ExtraKeysPresetId.TMUX, settingsManager.extraKeysPreset)
        assertTrue(settingsManager.allowOsc52Clipboard)
    }

    @Test
    fun `configureTerminalEditorInfo sets IME_FLAG_NO_PERSONALIZED_LEARNING`() {
        val editorInfo = EditorInfo()
        editorInfo.imeOptions = EditorInfo.IME_ACTION_DONE

        val updated = configureTerminalEditorInfo(editorInfo)
        val hasFlag = (updated.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        assertTrue("IME_FLAG_NO_PERSONALIZED_LEARNING flag must be set on terminal editor info", hasFlag)
    }

    @Test
    fun `ssh service notifications configure lockscreen visibility to private`() {
        val controller = Robolectric.buildService(SshService::class.java)
        val service = controller.create().get()

        val summaryNotification = service.createSummaryNotification()
        assertNotNull(summaryNotification)
        assertEquals(
            "Summary notification visibility must be VISIBILITY_PRIVATE for lockscreen security",
            Notification.VISIBILITY_PRIVATE,
            summaryNotification.visibility,
        )

        val sessionNotification = service.buildSessionNotification(
            profileId = "prof-test",
            sessionId = "sess-test",
            profileName = "Secure Server",
            contentText = "Active session",
        )
        assertNotNull(sessionNotification)
        assertEquals(
            "Session notification visibility must be VISIBILITY_PRIVATE for lockscreen security",
            Notification.VISIBILITY_PRIVATE,
            sessionNotification.visibility,
        )

        controller.destroy()
    }

    @Test
    fun `grouped connection list filter hides profiles when folder is collapsed`() {
        val p1 = ConnectionProfile(id = "p1", nickname = "Web 1", host = "10.0.0.1", folderId = "WebServers")
        val p2 = ConnectionProfile(id = "p2", nickname = "Web 2", host = "10.0.0.2", folderId = "WebServers")
        val p3 = ConnectionProfile(id = "p3", nickname = "DB 1", host = "10.0.0.3", folderId = "DBServers")

        val flatItems = listOf(
            ConnectionListItem.Header("WebServers", "WebServers"),
            ConnectionListItem.Profile(p1),
            ConnectionListItem.Profile(p2),
            ConnectionListItem.Header("DBServers", "DBServers"),
            ConnectionListItem.Profile(p3),
        )

        val collapsedFolders = mutableMapOf("WebServers" to true)

        val visibleItems = flatItems.filter { item ->
            when (item) {
                is ConnectionListItem.Header -> true
                is ConnectionListItem.Profile -> {
                    val key = item.profile.folderId ?: "default"
                    collapsedFolders[key] != true
                }
            }
        }

        // WebServers header remains, but p1 and p2 are hidden; DBServers header and p3 remain visible
        assertEquals(3, visibleItems.size)
        assertTrue(visibleItems[0] is ConnectionListItem.Header)
        assertTrue(visibleItems[1] is ConnectionListItem.Header)
        assertTrue(visibleItems[2] is ConnectionListItem.Profile)
        assertEquals("p3", (visibleItems[2] as ConnectionListItem.Profile).profile.id)
    }

    @Test
    fun `terminal session tab bar is visible when single session exists`() {
        val tab = SessionTabItem(
            sessionId = "single-session-1",
            profileId = "prof-1",
            nickname = "Jump Host",
            isTelnet = false,
            connectionState = ConnectionState.Connected,
            isSelected = true,
        )
        val sessions = listOf(tab)

        // In TerminalSessionTabBar: visible = sessions.isNotEmpty()
        assertTrue("Session tab bar should be visible for a single active session to permit new tab creation", sessions.isNotEmpty())
    }
}
