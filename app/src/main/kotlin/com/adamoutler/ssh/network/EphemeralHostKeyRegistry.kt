package com.adamoutler.ssh.network

import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, volatile in-memory registry for session-only accepted host keys.
 * Used during Quick Connect or when a user chooses "Trust for this session only".
 * Keys registered here are never committed to the persistent `ssh_known_hosts` file,
 * preventing orphan entry pollution from dynamic DHCP/cloud instances.
 */
object EphemeralHostKeyRegistry {
    private val sessionKeys = ConcurrentHashMap<String, PublicKey>()

    fun record(formattedHost: String, key: PublicKey) {
        sessionKeys[formattedHost] = key
    }

    fun verify(formattedHost: String, key: PublicKey): Boolean {
        val trusted = sessionKeys[formattedHost] ?: return false
        return trusted == key
    }

    fun contains(formattedHost: String): Boolean = sessionKeys.containsKey(formattedHost)

    fun clear() {
        sessionKeys.clear()
    }

    val count: Int
        get() = sessionKeys.size
}
