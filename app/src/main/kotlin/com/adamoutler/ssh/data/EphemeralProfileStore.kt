package com.adamoutler.ssh.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe, volatile in-memory storage for ephemeral Quick Connect profiles.
 * Ephemeral profiles are never written to disk or EncryptedSharedPreferences.
 * Sensitive byte buffers (e.g. passwords) are wiped upon removal.
 */
object EphemeralProfileStore {
    private val profiles = ConcurrentHashMap<String, ConnectionProfile>()

    fun put(profile: ConnectionProfile) {
        profiles[profile.id] = profile
    }

    fun get(id: String): ConnectionProfile? = profiles[id]

    fun remove(id: String): ConnectionProfile? {
        val removed = profiles.remove(id)
        removed?.clearSensitiveData()
        return removed
    }

    fun clear() {
        profiles.values.forEach { it.clearSensitiveData() }
        profiles.clear()
    }

    val count: Int
        get() = profiles.size
}
