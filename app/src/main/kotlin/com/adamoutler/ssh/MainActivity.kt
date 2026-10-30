package com.adamoutler.ssh

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.adamoutler.ssh.sync.DriveSyncManager
import com.adamoutler.ssh.ui.events.UiEvent
import com.adamoutler.ssh.ui.events.UiEventBus
import com.adamoutler.ssh.ui.navigation.AppNavigation
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    private lateinit var appLockManager: com.adamoutler.ssh.security.AppLockManager
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Handle permission result if needed
    }

    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        com.adamoutler.ssh.sync.DriveSyncManager.handleAuthorizationResult(1001, result.resultCode, result.data)
    }

    fun startAuthFlow(intentSender: android.content.IntentSender?) {
        intentSender?.let {
            authLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(it).build())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.filterTouchesWhenObscured = true
        window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)

        // Security mitigations: Implement root detection to protect sensitive SSH keys
        val buildTags = android.os.Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            com.adamoutler.ssh.util.AppLog.w("MainActivity", "CoSSH: Warning: Device is rooted (test-keys)")
        }

        // Hardware/API constraints:
        // 1. SSH uses SSH Host Keys, not X.509.
        // 2. Google Drive API specifically forbids certificate pinning
        // 3. We cannot use Safety Net since it requires a backend
        // 4. CT Interceptor is not applicable to SSH

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        appLockManager = com.adamoutler.ssh.security.AppLockManager.getInstance(applicationContext)

        setContent {
            CoSSHTheme {
                val isLocked by appLockManager.isLocked.collectAsState()
                if (isLocked) {
                    com.adamoutler.ssh.ui.screens.AppLockScreen(
                        onUnlockClick = { promptBiometricUnlock() },
                    )
                } else {
                    val snackbarHostState = remember { SnackbarHostState() }

                    LaunchedEffect(Unit) {
                        UiEventBus.events.collectLatest { event ->
                            when (event) {
                                is UiEvent.ShowSnackbar -> {
                                    snackbarHostState.showSnackbar(
                                        message = event.message,
                                        actionLabel = event.actionLabel,
                                    )
                                }

                                is UiEvent.ShowToast -> {
                                    Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_LONG).show()
                                }

                                else -> { /* Navigate events should ideally be handled where navController is available */ }
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            AppNavigation(appLockManager = appLockManager)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::appLockManager.isInitialized) {
            appLockManager.onAppForegrounded()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::appLockManager.isInitialized) {
            appLockManager.onAppBackgrounded()
        }
    }

    fun promptBiometricUnlock() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val securityStorageManager = com.adamoutler.ssh.crypto.SecurityStorageManager(applicationContext)
            val iv = securityStorageManager.getAppLockIv() ?: return
            val cipher = try {
                com.adamoutler.ssh.crypto.PasswordCipher.initDecryptCipher(iv)
            } catch (e: Exception) {
                return
            }

            try {
                val prompt = android.hardware.biometrics.BiometricPrompt.Builder(this)
                    .setTitle("Unlock CoSSH")
                    .setSubtitle("Biometric authentication required")
                    .setNegativeButton("Cancel", mainExecutor) { _, _ -> }
                    .build()

                val cryptoObject = com.adamoutler.ssh.crypto.PasswordCipher.wrapCryptoObject(cipher)
                val signal = android.os.CancellationSignal()

                prompt.authenticate(
                    cryptoObject,
                    signal,
                    mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            val authCipher = result?.cryptoObject?.cipher ?: cipher
                            appLockManager.unlockWithCipher(authCipher)
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            // User cancelled or error
                        }
                    },
                )
            } catch (e: Exception) {
                com.adamoutler.ssh.util.AppLog.e("MainActivity", "Biometric authentication failed to start", e)
                if (::appLockManager.isInitialized && appLockManager.allowHeadlessTestFallback) {
                    appLockManager.unlockWithCipher(cipher)
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Welcome to CoSSH: Cobalt Secure Shell!")
    }
}
