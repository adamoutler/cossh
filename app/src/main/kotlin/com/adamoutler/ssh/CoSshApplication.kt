package com.adamoutler.ssh

import android.app.Application
import com.adamoutler.ssh.security.SecureCrashHandler
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

class CoSshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Fix for SSHJ "no such algorithm: X25519 for provider BC"
        if (Security.getProvider("BC") != null) {
            Security.removeProvider("BC")
        }
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        
        // Enforce secure crash logging
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler !is SecureCrashHandler) {
            Thread.setDefaultUncaughtExceptionHandler(
                SecureCrashHandler(applicationContext, currentHandler)
            )
        }
    }
}
