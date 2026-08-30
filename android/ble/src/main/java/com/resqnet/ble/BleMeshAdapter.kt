package com.resqnet.ble

import com.resqnet.mesh.Packet
import com.resqnet.mesh.PacketCodec

/**
 * Smallest BLE → Mesh handoff. Does not route, validate, or forward.
 * GATT HELLO/ACK and PING/PONG/DATA are ignored here.
 */
class BleMeshAdapter(
    private val onHandoff: (Packet) -> Unit,
    private val log: (String) -> Unit = {}
) {
    fun ingest(raw: String, fromPeer: String? = null) {
        if (!PacketCodec.isMeshPayload(raw)) {
            return
        }
        if (!fromPeer.isNullOrBlank()) {
            log("BLE MESH RX FROM PEER=$fromPeer")
        } else {
            log("BLE MESH RX")
        }
        when (val result = PacketCodec.decodeResult(raw)) {
            is PacketCodec.DecodeResult.Success -> {
                log("BLE MESH DECODE SUCCESS")
                log("BLE MESH HANDOFF TO MESH=${result.packet.messageId}")
                onHandoff(result.packet)
            }
            is PacketCodec.DecodeResult.Failed -> {
                log("BLE MESH DECODE FAILED: ${result.reason}")
            }
            PacketCodec.DecodeResult.Ignored -> {
                log("BLE MESH DECODE FAILED: ignored")
            }
        }
    }
}
