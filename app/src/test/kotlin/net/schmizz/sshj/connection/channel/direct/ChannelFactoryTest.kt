package net.schmizz.sshj.connection.channel.direct

import net.schmizz.sshj.connection.Connection
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelFactoryTest {
    @Test
    fun testParametersCreation() {
        // We only test that we can create Parameters and assert its values, 
        // to cover some of the ChannelFactory logic without needing a mock connection.
        val params = Parameters("localhost", 1080, "google.com", 80)
        assertEquals("localhost", params.localHost)
        assertEquals(1080, params.localPort)
        assertEquals("google.com", params.remoteHost)
        assertEquals(80, params.remotePort)
    }
}
