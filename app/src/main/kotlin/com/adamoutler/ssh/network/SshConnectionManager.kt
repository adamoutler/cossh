package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import java.security.KeyPair

class SshConnectionManager {

    suspend fun connectAndExecute(profile: ConnectionProfile, command: String, keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val client = SSHClient()
        try {
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connect(profile.host, profile.port)

            when (profile.authType) {
                AuthType.PASSWORD -> {
                    val passwordBytes = profile.password ?: throw IllegalArgumentException("Password required for password auth")
                    val passwordString = String(passwordBytes)
                    client.authPassword(profile.username, passwordString)
                    // Volatile state: Active destruction of the secret in memory
                    passwordBytes.fill(0)
                }
                AuthType.KEY -> {
                    if (keyPair == null) {
                        throw IllegalArgumentException("KeyPair required for key-based authentication")
                    }
                    val keyProvider = KeyPairWrapper(keyPair)
                    client.authPublickey(profile.username, keyProvider)
                }
            }

            client.startSession().use { session ->
                val cmd = session.exec(command)
                val result = cmd.inputStream.bufferedReader().use { it.readText() }
                cmd.join()
                return@withContext result
            }
        } finally {
            client.disconnect()
        }
    }
}
