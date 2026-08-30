package com.resqnet.mesh

/**
 * Encodes [Packet] for the BLE GATT RX/TX path without changing Packet fields.
 *
 * Wire format (payload is last so it may contain `|`):
 * `MESH:v1|{messageId}|{sourceNodeId}|{senderNodeId}|{destinationNodeId}|{ttl}|{hopCount}|{payload}`
 *
 * Field mapping for the proven BLE test payload:
 * originNode = sourceNodeId, currentNode = senderNodeId.
 *
 * HELLO/ACK and PING/PONG/DATA keep their existing prefixes and will not decode.
 */
object PacketCodec {
    const val PREFIX = "MESH:"
    const val VERSION = "v1"
    private const val FIELD_COUNT = 8

    sealed class DecodeResult {
        data class Success(val packet: Packet) : DecodeResult()
        data class Failed(val reason: String) : DecodeResult()
        object Ignored : DecodeResult()
    }

    fun isMeshPayload(raw: String?): Boolean {
        return !raw.isNullOrEmpty() && raw.startsWith(PREFIX)
    }

    fun encode(packet: Packet): String {
        return PREFIX +
            VERSION + "|" +
            packet.messageId + "|" +
            packet.sourceNodeId + "|" +
            packet.senderNodeId + "|" +
            packet.destinationNodeId + "|" +
            packet.ttl + "|" +
            packet.hopCount + "|" +
            packet.payload
    }

    fun decode(raw: String?): Packet? {
        return when (val result = decodeResult(raw)) {
            is DecodeResult.Success -> result.packet
            else -> null
        }
    }

    fun decodeResult(raw: String?): DecodeResult {
        if (raw.isNullOrEmpty() || !raw.startsWith(PREFIX)) {
            return DecodeResult.Ignored
        }
        val body = raw.substring(PREFIX.length)
        val parts = body.split("|", limit = FIELD_COUNT)
        if (parts.size != FIELD_COUNT) {
            return DecodeResult.Failed("expected $FIELD_COUNT fields, got ${parts.size}")
        }
        if (parts[0] != VERSION) {
            return DecodeResult.Failed("unsupported version: ${parts[0]}")
        }
        val ttl = parts[5].toIntOrNull()
            ?: return DecodeResult.Failed("ttl is not an integer")
        val hopCount = parts[6].toIntOrNull()
            ?: return DecodeResult.Failed("hopCount is not an integer")
        if (parts[1].isEmpty()) {
            return DecodeResult.Failed("messageId is blank")
        }
        if (parts[2].isEmpty()) {
            return DecodeResult.Failed("sourceNodeId is blank")
        }
        if (parts[3].isEmpty()) {
            return DecodeResult.Failed("senderNodeId is blank")
        }
        return DecodeResult.Success(
            Packet(
                messageId = parts[1],
                sourceNodeId = parts[2],
                senderNodeId = parts[3],
                destinationNodeId = parts[4],
                payload = parts[7],
                ttl = ttl,
                hopCount = hopCount
            )
        )
    }
}
