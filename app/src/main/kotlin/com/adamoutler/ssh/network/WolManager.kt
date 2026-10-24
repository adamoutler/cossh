package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.WolConfig
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Hardened Wake-on-LAN (WOL) magic packet constructor and UDP broadcast dispatcher.
 * Validates MAC addresses, formats standard 102-byte magic packets (6 x 0xFF + 16 x MAC),
 * and safely transmits over bounded DatagramSockets with configurable post-wake delays.
 */
object WolManager {
    private const val TAG = "WolManager"
    val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")

    /**
     * Validates whether the given string conforms to standard 6-octet colon or hyphen separated MAC format.
     */
    fun isValidMacAddress(mac: String): Boolean = MAC_REGEX.matches(mac.trim())

    /**
     * Parses a validated MAC address string into 6 raw bytes.
     * Throws [IllegalArgumentException] if format is invalid.
     */
    fun parseMacBytes(mac: String): ByteArray {
        val trimmed = mac.trim()
        require(isValidMacAddress(trimmed)) { "Invalid MAC address format: '$mac'" }
        val parts = if (trimmed.contains(":")) trimmed.split(":") else trimmed.split("-")
        require(parts.size == 6) { "MAC address must contain exactly 6 octets" }
        return ByteArray(6) { i ->
            parts[i].toInt(16).toByte()
        }
    }

    /**
     * Constructs the standard 102-byte Wake-on-LAN magic packet:
     * - 6 bytes of 0xFF
     * - 16 consecutive repetitions of the target 6-byte MAC address
     */
    fun buildMagicPacket(macBytes: ByteArray): ByteArray {
        require(macBytes.size == 6) { "MAC bytes must be exactly 6 bytes in length" }
        val packet = ByteArray(102)
        // Header: 6 bytes of 0xFF
        for (i in 0 until 6) {
            packet[i] = 0xFF.toByte()
        }
        // Body: 16 repetitions of the 6-byte target MAC
        for (rep in 0 until 16) {
            val offset = 6 + (rep * 6)
            System.arraycopy(macBytes, 0, packet, offset, 6)
        }
        return packet
    }

    /**
     * Broadcasts the Wake-on-LAN magic packet as configured in [WolConfig].
     * Executes non-blocking network IO on [Dispatchers.IO] and safely closes the socket.
     */
    suspend fun sendMagicPacket(
        config: WolConfig,
        socketFactory: () -> DatagramSocket = { DatagramSocket() },
    ): Boolean = withContext(Dispatchers.IO) {
        val macBytes = parseMacBytes(config.macAddress)
        val payload = buildMagicPacket(macBytes)
        var socket: DatagramSocket? = null
        try {
            socket = socketFactory()
            socket.broadcast = true
            socket.soTimeout = 2000
            val targetAddress = InetAddress.getByName(config.broadcastIp.ifBlank { "255.255.255.255" })
            val datagram = DatagramPacket(payload, payload.size, targetAddress, config.port)
            socket.send(datagram)
            AppLog.i(TAG, "Successfully broadcast WOL magic packet to ${config.broadcastIp}:${config.port} for MAC ${config.macAddress}")
            if (config.postWakeDelayMs > 0) {
                delay(config.postWakeDelayMs)
            }
            true
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to broadcast WOL magic packet for ${config.macAddress}: ${e.message}", e)
            throw e
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }
}
