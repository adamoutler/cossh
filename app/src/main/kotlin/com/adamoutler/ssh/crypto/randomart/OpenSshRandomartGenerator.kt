package com.adamoutler.ssh.crypto.randomart

import java.security.MessageDigest

/**
 * Pure Kotlin implementation of the authentic OpenSSH "Drunken Bishop"
 * visual host key fingerprint algorithm.
 *
 * Simulates a bishop wandering randomly on a 17x9 toroidal/bounded chessboard
 * driven by 2-bit pairs of the cryptographic digest, rendering an ASCII art
 * visualization with header and footer borders.
 */
object OpenSshRandomartGenerator {

    const val FLDSIZE_X = 17
    const val FLDSIZE_Y = 9

    // Augmentation palette defined by OpenSSH:
    // Index 0: ' '
    // Indices 1..14: .o+=*BOX@%&#/^
    // Index 15: 'S' (Start point)
    // Index 16: 'E' (End point)
    private const val AUGMENTATION_STRING = " .o+=*BOX@%&#/^SE"
    private val PALETTE_MAX_INDEX = AUGMENTATION_STRING.length - 1 // 16

    /**
     * Generates the OpenSSH randomart string from a raw cryptographic digest.
     *
     * @param rawDigest The raw bytes of the key digest (e.g. SHA-256 or MD5).
     * @param keyTypeTitle The key title, e.g. "ED25519 256", "RSA 4096", or "[ED25519 256]".
     * @param hashAlgorithmTitle The hash algorithm, e.g. "SHA256", "MD5", or "[SHA256]".
     * @return 11-line formatted ASCII randomart string.
     */
    fun generate(
        rawDigest: ByteArray,
        keyTypeTitle: String,
        hashAlgorithmTitle: String,
    ): String {
        val field = Array(FLDSIZE_X) { IntArray(FLDSIZE_Y) }
        var x = FLDSIZE_X / 2
        var y = FLDSIZE_Y / 2

        // Walk through each byte of the digest
        for (byte in rawDigest) {
            var input = byte.toInt() and 0xFF
            for (b in 0 until 4) {
                x += if ((input and 0x1) != 0) 1 else -1
                y += if ((input and 0x2) != 0) 1 else -1

                x = x.coerceIn(0, FLDSIZE_X - 1)
                y = y.coerceIn(0, FLDSIZE_Y - 1)

                // Increment visit count up to len - 2 (index 14: '^')
                if (field[x][y] < PALETTE_MAX_INDEX - 2) {
                    field[x][y]++
                }
                input = input shr 2
            }
        }

        // Mark starting point and end point
        field[FLDSIZE_X / 2][FLDSIZE_Y / 2] = PALETTE_MAX_INDEX - 1 // 'S' (15)
        field[x][y] = PALETTE_MAX_INDEX // 'E' (16)

        // Assemble title
        val title = formatBorderTitle(keyTypeTitle)
        val padTitleLeft = (FLDSIZE_X - title.length) / 2
        val padTitleRight = FLDSIZE_X - padTitleLeft - title.length
        val header = "+" + "-".repeat(padTitleLeft) + title + "-".repeat(padTitleRight) + "+"

        // Assemble hash
        val hash = formatBorderTitle(hashAlgorithmTitle)
        val padHashLeft = (FLDSIZE_X - hash.length) / 2
        val padHashRight = FLDSIZE_X - padHashLeft - hash.length
        val footer = "+" + "-".repeat(padHashLeft) + hash + "-".repeat(padHashRight) + "+"

        val sb = StringBuilder()
        sb.append(header).append("\n")
        for (gridY in 0 until FLDSIZE_Y) {
            sb.append("|")
            for (gridX in 0 until FLDSIZE_X) {
                val charIndex = field[gridX][gridY].coerceIn(0, PALETTE_MAX_INDEX)
                sb.append(AUGMENTATION_STRING[charIndex])
            }
            sb.append("|\n")
        }
        sb.append(footer)

        return sb.toString()
    }

    /**
     * Computes the digest of the raw public key bytes and generates the corresponding randomart.
     *
     * @param publicKeyBytes The raw public key wire-format bytes.
     * @param keyTypeTitle The key type descriptor (e.g. "ED25519 256", "RSA 4096", "ECDSA 256").
     * @param useSha256 Whether to use SHA-256 (default, standard since OpenSSH 6.8) or MD5.
     */
    fun generateFromPublicKeyBytes(
        publicKeyBytes: ByteArray,
        keyTypeTitle: String,
        useSha256: Boolean = true,
    ): String {
        val algorithm = if (useSha256) "SHA-256" else "MD5"
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(publicKeyBytes)
        val hashTitle = if (useSha256) "SHA256" else "MD5"
        return generate(digest, keyTypeTitle, hashTitle)
    }

    private fun formatBorderTitle(raw: String): String {
        val trimmed = raw.trim()
        val wrapped = if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed
        } else {
            "[$trimmed]"
        }
        return if (wrapped.length > FLDSIZE_X) {
            wrapped.take(FLDSIZE_X)
        } else {
            wrapped
        }
    }
}
