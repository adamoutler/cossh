package com.adamoutler.ssh

import android.app.Application
import com.adamoutler.ssh.security.SecureCrashHandler

class CoSshApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Enforce secure crash logging
        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler !is SecureCrashHandler) {
            Thread.setDefaultUncaughtExceptionHandler(
                SecureCrashHandler(applicationContext, currentHandler)
            )
        }
    }
}
