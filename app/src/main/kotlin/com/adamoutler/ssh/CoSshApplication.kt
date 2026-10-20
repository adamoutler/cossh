package com.adamoutler.ssh

import android.app.Application
import com.adamoutler.ssh.security.SecureCrashHandler
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class CoSshApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Ensure BouncyCastle is registered without usurping the primary TLS provider at position 1
        if (Security.getProvider("BC") != null) {
            Security.removeProvider("BC")
        }
        Security.insertProviderAt(BouncyCastleProvider(), 2)

        // Enforce secure crash logging
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler !is SecureCrashHandler) {
            Thread.setDefaultUncaughtExceptionHandler(
                SecureCrashHandler(applicationContext, currentHandler),
            )
        }
    }
}
