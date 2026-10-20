package com.adamoutler.ssh.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.adamoutler.ssh.backup.BackupCryptoManager
import com.adamoutler.ssh.billing.BillingManager
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.crypto.SettingsManager
import com.adamoutler.ssh.util.AppLog
import java.io.ByteArrayOutputStream

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val settingsManager = SettingsManager(applicationContext)
        if (!settingsManager.isCloudSyncActive) {
            return Result.success()
        }

        val billingManager = BillingManager(applicationContext)
        if (!billingManager.awaitCloudSyncEntitlement()) {
            return Result.success()
        }

        val driveSyncManager = DriveSyncManager(applicationContext)
        val securityStorageManager = SecurityStorageManager(applicationContext)
        val identityStorageManager = IdentityStorageManager(applicationContext)

        val pass = securityStorageManager.getSyncPassphrase() ?: return Result.failure()

        return try {
            // Fetch fully hydrated profiles and identities for export (SSH-138)
            val profiles = securityStorageManager.getAllProfiles().mapNotNull { securityStorageManager.getProfile(it.id) }
            val identities = identityStorageManager.getAllIdentities().mapNotNull { identityStorageManager.getIdentity(it.id) }

            val outputStream = ByteArrayOutputStream()
            BackupCryptoManager.exportProfilesToZip(profiles, identities, pass, outputStream)
            val payload = outputStream.toByteArray()

            driveSyncManager.uploadBackup(payload, pass)

            // Scrub password array after use per security guidelines
            pass.fill('\u0000')

            Result.success()
        } catch (e: Exception) {
            AppLog.e("SyncWorker", "Cloud sync execution failed: ${e.message}", e)
            if (isAuthenticationOrPermanentError(e)) {
                Result.failure()
            } else if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    private fun isAuthenticationOrPermanentError(e: Throwable): Boolean {
        val msg = e.message ?: ""
        if (e is IllegalStateException && (msg.contains("Not authenticated", ignoreCase = true) || msg.contains("unauthenticated", ignoreCase = true))) {
            return true
        }
        if (msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized", ignoreCase = true)) {
            return true
        }
        return false
    }
}
