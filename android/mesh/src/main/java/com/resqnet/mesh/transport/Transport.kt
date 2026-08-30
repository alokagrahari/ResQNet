package com.resqnet.mesh.transport

import com.resqnet.mesh.Packet
import com.resqnet.mesh.Peer

/**
 * Transport layer interface for the mesh network.
 * Abstracts the underlying communication mechanism.
 *
 * Current implementation: [MockTransport] (for simulation/testing)
 * Future implementation:  BLETransport (by BLE engineer)
 *
 * The BLE engineer simply implements this interface:
 * ```
 * class BLETransport : Transport {
 *     override fun send(peer: Peer, packet: Packet) { /* BLE GATT write */ }
 *     override fun getPeers(): List<Peer> { /* BLE scan results */ }
 * }
 * ```
 */
interface Transport {

    /**
     * Send a packet to a specific peer.
     */
    fun send(peer: Peer, packet: Packet)

    /**
     * Get the list of currently available/connected peers.
     */
    fun getPeers(): List<Peer>
}
