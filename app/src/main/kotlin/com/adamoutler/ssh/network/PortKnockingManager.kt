package com.adamoutler.ssh.network

import com.adamoutler.ssh.data.KnockProtocol
import com.adamoutler.ssh.data.PortKnockStep
import com.adamoutler.ssh.data.PortKnockingConfig
import com.adamoutler.ssh.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Executes rate-limited, sequential TCP and UDP port knocking sequences.
 * Enforces bounded connect timeouts, inter-step delays, and a mandatory 5-second cooldown
 * per host to prevent network flooding and firewall rate-limiting lockouts.
 */
object PortKnockingManager {
    private const val TAG = "PortKnockingManager"
    const val COOLDOWN_PERIOD_MS = 5000L

    private val lastKnockTimestamps = ConcurrentHashMap<String, Long>()

    internal fun resetCooldownForTesting() {
        lastKnockTimestamps.clear()
    }

    /**
     * Checks whether a knock sequence to [host] is currently rate-limited by the cooldown window.
     */
    fun isRateLimited(host: String, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val lastTime = lastKnockTimestamps[host] ?: return false
        return (currentTimeMs - lastTime) < COOLDOWN_PERIOD_MS
    }

    /**
     * Executes the sequential port knocking sequence configured in [config] targeting [host].
     * @param onProgress Callback invoked before each knock step for UI/logging telemetry.
     * @return Number of knock steps successfully dispatched.
     */
    suspend fun executePortKnockSequence(
        host: String,
        config: PortKnockingConfig,
        onProgress: ((step: PortKnockStep, index: Int, total: Int) -> Unit)? = null,
        socketConnect: (host: String, port: Int, timeoutMs: Int) -> Unit = { h, p, t ->
            Socket().use { s ->
                s.connect(InetSocketAddress(h, p), t)
            }
        },
        udpSend: (host: String, port: Int) -> Unit = { h, p ->
            DatagramSocket().use { ds ->
                ds.soTimeout = 400
                val buf = byteArrayOf(0x00)
                val addr = InetAddress.getByName(h)
                val packet = DatagramPacket(buf, buf.size, addr, p)
                ds.send(packet)
            }
        },
    ): Int = withContext(Dispatchers.IO) {
        val steps = config.steps
        if (steps.isEmpty()) return@withContext 0

        val now = System.currentTimeMillis()
        if (isRateLimited(host, now)) {
            AppLog.w(TAG, "Port knocking for $host skipped: within 5000ms cooldown window")
            return@withContext 0
        }
        lastKnockTimestamps[host] = now

        AppLog.i(TAG, "Beginning port knocking sequence (${steps.size} steps) against $host")
        var executedCount = 0

        for ((index, step) in steps.withIndex()) {
            onProgress?.invoke(step, index + 1, steps.size)
            try {
                when (step.protocol) {
                    KnockProtocol.TCP -> {
                        try {
                            socketConnect(host, step.port, config.probeTimeoutMs)
                        } catch (_: IOException) {
                            // Expected behavior: firewall/closed port refuses or drops connection
                        }
                    }
                    KnockProtocol.UDP -> {
                        try {
                            udpSend(host, step.port)
                        } catch (_: IOException) {
                            // Expected behavior for stealth UDP ports
                        }
                    }
                }
                executedCount++
            } catch (e: Exception) {
                AppLog.w(TAG, "Knock step $index (${step.protocol} ${step.port}) caught unexpected error: ${e.message}")
            }

            // Inter-step delay between sequential knocks
            if (index < steps.size - 1 && config.stepDelayMs > 0) {
                delay(config.stepDelayMs)
            }
        }

        // Post-knock delay for firewall state table propagation
        if (config.postKnockDelayMs > 0) {
            delay(config.postKnockDelayMs)
        }

        AppLog.i(TAG, "Completed port knocking sequence against $host ($executedCount/${steps.size} steps)")
        executedCount
    }
}
