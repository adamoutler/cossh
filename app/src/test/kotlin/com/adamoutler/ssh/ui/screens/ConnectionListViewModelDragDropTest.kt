package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.backup.BackupManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class ConnectionListViewModelDragDropTest {

    private lateinit var storageManager: SecurityStorageManager
    private lateinit var viewModel: ConnectionListViewModel

    @Before
    fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        storageManager = SecurityStorageManager(app, app.getSharedPreferences("test_prefs_drag_drop", 0))

        val p1 = ConnectionProfile("id1", "Nick1", "host1", username = "u1", authType = AuthType.PASSWORD, sortOrder = 0)
        val p2 = ConnectionProfile("id2", "Nick2", "host2", username = "u2", authType = AuthType.PASSWORD, sortOrder = 1)
        val p3 = ConnectionProfile("id3", "Nick3", "host3", username = "u3", authType = AuthType.PASSWORD, sortOrder = 2)

        storageManager.saveProfile(p1)
        storageManager.saveProfile(p2)
        storageManager.saveProfile(p3)

        viewModel = ConnectionListViewModel(app, storageManager, BackupManager(app, storageManager, com.adamoutler.ssh.crypto.IdentityStorageManager(app)))
    }

    @Test
    fun testDragAndDropReordersItems() {
        // Wait for items to load initially
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Verify initial order (3 Profiles, 0 Headers because showHeaders is false for single null group)
        assertEquals(3, viewModel.flatItems.value.size)
        val initialP1 = viewModel.flatItems.value[0] as ConnectionListItem.Profile
        val initialP2 = viewModel.flatItems.value[1] as ConnectionListItem.Profile
        val initialP3 = viewModel.flatItems.value[2] as ConnectionListItem.Profile

        assertEquals("id1", initialP1.profile.id)
        assertEquals("id2", initialP2.profile.id)
        assertEquals("id3", initialP3.profile.id)

        // Drag profile from flat list index 0 to index 1
        viewModel.moveProfileInFlatList(0, 1)

        // Give coroutines time to launch and save
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        var retries = 0
        while (retries < 50) {
            val p1 = storageManager.getProfile("id1")
            val p2 = storageManager.getProfile("id2")
            if (p1 != null && p2 != null && p1.sortOrder > p2.sortOrder) break
            Thread.sleep(10)
            retries++
        }

        // Let's reload a brand new view model to prove storage persistency
        val app = ApplicationProvider.getApplicationContext<Application>()
        val newViewModel = ConnectionListViewModel(app, storageManager, BackupManager(app, storageManager, com.adamoutler.ssh.crypto.IdentityStorageManager(app)))

        var reloadRetries = 0
        while (newViewModel.flatItems.value.size < 3 && reloadRetries < 50) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(10)
            reloadRetries++
        }

        // Verify new order is successfully loaded into the UI layer from local storage
        val newP1 = newViewModel.flatItems.value[0] as ConnectionListItem.Profile
        val newP2 = newViewModel.flatItems.value[1] as ConnectionListItem.Profile
        val newP3 = newViewModel.flatItems.value[2] as ConnectionListItem.Profile

        // The move was from 0 to 1, so it should be id2, id1, id3
        assertEquals("id2", newP1.profile.id)
        assertEquals("id1", newP2.profile.id)
        assertEquals("id3", newP3.profile.id)

        println("SUCCESS: Reorder correctly preserved across reloads")
    }
}
