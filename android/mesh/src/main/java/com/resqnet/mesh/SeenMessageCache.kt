package com.resqnet.mesh

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe cache for tracking seen message IDs.
 * Used for deduplication — the same message is never processed
 * twice by the same node.
 *
 * Uses [ConcurrentHashMap]-backed set for thread safety.
 * Designed so an expiration/cleanup mechanism can be added later
 * (e.g., timestamped entries with periodic eviction).
 */
class SeenMessageCache {

    private val seen: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Check if a message has already been seen.
     */
    fun hasSeen(messageId: String): Boolean = seen.contains(messageId)

    /**
     * Mark a message as seen.
     */
    fun markSeen(messageId: String) {
        seen.add(messageId)
    }

    /**
     * Clear all seen messages. Useful for testing.
     */
    fun clear() {
        seen.clear()
    }

    /**
     * Number of seen messages. Useful for monitoring/testing.
     */
    fun size(): Int = seen.size
}
