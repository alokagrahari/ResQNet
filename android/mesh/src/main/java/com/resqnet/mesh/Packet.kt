package com.resqnet.mesh

/**
 * Represents a packet in the mesh network.
 *
 * Fields:
 * - messageId:       Unique ID for deduplication
 * - sourceNodeId:    Original sender (never changes)
 * - senderNodeId:    Last hop sender (updated on each forward)
 * - destinationNodeId: Target node ID, or "*" for broadcast
 * - payload:         Message content
 * - ttl:             Time-to-live (decremented on each forward)
 * - hopCount:        Number of hops so far (incremented on each forward)
 *
 * Designed as a Kotlin data class for easy copy/serialization.
 * BLE serialization can be added later without changing this model.
 */
data class Packet(
    val messageId: String,
    val sourceNodeId: String,
    val senderNodeId: String,
    val destinationNodeId: String,
    val payload: String,
    val ttl: Int,
    val hopCount: Int
) {
    /**
     * Creates a copy of this packet prepared for forwarding.
     * Decrements TTL, increments hop count, and updates senderNodeId.
     */
    fun prepareForForward(newSenderId: String): Packet {
        return copy(
            senderNodeId = newSenderId,
            ttl = ttl - 1,
            hopCount = hopCount + 1
        )
    }
}
