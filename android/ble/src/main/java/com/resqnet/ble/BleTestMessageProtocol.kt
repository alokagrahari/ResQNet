package com.resqnet.ble

sealed class ParsedTestMessage {
    data class Ping(val messageId: String) : ParsedTestMessage()
    data class Pong(val messageId: String) : ParsedTestMessage()
    data class Data(val messageId: String, val payload: String) : ParsedTestMessage()
    object Invalid : ParsedTestMessage()
}

object BleTestMessageProtocol {
    const val PING_PREFIX = "PING:"
    const val PONG_PREFIX = "PONG:"
    const val DATA_PREFIX = "DATA:"
    const val MESH_PREFIX = "MESH:"
    const val TEST_PAYLOAD = "Hello from ResQNet"

    fun ping(messageId: String): String = PING_PREFIX + messageId

    fun pong(messageId: String): String = PONG_PREFIX + messageId

    fun data(messageId: String, payload: String): String {
        return DATA_PREFIX + messageId + ":" + payload
    }

    fun newMessageId(): String {
        return "m" + (System.nanoTime() and 0xFFFFFF).toString(16)
    }

    fun parse(raw: String?): ParsedTestMessage {
        if (raw.isNullOrEmpty()) {
            return ParsedTestMessage.Invalid
        }
        if (raw.startsWith(PING_PREFIX)) {
            val messageId = raw.substring(PING_PREFIX.length)
            if (messageId.isEmpty()) {
                return ParsedTestMessage.Invalid
            }
            return ParsedTestMessage.Ping(messageId)
        }
        if (raw.startsWith(PONG_PREFIX)) {
            val messageId = raw.substring(PONG_PREFIX.length)
            if (messageId.isEmpty()) {
                return ParsedTestMessage.Invalid
            }
            return ParsedTestMessage.Pong(messageId)
        }
        if (raw.startsWith(DATA_PREFIX)) {
            val rest = raw.substring(DATA_PREFIX.length)
            val colon = rest.indexOf(':')
            if (colon <= 0) {
                return ParsedTestMessage.Invalid
            }
            val messageId = rest.substring(0, colon)
            val payload = rest.substring(colon + 1)
            if (messageId.isEmpty()) {
                return ParsedTestMessage.Invalid
            }
            return ParsedTestMessage.Data(messageId, payload)
        }
        return ParsedTestMessage.Invalid
    }

    fun parsedType(parsed: ParsedTestMessage): String = when (parsed) {
        is ParsedTestMessage.Ping -> "PING"
        is ParsedTestMessage.Pong -> "PONG"
        is ParsedTestMessage.Data -> "DATA"
        ParsedTestMessage.Invalid -> "UNKNOWN"
    }

    /**
     * Log/UI type for a GATT payload. `MESH:v1|...` stays [ParsedTestMessage.Invalid]
     * so PING/PONG/DATA handling is unchanged; the type string is MESH.
     */
    fun parsedType(raw: String?, parsed: ParsedTestMessage): String {
        if (!raw.isNullOrEmpty() && raw.startsWith(MESH_PREFIX)) {
            return "MESH"
        }
        return parsedType(parsed)
    }

    fun shouldGenerateResponse(parsed: ParsedTestMessage): Boolean {
        return parsed is ParsedTestMessage.Ping
    }

    fun responseFor(parsed: ParsedTestMessage): String? {
        return if (parsed is ParsedTestMessage.Ping) {
            pong(parsed.messageId)
        } else {
            null
        }
    }
}
