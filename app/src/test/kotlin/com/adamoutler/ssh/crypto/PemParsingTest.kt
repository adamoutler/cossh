package com.adamoutler.ssh.crypto

import androidx.test.core.app.ApplicationProvider
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.network.SshConnectionManager
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringWriter
import java.security.Security

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PemParsingTest {
    @Test
    fun testParsePem() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        val kp = SSHKeyGenerator.generateRSAKeyPair()
        val sw = StringWriter()
        val pw = JcaPEMWriter(sw)
        pw.writeObject(kp.private)
        pw.close()
        val pem = sw.toString()

        val identity = IdentityProfile(name = "test", username = "test", privateKey = pem.toByteArray())
        val manager = com.adamoutler.ssh.network.SshHandshakeCoordinator(context = ApplicationProvider.getApplicationContext())
        try {
            val parsedKp = manager.loadKeyPairFromIdentity(identity)
            assertNotNull(parsedKp)
            assertNotNull(parsedKp.private)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    @Test
    fun testParseOpenSshEd25519() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        val pem = """-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
QyNTUxOQAAACCnNxk0iLUCuAaRrTYcBLQu8dwMKeavOHjLnZ/Qu2OfNAAAAJizHwIXsx8C
FwAAAAtzc2gtZWQyNTUxOQAAACCnNxk0iLUCuAaRrTYcBLQu8dwMKeavOHjLnZ/Qu2OfNA
AAAEBKiLMDOccl0BWyiGJ1QQUyW0PznmjKtml2gwOymx/MBKc3GTSItQK4BpGtNhwEtC7x
3Awp5q84eMudn9C7Y580AAAAE2FkYW1vdXRsZXJASExBQi1BMjUBAg==
-----END OPENSSH PRIVATE KEY-----"""
        val identity = IdentityProfile(name = "test", username = "test", privateKey = pem.toByteArray())
        val manager = com.adamoutler.ssh.network.SshHandshakeCoordinator(context = ApplicationProvider.getApplicationContext())
        val parsedKp = manager.loadKeyPairFromIdentity(identity)
        assertNotNull(parsedKp)
        assertNotNull(parsedKp.private)
    }
}
