package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import kotlinx.coroutines.runBlocking
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource

class PasswordAuthenticator(private val identityPassword: ByteArray? = null) : SshAuthenticator {
    override fun authenticate(client: SSHClient, profile: ConnectionProfile) {
        var passwordChars: CharArray? = null
        var passwordBytes = identityPassword ?: profile.password

        try {
            if (passwordBytes != null) {
                passwordChars = CharArray(passwordBytes.size) { i -> passwordBytes[i].toInt().toChar() }
            } else {
                if (ConnectionStateRepository.isHeadlessTest) {
                    throw IllegalStateException("Headless test: cannot prompt for password")
                }
                passwordChars = runBlocking {
                    kotlinx.coroutines.withTimeoutOrNull(5000L) {
                        ConnectionStateRepository.requestPasswordPrompt(profile.id)
                    }
                }
            }

            if (passwordChars == null) {
                throw IllegalArgumentException("Password required for password auth")
            }

            val finalChars = passwordChars
            val passwordFinder = object : PasswordFinder {
                override fun reqPassword(resource: Resource<*>?): CharArray = finalChars
                override fun shouldRetry(resource: Resource<*>?): Boolean = false
            }
            client.authPassword(profile.username, passwordFinder)
        } finally {
            passwordChars?.fill('\u0000')
            // Only clear the identityPassword if we used it as passwordBytes? Actually it's probably best not to clear the passed in array if it belongs to the profile/identity, but since we were clearing it before, we'll keep the logic the same or just clear if we copied it. 
            // passwordBytes?.fill(0) // Let's avoid clearing the profile/identity password array directly here as it may be reused. 
        }
    }
}
