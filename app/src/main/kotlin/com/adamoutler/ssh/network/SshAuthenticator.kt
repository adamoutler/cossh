package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import net.schmizz.sshj.SSHClient

interface SshAuthenticator {
    fun authenticate(client: SSHClient, profile: ConnectionProfile)
}
