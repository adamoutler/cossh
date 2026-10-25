package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.ConnectionProfile
import com.adamoutler.ssh.data.IdentityProfile
import com.adamoutler.ssh.util.AppLog
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.userauth.UserAuthException

/**
 * Handles RFC 4252 §5.1 SSH_AUTH_PARTIAL multi-factor continuation on the live transport.
 * When the server accepts a credential (such as a public key) but requires subsequent factors,
 * it inspects allowedMethods and orchestrates the secondary factor without disconnecting.
 */
open class MultiFactorContinuationEngine(
    private val maxRounds: Int = 5,
) {
    open fun continueIfPartialSuccess(
        client: SSHClient,
        profile: ConnectionProfile,
        identity: IdentityProfile? = null,
        sessionId: String? = null,
    ): Boolean {
        val userAuth = client.userAuth ?: return false
        if (!userAuth.hadPartialSuccess()) {
            return false
        }

        val allowedMethods = userAuth.allowedMethods ?: emptyList()
        AppLog.i(
            "MultiFactorContinuationEngine",
            "[CoSSH MFA: SSH_AUTH_PARTIAL detected. Allowed secondary factors: $allowedMethods]"
        )

        sessionId?.let {
            ConnectionLogRepository.log(
                sessionId = it,
                milestone = ConnectionMilestone.MFA,
                message = "Partial authentication success (SSH_AUTH_PARTIAL). Allowed secondary factors: ${allowedMethods.joinToString(", ")}",
                level = ConnectionLogLevel.WARN,
            )
        }

        val secondaryAuthenticators = mutableListOf<SshAuthenticator>()

        // 1. If Keyboard-Interactive (PAM/TOTP/2FA) is allowed, prioritize it for MFA
        if (allowedMethods.any { it.equals("keyboard-interactive", ignoreCase = true) }) {
            secondaryAuthenticators.add(
                KeyboardInteractiveAuthenticator(
                    maxRounds = maxRounds,
                    isMfa = true,
                    stepDescription = "[Step 2/2: Second Factor Required]",
                )
            )
        }

        // 2. If Password authentication is allowed
        if (allowedMethods.any { it.equals("password", ignoreCase = true) }) {
            val candidatePassword = identity?.password ?: profile.password
            secondaryAuthenticators.add(
                PasswordAuthenticator(
                    identityPassword = candidatePassword,
                    isMfa = true,
                    stepDescription = "[Step 2/2: Second Factor Required]",
                )
            )
        }

        // 3. Fallback if server sent partial_success=true but empty allowedMethods list
        if (secondaryAuthenticators.isEmpty()) {
            secondaryAuthenticators.add(
                PasswordAuthenticator(
                    identityPassword = identity?.password ?: profile.password,
                    isMfa = true,
                    stepDescription = "[Step 2/2: Second Factor Required]",
                )
            )
            secondaryAuthenticators.add(
                KeyboardInteractiveAuthenticator(
                    maxRounds = maxRounds,
                    isMfa = true,
                    stepDescription = "[Step 2/2: Second Factor Required]",
                )
            )
        }

        var lastException: Exception? = null
        for (secAuth in secondaryAuthenticators) {
            try {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Authentication interrupted during secondary factor")
                }
                secAuth.authenticate(client, profile)
                if (client.isAuthenticated) {
                    AppLog.i("MultiFactorContinuationEngine", "Secondary MFA succeeded via ${secAuth::class.java.simpleName}")
                    sessionId?.let {
                        ConnectionLogRepository.log(
                            sessionId = it,
                            milestone = ConnectionMilestone.AUTH,
                            message = "Secondary factor verified successfully via ${secAuth::class.java.simpleName}",
                        )
                    }
                    return true
                }
            } catch (e: Exception) {
                lastException = e
                AppLog.w("MultiFactorContinuationEngine", "Secondary MFA attempt failed with ${secAuth::class.java.simpleName}: ${e.message}")
            }
        }

        throw UserAuthException("Multi-factor secondary authentication failed", lastException)
    }
}
