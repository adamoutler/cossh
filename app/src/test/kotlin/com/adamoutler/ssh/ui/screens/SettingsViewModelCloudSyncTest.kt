package com.adamoutler.ssh.ui.screens

import android.app.Application
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkRequest
import com.adamoutler.ssh.backup.BackupCryptoManager
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.sync.DriveSyncManager
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelCloudSyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var settingsManager: SettingsManager
    private lateinit var securityStorageManager: SecurityStorageManager
    private lateinit var identityStorageManager: IdentityStorageManager
    private lateinit var fakeWorkManager: FakeWorkManager
    private lateinit var fakeDriveSyncManager: FakeDriveSyncManager
    private lateinit var billingManager: BillingManager
    private lateinit var viewModel: SettingsViewModel

    class FakeDriveSyncManager(context: Context) : DriveSyncManager(context) {
        var deleteBackupCalled = false
        var downloadBackupResponse: ByteArray? = null
        var downloadBackupThrows: Exception? = null

        override suspend fun deleteBackup(): Boolean {
            deleteBackupCalled = true
            return true
        }

        override suspend fun downloadBackup(pass: CharArray): ByteArray? {
            downloadBackupThrows?.let { throw it }
            return downloadBackupResponse
        }
    }

    class FakeWorkManager : WorkManager() {
        var cancelledUniqueWorkName: String? = null
        var enqueuedUniqueWorkName: String? = null

        private val dummyOperation = object : Operation {
            override fun getState(): LiveData<Operation.State> = MutableLiveData(Operation.SUCCESS)
            override fun getResult(): ListenableFuture<Operation.State.SUCCESS> = object : ListenableFuture<Operation.State.SUCCESS> {
                override fun addListener(listener: Runnable, executor: Executor) {
                    executor.execute(listener)
                }
                override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
                override fun isCancelled(): Boolean = false
                override fun isDone(): Boolean = true
                override fun get(): Operation.State.SUCCESS = Operation.SUCCESS
                override fun get(timeout: Long, unit: TimeUnit): Operation.State.SUCCESS = Operation.SUCCESS
            }
        }

        override fun cancelUniqueWork(uniqueWorkName: String): Operation {
            cancelledUniqueWorkName = uniqueWorkName
            return dummyOperation
        }

        override fun enqueueUniqueWork(
            uniqueWorkName: String,
            existingWorkPolicy: ExistingWorkPolicy,
            work: List<OneTimeWorkRequest>,
        ): Operation {
            enqueuedUniqueWorkName = uniqueWorkName
            return dummyOperation
        }

        override fun getConfiguration(): Configuration = Configuration.Builder().build()
        override fun enqueue(requests: List<WorkRequest>): Operation = dummyOperation
        override fun beginWith(work: List<OneTimeWorkRequest>): WorkContinuation = throw UnsupportedOperationException()
        override fun beginUniqueWork(
            uniqueWorkName: String,
            existingWorkPolicy: ExistingWorkPolicy,
            work: List<OneTimeWorkRequest>,
        ): WorkContinuation = throw UnsupportedOperationException()
        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            existingPeriodicWorkPolicy: ExistingPeriodicWorkPolicy,
            periodicWork: PeriodicWorkRequest,
        ): Operation = dummyOperation
        override fun cancelWorkById(id: UUID): Operation = dummyOperation
        override fun cancelAllWorkByTag(tag: String): Operation = dummyOperation
        override fun cancelAllWork(): Operation = dummyOperation
        override fun createCancelPendingIntent(id: UUID): android.app.PendingIntent = throw UnsupportedOperationException()
        override fun pruneWork(): Operation = dummyOperation
        override fun getLastCancelAllTimeMillisLiveData(): LiveData<Long> = MutableLiveData(0L)
        override fun getLastCancelAllTimeMillis(): ListenableFuture<Long> = throw UnsupportedOperationException()
        override fun getWorkInfoByIdLiveData(id: UUID): LiveData<WorkInfo> = throw UnsupportedOperationException()
        override fun getWorkInfoByIdFlow(id: UUID): kotlinx.coroutines.flow.Flow<WorkInfo> = throw UnsupportedOperationException()
        override fun getWorkInfoById(id: UUID): ListenableFuture<WorkInfo> = throw UnsupportedOperationException()
        override fun getWorkInfosByTagLiveData(tag: String): LiveData<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun getWorkInfosByTagFlow(tag: String): kotlinx.coroutines.flow.Flow<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun getWorkInfosByTag(tag: String): ListenableFuture<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun getWorkInfosForUniqueWorkLiveData(uniqueWorkName: String): LiveData<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun getWorkInfosForUniqueWorkFlow(uniqueWorkName: String): kotlinx.coroutines.flow.Flow<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun getWorkInfosForUniqueWork(uniqueWorkName: String): ListenableFuture<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun getWorkInfosLiveData(workQuery: WorkQuery): LiveData<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun getWorkInfosFlow(workQuery: WorkQuery): kotlinx.coroutines.flow.Flow<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun getWorkInfos(workQuery: WorkQuery): ListenableFuture<List<WorkInfo>> = throw UnsupportedOperationException()
        override fun updateWork(request: WorkRequest): ListenableFuture<WorkManager.UpdateResult> = throw UnsupportedOperationException()
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(application)
        securityStorageManager = SecurityStorageManager(application)
        identityStorageManager = IdentityStorageManager(application)
        fakeWorkManager = FakeWorkManager()
        fakeDriveSyncManager = FakeDriveSyncManager(application)
        billingManager = BillingManager(application)

        viewModel = SettingsViewModel(
            application = application,
            billingManager = billingManager,
            driveSyncManager = fakeDriveSyncManager,
            settingsManager = settingsManager,
            securityStorageManager = securityStorageManager,
            identityStorageManager = identityStorageManager,
            workManager = fakeWorkManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testEnableCloudSync_UpdatesStateAndSettingsManager() {
        settingsManager.isCloudSyncActive = false

        viewModel.enableCloudSync()

        assertTrue(viewModel.uiState.value.isCloudSyncActive)
        assertTrue(settingsManager.isCloudSyncActive)
    }

    @Test
    fun testDisableCloudSync_CancelsWorkAndClearsPassphrase() = runTest(testDispatcher) {
        securityStorageManager.saveSyncPassphrase("test_pass".toCharArray())
        settingsManager.isCloudSyncActive = true

        viewModel.disableCloudSync(deleteRemoteBackup = false)
        testScheduler.advanceUntilIdle()

        assertEquals(SettingsViewModel.WORK_NAME_CLOUD_SYNC, fakeWorkManager.cancelledUniqueWorkName)
        assertNull(securityStorageManager.getSyncPassphrase())
        assertFalse(settingsManager.isCloudSyncActive)
        assertFalse(viewModel.uiState.value.isCloudSyncActive)
        assertFalse(viewModel.uiState.value.isPassphraseSet)
        assertFalse(viewModel.uiState.value.showDisableSyncDialog)
        assertFalse(fakeDriveSyncManager.deleteBackupCalled)
    }

    @Test
    fun testDisableCloudSync_WithDeleteRemoteBackup_CallsDeleteBackup() = runTest(testDispatcher) {
        viewModel.disableCloudSync(deleteRemoteBackup = true)
        testScheduler.advanceUntilIdle()

        assertEquals(SettingsViewModel.WORK_NAME_CLOUD_SYNC, fakeWorkManager.cancelledUniqueWorkName)
        assertTrue(fakeDriveSyncManager.deleteBackupCalled)
    }

    @Test
    fun testRestoreFromCloud_DownloadsAndPersistsProfiles() = runTest(testDispatcher) {
        val testProfiles = listOf(
            ConnectionProfile(id = "p-cloud-1", nickname = "Cloud Server", host = "10.0.0.1", username = "root"),
        )
        val testIdentities = listOf(
            IdentityProfile(id = "i-cloud-1", name = "Cloud Key", username = "root"),
        )
        val password = "secret_passphrase_123".toCharArray()
        val baos = ByteArrayOutputStream()
        BackupCryptoManager.exportProfilesToZip(testProfiles, testIdentities, password, baos)
        fakeDriveSyncManager.downloadBackupResponse = baos.toByteArray()

        viewModel.restoreFromCloud("secret_passphrase_123".toCharArray())
        testScheduler.advanceUntilIdle()

        val restoredProfiles = securityStorageManager.getAllProfiles()
        val restoredIdentities = identityStorageManager.getAllIdentities()
        assertTrue(restoredProfiles.any { it.id == "p-cloud-1" })
        assertTrue(restoredIdentities.any { it.id == "i-cloud-1" })

        val savedPassphrase = securityStorageManager.getSyncPassphrase()
        assertNotNull(savedPassphrase)
        assertTrue(viewModel.uiState.value.isPassphraseSet)
        assertFalse(viewModel.uiState.value.isRestoring)
        assertFalse(viewModel.uiState.value.showRestoreDialog)
        assertEquals(
            "Successfully restored 1 profiles and 1 identities.",
            viewModel.uiState.value.restoreStatusMessage,
        )
    }

    @Test
    fun testRestoreFromCloud_ZeroWipesPassphraseBuffer() = runTest(testDispatcher) {
        val pass = "sensitive_passphrase".toCharArray()
        fakeDriveSyncManager.downloadBackupResponse = null

        viewModel.restoreFromCloud(pass)
        testScheduler.advanceUntilIdle()

        assertTrue("Passphrase buffer must be zero-wiped in finally block", pass.all { it == '\u0000' })
    }

    @Test
    fun testRestoreFromCloud_WhenNoRemoteBackupFound() = runTest(testDispatcher) {
        fakeDriveSyncManager.downloadBackupResponse = null

        viewModel.restoreFromCloud("pass".toCharArray())
        testScheduler.advanceUntilIdle()

        assertEquals("No remote backup found.", viewModel.uiState.value.restoreStatusMessage)
        assertFalse(viewModel.uiState.value.isRestoring)
    }

    @Test
    fun testRestoreFromCloud_WhenExceptionThrown_UpdatesStatusMessage() = runTest(testDispatcher) {
        fakeDriveSyncManager.downloadBackupThrows = RuntimeException("Google Drive network timeout")

        viewModel.restoreFromCloud("pass".toCharArray())
        testScheduler.advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.restoreStatusMessage?.startsWith("Restore failed:") == true,
        )
        assertFalse(viewModel.uiState.value.isRestoring)
    }

    @Test
    fun testShowAndDismissDialogs() {
        viewModel.showRestoreDialog()
        assertTrue(viewModel.uiState.value.showRestoreDialog)
        viewModel.dismissRestoreDialog()
        assertFalse(viewModel.uiState.value.showRestoreDialog)

        viewModel.showDisableSyncDialog()
        assertTrue(viewModel.uiState.value.showDisableSyncDialog)
        viewModel.dismissDisableSyncDialog()
        assertFalse(viewModel.uiState.value.showDisableSyncDialog)

        viewModel.clearRestoreStatusMessage()
        assertNull(viewModel.uiState.value.restoreStatusMessage)
    }
}
