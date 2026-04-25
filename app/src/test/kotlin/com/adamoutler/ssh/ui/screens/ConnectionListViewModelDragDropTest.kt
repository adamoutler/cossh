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
        Thread.sleep(100)

        // Verify initial order
        assertEquals(3, viewModel.profiles.value.size)
        assertEquals("id1", viewModel.profiles.value[0].id)
        assertEquals("id2", viewModel.profiles.value[1].id)
        assertEquals("id3", viewModel.profiles.value[2].id)

        // Drag profile index 0 to index 1
        viewModel.moveProfile(0, 1)
        
        // Give coroutines time to launch and save
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        Thread.sleep(100)
        
        // Let's reload a brand new view model to prove storage persistency
        val app = ApplicationProvider.getApplicationContext<Application>()
        val newViewModel = ConnectionListViewModel(app, storageManager, BackupManager(app, storageManager, com.adamoutler.ssh.crypto.IdentityStorageManager(app)))
        
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        Thread.sleep(100)

        // Verify new order is successfully loaded into the UI layer from local storage
        assertEquals("id2", newViewModel.profiles.value[0].id)
        assertEquals("id1", newViewModel.profiles.value[1].id)
        assertEquals("id3", newViewModel.profiles.value[2].id)
        
        println("SUCCESS: Reorder correctly preserved across reloads")
    }
}
