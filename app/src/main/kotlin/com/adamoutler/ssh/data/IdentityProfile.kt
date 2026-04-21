package com.adamoutler.ssh.data

import kotlinx.serialization.Serializable
import java.util.*

/**
 * IdentityProfile represents a reusable set of credentials (username, password, SSH keys).
 */
@Serializable
data class IdentityProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val username: String,
    @kotlinx.serialization.Transient
    var password: ByteArray? = null,
    @kotlinx.serialization.Transient
    var privateKey: ByteArray? = null,
    val publicKey: String? = null,
    val authType: AuthType = AuthType.PASSWORD
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentityProfile

        if (id != other.id) return false
        if (name != other.name) return false
        if (username != other.username) return false
        if (password != null) {
            if (other.password == null) return false
            if (!password.contentEquals(other.password)) return false
        } else if (other.password != null) return false
        if (privateKey != null) {
            if (other.privateKey == null) return false
            if (!privateKey.contentEquals(other.privateKey)) return false
        } else if (other.privateKey != null) return false
        if (publicKey != other.publicKey) return false
        if (authType != other.authType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + (password?.contentHashCode() ?: 0)
        result = 31 * result + (privateKey?.contentHashCode() ?: 0)
        result = 31 * result + (publicKey?.hashCode() ?: 0)
        result = 31 * result + authType.hashCode()
        return result
    }

    /**
     * Volatile state sanitization: Zero out sensitive bytes in memory.
     */
    fun clearSensitiveData() {
        password?.fill(0)
        privateKey?.fill(0)
    }
}
