package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.keyprovider.KeyPairWrapper
import java.security.KeyPair

class KeyAuthenticator(private val keyPair: KeyPair) : SshAuthenticator {
    override fun authenticate(client: SSHClient, profile: ConnectionProfile) {
        client.authPublickey(profile.username, KeyPairWrapper(keyPair))
    }
}
