package com.adamoutler.ssh.security

import com.adamoutler.ssh.CoSshApplication
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.security.Security
import javax.net.ssl.SSLContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderPrecedenceTest {

    @Test
    fun testProviderPrecedence_preservesPrimaryTlsProviderAtPosition1() {
        val app = RuntimeEnvironment.getApplication() as CoSshApplication
        assertNotNull("CoSshApplication must be initialized", app)

        val providers = Security.getProviders()
        assertTrue("Security providers must be registered", providers.isNotEmpty())

        val firstProvider = providers[0]
        assertFalse(
            "Primary security provider at position 1 must NOT be BouncyCastleProvider (must preserve platform TLS)",
            firstProvider is BouncyCastleProvider,
        )

        val bcProvider = Security.getProvider("BC")
        assertNotNull("BouncyCastleProvider must be registered", bcProvider)
        assertTrue("BouncyCastleProvider must be an instance of BouncyCastleProvider", bcProvider is BouncyCastleProvider)

        if (providers.size >= 2) {
            val secondProvider = providers[1]
            assertTrue(
                "BouncyCastleProvider must be positioned at index 1 (position 2)",
                secondProvider is BouncyCastleProvider,
            )
        }
    }

    @Test
    fun testTlsContext_doesNotDefaultToBouncyCastle() {
        val tlsContext = SSLContext.getInstance("TLS")
        tlsContext.init(null, null, null)

        val provider = tlsContext.provider
        assertFalse(
            "Default TLS SSLContext provider must NOT be BouncyCastle (must be platform native/Conscrypt)",
            provider is BouncyCastleProvider,
        )
    }
}
