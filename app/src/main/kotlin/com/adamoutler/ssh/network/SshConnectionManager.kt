package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.security.KeyPair

class SshConnectionManager(
    private val hostKeyVerifier: HostKeyVerifier? = null
) {
    suspend fun connectAndExecute(profile: ConnectionProfile, command: String, keyPair: KeyPair? = null): String = withContext(Dispatchers.IO) {
        val client = SSHClient()
        try {
            if (hostKeyVerifier != null) {
                client.addHostKeyVerifier(hostKeyVerifier)
            } else {
                client.loadKnownHosts()
            }
            
            client.connect(profile.host, profile.port)

            when (profile.authType) {
                AuthType.PASSWORD -> {
                    val passwordBytes = profile.password ?: throw IllegalArgumentException("Password required for password auth")
                    val passwordChars = CharArray(passwordBytes.size)
                    for (i in passwordBytes.indices) {
                        passwordChars[i] = passwordBytes[i].toInt().toChar()
                    }
                    
                    val passwordFinder = object : PasswordFinder {
                        override fun reqPassword(resource: Resource<*>?): CharArray {
                            return passwordChars
                        }
                        override fun shouldRetry(resource: Resource<*>?): Boolean = false
                    }
                    
                    client.authPassword(profile.username, passwordFinder)
                    
                    passwordChars.fill('\u0000')
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
