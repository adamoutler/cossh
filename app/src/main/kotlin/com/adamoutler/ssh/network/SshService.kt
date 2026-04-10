package com.adamoutler.ssh.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.adamoutler.ssh.MainActivity
import com.adamoutler.ssh.crypto.SecurityStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SshService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var sshManager: SshConnectionManager? = null

    companion object {
        private const val CHANNEL_ID = "SshServiceChannel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.adamoutler.ssh.START_SSH"
        const val ACTION_DISCONNECT = "com.adamoutler.ssh.DISCONNECT_SSH"
        const val EXTRA_PROFILE_ID = "profile_id"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId != null) {
                    startSshConnection(profileId)
                } else {
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                stopSshConnection()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSshConnection(profileId: String) {
        val notification = createNotification("Connecting...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        serviceScope.launch {
            try {
                val storageManager = SecurityStorageManager(applicationContext)
                val profile = storageManager.getProfile(profileId)
                if (profile != null) {
                    SshSessionProvider.addConnection(profileId)
                    updateNotification("Connected to ${profile.nickname}")
                    sshManager = SshConnectionManager()
                    
                    sshManager?.connectPty(
                        profile = profile,
                        onConnect = { outStream ->
                            SshSessionProvider.ptyOutputStream = outStream
                        },
                        onOutput = { bytes, length ->
                            val session = SshSessionProvider.getOrCreateSession()
                            session?.emulator?.append(bytes, length)
                            SshSessionProvider.onScreenUpdated?.invoke()
                        }
                    )
                } else {
                    Log.e("SshService", "Profile not found")
                }
            } catch (e: Exception) {
                Log.e("SshService", "SSH Connection failed", e)
                updateNotification("Connection failed")
            } finally {
                SshSessionProvider.removeConnection(profileId)
                SshSessionProvider.clearSession()
                stopSelf()
            }
        }
    }

    private fun stopSshConnection() {
        serviceScope.cancel()
        // SshConnectionManager disconnect is handled by its finally block when the coroutine is cancelled
    }

    private fun createNotification(contentText: String): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        val disconnectIntent = Intent(this, SshService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent: PendingIntent =
            PendingIntent.getService(
                this, 1, disconnectIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoSSH Session")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SSH Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // We don't provide binding for now
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSshConnection()
    }
}
