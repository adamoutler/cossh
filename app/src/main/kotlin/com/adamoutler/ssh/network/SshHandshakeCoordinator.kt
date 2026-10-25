package com.adamoutler.ssh.network

import android.content.Context
import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.crypto.PemUtils
import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import java.io.File
import java.io.IOException
import java.security.KeyPair
import java.security.PublicKey

/**
 * Isolates the SSH handshake and connection logic from the main SshConnectionManager.
 * Handles Host Key Verification (TOFU), Identity resolution, and specific Authentication strategies.
 */
open class SshHandshakeCoordinator(
    private val hostKeyVerifier: HostKeyVerifier? = null,
    private val identityStorageManager: IdentityStorageManager? = null,
    private val context: Context? = null,
) {

    open fun configureHostKeyVerifier(client: SSHClient, isAutonomousReconnect: Boolean = false) {
        if (hostKeyVerifier != null) {
            client.addHostKeyVerifier(hostKeyVerifier)
        } else {
            val knownHostsFile = context?.let { File(it.filesDir, "ssh_known_hosts") }
            if (knownHostsFile != null) {
                if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
                client.addHostKeyVerifier(TofuHostKeyVerifier(knownHostsFile, isAutonomousReconnect, context))
            } else {
                throw IOException("No Context provided for TOFU verifier and no HostKeyVerifier configured. Refusing to connect insecurely.")
            }
        }
    }

    private class CompositeAuthenticator(
        private val authenticators: List<SshAuthenticator>,
        private val identity: IdentityProfile? = null,
        private val sessionId: String? = null,
        private val continuationEngine: MultiFactorContinuationEngine = MultiFactorContinuationEngine(),
    ) : SshAuthenticator {
        override fun authenticate(client: SSHClient, profile: ConnectionProfile) {
            var lastException: Exception? = null
            for (authenticator in authenticators) {
                try {
                    // Check if we are interrupted to prevent infinite hangs if this loop somehow gets stuck
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedException("Authentication interrupted")
                    }
                    authenticator.authenticate(client, profile)
                    if (client.isAuthenticated) {
                        sessionId?.let {
                            ConnectionLogRepository.log(
                                sessionId = it,
                                milestone = ConnectionMilestone.AUTH,
                                message = "Authentication successful via ${authenticator::class.java.simpleName}",
                            )
                        }
                        return
                    }
                } catch (e: Exception) {
                    lastException = e
                    println(("CompositeAuthenticator").toString() + ": " + ("Authentication failed with ${authenticator::class.java.simpleName}, checking MFA continuation").toString() + " " + (e).toString())

                    // Check if server returned SSH_AUTH_PARTIAL (RFC 4252 §5.1)
                    val userAuth = client.userAuth
                    if (userAuth != null && userAuth.hadPartialSuccess()) {
                        try {
                            val continued = continuationEngine.continueIfPartialSuccess(
                                client = client,
                                profile = profile,
                                identity = identity,
                                sessionId = sessionId,
                            )
                            if (continued && client.isAuthenticated) {
                                return
                            }
                        } catch (mfaEx: Exception) {
                            lastException = mfaEx
                        }
                    }
                }
            }
            throw UserAuthException("All authentication methods failed", lastException)
        }
    }

    @PublishedApi
    internal fun getAuthenticator(
        profile: ConnectionProfile,
        keyPair: KeyPair?,
        identity: IdentityProfile? = null,
        sessionId: String? = null,
    ): SshAuthenticator {
        if (identity != null) {
            val authenticators = mutableListOf<SshAuthenticator>()

            // Try key auth if a private key exists
            if (identity.privateKey != null || keyPair != null) {
                try {
                    val resolvedKeyPair = if (identity.privateKey != null) {
                        loadKeyPairFromIdentity(identity)
                    } else {
                        keyPair
                    }
                    if (resolvedKeyPair?.public != null) {
                        authenticators.add(KeyAuthenticator(resolvedKeyPair))
                    } else {
                        println(("SshHandshakeCoordinator").toString() + ": " + ("Public key is null, skipping KeyAuthenticator").toString())
                    }
                } catch (e: Exception) {
                    println(("SshHandshakeCoordinator").toString() + ": " + ("Failed to initialize keypair").toString() + " " + (e).toString())
                }
            }

            // Try password or keyboard-interactive auth if a password exists or if authType is PASSWORD
            if (identity.password != null || profile.authType == AuthType.PASSWORD) {
                authenticators.add(PasswordAuthenticator(identity.password))
                authenticators.add(KeyboardInteractiveAuthenticator())
            }

            if (authenticators.isNotEmpty()) {
                return CompositeAuthenticator(authenticators, identity, sessionId)
            }

            throw UserAuthException("Selected Identity '${identity.name}' has neither a valid password nor an SSH private key")
        }

        return when (profile.authType) {
            AuthType.PASSWORD -> CompositeAuthenticator(
                authenticators = listOf(PasswordAuthenticator(), KeyboardInteractiveAuthenticator()),
                identity = null,
                sessionId = sessionId,
            )

            AuthType.KEY -> {
                if (keyPair?.public != null) {
                    if (sessionId != null) {
                        CompositeAuthenticator(
                            authenticators = listOf(KeyAuthenticator(keyPair)),
                            identity = null,
                            sessionId = sessionId,
                        )
                    } else {
                        KeyAuthenticator(keyPair)
                    }
                } else {
                    throw UserAuthException(
                        "SSH Key authentication requires an Identity Profile. Please select or configure an Identity in the connection profile settings.",
                    )
                }
            }
        }
    }

    @PublishedApi
    internal fun loadKeyPairFromIdentity(identity: IdentityProfile): KeyPair {
        val privateKeyBytes = identity.privateKey ?: throw IllegalArgumentException("Identity has no private key")

        var publicKey: PublicKey? = null
        try {
            val pubKeyStr = identity.publicKey
            if (!pubKeyStr.isNullOrEmpty()) {
                val parts = pubKeyStr.split(" ")
                if (parts.size >= 2) {
                    val type = parts[0]
                    val base64 = parts[1]
                    val decoded = java.util.Base64.getDecoder().decode(base64)
                    val buffer = net.schmizz.sshj.common.Buffer.PlainBuffer(decoded)
                    buffer.readString() // Read algorithm name
                    publicKey = net.schmizz.sshj.common.KeyType.fromString(type).readPubKeyFromBuffer(buffer)
                }
            }
        } catch (e: Exception) {
            println(("SshHandshakeCoordinator").toString() + ": " + ("Failed to parse public key from identity").toString() + " " + (e).toString())
        }

        return PemUtils.parsePemToKeyPair(privateKeyBytes, publicKey)
    }

    @PublishedApi
    internal fun resolveIdentity(profile: ConnectionProfile): IdentityProfile? {
        val id = profile.identityId ?: return null
        return identityStorageManager?.getIdentity(id)
    }

    @PublishedApi
    internal open fun resolveHostLocally(host: String): Array<java.net.InetAddress>? {
        if (context == null) return null
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        return try {
            network.getAllByName(host)
        } catch (e: Exception) {
            null
        }
    }

    @PublishedApi
    internal open fun connectClient(client: SSHClient, inetAddress: java.net.InetAddress, port: Int) {
        client.connect(inetAddress, port)
    }

    @PublishedApi
    internal open fun connectClient(client: SSHClient, host: String, port: Int) {
        client.connect(host, port)
    }

    @PublishedApi
    internal open fun authenticateClient(
        client: SSHClient,
        profile: ConnectionProfile,
        keyPair: KeyPair?,
        identity: IdentityProfile?,
        effectiveProfile: ConnectionProfile,
    ) {
        authenticateClient(client, profile, keyPair, identity, effectiveProfile, null)
    }

    @PublishedApi
    internal open fun authenticateClient(
        client: SSHClient,
        profile: ConnectionProfile,
        keyPair: KeyPair?,
        identity: IdentityProfile?,
        effectiveProfile: ConnectionProfile,
        sessionId: String?,
    ) {
        getAuthenticator(profile, keyPair, identity, sessionId).authenticate(client, effectiveProfile)
    }

    /**
     * Orchestrates the connection and authentication phase of an SSHClient.
     * It handles timeouts, Host Key Verification (TOFU), Identity resolution, and authentication.
     * Returns the resolved Effective Profile (containing injected credentials if an identity was used).
     */
    suspend fun <T> executeWithConnection(
        client: SSHClient,
        profile: ConnectionProfile,
        keyPair: KeyPair? = null,
        skipSocketConnect: Boolean = false,
        isAutonomousReconnect: Boolean = false,
        sessionId: String? = null,
        block: suspend (effectiveProfile: ConnectionProfile) -> T,
    ): T {
        client.connectTimeout = 10000
        client.timeout = 10000

        val identity = resolveIdentity(profile)

        try {
            configureHostKeyVerifier(client, isAutonomousReconnect)

            if (!skipSocketConnect) {
                // Pre-Connect Step 1: Wake-on-LAN
                profile.wolConfig?.let { wol ->
                    if (WolManager.isValidMacAddress(wol.macAddress)) {
                        com.adamoutler.ssh.util.AppLog.i("SshHandshakeCoordinator", "[CoSSH: Sending Wake-on-LAN packet to ${wol.macAddress}...]")
                        sessionId?.let {
                            ConnectionLogRepository.log(
                                sessionId = it,
                                milestone = ConnectionMilestone.TCP,
                                message = "Sending Wake-on-LAN packet to ${wol.macAddress}...",
                            )
                        }
                        try {
                            WolManager.sendMagicPacket(wol)
                        } catch (e: Exception) {
                            com.adamoutler.ssh.util.AppLog.w("SshHandshakeCoordinator", "WOL broadcast failed (proceeding): ${e.message}")
                        }
                    }
                }

                // Pre-Connect Step 2: Port Knocking
                profile.portKnockingConfig?.let { knock ->
                    if (knock.steps.isNotEmpty()) {
                        com.adamoutler.ssh.util.AppLog.i("SshHandshakeCoordinator", "[CoSSH: Executing port knocking sequence (${knock.steps.size} steps)...]")
                        sessionId?.let {
                            ConnectionLogRepository.log(
                                sessionId = it,
                                milestone = ConnectionMilestone.TCP,
                                message = "Executing port knocking sequence (${knock.steps.size} steps)...",
                            )
                        }
                        try {
                            PortKnockingManager.executePortKnockSequence(profile.host, knock)
                        } catch (e: Exception) {
                            com.adamoutler.ssh.util.AppLog.w("SshHandshakeCoordinator", "Port knocking sequence failed (proceeding): ${e.message}")
                        }
                    }
                }

                sessionId?.let {
                    ConnectionLogRepository.log(
                        sessionId = it,
                        milestone = ConnectionMilestone.DNS,
                        message = "Resolving host '${profile.host}'...",
                    )
                }

                if (profile.useLocalDns) {
                    val inetAddresses = resolveHostLocally(profile.host)
                    if (!inetAddresses.isNullOrEmpty()) {
                        val inetAddress = java.net.InetAddress.getByAddress(profile.host, inetAddresses[0].address)
                        com.adamoutler.ssh.util.AppLog.d("SshHandshakeCoordinator", "Resolved ${profile.host} via local network DNS: ${inetAddress.hostAddress}")
                        sessionId?.let {
                            ConnectionLogRepository.log(
                                sessionId = it,
                                milestone = ConnectionMilestone.DNS,
                                message = "Resolved ${profile.host} to ${inetAddress.hostAddress} via local network DNS",
                            )
                            ConnectionLogRepository.log(
                                sessionId = it,
                                milestone = ConnectionMilestone.TCP,
                                message = "Connecting TCP socket to ${inetAddress.hostAddress}:${profile.port}...",
                            )
                        }
                        connectClient(client, inetAddress, profile.port)
                    } else {
                        com.adamoutler.ssh.util.AppLog.d("SshHandshakeCoordinator", "Local DNS resolution returned empty for ${profile.host}, using system fallback")
                        sessionId?.let {
                            ConnectionLogRepository.log(
                                sessionId = it,
                                milestone = ConnectionMilestone.DNS,
                                message = "Local DNS returned empty, falling back to system resolver",
                            )
                            ConnectionLogRepository.log(
                                sessionId = it,
                                milestone = ConnectionMilestone.TCP,
                                message = "Connecting TCP socket to ${profile.host}:${profile.port}...",
                            )
                        }
                        connectClient(client, profile.host, profile.port)
                    }
                } else {
                    sessionId?.let {
                        ConnectionLogRepository.log(
                            sessionId = it,
                            milestone = ConnectionMilestone.TCP,
                            message = "Connecting TCP socket to ${profile.host}:${profile.port}...",
                        )
                    }
                    connectClient(client, profile.host, profile.port)
                }

                sessionId?.let {
                    ConnectionLogRepository.log(
                        sessionId = it,
                        milestone = ConnectionMilestone.TCP,
                        message = "TCP connection established to ${profile.host}:${profile.port}",
                    )
                    try {
                        val banner = client.transport.serverVersion
                        if (!banner.isNullOrEmpty()) {
                            ConnectionLogRepository.log(
                                sessionId = it,
                                milestone = ConnectionMilestone.SSH,
                                message = "Server banner: $banner",
                            )
                        }
                    } catch (_: Exception) {}
                }
            }

            val effectiveProfile = if (identity != null) {
                profile.copy(username = identity.username, password = identity.password)
            } else {
                profile
            }

            try {
                sessionId?.let {
                    val authTypeDesc = if (identity != null) "Identity '${identity.name}'" else effectiveProfile.authType.name
                    ConnectionLogRepository.log(
                        sessionId = it,
                        milestone = ConnectionMilestone.AUTH,
                        message = "Authenticating user '${effectiveProfile.username}' ($authTypeDesc)...",
                    )
                }
                if (sessionId != null) {
                    authenticateClient(client, profile, keyPair, identity, effectiveProfile, sessionId)
                } else {
                    authenticateClient(client, profile, keyPair, identity, effectiveProfile)
                }
                try {
                    val auditManager = context?.let { com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstance(it) }
                        ?: com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstanceOrNull()
                    auditManager?.logSessionStart(
                        profileId = effectiveProfile.id,
                        host = effectiveProfile.host,
                        port = effectiveProfile.port,
                        username = effectiveProfile.username,
                    )
                    auditManager?.logAuthResult(
                        profileId = effectiveProfile.id,
                        host = effectiveProfile.host,
                        username = effectiveProfile.username,
                        authType = effectiveProfile.authType.name,
                        success = true,
                    )
                } catch (_: Exception) {}
            } catch (e: Exception) {
                sessionId?.let {
                    ConnectionLogRepository.log(
                        sessionId = it,
                        milestone = ConnectionMilestone.ERROR,
                        message = "Connection / authentication error: ${e.message}",
                        level = ConnectionLogLevel.ERROR,
                    )
                }
                try {
                    val auditManager = context?.let { com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstance(it) }
                        ?: com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstanceOrNull()
                    auditManager?.logAuthResult(
                        profileId = effectiveProfile.id,
                        host = effectiveProfile.host,
                        username = effectiveProfile.username,
                        authType = effectiveProfile.authType.name,
                        success = false,
                        failureReason = e.message,
                    )
                } catch (_: Exception) {}
                throw e
            }

            // Configure KeepAlive if requested, applying baseband cellular anti-thrashing clamping
            val keepAliveSecs = profile.effectiveKeepAliveInterval
            if (keepAliveSecs > 0) {
                client.connection.keepAlive.keepAliveInterval = keepAliveSecs
                // Reset socket read timeout to 0 (infinite) so quiescent reads rely on transport keepalive
                client.timeout = 0
            }

            return block(effectiveProfile)
        } finally {
            identity?.clearSensitiveData()
            profile.clearSensitiveData()
        }
    }
}
