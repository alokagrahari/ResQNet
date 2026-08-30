package com.resqnet.mesh

/**
 * Simple peer abstraction for the mesh network.
 * Represents a reachable node without any BLE dependency.
 *
 * The BLE engineer can extend or wrap this with BLE-specific
 * connection information later.
 */
data class Peer(
    val nodeId: String
)
