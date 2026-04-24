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
import com.adamoutler.ssh.crypto.IdentityStorageManager
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
        const val EXTRA_SESSION_ID = "session_id"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: java.util.UUID.randomUUID().toString()
        
        when (action) {
            ACTION_START -> {
                if (profileId != null) {
                    startSshConnection(profileId, sessionId)
                } else {
                    stopSelf()
                }
            }
            ACTION_DISCONNECT -> {
                if (intent?.hasExtra(EXTRA_SESSION_ID) == true) {
                    stopSshConnection(sessionId)
                } else if (profileId != null) {
                    // Disconnect all sessions for this profile? The prompt said stopSshConnection to take sessionId.
                    // If no sessionId provided but we want to disconnect, maybe stop all?
                    // Let's just iterate and stop sessions matching profileId if we really need to, 
                    // but the new intent will have EXTRA_SESSION_ID.
                    val sessionsToStop = ConnectionStateRepository.sessions.values.filter { it.profileId == profileId }.map { it.sessionId }
                    sessionsToStop.forEach { stopSshConnection(it) }
                } else {
                    stopAllConnections()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun getNotificationId(sessionId: String): Int = sessionId.hashCode()

    private fun startSshConnection(profileId: String, sessionId: String) {
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
        connectionJobs[sessionId] = job

        serviceScope.launch(job) {
            try {
                val storageManager = SecurityStorageManager(applicationContext)
                val identityStorageManager = IdentityStorageManager(applicationContext)
                val profile = storageManager.getProfile(profileId)
                if (profile != null) {
                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Connecting)
                    updateSessionNotification(profileId, sessionId, profile.nickname, "Connecting...")
                    val manager = SshConnectionManager(identityStorageManager = identityStorageManager, context = applicationContext)
                    sshManagers[sessionId] = manager
                    
                    manager.connectPty(
                        profile = profile,
                        onConnect = { outStream, session ->
                            val activeSession = ConnectionStateRepository.getOrCreateSession(profileId, sessionId)
                            activeSession.ptyOutputStream = outStream
                            activeSession.sshShell = session
                            ConnectionStateRepository.addConnection(profileId)
                            ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Connected)
                            updateSessionNotification(profileId, sessionId, profile.nickname, "Connected")
                        },
                        onOutput = { bytes, length ->
                            val activeSession = ConnectionStateRepository.getOrCreateSession(profileId, sessionId)
                            if (ConnectionStateRepository.isHeadlessTest) {
                                val newText = String(bytes, 0, length, Charsets.UTF_8)
                                val current = ConnectionStateRepository.mockTestTranscripts[sessionId] ?: ""
                                ConnectionStateRepository.mockTestTranscripts[sessionId] = current + newText
                            }
                            
                            // Send bytes to UI via shared flow
                            val copyBytes = bytes.copyOf(length)
                            ConnectionStateRepository.emitOutput(sessionId, copyBytes)
                            
                            if (!activeSession.firstSshOutputReceived) {
                                activeSession.firstSshOutputReceived = true
                                Log.d("SshService", "First SSH output received for $sessionId")
                            }
                        }
                    )
                } else {
                    Log.e("SshService", "Profile not found: $profileId")
                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Error("Profile not found"))
                }
            } catch (e: Exception) {
                Log.e("SshService", "SSH Connection failed for $profileId (Session: $sessionId)", e)
                updateSessionNotification(profileId, sessionId, "Connection", "Connection failed")
                ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Error(e.message ?: "Connection failed"))
            } finally {
                ConnectionStateRepository.removeConnection(profileId)
                val activeCount = ConnectionStateRepository.activeConnectionCounts.value[profileId] ?: 0
                val currentState = ConnectionStateRepository.connectionStates.value[profileId]
                if (currentState !is ConnectionState.Error && activeCount == 0) {
                    ConnectionStateRepository.clearConnectionState(profileId)
                }
                
                ConnectionStateRepository.clearSession(sessionId)
                
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.cancel(getNotificationId(sessionId))
                
                connectionJobs.remove(sessionId)
                sshManagers.remove(sessionId)
                
                if (connectionJobs.isEmpty()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    updateSummaryNotification()
                }
            }
        }
    }

    private fun stopSshConnection(sessionId: String) {
        sshManagers[sessionId]?.disconnect()
        connectionJobs[sessionId]?.cancel()
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

    private fun updateSessionNotification(profileId: String, sessionId: String, profileName: String, contentText: String) {
        val activeSession = ConnectionStateRepository.sessions[sessionId]
        val connectedAt = activeSession?.connectedAt ?: System.currentTimeMillis()

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                notificationIntent.action = "com.adamoutler.ssh.RESUME_$sessionId"
                notificationIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                notificationIntent.putExtra(EXTRA_PROFILE_ID, profileId)
                notificationIntent.putExtra(EXTRA_SESSION_ID, sessionId)
                PendingIntent.getActivity(
                    this, sessionId.hashCode(), notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

        val disconnectIntent = Intent(this, SshService::class.java).apply {
            action = ACTION_DISCONNECT
            putExtra(EXTRA_PROFILE_ID, profileId)
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val disconnectPendingIntent: PendingIntent =
            PendingIntent.getService(
                this, sessionId.hashCode(), disconnectIntent,
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
        notificationManager.notify(getNotificationId(sessionId), notification)
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