package com.resqnet.mesh.transport

import com.resqnet.mesh.MeshEngine
import com.resqnet.mesh.Packet
import com.resqnet.mesh.Peer

/**
 * Simulated mesh network for testing without BLE.
 *
 * Manages a set of [MeshEngine] instances and a configurable
 * topology (who can reach whom). When a packet is "sent",
 * it is delivered directly to the target engine's [MeshEngine.receivePacket].
 *
 * Usage:
 * ```
 * val network = MockNetwork()
 * val engineA = network.createTransportAndEngine("NODE_A")
 * val engineB = network.createTransportAndEngine("NODE_B")
 * network.addLink("NODE_A", "NODE_B")
 * engineA.sendMessage("NODE_B", "Hello!")
 * ```
 */
class MockNetwork {

    /** Registered engines by nodeId. */
    private val engines = mutableMapOf<String, MeshEngine>()

    /** Bidirectional links: nodeId → set of connected peer nodeIds. */
    private val topology = mutableMapOf<String, MutableSet<String>>()

    /**
     * Create a [MockTransport] for a node, wire it into a new [MeshEngine],
     * and register both in this network.
     *
     * @param nodeId     Unique ID for the node
     * @param defaultTtl Default TTL for packets created by this engine
     * @return The configured [MeshEngine]
     */
    fun createTransportAndEngine(
        nodeId: String,
        defaultTtl: Int = MeshEngine.DEFAULT_TTL
    ): MeshEngine {
        val transport = MockTransport(this, nodeId)
        val engine = MeshEngine(nodeId, transport, defaultTtl)
        engines[nodeId] = engine
        topology.getOrPut(nodeId) { mutableSetOf() }
        return engine
    }

    /**
     * Add a bidirectional link between two nodes.
     * Both nodes become peers of each other.
     */
    fun addLink(nodeId1: String, nodeId2: String) {
        topology.getOrPut(nodeId1) { mutableSetOf() }.add(nodeId2)
        topology.getOrPut(nodeId2) { mutableSetOf() }.add(nodeId1)
    }

    /**
     * Get the list of peers for a given node based on the current topology.
     */
    fun getPeersFor(nodeId: String): List<Peer> {
        return topology[nodeId]?.map { Peer(it) } ?: emptyList()
    }

    /**
     * Deliver a packet to a target node's engine.
     * Simulates network delivery — the packet arrives at the target instantly.
     */
    fun deliverTo(targetNodeId: String, packet: Packet) {
        engines[targetNodeId]?.receivePacket(packet)
    }
}

/**
 * Mock transport for simulation.
 * Each node gets its own [MockTransport] instance that routes
 * through the [MockNetwork].
 *
 * Later, the BLE engineer creates `BLETransport` implementing
 * the same [Transport] interface — zero changes to [MeshEngine].
 */
class MockTransport(
    private val network: MockNetwork,
    private val nodeId: String
) : Transport {

    override fun send(peer: Peer, packet: Packet) {
        network.deliverTo(peer.nodeId, packet)
    }

    override fun getPeers(): List<Peer> {
        return network.getPeersFor(nodeId)
    }
}
