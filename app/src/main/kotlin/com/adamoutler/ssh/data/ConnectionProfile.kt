package com.adamoutler.ssh.data

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionProfile(
    val id: String,
    val nickname: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType,
    val password: String? = null,
    val sshKeyPasswordReferenceId: String? = null
)

@Serializable
enum class AuthType {
    PASSWORD,
    KEY
}
