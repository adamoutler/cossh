package com.adamoutler.ssh.network

import com.adamoutler.ssh.crypto.IdentityStorageManager
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyPair
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

class TofuHostKeyVerifier(
    private val knownHostsFile: File,
    private val isAutonomousReconnect: Boolean = false,
    private val context: android.content.Context? = null,
) : OpenSSHKnownHosts(knownHostsFile) {
    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        // Zero-TOFU Host Certificate Verification via Trusted Host-CA
        val hostCert = try {
            when (key) {
                is com.hierynomus.sshj.userauth.certificate.Certificate<*> -> {
                    if (key.encoded != null) {
                        com.adamoutler.ssh.crypto.certificate.OpenSshCertificateParser.parseWireBytes(key.encoded)
                    } else {
                        null
                    }
                }

                else -> {
                    if (key.encoded != null && (key.algorithm.contains("cert", ignoreCase = true) || key.format == "OpenSSH")) {
                        try {
                            com.adamoutler.ssh.crypto.certificate.OpenSshCertificateParser.parseWireBytes(key.encoded)
                        } catch (_: Throwable) {
                            null
                        }
                    } else {
                        null
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }

        if (hostCert != null && hostCert.certType == com.adamoutler.ssh.crypto.certificate.OpenSshCertType.HOST) {
            val knownHostsMgr = com.adamoutler.ssh.crypto.KnownHostsManager(knownHostsFile)
            val trustedCas = knownHostsMgr.findTrustedCaForHost(hostname, port)
            if (trustedCas.isNotEmpty()) {
                val result = com.adamoutler.ssh.crypto.certificate.OpenSshCertificateValidator.validate(
                    cert = hostCert,
                    requestedPrincipal = hostname,
                    trustedCaKeys = trustedCas,
                )
                if (result is com.adamoutler.ssh.crypto.certificate.CertValidationResult.Valid) {
                    println("TofuHostKeyVerifier: Valid Host Certificate for $hostname verified via trusted CA. Zero-TOFU auto-accepted.")
                    return true
                }
            }
        }

        if (super.verify(hostname, port, key)) {
            return true
        }

        if (isAutonomousReconnect) {
            // Zero-Prompt Security Policy: Never prompt in background on host key mismatch or unknown host
            return false
        }

        val formattedHost = if (port != 22) "[$hostname]:$port" else hostname

        if (EphemeralHostKeyRegistry.verify(formattedHost, key)) {
            return true
        }

        var oldFingerprint: String? = null
        var oldRandomart: String? = null
        var hostExists = false

        if (knownHostsFile.exists()) {
            try {
                val currentEntries = OpenSSHKnownHosts(knownHostsFile).entries()
                for (entry in currentEntries) {
                    val applies = try {
                        entry.appliesTo(formattedHost) || (port == 22 && entry.appliesTo(hostname))
                    } catch (e: Exception) {
                        false
                    }
                    if (applies) {
                        hostExists = true
                        val tokens = entry.line.trim().split("\\s+".toRegex())
                        if (tokens.size >= 3) {
                            try {
                                val decodedOld = Base64.getDecoder().decode(tokens[2])
                                val md = MessageDigest.getInstance("SHA-256")
                                oldFingerprint = "SHA256:" + Base64.getEncoder().encodeToString(md.digest(decodedOld))
                                oldRandomart = com.adamoutler.ssh.crypto.randomart.OpenSshRandomartGenerator.generateFromPublicKeyBytes(decodedOld, tokens[1])
                            } catch (e: Exception) {
                                oldFingerprint = "Unknown/Unparseable"
                            }
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                // Fall back to line parsing below if OpenSSHKnownHosts fails to parse
            }

            if (!hostExists) {
                knownHostsFile.useLines { lines ->
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                        val tokens = trimmed.split("\\s+".toRegex())
                        val hostsToken = tokens.firstOrNull() ?: continue
                        val hostList = hostsToken.split(",")
                        if (hostList.contains(formattedHost) || (port == 22 && hostList.contains(hostname))) {
                            hostExists = true
                            if (tokens.size >= 3) {
                                try {
                                    val decodedOld = Base64.getDecoder().decode(tokens[2])
                                    val md = MessageDigest.getInstance("SHA-256")
                                    oldFingerprint = "SHA256:" + Base64.getEncoder().encodeToString(md.digest(decodedOld))
                                    oldRandomart = com.adamoutler.ssh.crypto.randomart.OpenSshRandomartGenerator.generateFromPublicKeyBytes(decodedOld, tokens[1])
                                } catch (e: Exception) {
                                    oldFingerprint = "Unknown/Unparseable"
                                }
                            }
                            break
                        }
                    }
                }
            }
        }

        val keyType = net.schmizz.sshj.common.KeyType.fromKey(key).toString()
        val buffer = net.schmizz.sshj.common.Buffer.PlainBuffer().putPublicKey(key)
        val keyBlobBase64 = Base64.getEncoder().encodeToString(buffer.compactData)
        val receivedFingerprint = try {
            val md = MessageDigest.getInstance("SHA-256")
            "SHA256:" + Base64.getEncoder().encodeToString(md.digest(buffer.compactData))
        } catch (e: Exception) {
            net.schmizz.sshj.common.SecurityUtils.getFingerprint(key)
        }
        val receivedRandomart = try {
            com.adamoutler.ssh.crypto.randomart.OpenSshRandomartGenerator.generateFromPublicKeyBytes(buffer.compactData, keyType)
        } catch (e: Exception) {
            null
        }

        // Prompt user (runBlocking because verify is synchronous but we need to await the user's UI action)
        val userAccepted = kotlinx.coroutines.runBlocking {
            ConnectionStateRepository.requestPrompt(
                hostname = formattedHost,
                expectedFingerprint = oldFingerprint,
                receivedFingerprint = receivedFingerprint,
                isKeyChanged = hostExists,
                expectedRandomart = oldRandomart,
                receivedRandomart = receivedRandomart,
            )
        }

        // Record audit event
        kotlinx.coroutines.runBlocking {
            try {
                val auditManager = context?.let { com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstance(it) }
                    ?: com.adamoutler.ssh.security.audit.SecurityAuditLogManager.getInstanceOrNull()
                auditManager?.logHostKeyVerification(
                    host = formattedHost,
                    port = port,
                    keyType = keyType,
                    fingerprint = receivedFingerprint,
                    accepted = userAccepted,
                    isKeyChanged = hostExists,
                )
            } catch (_: Exception) {
                // Audit logging failure should not abort connection
            }
        }

        if (userAccepted) {
            EphemeralHostKeyRegistry.record(formattedHost, key)
            val hostsManager = com.adamoutler.ssh.crypto.KnownHostsManager(knownHostsFile)
            val existingEntries = hostsManager.getEntries()
            val oldMatching = existingEntries.find { hostsManager.matchesHost(it, formattedHost, port) }
            val shouldHash = oldMatching?.isHashed ?: (existingEntries.isNotEmpty() && existingEntries.all { it.isHashed })
            hostsManager.saveOrUpdateHostKey(formattedHost, port, keyType, keyBlobBase64, hashHost = shouldHash)
            try {
                entries.clear()
                entries.addAll(OpenSSHKnownHosts(knownHostsFile).entries())
            } catch (e: Exception) {
                // In-memory sync fallback
            }
            try {
                println(("TofuHostKeyVerifier").toString() + ": " + ("Host $hostname key accepted and saved.").toString())
            } catch (e: Throwable) {
                println("Host $hostname key accepted and saved.")
            }
            return true
        } else {
            try {
                println(("TofuHostKeyVerifier").toString() + ": " + ("Host key for $hostname rejected by user. Connection aborted.").toString())
            } catch (e: Throwable) {
                println("Host key rejected by user.")
            }
            return false
        }
    }
}

class SshConnectionManager(
    private val hostKeyVerifier: HostKeyVerifier? = null,
    private val identityStorageManager: IdentityStorageManager? = null,
    private val context: android.content.Context? = null,
    private val securityStorageManager: com.adamoutler.ssh.crypto.SecurityStorageManager? = null,
) {
    private val portForwardingOrchestrator = PortForwardingOrchestrator()
    private val protocolFactory = ConnectionProtocolFactory(
        hostKeyVerifier = hostKeyVerifier,
        identityStorageManager = identityStorageManager,
        context = context,
        portForwardingOrchestrator = portForwardingOrchestrator,
        securityStorageManager = securityStorageManager,
    )

    private var activeProtocol: ConnectionProtocol? = null

    // Exposed for tests or legacy code that might need the underlying client.
    val client: SSHClient?
        get() = (activeProtocol as? SshConnectionHandler)?.client

    fun disconnect() {
        try {
            activeProtocol?.disconnect()
        } catch (e: Exception) {
            println(("SshConnectionManager").toString() + ": " + ("Error during manual disconnect").toString() + " " + (e).toString())
        }
        try {
            portForwardingOrchestrator.stopAll()
        } catch (e: Exception) {
            println(("SshConnectionManager").toString() + ": " + ("Error closing local port forwarders").toString() + " " + (e).toString())
        }
    }

    fun updateWindowSize(cols: Int, rows: Int, widthPx: Int = 0, heightPx: Int = 0) {
        activeProtocol?.updateWindowSize(cols, rows, widthPx, heightPx)
    }

    suspend fun connectAndExecute(profile: ConnectionProfile, command: String, keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val sshClient = SSHClient(SecureSshConfig())
        val handshakeCoordinator = SshHandshakeCoordinator(hostKeyVerifier, identityStorageManager, context)

        try {
            handshakeCoordinator.executeWithConnection(sshClient, profile, keyPair) { _ ->
                sshClient.startSession().use { session ->
                    val cmd = session.exec(command)
                    val result = cmd.inputStream.bufferedReader().use { it.readText() }
                    cmd.join()
                    result
                }
            }
        } finally {
            profile.clearSensitiveData()
            try {
                sshClient.disconnect()
            } catch (e: Exception) {
                println(("SshConnectionManager").toString() + ": " + ("Error during disconnect").toString() + " " + (e).toString())
            }
        }
    }

    suspend fun connectPty(
        profile: ConnectionProfile,
        keyPair: KeyPair? = null,
        onOutput: suspend (ByteArray, Int) -> Unit,
        onConnect: (java.io.OutputStream, net.schmizz.sshj.connection.channel.direct.Session.Shell?) -> Unit,
        isAutonomousReconnect: Boolean = false,
        sessionId: String? = null,
    ) = withContext(Dispatchers.IO) {
        activeProtocol = protocolFactory.create(profile.protocol)
        activeProtocol?.connect(profile, keyPair, onOutput, onConnect, isAutonomousReconnect, sessionId)
    }

    /**
     * Injects a public key into the remote server's authorized_keys file.
     * Uses a temporary password for authentication.
     */
    suspend fun injectPublicKey(
        profile: ConnectionProfile,
        publicKey: String,
    ): Boolean = withContext(Dispatchers.IO) {
        // Validate public key to prevent shell injection
        val regex = Regex("^[a-zA-Z0-9+/= \\-_@]+$")
        if (!regex.matches(publicKey)) {
            println(("SshConnectionManager").toString() + ": " + ("Invalid public key format for injection").toString())
            return@withContext false
        }

        val injectionCommand = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && echo \"$publicKey\" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"

        try {
            connectAndExecute(profile, injectionCommand)
            true
        } catch (e: Exception) {
            println(("SshConnectionManager").toString() + ": " + ("Failed to inject public key").toString() + " " + (e).toString())
            false
        }
    }
}
