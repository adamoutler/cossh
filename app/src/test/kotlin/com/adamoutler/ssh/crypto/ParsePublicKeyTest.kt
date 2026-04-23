package com.adamoutler.ssh.crypto

import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.KeyType
import org.junit.Test
import java.util.Base64

class ParsePublicKeyTest {
    @Test
    fun testParse() {
        val pubKeyString = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl"
        val parts = pubKeyString.split(" ")
        val type = parts[0]
        val base64 = parts[1]
        val decoded = Base64.getDecoder().decode(base64)
        val buffer = Buffer.PlainBuffer(decoded)
        buffer.readString() // Read algorithm name
        val pubKey = KeyType.fromString(type).readPubKeyFromBuffer(buffer)
        println("Algorithm: " + pubKey.algorithm)
    }
}
