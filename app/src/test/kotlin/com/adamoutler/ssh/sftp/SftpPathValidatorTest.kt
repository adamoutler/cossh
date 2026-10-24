package com.adamoutler.ssh.sftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SftpPathValidatorTest {

    @Test
    fun `canonicalize normalizes standard paths`() {
        assertEquals("/", SftpPathValidator.canonicalize("/"))
        assertEquals("/", SftpPathValidator.canonicalize(""))
        assertEquals("/var/log", SftpPathValidator.canonicalize("/var/log"))
        assertEquals("/var/log", SftpPathValidator.canonicalize("/var/log/"))
        assertEquals("/var/log", SftpPathValidator.canonicalize("var/log"))
    }

    @Test
    fun `canonicalize resolves relative parent and current dot segments`() {
        assertEquals("/var/etc", SftpPathValidator.canonicalize("/var/log/../etc"))
        assertEquals("/etc/passwd", SftpPathValidator.canonicalize("/var/../../etc/passwd"))
        assertEquals("/var/log", SftpPathValidator.canonicalize("/var/./log/./"))
        assertEquals("/", SftpPathValidator.canonicalize("/../../../.."))
    }

    @Test
    fun `canonicalize converts backslashes to forward slashes`() {
        assertEquals("/home/user/docs", SftpPathValidator.canonicalize("\\home\\user\\docs"))
        assertEquals("/home/user/docs", SftpPathValidator.canonicalize("/home/user\\docs"))
    }

    @Test
    fun `canonicalize rejects null byte injection attempts`() {
        try {
            SftpPathValidator.canonicalize("/var/log\u0000/something")
            fail("Expected IllegalArgumentException for null byte in path")
        } catch (_: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun `join safely combines directory and child name`() {
        assertEquals("/var/log/syslog", SftpPathValidator.join("/var/log", "syslog"))
        assertEquals("/syslog", SftpPathValidator.join("/", "syslog"))
    }

    @Test
    fun `join rejects traversal tokens and path separators in child name`() {
        try {
            SftpPathValidator.join("/var", "..")
            fail("Expected IllegalArgumentException for '..'")
        } catch (_: IllegalArgumentException) {}

        try {
            SftpPathValidator.join("/var", ".")
            fail("Expected IllegalArgumentException for '.'")
        } catch (_: IllegalArgumentException) {}

        try {
            SftpPathValidator.join("/var", "log/syslog")
            fail("Expected IllegalArgumentException for slash in child name")
        } catch (_: IllegalArgumentException) {}

        try {
            SftpPathValidator.join("/var", "bad\u0000file")
            fail("Expected IllegalArgumentException for null byte in child name")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `isWithinBounds correctly verifies sandbox containment`() {
        assertTrue(SftpPathValidator.isWithinBounds("/home/user/docs/file.txt", "/home/user"))
        assertTrue(SftpPathValidator.isWithinBounds("/home/user", "/home/user"))
        assertTrue(SftpPathValidator.isWithinBounds("/anywhere/on/fs", "/"))

        assertFalse(SftpPathValidator.isWithinBounds("/etc/passwd", "/home/user"))
        assertFalse(SftpPathValidator.isWithinBounds("/home/otheruser", "/home/user"))
        assertFalse(SftpPathValidator.isWithinBounds("/home/user/../other", "/home/user"))
    }

    @Test
    fun `getParent and getFilename extract segments accurately`() {
        assertEquals("/var", SftpPathValidator.getParent("/var/log"))
        assertEquals("/", SftpPathValidator.getParent("/var"))
        assertEquals("/", SftpPathValidator.getParent("/"))

        assertEquals("log", SftpPathValidator.getFilename("/var/log"))
        assertEquals("syslog.txt", SftpPathValidator.getFilename("/var/log/syslog.txt"))
        assertEquals("", SftpPathValidator.getFilename("/"))
    }
}
