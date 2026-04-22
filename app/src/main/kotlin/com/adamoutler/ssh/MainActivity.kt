package com.adamoutler.ssh

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import com.adamoutler.ssh.ui.events.UiEvent
import com.adamoutler.ssh.ui.events.UiEventBus
import com.adamoutler.ssh.ui.theme.CoSSHTheme
import com.adamoutler.ssh.ui.navigation.AppNavigation
import kotlinx.coroutines.flow.collectLatest
import android.content.Intent
import com.adamoutler.ssh.network.SshService

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.hasExtra(SshService.EXTRA_PROFILE_ID) == true) {
            val profileId = intent.getStringExtra(SshService.EXTRA_PROFILE_ID)
            val sessionId = intent.getStringExtra(SshService.EXTRA_SESSION_ID)
            if (profileId != null && sessionId != null) {
                UiEventBus.publish(UiEvent.Navigate("terminal?profileId=$profileId&sessionId=$sessionId"))
            } else if (profileId != null) {
                UiEventBus.publish(UiEvent.Navigate("terminal?profileId=$profileId"))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CoSSHTheme {
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(Unit) {
                    UiEventBus.events.collectLatest { event ->
                        when (event) {
                            is UiEvent.ShowSnackbar -> {
                                snackbarHostState.showSnackbar(
                                    message = event.message,
                                    actionLabel = event.actionLabel
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
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
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
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Welcome to CoSSH: Cobalt Secure Shell!")
    }
}
