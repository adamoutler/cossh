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
                    SshSessionProvider.updateConnectionState(profileId, ConnectionState.Connecting)
                    updateNotification("Connecting to ${profile.nickname}")
                    sshManager = SshConnectionManager()
                    
                    sshManager?.connectPty(
                        profile = profile,
                        onConnect = { outStream, session ->
                            SshSessionProvider.ptyOutputStream = outStream
                            SshSessionProvider.activeSshSession = session
                            SshSessionProvider.updateConnectionState(profileId, ConnectionState.Connected)
                            updateNotification("Connected to ${profile.nickname}")
                        },
                        onOutput = { bytes, length ->
                            if (SshSessionProvider.isHeadlessTest) {
                                val newText = String(bytes, 0, length, Charsets.UTF_8)
                                val current = SshSessionProvider.mockTestTranscript ?: ""
                                SshSessionProvider.mockTestTranscript = current + newText
                            } else {
                                val session = SshSessionProvider.getOrCreateSession()
                                val emulator = session?.emulator
                                if (emulator != null) {
                                    if (!SshSessionProvider.firstSshOutputReceived) {
                                        SshSessionProvider.firstSshOutputReceived = true
                                        emulator.screen.clearTranscript()
                                        val clearSeq = "\u001B[2J\u001B[H".toByteArray()
                                        emulator.append(clearSeq, clearSeq.size)
                                        Log.d("SshService", "Cleared screen on first SSH output")
                                    }
                                    emulator.append(bytes, length)
                                }
                            }
                            SshSessionProvider.postScreenUpdate()
                        }
                    )
                } else {
                    Log.e("SshService", "Profile not found")
                    SshSessionProvider.updateConnectionState(profileId, ConnectionState.Error("Profile not found"))
                }
            } catch (e: Exception) {
                Log.e("SshService", "SSH Connection failed", e)
                updateNotification("Connection failed")
                SshSessionProvider.updateConnectionState(profileId, ConnectionState.Error(e.message ?: "Connection failed"))
            } finally {
                // Remove from active connections, but don't clear state if it's an Error, so UI can display it.
                SshSessionProvider.removeConnection(profileId)
                val currentState = SshSessionProvider.connectionStates.value[profileId]
                if (currentState !is ConnectionState.Error) {
                    SshSessionProvider.clearConnectionState(profileId)
                }
                SshSessionProvider.activeSshSession = null
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
