package com.resqnet.mesh

import java.util.UUID

/**
 * Represents the identity of a node in the mesh network.
 * Uses UUID-based IDs — does NOT use BLE device addresses.
 *
 * Each node in the mesh gets a unique identity that persists
 * for the lifetime of the application instance.
 */
class NodeIdentity(
    val nodeId: String = generateNodeId()
) {
    companion object {
        /**
         * Generate a new unique node ID.
         */
        fun generateNodeId(): String = UUID.randomUUID().toString()
    }

    override fun toString(): String = "Node($nodeId)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NodeIdentity) return false
        return nodeId == other.nodeId
    }

    override fun hashCode(): Int = nodeId.hashCode()
}
