package com.adamoutler.ssh.security.automation

/**
 * Thread-safe bounded Least Recently Used (LRU) cache preventing nonce replay attacks
 * in external automation intents.
 *
 * Enforces a strict capacity boundary (default 1,000 entries) and time-to-live
 * (default 60 seconds). Entries older than [ttlMs] are purged automatically.
 */
class NonceReplayCache(
    private val maxCapacity: Int = DEFAULT_MAX_CAPACITY,
    private val ttlMs: Long = DEFAULT_TTL_MS,
) {
    companion object {
        const val DEFAULT_MAX_CAPACITY = 1000
        const val DEFAULT_TTL_MS = 60_000L
    }

    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Long>(maxCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > maxCapacity
        }
    }

    /**
     * Purges expired entries based on [nowMs].
     */
    private fun purgeExpiredLocked(nowMs: Long) {
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMs - entry.value > ttlMs) {
                iterator.remove()
            }
        }
    }

    /**
     * Attempts to record a nonce.
     *
     * @param nonce The unique nonce to verify and record.
     * @param nowMs The current timestamp in milliseconds.
     * @return `true` if the nonce was fresh and recorded successfully;
     *         `false` if the nonce was already observed within [ttlMs] (replay detected).
     */
    fun recordNonce(nonce: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(lock) {
            purgeExpiredLocked(nowMs)
            val seenAt = cache[nonce]
            if (seenAt != null && nowMs - seenAt <= ttlMs) {
                return false
            }
            cache[nonce] = nowMs
            return true
        }
    }

    /**
     * Checks if a nonce is currently known and not expired without updating its recorded timestamp.
     */
    fun contains(nonce: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(lock) {
            purgeExpiredLocked(nowMs)
            val seenAt = cache[nonce] ?: return false
            return nowMs - seenAt <= ttlMs
        }
    }

    /**
     * Checks if the nonce is a replay. If not replayed, it is recorded.
     *
     * @return `true` if replayed; `false` if freshly recorded.
     */
    fun isReplayed(nonce: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        return !recordNonce(nonce, nowMs)
    }

    /**
     * Returns the current number of active cached nonces.
     */
    fun size(): Int = synchronized(lock) { cache.size }

    /**
     * Clears all cached nonces.
     */
    fun clear(): Unit = synchronized(lock) { cache.clear() }
}
