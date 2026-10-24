package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.Protocol
import com.adamoutler.ssh.ui.components.JumpHostGraphValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Standalone unit tests for Jump Host (ProxyJump) cycle detection graph algorithms (SSH-209).
 */
class JumpHostCycleDetectionTest {

    private fun createProfile(id: String, nickname: String, jumpHostId: String? = null): ConnectionProfile = ConnectionProfile(
        id = id,
        nickname = nickname,
        host = "$nickname.local",
        port = 22,
        protocol = Protocol.SSH,
        username = "user",
        authType = AuthType.PASSWORD,
        jumpHostProfileId = jumpHostId,
    )

    @Test
    fun hasJumpHostCycle_selfSelection_detectsCycle() {
        val profileA = createProfile("A", "HostA")
        val map = mapOf("A" to profileA)

        val hasCycle = JumpHostGraphValidator.hasJumpHostCycle(
            currentProfileId = "A",
            selectedJumpHostId = "A",
            profiles = map,
        )
        assertTrue("Selecting self as jump host must detect a cycle", hasCycle)
    }

    @Test
    fun hasJumpHostCycle_directTwoNodeCycle_detectsCycle() {
        // B jumps through A. If A selects B, it forms A -> B -> A
        val profileB = createProfile("B", "HostB", jumpHostId = "A")
        val map = mapOf("B" to profileB)

        val hasCycle = JumpHostGraphValidator.hasJumpHostCycle(
            currentProfileId = "A",
            selectedJumpHostId = "B",
            profiles = map,
        )
        assertTrue("A -> B -> A cycle must be detected", hasCycle)
    }

    @Test
    fun hasJumpHostCycle_multiNodeCycle_detectsCycle() {
        // C jumps through B, B jumps through A. If A selects C, it forms A -> C -> B -> A
        val profileC = createProfile("C", "HostC", jumpHostId = "B")
        val profileB = createProfile("B", "HostB", jumpHostId = "A")
        val map = mapOf("B" to profileB, "C" to profileC)

        val hasCycle = JumpHostGraphValidator.hasJumpHostCycle(
            currentProfileId = "A",
            selectedJumpHostId = "C",
            profiles = map,
        )
        assertTrue("A -> C -> B -> A transitive cycle must be detected", hasCycle)
    }

    @Test
    fun hasJumpHostCycle_validAcyclicChain_returnsFalse() {
        // B jumps through C, C has no jump host. If A selects B, chain is A -> B -> C -> null (valid!)
        val profileC = createProfile("C", "HostC", jumpHostId = null)
        val profileB = createProfile("B", "HostB", jumpHostId = "C")
        val map = mapOf("B" to profileB, "C" to profileC)

        val hasCycle = JumpHostGraphValidator.hasJumpHostCycle(
            currentProfileId = "A",
            selectedJumpHostId = "B",
            profiles = map,
        )
        assertFalse("Acyclic chain A -> B -> C must not report a cycle", hasCycle)
    }

    @Test
    fun hasJumpHostCycle_nullJumpHost_returnsFalse() {
        val map = emptyMap<String, ConnectionProfile>()
        val hasCycle = JumpHostGraphValidator.hasJumpHostCycle(
            currentProfileId = "A",
            selectedJumpHostId = null,
            profiles = map,
        )
        assertFalse("Direct connection (no jump host) cannot have a cycle", hasCycle)
    }
}
