package com.adamoutler.ssh.network

import net.schmizz.sshj.AndroidConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SecureSshConfig] verifying cryptographic algorithm hardening
 * and purge of obsolete/vulnerable primitives (SHA-1, MD5, 3DES, Blowfish, DH Group 1).
 */
class SecureSshConfigTest {

    @Test
    fun keyExchangeFactories_doesNotContainSha1OrGroup1() {
        val defaultConfig = AndroidConfig()
        val secureConfig = SecureSshConfig()

        val defaultKexNames = defaultConfig.keyExchangeFactories.map { it.name.lowercase() }
        val secureKexNames = secureConfig.keyExchangeFactories.map { it.name.lowercase() }

        // Verify that default config had weak algorithms that needed stripping
        assertTrue(
            "Default config should contain sha1 or group1- algorithms before hardening",
            defaultKexNames.any { it.contains("sha1") || it.contains("group1-") }
        )

        // Verify hardened config strips all sha1 and group1- algorithms
        assertTrue("Hardened key exchange factories must not be empty", secureKexNames.isNotEmpty())
        for (kexName in secureKexNames) {
            assertFalse(
                "Hardened key exchange should not contain sha1: $kexName",
                kexName.contains("sha1")
            )
            assertFalse(
                "Hardened key exchange should not contain group1-: $kexName",
                kexName.contains("group1-")
            )
        }
    }

    @Test
    fun cipherFactories_doesNotContain3desOrBlowfish() {
        val defaultConfig = AndroidConfig()
        val secureConfig = SecureSshConfig()

        val defaultCipherNames = defaultConfig.cipherFactories.map { it.name.lowercase() }
        val secureCipherNames = secureConfig.cipherFactories.map { it.name.lowercase() }

        // Verify that default config had weak 64-bit ciphers before hardening
        assertTrue(
            "Default config should contain 3des or blowfish ciphers before hardening",
            defaultCipherNames.any { it.contains("3des") || it.contains("blowfish") }
        )

        // Verify hardened config strips all 3des and blowfish ciphers
        assertTrue("Hardened cipher factories must not be empty", secureCipherNames.isNotEmpty())
        for (cipherName in secureCipherNames) {
            assertFalse(
                "Hardened cipher should not contain 3des: $cipherName",
                cipherName.contains("3des")
            )
            assertFalse(
                "Hardened cipher should not contain blowfish: $cipherName",
                cipherName.contains("blowfish")
            )
        }
    }

    @Test
    fun macFactories_doesNotContainSha1OrMd5() {
        val defaultConfig = AndroidConfig()
        val secureConfig = SecureSshConfig()

        val defaultMacNames = defaultConfig.macFactories.map { it.name.lowercase() }
        val secureMacNames = secureConfig.macFactories.map { it.name.lowercase() }

        // Verify that default config had weak hash MACs before hardening
        assertTrue(
            "Default config should contain sha1 or md5 MACs before hardening",
            defaultMacNames.any { it.contains("sha1") || it.contains("md5") }
        )

        // Verify hardened config strips all sha1 and md5 MACs
        assertTrue("Hardened MAC factories must not be empty", secureMacNames.isNotEmpty())
        for (macName in secureMacNames) {
            assertFalse(
                "Hardened MAC should not contain sha1: $macName",
                macName.contains("sha1")
            )
            assertFalse(
                "Hardened MAC should not contain md5: $macName",
                macName.contains("md5")
            )
        }
    }

    @Test
    fun modernAlgorithmsRetainedAndNonEmpty() {
        val secureConfig = SecureSshConfig()

        val kexNames = secureConfig.keyExchangeFactories.map { it.name.lowercase() }
        val cipherNames = secureConfig.cipherFactories.map { it.name.lowercase() }
        val macNames = secureConfig.macFactories.map { it.name.lowercase() }

        // Modern KEX: curve25519-sha256 or ECDH NIST curves
        assertTrue(
            "Hardened config must retain modern KEX (e.g., curve25519 or ecdh): $kexNames",
            kexNames.any { it.contains("curve25519") || it.contains("ecdh") || it.contains("sha256") }
        )

        // Modern Ciphers: AES-CTR or AES-GCM
        assertTrue(
            "Hardened config must retain AES ciphers: $cipherNames",
            cipherNames.any { it.contains("aes") }
        )

        // Modern MACs: HMAC-SHA2 (256 or 512)
        assertTrue(
            "Hardened config must retain HMAC-SHA2 algorithms: $macNames",
            macNames.any { it.contains("hmac-sha2-256") || it.contains("hmac-sha2-512") }
        )
    }

    @Test
    fun printAlgorithmAudit() {
        val defaultConfig = AndroidConfig()
        val secureConfig = SecureSshConfig()

        val defaultKex = defaultConfig.keyExchangeFactories.map { it.name }
        val secureKex = secureConfig.keyExchangeFactories.map { it.name }
        val strippedKex = defaultKex.filterNot { secureKex.contains(it) }

        val defaultCiphers = defaultConfig.cipherFactories.map { it.name }
        val secureCiphers = secureConfig.cipherFactories.map { it.name }
        val strippedCiphers = defaultCiphers.filterNot { secureCiphers.contains(it) }

        val defaultMacs = defaultConfig.macFactories.map { it.name }
        val secureMacs = secureConfig.macFactories.map { it.name }
        val strippedMacs = defaultMacs.filterNot { secureMacs.contains(it) }

        println("=== KEX AUDIT ===")
        println("STRIPPED KEX: $strippedKex")
        println("RETAINED KEX: $secureKex")
        println("=== CIPHER AUDIT ===")
        println("STRIPPED CIPHERS: $strippedCiphers")
        println("RETAINED CIPHERS: $secureCiphers")
        println("=== MAC AUDIT ===")
        println("STRIPPED MACS: $strippedMacs")
        println("RETAINED MACS: $secureMacs")
    }
}
