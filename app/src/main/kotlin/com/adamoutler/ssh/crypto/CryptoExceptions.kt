package com.adamoutler.ssh.crypto

import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException

open class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause)
class SecureStorageUnavailableException(message: String, cause: Throwable? = null) : CryptoException(message, cause)
class KeyInvalidatedException(message: String, cause: Throwable? = null) : CryptoException(message, cause)
class AuthenticationRequiredException(message: String, cause: Throwable? = null) : CryptoException(message, cause)

fun Throwable.isCausedBy(target: Class<out Throwable>): Boolean {
    var currentCause: Throwable? = this
    while (currentCause != null) {
        if (target.isInstance(currentCause)) return true
        if (currentCause == currentCause.cause) break
        currentCause = currentCause.cause
    }
    return false
}

fun Throwable.handleKeystoreExceptions(defaultMessage: String): Nothing {
    if (this.isCausedBy(KeyPermanentlyInvalidatedException::class.java)) {
        throw KeyInvalidatedException("Keystore key permanently invalidated. Storage must be wiped.", this)
    }
    if (this.isCausedBy(UserNotAuthenticatedException::class.java)) {
        throw AuthenticationRequiredException("User authentication required to access Keystore.", this)
    }
    throw SecureStorageUnavailableException(defaultMessage, this)
}
