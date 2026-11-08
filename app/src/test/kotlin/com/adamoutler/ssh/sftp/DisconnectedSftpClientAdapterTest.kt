package com.adamoutler.ssh.sftp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class DisconnectedSftpClientAdapterTest {

    private val adapter = DisconnectedSftpClientAdapter()
    private val expectedMessage = "SFTP client is not connected: active SSH session required"

    @Test
    fun testList_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            runBlocking { adapter.list("/root") }
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testMakeDirectory_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            runBlocking { adapter.makeDirectory("/tmp/dir") }
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testRemove_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            runBlocking { adapter.remove("/tmp/file", isDirectory = false) }
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testRename_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            runBlocking { adapter.rename("/tmp/old", "/tmp/new") }
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testSetPermissions_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            runBlocking { adapter.setPermissions("/tmp/file", 0x1ED) }
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testOpenInputStream_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            runBlocking { adapter.openInputStream("/tmp/file") }
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testOpenOutputStream_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            runBlocking { adapter.openOutputStream("/tmp/file", append = false) }
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testStat_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            runBlocking { adapter.stat("/tmp/file") }
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testClose_throwsIOException() {
        val ex = assertThrows(IOException::class.java) {
            adapter.close()
        }
        assertEquals(expectedMessage, ex.message)
    }

    @Test
    fun testCustomErrorMessage() {
        val customMsg = "Custom offline error message"
        val customAdapter = DisconnectedSftpClientAdapter(customMsg)

        val ex = assertThrows(IOException::class.java) {
            runBlocking { customAdapter.list("/") }
        }
        assertEquals(customMsg, ex.message)
    }
}
