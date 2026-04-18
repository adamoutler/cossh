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
import java.util.concurrent.ConcurrentHashMap

class SshService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    private val sshManagers = ConcurrentHashMap<String, SshConnectionManager>()
    private val connectionJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val CHANNEL_ID = "SshServiceChannel"
        const val GROUP_KEY_SSH = "com.adamoutler.ssh.ACTIVE_SESSIONS"
        const val SUMMARY_NOTIFICATION_ID = 1000
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
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        
        when (action) {
            ACTION_START -> {
                if (profileId != null) {
                    startSshConnection(profileId)
                } else {
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                if (profileId != null) {
                    stopSshConnection(profileId)
                } else {
                    stopAllConnections()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun getNotificationId(profileId: String): Int = profileId.hashCode()

    private fun startSshConnection(profileId: String) {
        val notification = createSummaryNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SUMMARY_NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SUMMARY_NOTIFICATION_ID, notification)
        }

        val job = Job(serviceJob)
        connectionJobs[profileId] = job

        serviceScope.launch(job) {
            try {
                val storageManager = SecurityStorageManager(applicationContext)
                val profile = storageManager.getProfile(profileId)
                if (profile != null) {
                    SshSessionProvider.updateConnectionState(profileId, ConnectionState.Connecting)
                    updateSessionNotification(profileId, profile.nickname, "Connecting...")
                    val manager = SshConnectionManager()
                    sshManagers[profileId] = manager
                    
                    manager.connectPty(
                        profile = profile,
                        onConnect = { outStream, session ->
                            val activeSession = SshSessionProvider.getOrCreateSession(profileId)
                            activeSession.ptyOutputStream = outStream
                            activeSession.sshShell = session
                            SshSessionProvider.updateConnectionState(profileId, ConnectionState.Connected)
                            updateSessionNotification(profileId, profile.nickname, "Connected")
                        },
                        onOutput = { bytes, length ->
                            val activeSession = SshSessionProvider.getOrCreateSession(profileId)
                            if (SshSessionProvider.isHeadlessTest) {
                                val newText = String(bytes, 0, length, Charsets.UTF_8)
                                val current = activeSession.mockTestTranscript ?: ""
                                activeSession.mockTestTranscript = current + newText
                            } else {
                                val emulator = activeSession.terminalSession?.emulator
                                if (emulator != null) {
                                    if (!activeSession.firstSshOutputReceived) {
                                        activeSession.firstSshOutputReceived = true
                                        emulator.screen.clearTranscript()
                                        val clearSeq = "\u001B[2J\u001B[H".toByteArray()
                                        emulator.append(clearSeq, clearSeq.size)
                                        Log.d("SshService", "Cleared screen on first SSH output for $profileId")
                                    }
                                    emulator.append(bytes, length)
                                }
                            }
                            SshSessionProvider.postScreenUpdate(profileId)
                        }
                    )
                } else {
                    Log.e("SshService", "Profile not found: $profileId")
                    SshSessionProvider.updateConnectionState(profileId, ConnectionState.Error("Profile not found"))
                }
            } catch (e: Exception) {
                Log.e("SshService", "SSH Connection failed for $profileId", e)
                updateSessionNotification(profileId, "Connection", "Connection failed")
                SshSessionProvider.updateConnectionState(profileId, ConnectionState.Error(e.message ?: "Connection failed"))
            } finally {
                SshSessionProvider.removeConnection(profileId)
                val currentState = SshSessionProvider.connectionStates.value[profileId]
                if (currentState !is ConnectionState.Error) {
                    SshSessionProvider.clearConnectionState(profileId)
                }
                
                SshSessionProvider.clearSession(profileId)
                
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.cancel(getNotificationId(profileId))
                
                connectionJobs.remove(profileId)
                sshManagers.remove(profileId)
                
                if (connectionJobs.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateSummaryNotification()
                }
            }
        }
    }

    private fun stopSshConnection(profileId: String) {
        connectionJobs[profileId]?.cancel()
        // The finally block in startSshConnection will clean up state and stop the service if needed.
    }

    private fun stopAllConnections() {
        serviceScope.cancel()
    }

    private fun createSummaryNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoSSH")
            .setContentText("${connectionJobs.size.coerceAtLeast(1)} Active Sessions")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setGroup(GROUP_KEY_SSH)
            .setGroupSummary(true)
            .setOngoing(true)
            .build()
    }

    private fun updateSummaryNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, createSummaryNotification())
    }

    private fun updateSessionNotification(profileId: String, profileName: String, contentText: String) {
        val activeSession = SshSessionProvider.sessions[profileId]
        val connectedAt = activeSession?.connectedAt ?: System.currentTimeMillis()

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                // To allow navigation back to the specific terminal, pass profileId
                notificationIntent.putExtra(EXTRA_PROFILE_ID, profileId)
                PendingIntent.getActivity(
                    this, profileId.hashCode(), notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val disconnectIntent = Intent(this, SshService::class.java).apply {
            action = ACTION_DISCONNECT
            putExtra(EXTRA_PROFILE_ID, profileId)
        }
        val disconnectPendingIntent: PendingIntent =
            PendingIntent.getService(
                this, profileId.hashCode(), disconnectIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(profileName)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setUsesChronometer(true)
            .setWhen(connectedAt)
            .setGroup(GROUP_KEY_SSH)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(getNotificationId(profileId), notification)
        updateSummaryNotification()
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
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllConnections()
    }
}
