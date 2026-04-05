package com.adamoutler.ssh.ui.screens

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(instrumentedPackages = ["androidx.loader.content"])
class ConnectionListViewModelDragDropTest {

    private lateinit var storageManager: SecurityStorageManager

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
    }

    @Test
    fun testDragAndDropReordersItems() {
        // Verify initial order
        val allProfilesInitial = storageManager.getAllProfiles().sortedBy { it.sortOrder }
        assertEquals("id1", allProfilesInitial[0].id)
        assertEquals("id2", allProfilesInitial[1].id)
        assertEquals("id3", allProfilesInitial[2].id)

        // Simulate drag profile index 0 to index 1
        val currentList = allProfilesInitial.toMutableList()
        val item = currentList.removeAt(0)
        currentList.add(1, item)

        // Persist the new order as the ViewModel does
        currentList.forEachIndexed { index, profile ->
            if (profile.sortOrder != index) {
                profile.sortOrder = index
                storageManager.saveProfile(profile)
            }
        }
        
        // Verify new order in Storage is successfully persisted
        val allProfilesUpdated = storageManager.getAllProfiles().sortedBy { it.sortOrder }
        println("Updated profiles order: ${allProfilesUpdated.map { it.id }}")
        assertEquals("id2", allProfilesUpdated[0].id)
        assertEquals("id1", allProfilesUpdated[1].id)
        assertEquals("id3", allProfilesUpdated[2].id)
        
        println("SUCCESS: Reorder persisted to local storage")
    }
}
