package com.adamoutler.ssh.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.backup.BackupCryptoManager
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val billingManager = BillingManager(applicationContext)
        val isCloudSyncEnabled = billingManager.isCloudSyncEnabled.first()

        if (!isCloudSyncEnabled) {
            return Result.success()
        }

        val driveSyncManager = DriveSyncManager(applicationContext)
        val securityStorageManager = SecurityStorageManager(applicationContext)
        val identityStorageManager = IdentityStorageManager(applicationContext)

        return try {
            val pass = securityStorageManager.getSyncPassphrase() ?: return Result.failure()

            val profiles = securityStorageManager.getAllProfiles()
            val identities = identityStorageManager.getAllIdentities()
            
            val outputStream = ByteArrayOutputStream()
            BackupCryptoManager.exportProfilesToZip(profiles, identities, pass, outputStream)
            val payload = outputStream.toByteArray()

            driveSyncManager.uploadBackup(payload, pass)
            
            // Scrub password array after use per security guidelines
            pass.fill('\u0000')
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
