package com.adamoutler.ssh.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

enum class ConnectionLogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

enum class ConnectionMilestone(val tag: String) {
    DNS("DNS"),
    TCP("TCP"),
    SSH("SSH"),
    KEX("KEX"),
    HOSTKEY("HOSTKEY"),
    AUTH("AUTH"),
    MFA("MFA"),
    CHANNEL("CHANNEL"),
    READY("READY"),
    ERROR("ERROR");

    override fun toString(): String = tag
}

data class ConnectionLogEntry(
    val id: Long = System.nanoTime(),
    val timestampMs: Long = System.currentTimeMillis(),
    val level: ConnectionLogLevel = ConnectionLogLevel.INFO,
    val milestone: ConnectionMilestone,
    val message: String,
) {
    fun formattedTimestamp(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    fun toFormattedLine(): String =
        "${formattedTimestamp()} [${milestone.tag}] $message"
}

object ConnectionLogRepository {
    const val MAX_ENTRIES_PER_SESSION = 150

    private val sessionBuffers = ConcurrentHashMap<String, ConcurrentLinkedDeque<ConnectionLogEntry>>()
    private val sessionFlows = ConcurrentHashMap<String, MutableStateFlow<List<ConnectionLogEntry>>>()

    fun getLogFlow(sessionId: String): StateFlow<List<ConnectionLogEntry>> =
        sessionFlows.computeIfAbsent(sessionId) {
            MutableStateFlow(emptyList())
        }.asStateFlow()

    fun log(
        sessionId: String,
        milestone: ConnectionMilestone,
        message: String,
        level: ConnectionLogLevel = ConnectionLogLevel.INFO,
    ) {
        val deque = sessionBuffers.computeIfAbsent(sessionId) {
            ConcurrentLinkedDeque()
        }
        val entry = ConnectionLogEntry(
            milestone = milestone,
            message = message,
            level = level,
        )

        synchronized(deque) {
            deque.addLast(entry)
            while (deque.size > MAX_ENTRIES_PER_SESSION) {
                deque.pollFirst()
            }
            val flow = sessionFlows.computeIfAbsent(sessionId) {
                MutableStateFlow(emptyList())
            }
            flow.value = deque.toList()
        }
    }

    fun getFormattedTranscript(sessionId: String): String {
        val deque = sessionBuffers[sessionId] ?: return ""
        synchronized(deque) {
            return deque.joinToString(separator = "\n") { it.toFormattedLine() }
        }
    }

    fun clearSessionLogs(sessionId: String) {
        sessionBuffers.remove(sessionId)
        sessionFlows[sessionId]?.value = emptyList()
        sessionFlows.remove(sessionId)
    }

    fun clearAll() {
        sessionBuffers.clear()
        sessionFlows.forEach { (_, flow) -> flow.value = emptyList() }
        sessionFlows.clear()
    }
}
