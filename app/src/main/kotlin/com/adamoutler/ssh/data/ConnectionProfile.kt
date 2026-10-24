package com.adamoutler.ssh.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
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

    override fun deserialize(decoder: Decoder): ByteArray = Base64.getDecoder().decode(decoder.decodeString())
}

@Serializable
enum class PortForwardType {
    @SerialName("LOCAL")
    LOCAL,

    @SerialName("REMOTE")
    REMOTE,

    @SerialName("DYNAMIC")
    DYNAMIC,
}

@Serializable
data class PortForwardConfig(
    val type: PortForwardType,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int,
)

@Serializable
enum class Protocol {
    @SerialName("SSH")
    SSH,

    @SerialName("TELNET")
    TELNET,
}

@Serializable
enum class KnockProtocol {
    @SerialName("TCP")
    TCP,

    @SerialName("UDP")
    UDP,
}

@Serializable
data class PortKnockStep(
    val port: Int,
    val protocol: KnockProtocol = KnockProtocol.TCP,
)

@Serializable
data class PortKnockingConfig(
    val steps: List<PortKnockStep> = emptyList(),
    val stepDelayMs: Long = 150L,
    val postKnockDelayMs: Long = 1000L,
    val probeTimeoutMs: Int = 400,
)

@Serializable
data class WolConfig(
    val macAddress: String,
    val broadcastIp: String = "255.255.255.255",
    val port: Int = 9,
    val postWakeDelayMs: Long = 2000L,
)

@Serializable
enum class KeepScreenOnMode {
    @SerialName("SYSTEM_DEFAULT")
    SYSTEM_DEFAULT,

    @SerialName("SMART_AWAKE")
    SMART_AWAKE,

    @SerialName("ALWAYS_ON")
    ALWAYS_ON,
}

fun shouldKeepScreenOn(
    mode: KeepScreenOnMode,
    isProximityNear: Boolean,
    isDataFlowing: Boolean,
): Boolean = when (mode) {
    KeepScreenOnMode.SYSTEM_DEFAULT -> false
    KeepScreenOnMode.ALWAYS_ON -> !isProximityNear
    KeepScreenOnMode.SMART_AWAKE -> !isProximityNear && isDataFlowing
}

@Serializable
data class ConnectionProfile(
    val id: String,
    val nickname: String,
    val host: String,
    val port: Int = 22,
    val protocol: Protocol = Protocol.SSH,
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    var sortOrder: Int = 0,
    @kotlinx.serialization.Transient
    var password: ByteArray? = null,
    val sshKeyPasswordReferenceId: String? = null,
    val identityId: String? = null,
    val fontSize: Int? = null,
    val folderId: String? = null,
    val envVars: Map<String, String> = emptyMap(),
    val portForwards: List<PortForwardConfig> = emptyList(),
    val commandSnippets: List<CommandSnippet> = emptyList(),
    val initialDirectory: String? = null,
    val terminalInputState: Int = 0,
    val keepScreenOnMode: KeepScreenOnMode = KeepScreenOnMode.SYSTEM_DEFAULT,
    val useLocalDns: Boolean = false,
    val colorTheme: String? = null,
    val fontChoice: String? = null,
    val keepAliveIntervalSeconds: Int = 0,
    val autoReconnect: Boolean = false,
    val maxReconnectAttempts: Int = 5,
    val jumpHostProfileId: String? = null,
    val isEphemeral: Boolean = false,
    val cursorStyle: String? = null,
    val cursorBlink: Boolean? = null,
    val wolConfig: WolConfig? = null,
    val portKnockingConfig: PortKnockingConfig? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConnectionProfile

        if (id != other.id) return false
        if (nickname != other.nickname) return false
        if (host != other.host) return false
        if (port != other.port) return false
        if (protocol != other.protocol) return false
        if (username != other.username) return false
        if (authType != other.authType) return false
        if (sortOrder != other.sortOrder) return false
        if (password != null) {
            if (other.password == null) return false
            if (!password.contentEquals(other.password)) return false
        } else if (other.password != null) {
            return false
        }
        if (sshKeyPasswordReferenceId != other.sshKeyPasswordReferenceId) return false
        if (identityId != other.identityId) return false
        if (fontSize != other.fontSize) return false
        if (folderId != other.folderId) return false
        if (envVars != other.envVars) return false
        if (portForwards != other.portForwards) return false
        if (initialDirectory != other.initialDirectory) return false
        if (terminalInputState != other.terminalInputState) return false
        if (keepScreenOnMode != other.keepScreenOnMode) return false
        if (useLocalDns != other.useLocalDns) return false
        if (commandSnippets != other.commandSnippets) return false
        if (colorTheme != other.colorTheme) return false
        if (fontChoice != other.fontChoice) return false
        if (keepAliveIntervalSeconds != other.keepAliveIntervalSeconds) return false
        if (autoReconnect != other.autoReconnect) return false
        if (maxReconnectAttempts != other.maxReconnectAttempts) return false
        if (jumpHostProfileId != other.jumpHostProfileId) return false
        if (isEphemeral != other.isEphemeral) return false
        if (cursorStyle != other.cursorStyle) return false
        if (cursorBlink != other.cursorBlink) return false
        if (wolConfig != other.wolConfig) return false
        if (portKnockingConfig != other.portKnockingConfig) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + host.hashCode()
        result = 31 * result + port
        result = 31 * result + protocol.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + authType.hashCode()
        result = 31 * result + sortOrder
        result = 31 * result + (password?.contentHashCode() ?: 0)
        result = 31 * result + (sshKeyPasswordReferenceId?.hashCode() ?: 0)
        result = 31 * result + (identityId?.hashCode() ?: 0)
        result = 31 * result + (fontSize ?: 0)
        result = 31 * result + (folderId?.hashCode() ?: 0)
        result = 31 * result + envVars.hashCode()
        result = 31 * result + portForwards.hashCode()
        result = 31 * result + (initialDirectory?.hashCode() ?: 0)
        result = 31 * result + terminalInputState
        result = 31 * result + keepScreenOnMode.hashCode()
        result = 31 * result + useLocalDns.hashCode()
        result = 31 * result + commandSnippets.hashCode()
        result = 31 * result + (colorTheme?.hashCode() ?: 0)
        result = 31 * result + (fontChoice?.hashCode() ?: 0)
        result = 31 * result + keepAliveIntervalSeconds
        result = 31 * result + autoReconnect.hashCode()
        result = 31 * result + maxReconnectAttempts
        result = 31 * result + (jumpHostProfileId?.hashCode() ?: 0)
        result = 31 * result + isEphemeral.hashCode()
        result = 31 * result + (cursorStyle?.hashCode() ?: 0)
        result = 31 * result + (cursorBlink?.hashCode() ?: 0)
        result = 31 * result + (wolConfig?.hashCode() ?: 0)
        result = 31 * result + (portKnockingConfig?.hashCode() ?: 0)
        return result
    }

    fun clearSensitiveData() {
        password?.fill(0)
    }

    val effectiveKeepAliveInterval: Int
        get() = sanitizeKeepAliveInterval(keepAliveIntervalSeconds)

    companion object {
        const val MIN_ACTIVE_KEEPALIVE_SECONDS = 10

        fun sanitizeKeepAliveInterval(seconds: Int): Int {
            return when {
                seconds <= 0 -> 0
                seconds < MIN_ACTIVE_KEEPALIVE_SECONDS -> MIN_ACTIVE_KEEPALIVE_SECONDS
                else -> seconds
            }
        }
    }
}

@Serializable
enum class AuthType {
    @SerialName("PASSWORD")
    PASSWORD,

    @SerialName("KEY")
    KEY,
}
