package com.adamoutler.ssh.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

object ByteArrayAsBase64Serializer : KSerializer<ByteArray> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ByteArrayAsBase64", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) {
        encoder.encodeString(Base64.getEncoder().encodeToString(value))
    }

    override fun deserialize(decoder: Decoder): ByteArray {
        return Base64.getDecoder().decode(decoder.decodeString())
    }
}

@Serializable
data class ConnectionProfile(
    val id: String,
    val nickname: String,
    val host: String,
    val port: Int = 22,
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    var sortOrder: Int = 0,
    @kotlinx.serialization.Transient
    var password: ByteArray? = null,
    val sshKeyPasswordReferenceId: String? = null,
    val identityId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConnectionProfile

        if (id != other.id) return false
        if (nickname != other.nickname) return false
        if (host != other.host) return false
        if (port != other.port) return false
        if (username != other.username) return false
        if (authType != other.authType) return false
        if (sortOrder != other.sortOrder) return false
        if (password != null) {
            if (other.password == null) return false
            if (!password.contentEquals(other.password)) return false
        } else if (other.password != null) return false
        if (sshKeyPasswordReferenceId != other.sshKeyPasswordReferenceId) return false
        if (identityId != other.identityId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + port
        result = 31 * result + username.hashCode()
        result = 31 * result + authType.hashCode()
        result = 31 * result + sortOrder
        result = 31 * result + (password?.contentHashCode() ?: 0)
        result = 31 * result + (sshKeyPasswordReferenceId?.hashCode() ?: 0)
        result = 31 * result + (identityId?.hashCode() ?: 0)
        return result
    }

    fun clearSensitiveData() {
        password?.fill(0)
    }
}

@Serializable
enum class AuthType {
    PASSWORD,
    KEY
}
