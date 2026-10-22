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
import androidx.core.app.NotificationCompat
import com.adamoutler.ssh.MainActivity
import com.adamoutler.ssh.R
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.SecurityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
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

        fun isCircuitBreakerException(e: Throwable): Boolean {
            val isAuthFailure = e is net.schmizz.sshj.userauth.UserAuthException ||
                e.message?.contains("authentication failed", ignoreCase = true) == true ||
                e.message?.contains("Exhausted available authentication methods", ignoreCase = true) == true ||
                e.message?.contains("Auth fail", ignoreCase = true) == true

            val isHostKeyMismatch = e.message?.contains("HOST_KEY", ignoreCase = true) == true ||
                e.message?.contains("Host key", ignoreCase = true) == true

            return isAuthFailure || isHostKeyMismatch
        }

        fun calculateBackoffDelay(attempt: Int, baseDelayMs: Long = 2000L, maxDelayMs: Long = 60000L): Long {
            if (attempt <= 1) return baseDelayMs
            val shift = (attempt - 1).coerceAtMost(5)
            val factor = 1L shl shift
            return (baseDelayMs * factor).coerceAtMost(maxDelayMs)
        }

        fun mapExceptionMessage(e: Exception): String {
            val rawMessage = e.message ?: "Connection failed"
            return if (rawMessage.contains("Exhausted available authentication methods") || rawMessage.contains("All authentication methods failed")) {
                "Connection failed"
            } else {
                rawMessage
            }
        }
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
                if (intent.hasExtra(EXTRA_SESSION_ID) == true) {
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
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(SUMMARY_NOTIFICATION_ID, notification)
        }

        val job = Job(serviceJob)
        connectionJobs[sessionId] = job

        serviceScope.launch(job) {
            var profile: ConnectionProfile? = null
            try {
                val storageManager = SecurityStorageManager(applicationContext)
                val identityStorageManager = IdentityStorageManager(applicationContext)
                profile = storageManager.getProfile(profileId)
                if (profile != null) {
                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Connecting)
                    updateSessionNotification(profileId, sessionId, profile.nickname, "Connecting...")

                    var reconnectAttempt = 0
                    val maxAttempts = profile.maxReconnectAttempts.coerceIn(1, 10)
                    var shouldAttemptConnection = true

                    while (shouldAttemptConnection && job.isActive) {
                        var attemptProfile: ConnectionProfile? = null
                        try {
                            // Pause retries when ConnectivityManager detects no active internet network
                            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                            while (cm != null && cm.activeNetwork == null && job.isActive) {
                                kotlinx.coroutines.delay(1000L)
                            }

                            // Fetch fresh credentials from Keystore on-demand per attempt
                            val freshProfile = storageManager.getProfile(profileId) ?: profile
                            attemptProfile = freshProfile

                            val manager = SshConnectionManager(
                                identityStorageManager = identityStorageManager,
                                context = applicationContext,
                                securityStorageManager = storageManager,
                            )
                            sshManagers[sessionId] = manager

                            val isAutonomous = reconnectAttempt > 0
                            manager.connectPty(
                                profile = freshProfile,
                                isAutonomousReconnect = isAutonomous,
                                onConnect = { outStream, session ->
                                    val activeSession = ConnectionStateRepository.getOrCreateSession(profileId, sessionId)
                                    activeSession.ptyOutputStream = outStream
                                    activeSession.sshShell = session
                                    if (reconnectAttempt == 0) {
                                        ConnectionStateRepository.addConnection(profileId)
                                    } else {
                                        val restoredMsg = "\r\n\u001B[32m[CoSSH: Connection restored]\u001B[0m\r\n"
                                        serviceScope.launch {
                                            ConnectionStateRepository.emitOutput(sessionId, restoredMsg.toByteArray(Charsets.UTF_8))
                                        }
                                    }
                                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Connected)
                                    updateSessionNotification(profileId, sessionId, freshProfile.nickname, "Connected")
                                    reconnectAttempt = 0
                                },
                                onOutput = { bytes, length ->
                                    val activeSession = ConnectionStateRepository.getOrCreateSession(profileId, sessionId)

                                    if (ConnectionStateRepository.isHeadlessTest) {
                                        val newText = String(bytes, 0, length, Charsets.UTF_8)
                                        val current = ConnectionStateRepository.mockTestTranscripts[sessionId] ?: ""
                                        ConnectionStateRepository.mockTestTranscripts[sessionId] = current + newText
                                    }

                                    val copyBytes = bytes.copyOf(length)
                                    ConnectionStateRepository.emitOutput(sessionId, copyBytes)

                                    if (!activeSession.firstSshOutputReceived) {
                                        activeSession.firstSshOutputReceived = true
                                    }
                                },
                            )
                            shouldAttemptConnection = false
                            ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Disconnected)
                        } catch (e: Exception) {
                            val currentState = ConnectionStateRepository.connectionStates.value[profileId]
                            val isManualDisconnect = !job.isActive || currentState is ConnectionState.Disconnecting || e is kotlinx.coroutines.CancellationException
                            val isCircuitBreaker = isCircuitBreakerException(e)
                            val isHostKeyMismatch = e.message?.contains("HOST_KEY", ignoreCase = true) == true ||
                                e.message?.contains("Host key", ignoreCase = true) == true

                            if (isManualDisconnect || isCircuitBreaker || !profile.autoReconnect || reconnectAttempt >= maxAttempts) {
                                if (isHostKeyMismatch) {
                                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Terminated("HOST_KEY_MISMATCH_SECURITY_ALERT"))
                                    updateSessionNotification(profileId, sessionId, "Security Alert", "Host key mismatch rejected")
                                } else if (!isManualDisconnect) {
                                    val userMessage = mapExceptionMessage(e)
                                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Error(userMessage))
                                    updateSessionNotification(profileId, sessionId, "Connection", "Connection failed")
                                } else {
                                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Disconnected)
                                }
                                break
                            }

                            reconnectAttempt++
                            ConnectionStateRepository.updateConnectionState(
                                profileId,
                                ConnectionState.Reconnecting(reconnectAttempt, maxAttempts, "Reconnecting ($reconnectAttempt/$maxAttempts)..."),
                            )
                            updateSessionNotification(profileId, sessionId, profile.nickname, "Reconnecting ($reconnectAttempt/$maxAttempts)...")

                            val warnMsg = "\r\n\u001B[33m[CoSSH: Connection lost. Reconnecting ($reconnectAttempt/$maxAttempts)...]\u001B[0m\r\n"
                            ConnectionStateRepository.emitOutput(sessionId, warnMsg.toByteArray(Charsets.UTF_8))

                            val backoffMs = calculateBackoffDelay(reconnectAttempt)
                            kotlinx.coroutines.delay(backoffMs)
                        } finally {
                            attemptProfile?.clearSensitiveData()
                        }
                    }
                } else {
                    println(("SshService").toString() + ": " + ("Profile not found: $profileId").toString())
                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Error("Profile not found"))
                }
            } catch (e: Exception) {
                val isNormalClosure = e is kotlinx.coroutines.CancellationException ||
                    e.message?.contains("Disconnected", ignoreCase = true) == true ||
                    e.message?.contains("Socket closed", ignoreCase = true) == true ||
                    e.message?.contains("EOF", ignoreCase = true) == true

                if (!isNormalClosure) {
                    println(("SshService").toString() + ": " + ("SSH Connection failed for $profileId (Session: $sessionId)").toString() + " " + (e).toString())
                    updateSessionNotification(profileId, sessionId, "Connection", "Connection failed")
                    val userMessage = mapExceptionMessage(e)
                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Error(userMessage))
                } else {
                    println(("SshService").toString() + ": " + ("SSH Session disconnected normally for $profileId (Session: $sessionId)").toString())
                    ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Disconnected)
                }
            } finally {
                profile?.clearSensitiveData()
                ConnectionStateRepository.removeConnection(profileId)
                val activeCount = ConnectionStateRepository.activeConnectionCounts.value[profileId] ?: 0
                val currentState = ConnectionStateRepository.connectionStates.value[profileId]
                if (currentState !is ConnectionState.Error && currentState !is ConnectionState.Terminated && currentState !is ConnectionState.Disconnected && activeCount == 0) {
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
        val profileId = ConnectionStateRepository.sessions[sessionId]?.profileId
        if (profileId != null) {
            ConnectionStateRepository.updateConnectionState(profileId, ConnectionState.Disconnecting)
        }
        sshManagers[sessionId]?.disconnect()
        connectionJobs[sessionId]?.cancel()
    }

    private fun stopAllConnections() {
        ConnectionStateRepository.sessions.values.forEach { session ->
            ConnectionStateRepository.updateConnectionState(session.profileId, ConnectionState.Disconnecting)
        }
        serviceScope.cancel()
    }

    internal fun createSummaryNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("CoSSH")
        .setContentText("${connectionJobs.size.coerceAtLeast(1)} Active Sessions")
        .setSmallIcon(R.drawable.ic_stat_ssh)
        .setGroup(GROUP_KEY_SSH)
        .setGroupSummary(true)
        .setOngoing(true)
        .build()

    private fun updateSummaryNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(SUMMARY_NOTIFICATION_ID, createSummaryNotification())
    }

    internal fun buildSessionNotification(profileId: String, sessionId: String, profileName: String, contentText: String): Notification {
        val activeSession = ConnectionStateRepository.sessions[sessionId]
        val connectedAt = activeSession?.connectedAt ?: System.currentTimeMillis()

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                notificationIntent.action = "com.adamoutler.ssh.RESUME_$sessionId"
                notificationIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                notificationIntent.putExtra(EXTRA_PROFILE_ID, profileId)
                notificationIntent.putExtra(EXTRA_SESSION_ID, sessionId)
                PendingIntent.getActivity(
                    this,
                    sessionId.hashCode(),
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }

        val disconnectIntent = Intent(this, SshService::class.java).apply {
            action = ACTION_DISCONNECT
            putExtra(EXTRA_PROFILE_ID, profileId)
            putExtra(EXTRA_SESSION_ID, sessionId)
        }
        val disconnectPendingIntent: PendingIntent =
            PendingIntent.getService(
                this,
                sessionId.hashCode(),
                disconnectIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(profileName)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_ssh)
            .setContentIntent(pendingIntent)
            .setUsesChronometer(true)
            .setWhen(connectedAt)
            .setGroup(GROUP_KEY_SSH)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateSessionNotification(profileId: String, sessionId: String, profileName: String, contentText: String) {
        val notification = buildSessionNotification(profileId, sessionId, profileName, contentText)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(getNotificationId(sessionId), notification)
        updateSummaryNotification()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "SSH Service Channel",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAllConnections()
    }
}
