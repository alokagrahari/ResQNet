package com.resqnet.ble

import com.resqnet.mesh.PacketCodec

/**
 * Classifies a GATT payload without treating MESH as an ignored/unknown test
 * message. HELLO/ACK and PING/PONG/DATA keep their existing parsers.
 */
object BleInboundClassifier {
    enum class Kind {
        HELLO,
        ACK,
        PING,
        PONG,
        DATA,
        MESH,
        UNKNOWN
    }

    fun classify(raw: String?): Kind {
        if (raw.isNullOrEmpty()) {
            return Kind.UNKNOWN
        }
        if (PacketCodec.isMeshPayload(raw)) {
            return Kind.MESH
        }
        return when (BleHandshakeProtocol.parse(raw)) {
            is ParsedHandshake.Hello -> Kind.HELLO
            is ParsedHandshake.Ack -> Kind.ACK
            else -> when (BleTestMessageProtocol.parse(raw)) {
                is ParsedTestMessage.Ping -> Kind.PING
                is ParsedTestMessage.Pong -> Kind.PONG
                is ParsedTestMessage.Data -> Kind.DATA
                ParsedTestMessage.Invalid -> Kind.UNKNOWN
            }
        }
    }

    fun typeLabel(raw: String?): String = classify(raw).name
}
