package com.adamoutler.ssh.backup

import com.adamoutler.ssh.data.AuthType
import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.data.Protocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BackupSanitizerTest {

    @Test
    fun `test whitespace trimming on profiles and identities`() {
        val p = ConnectionProfile(
            id = "p1",
            nickname = "  Server A  ",
            host = "  192.168.1.100  ",
            username = "  admin  ",
            folderId = "  Production  ",
            initialDirectory = "  /home/admin  ",
        )
        val i = IdentityProfile(
            id = "i1",
            name = "  My Key  ",
            username = "  admin  ",
            publicKey = "  ssh-ed25519 AAAAC3N...  ",
        )

        val (cleanP, cleanI) = BackupSanitizer.sanitize(listOf(p), listOf(i))

        assertEquals("Server A", cleanP[0].nickname)
        assertEquals("192.168.1.100", cleanP[0].host)
        assertEquals("admin", cleanP[0].username)
        assertEquals("Production", cleanP[0].folderId)
        assertEquals("/home/admin", cleanP[0].initialDirectory)

        assertEquals("My Key", cleanI[0].name)
        assertEquals("admin", cleanI[0].username)
        assertEquals("ssh-ed25519 AAAAC3N...", cleanI[0].publicKey)
    }

    @Test
    fun `test dropping ghost profile with empty host and nickname`() {
        val ghost = ConnectionProfile(
            id = "ghost",
            nickname = "   ",
            host = "",
        )
        val valid = ConnectionProfile(
            id = "valid",
            nickname = "Good Server",
            host = "10.0.0.1",
        )

        val (cleanP, _) = BackupSanitizer.sanitize(listOf(ghost, valid), emptyList())

        assertEquals(1, cleanP.size)
        assertEquals("valid", cleanP[0].id)
    }

    @Test
    fun `test blank nickname falls back to host`() {
        val p = ConnectionProfile(
            id = "p1",
            nickname = "  ",
            host = "192.168.1.50",
        )

        val (cleanP, _) = BackupSanitizer.sanitize(listOf(p), emptyList())

        assertEquals("192.168.1.50", cleanP[0].nickname)
    }

    @Test
    fun `test blank identity name falls back to username`() {
        val i = IdentityProfile(
            id = "i1",
            name = "",
            username = "deployer",
        )

        val (_, cleanI) = BackupSanitizer.sanitize(emptyList(), listOf(i))

        assertEquals("deployer", cleanI[0].name)
    }

    @Test
    fun `test port and fontSize bounds validation`() {
        val pBadPort = ConnectionProfile(
            id = "p1",
            nickname = "Server",
            host = "1.2.3.4",
            port = 99999, // invalid port
            fontSize = 72, // invalid font size
        )

        val (cleanP, _) = BackupSanitizer.sanitize(listOf(pBadPort), emptyList())

        assertEquals(22, cleanP[0].port)
        assertNull(cleanP[0].fontSize)
    }

    @Test
    fun `test duplicate ID replacement`() {
        val p1 = ConnectionProfile(id = "same_id", nickname = "Server 1", host = "1.1.1.1")
        val p2 = ConnectionProfile(id = "same_id", nickname = "Server 2", host = "2.2.2.2")

        val (cleanP, _) = BackupSanitizer.sanitize(listOf(p1, p2), emptyList())

        assertEquals(2, cleanP.size)
        assertEquals("same_id", cleanP[0].id)
        assertNotEquals("same_id", cleanP[1].id)
    }

    @Test
    fun `test credentials preservation`() {
        val pwd = "secret".toByteArray()
        val key = "private_key".toByteArray()
        val p = ConnectionProfile(id = "p1", nickname = "Server", host = "1.2.3.4", password = pwd)
        val i = IdentityProfile(id = "i1", name = "Key", username = "user", password = pwd, privateKey = key)

        val (cleanP, cleanI) = BackupSanitizer.sanitize(listOf(p), listOf(i))

        assertArrayEquals(pwd, cleanP[0].password)
        assertArrayEquals(pwd, cleanI[0].password)
        assertArrayEquals(key, cleanI[0].privateKey)
    }
}
