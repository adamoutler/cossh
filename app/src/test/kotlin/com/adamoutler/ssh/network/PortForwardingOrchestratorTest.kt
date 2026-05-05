package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.PortForwardConfig
import com.adamoutler.ssh.data.PortForwardType
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import org.junit.Test
import java.net.ServerSocket
import org.junit.Assert.assertTrue

class PortForwardingOrchestratorTest {

    open class StubLocalPortForwarder : LocalPortForwarder(
        null, null, null, null
    ) {
        var listenCalled = false
        override fun listen() {
            listenCalled = true
        }
    }

    open class StubRemotePortForwarder : RemotePortForwarder(null) {
        var bindCalled = false
        override fun bind(forward: Forward?, listener: net.schmizz.sshj.connection.channel.forwarded.ConnectListener?): net.schmizz.concurrent.Promise<Forward, net.schmizz.sshj.connection.ConnectionException> {
            bindCalled = true
            return net.schmizz.concurrent.Promise<Forward, net.schmizz.sshj.connection.ConnectionException>(null, null)
        }
    }

    open class StubSSHClient : SSHClient() {
        var createLocalCalled = false
        var stubLocalForwarder = StubLocalPortForwarder()
        var stubRemoteForwarder = StubRemotePortForwarder()

        override fun newLocalPortForwarder(
            params: net.schmizz.sshj.connection.channel.direct.Parameters?,
            serverSocket: ServerSocket?
        ): LocalPortForwarder {
            createLocalCalled = true
            return stubLocalForwarder
        }

        override fun getRemotePortForwarder(): RemotePortForwarder {
            return stubRemoteForwarder
        }
    }

    @Test
    fun testStartLocalPortForwards() {
        val orchestrator = PortForwardingOrchestrator()
        val client = StubSSHClient()
        
        val config = PortForwardConfig(
            type = PortForwardType.LOCAL,
            localPort = 8080,
            remoteHost = "localhost",
            remotePort = 80
        )
        val profile = ConnectionProfile(id = "1", nickname = "Test", username = "test", host = "host", portForwards = listOf(config))
        
        orchestrator.startPortForwards(client, profile)
        Thread.sleep(200) // allow thread to start
        assertTrue(client.createLocalCalled)
        assertTrue(client.stubLocalForwarder.listenCalled)
        orchestrator.stopAll()
    }

    @Test
    fun testStartRemotePortForwards() {
        val orchestrator = PortForwardingOrchestrator()
        val client = StubSSHClient()
        
        val config = PortForwardConfig(
            type = PortForwardType.REMOTE,
            localPort = 9090,
            remoteHost = "",
            remotePort = 90
        )
        val profile = ConnectionProfile(id = "1", nickname = "Test", username = "test", host = "host", portForwards = listOf(config))
        
        orchestrator.startPortForwards(client, profile)
        
        assertTrue(client.stubRemoteForwarder.bindCalled)
        orchestrator.stopAll()
    }
    
    @Test
    fun testStartLocalPortForwardsExceptionHandling() {
        val orchestrator = PortForwardingOrchestrator()
        val client = object : StubSSHClient() {
            override fun newLocalPortForwarder(
                params: net.schmizz.sshj.connection.channel.direct.Parameters?,
                serverSocket: ServerSocket?
            ): LocalPortForwarder {
                throw RuntimeException("Simulated client error")
            }
        }
        
        val config = PortForwardConfig(
            type = PortForwardType.LOCAL,
            localPort = 9191, // Different port to avoid conflict
            remoteHost = "localhost",
            remotePort = 80
        )
        val profile = ConnectionProfile(id = "1", nickname = "Test", username = "test", host = "host", portForwards = listOf(config))
        
        orchestrator.startPortForwards(client, profile)
        orchestrator.stopAll()
    }
    
    @Test
    fun testLocalPortForwarderThreadException() {
        val orchestrator = PortForwardingOrchestrator()
        val client = StubSSHClient()
        client.stubLocalForwarder = object : StubLocalPortForwarder() {
            override fun listen() {
                throw RuntimeException("Simulated listen error")
            }
        }
        
        val config = PortForwardConfig(
            type = PortForwardType.LOCAL,
            localPort = 9292,
            remoteHost = "localhost",
            remotePort = 80
        )
        val profile = ConnectionProfile(id = "1", nickname = "Test", username = "test", host = "host", portForwards = listOf(config))
        
        orchestrator.startPortForwards(client, profile)
        Thread.sleep(100) // allow thread to throw
        orchestrator.stopAll()
    }
}
