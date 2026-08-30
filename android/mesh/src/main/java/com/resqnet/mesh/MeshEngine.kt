package com.resqnet.mesh

import com.resqnet.mesh.transport.Transport

/**
 * Core mesh networking engine.
 *
 * Implements the full receive pipeline:
 * ```
 * RECEIVE → VALIDATE → DEDUPLICATE → TTL CHECK → UPDATE TTL/HOP → FORWARD
 * ```
 *
 * Completely independent from BLE. Uses [Transport] interface for communication.
 * The BLE engineer can plug in BLETransport later without changing this class.
 *
 * @param nodeId      This node's unique identifier
 * @param transport   Transport layer for sending packets and discovering peers
 * @param defaultTtl  Default TTL for new packets (configurable)
 */
class MeshEngine(
    val nodeId: String,
    private val transport: Transport,
    private val defaultTtl: Int = DEFAULT_TTL
) {
    companion object {
        const val DEFAULT_TTL = 5
    }

    private val seenCache = SeenMessageCache()

    /**
     * Callback invoked when a packet arrives at its destination (this node).
     * The packet contains the values as received (before any forward preparation).
     */
    var onMessageReceived: ((Packet) -> Unit)? = null

    /**
     * Callback invoked when a packet is forwarded to peers.
     * Useful for testing and monitoring.
     */
    var onMessageForwarded: ((Packet) -> Unit)? = null

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Create a new packet originating from this node.
     */
    fun createPacket(destinationNodeId: String, payload: String): Packet {
        return Packet(
            messageId = MessageIdGenerator.generate(),
            sourceNodeId = nodeId,
            senderNodeId = nodeId,
            destinationNodeId = destinationNodeId,
            payload = payload,
            ttl = defaultTtl,
            hopCount = 0
        )
    }

    /**
     * Create and send a new message into the mesh network.
     * Returns the created packet.
     */
    fun sendMessage(destinationNodeId: String, payload: String): Packet {
        val packet = createPacket(destinationNodeId, payload)
        MeshLogger.send(packet.messageId)

        // Mark as seen so we don't process our own message if it bounces back
        seenCache.markSeen(packet.messageId)

        // Flood to all peers
        val peers = transport.getPeers()
        for (peer in peers) {
            MeshLogger.forward(packet.messageId, nodeId, peer.nodeId)
            transport.send(peer, packet)
        }

        return packet
    }

    /**
     * Main entry point: receive a packet from the network.
     * VALIDATE → DEDUPLICATE → DELIVER (if dest is this node or `*`) →
     * stop if unicast dest reached → TTL CHECK → FORWARD
     */
    fun receivePacket(packet: Packet) {
        MeshLogger.receive(packet.messageId, packet.senderNodeId)

        // Step 1: Validate
        val validation = PacketValidator.validate(packet)
        if (!validation.isValid) {
            MeshLogger.validateFail(packet.messageId, validation.reason)
            MeshLogger.drop(packet.messageId, "validation failed: ${validation.reason}")
            return
        }
        MeshLogger.validatePass(packet.messageId)

        // Step 2: Deduplicate
        if (isDuplicate(packet)) {
            MeshLogger.dedupDuplicate(packet.messageId)
            MeshLogger.drop(packet.messageId, "duplicate")
            return
        }
        MeshLogger.dedupNew(packet.messageId)

        // Mark as seen
        seenCache.markSeen(packet.messageId)

        // Step 3: Deliver only if this node is the destination (or broadcast).
        val isBroadcast = packet.destinationNodeId == "*"
        val isForThisNode = packet.destinationNodeId == nodeId
        val destDecision = when {
            isForThisNode -> "DELIVER"
            isBroadcast -> "DELIVER+FORWARD"
            else -> "FORWARD"
        }
        MeshLogger.destination(packet.messageId, packet.destinationNodeId, destDecision)

        if (isForThisNode || isBroadcast) {
            MeshLogger.delivered(packet.messageId, nodeId)
            onMessageReceived?.invoke(packet)
        }

        // Unicast that has reached its destination must not be forwarded.
        if (isForThisNode && !isBroadcast) {
            return
        }

        // Step 4: Check TTL for forwarding
        if (!shouldForward(packet)) {
            MeshLogger.ttlExpired(packet.messageId)
            MeshLogger.drop(packet.messageId, "TTL expired")
            return
        }

        // Step 5: Prepare for forward (update TTL and hop count)
        val forwardedPacket = prepareForForward(packet)
        MeshLogger.ttlUpdate(packet.messageId, packet.ttl, forwardedPacket.ttl)
        MeshLogger.hopUpdate(packet.messageId, packet.hopCount, forwardedPacket.hopCount)

        // Step 6: Flood to connected peers except the previous hop
        forwardPacket(forwardedPacket, packet.senderNodeId)
    }

    /**
     * Get the number of messages in the seen cache (for monitoring/testing).
     */
    fun getSeenCacheSize(): Int = seenCache.size()

    /**
     * Clear the seen message cache (for testing).
     */
    fun clearSeenCache() = seenCache.clear()

    // =========================================================================
    // Pipeline internals
    // =========================================================================

    /**
     * Check if we've already seen this message.
     */
    private fun isDuplicate(packet: Packet): Boolean {
        return seenCache.hasSeen(packet.messageId)
    }

    /**
     * Check if a packet should be forwarded based on TTL.
     * After decrementing, TTL must remain > 0.
     */
    private fun shouldForward(packet: Packet): Boolean {
        return packet.ttl > 1
    }

    /**
     * Prepare a packet for forwarding: decrement TTL, increment hop count, update sender.
     */
    private fun prepareForForward(packet: Packet): Packet {
        return packet.prepareForForward(nodeId)
    }

    /**
     * Flood to currently connected peers.
     *
     * Used for broadcast (`*`) and for unicast still in transit. The destination
     * does not need to be a direct neighbor. The only node excluded is the
     * immediate previous hop ([originalSenderId]), plus this node if it
     * appears in the peer list.
     */
    private fun forwardPacket(packet: Packet, originalSenderId: String) {
        val eligiblePeers = transport.getPeers().filter { peer ->
            peer.nodeId != originalSenderId && peer.nodeId != nodeId
        }

        if (eligiblePeers.isEmpty()) {
            MeshLogger.drop(packet.messageId, "no eligible peers to forward to")
            return
        }

        for (peer in eligiblePeers) {
            MeshLogger.forward(packet.messageId, nodeId, peer.nodeId)
            transport.send(peer, packet)
        }

        onMessageForwarded?.invoke(packet)
    }
}
