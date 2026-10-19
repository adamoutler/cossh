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

    @PublishedApi
    internal fun configureHostKeyVerifier(client: SSHClient) {
        if (hostKeyVerifier != null) {
            client.addHostKeyVerifier(hostKeyVerifier)
        } else {
            val knownHostsFile = context?.let { File(it.filesDir, "ssh_known_hosts") }
            if (knownHostsFile != null) {
                if (!knownHostsFile.exists()) knownHostsFile.createNewFile()
                client.addHostKeyVerifier(TofuHostKeyVerifier(knownHostsFile))
            } else {
                throw IOException("No Context provided for TOFU verifier and no HostKeyVerifier configured. Refusing to connect insecurely.")
            }
        }
    }

    private class CompositeAuthenticator(private val authenticators: List<SshAuthenticator>) : SshAuthenticator {
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
                        return
                    }
                } catch (e: Exception) {
                    lastException = e
                    println(("CompositeAuthenticator").toString() + ": " + ("Authentication failed with ${authenticator::class.java.simpleName}, trying next").toString() + " " + (e).toString())
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

            // Try password auth if a password exists or if authType is PASSWORD
            if (identity.password != null || profile.authType == AuthType.PASSWORD) {
                authenticators.add(PasswordAuthenticator(identity.password))
            }

            if (authenticators.isNotEmpty()) {
                return CompositeAuthenticator(authenticators)
            }

            throw IllegalArgumentException("Identity has neither valid password nor complete private/public key")
        }

        return when (profile.authType) {
            AuthType.PASSWORD -> PasswordAuthenticator()

            AuthType.KEY -> {
                val validKeyPair = requireNotNull(keyPair) { "Valid KeyPair required for key-based authentication" }
                requireNotNull(validKeyPair.public) { "Valid KeyPair with public key required for key-based authentication" }
                KeyAuthenticator(validKeyPair)
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
        getAuthenticator(profile, keyPair, identity).authenticate(client, effectiveProfile)
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
        block: suspend (effectiveProfile: ConnectionProfile) -> T,
    ): T {
        client.connectTimeout = 10000
        client.timeout = 10000

        val identity = resolveIdentity(profile)

        try {
            configureHostKeyVerifier(client)

            if (profile.useLocalDns) {
                val inetAddresses = resolveHostLocally(profile.host)
                if (!inetAddresses.isNullOrEmpty()) {
                    val inetAddress = java.net.InetAddress.getByAddress(profile.host, inetAddresses[0].address)
                    println("SshHandshakeCoordinator: Resolved ${profile.host} via local network DNS: ${inetAddress.hostAddress}")
                    connectClient(client, inetAddress, profile.port)
                } else {
                    println("SshHandshakeCoordinator: Local DNS resolution returned empty for ${profile.host}, using system fallback")
                    connectClient(client, profile.host, profile.port)
                }
            } else {
                connectClient(client, profile.host, profile.port)
            }

            val effectiveProfile = if (identity != null) {
                profile.copy(username = identity.username, password = identity.password)
            } else {
                profile
            }

            authenticateClient(client, profile, keyPair, identity, effectiveProfile)

            return block(effectiveProfile)
        } finally {
            identity?.clearSensitiveData()
            profile.clearSensitiveData()
        }
    }
}
