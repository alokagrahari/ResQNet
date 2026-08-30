package com.resqnet.ble

import com.resqnet.mesh.Packet
import com.resqnet.mesh.PacketCodec
import com.resqnet.mesh.Peer
import com.resqnet.mesh.transport.Transport

/**
 * BLE-backed [Transport] for [Packet].
 *
 * Live peers come from [BleConnectionManager]. [send] targets that peer's
 * GATT session. HELLO/ACK and MESH:v1 format are unchanged.
 */
class BleTransport(
    private val outbound: Outbound,
    private val connections: BleConnectionManager = BleConnectionManager(),
    private val adapterLog: (String) -> Unit = {}
) : Transport {

    interface Outbound {
        fun sendTo(nodeId: String, encoded: String): Boolean
    }

    var onPacketReceived: ((Packet) -> Unit)? = null

    @Volatile
    private var ingestFromAddress: String? = null

    private val adapter = BleMeshAdapter(
        onHandoff = { packet ->
            val address = ingestFromAddress
            if (!address.isNullOrBlank()) {
                connections.bindNodeId(address, packet.senderNodeId)
            } else if (connections.getPeerByNodeId(packet.senderNodeId) == null) {
                addPeer(packet.senderNodeId)
            }
            onPacketReceived?.invoke(packet)
        },
        log = adapterLog
    )

    fun addPeer(nodeId: String) {
        if (nodeId.isNotBlank()) {
            connections.registerLogicalPeer(nodeId)
        }
    }

    fun removePeer(nodeId: String) {
        connections.removePeerByNodeId(nodeId)
    }

    fun clearPeers() {
        connections.clear()
    }

    fun ingestRaw(raw: String, fromAddress: String? = null) {
        ingestFromAddress = fromAddress
        val fromPeer = when {
            fromAddress.isNullOrBlank() -> null
            else -> connections.getPeer(fromAddress)?.nodeId ?: fromAddress
        }
        try {
            adapter.ingest(raw, fromPeer = fromPeer)
        } finally {
            ingestFromAddress = null
        }
    }

    override fun send(peer: Peer, packet: Packet) {
        outbound.sendTo(peer.nodeId, PacketCodec.encode(packet))
    }

    override fun getPeers(): List<Peer> {
        return connections.meshPeers()
    }
}
