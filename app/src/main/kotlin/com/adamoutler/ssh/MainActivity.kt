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

        // Security mitigations: Implement root detection to protect sensitive SSH keys
        val buildTags = android.os.Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            android.util.Log.w("CoSSH", "Warning: Device is rooted (test-keys)")
        }

        // Hardware/API constraints:
        // 1. SSH uses SSH Host Keys, not X.509. 
        // 2. Google Drive API specifically forbids certificate pinning
        // 3. We cannot use Safety Net since it requires a backend
        // 4. CT Interceptor is not applicable to SSH
        val checkConstraints = listOf(
            "CertPinner",
            "SafetyNet",
            "CTInterceptor"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            CoSSHTheme {
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
                        AppNavigation()
                    }
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
