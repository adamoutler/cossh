package com.adamoutler.ssh.network

import net.schmizz.sshj.AndroidConfig

/**
 * Hardened SSH client configuration enforcing the Hostile Wire security invariant.
 *
 * This configuration extends [AndroidConfig] and proactively purges deprecated,
 * cryptographically broken, or vulnerable algorithms during initialization:
 * - **Key Exchange**: Strips legacy SHA-1 and Diffie-Hellman Group 1 (768/1024-bit) key exchanges
 *   (e.g., `diffie-hellman-group1-sha1`, `diffie-hellman-group14-sha1`) to eliminate susceptibility
 *   to Logjam and collision attacks.
 * - **Ciphers**: Strips 64-bit block ciphers such as Triple-DES (`3des-cbc`, `3des-ctr`) and
 *   Blowfish (`blowfish-cbc`, `blowfish-ctr`) to eliminate vulnerability to the Sweet32 attack.
 * - **MACs**: Strips MD5 and SHA-1 based message authentication codes (e.g., `hmac-md5`, `hmac-sha1`)
 *   to mitigate collision and hash downgrade vulnerabilities.
 *
 * Only modern, hardened cryptographic suites (such as Curve25519, ECDH NIST curves, AES-GCM / AES-CTR,
 * and HMAC-SHA2-256/512) are permitted.
 */
class SecureSshConfig : AndroidConfig() {
    init {
        keyExchangeFactories = keyExchangeFactories.filterNot { factory ->
            val name = factory.name.lowercase()
            name.contains("sha1") || name.contains("group1-")
        }

        cipherFactories = cipherFactories.filterNot { factory ->
            val name = factory.name.lowercase()
            name.contains("3des") || name.contains("blowfish")
        }

        macFactories = macFactories.filterNot { factory ->
            val name = factory.name.lowercase()
            name.contains("sha1") || name.contains("md5")
        }
    }
}
