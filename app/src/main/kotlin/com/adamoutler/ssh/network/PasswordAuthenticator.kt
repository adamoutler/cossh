package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource

class PasswordAuthenticator : SshAuthenticator {
    override fun authenticate(client: SSHClient, profile: ConnectionProfile) {
        val passwordBytes = profile.password ?: throw IllegalArgumentException("Password required for password auth")
        val passwordChars = CharArray(passwordBytes.size)
        try {
            for (i in passwordBytes.indices) {
                passwordChars[i] = passwordBytes[i].toInt().toChar()
            }
            val passwordFinder = object : PasswordFinder {
                override fun reqPassword(resource: Resource<*>?): CharArray = passwordChars
                override fun shouldRetry(resource: Resource<*>?): Boolean = false
            }
            client.authPassword(profile.username, passwordFinder)
        } finally {
            passwordChars.fill('\u0000')
            passwordBytes.fill(0)
        }
    }
}
