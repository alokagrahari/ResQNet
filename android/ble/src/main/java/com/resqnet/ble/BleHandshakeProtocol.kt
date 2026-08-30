package com.resqnet.ble

sealed class ParsedHandshake {
    data class Hello(val nodeId: String) : ParsedHandshake()
    data class Ack(val nodeId: String) : ParsedHandshake()
    object Invalid : ParsedHandshake()
}

object BleHandshakeProtocol {
    const val HELLO_PREFIX = BleConstants.HELLO_MESSAGE + ":"
    const val ACK_PREFIX = BleConstants.ACK_MESSAGE + ":"

    fun hello(nodeId: String): String = HELLO_PREFIX + nodeId

    fun ack(nodeId: String): String = ACK_PREFIX + nodeId

    fun parse(raw: String?): ParsedHandshake {
        if (raw.isNullOrEmpty()) {
            return ParsedHandshake.Invalid
        }
        if (raw.startsWith(HELLO_PREFIX)) {
            val nodeId = raw.substring(HELLO_PREFIX.length)
            if (nodeId.isEmpty()) {
                return ParsedHandshake.Invalid
            }
            return ParsedHandshake.Hello(nodeId)
        }
        if (raw.startsWith(ACK_PREFIX)) {
            val nodeId = raw.substring(ACK_PREFIX.length)
            if (nodeId.isEmpty()) {
                return ParsedHandshake.Invalid
            }
            return ParsedHandshake.Ack(nodeId)
        }
        return ParsedHandshake.Invalid
    }

    fun decodeUtf8(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) {
            return ""
        }
        return bytes.toString(Charsets.UTF_8)
    }
}

class HelloDuplicateGuard {
    private val acknowledgedConnections = mutableSetOf<String>()

    fun shouldAck(connectionId: String): Boolean {
        if (connectionId.isEmpty()) {
            return false
        }
        return acknowledgedConnections.add(connectionId)
    }

    fun onDisconnected(connectionId: String) {
        acknowledgedConnections.remove(connectionId)
    }

    fun clear() {
        acknowledgedConnections.clear()
    }

    fun hasAcknowledged(connectionId: String): Boolean {
        return acknowledgedConnections.contains(connectionId)
    }
}

object BleHandshakeMachine {
    fun helloSkipReason(
        gattNonNull: Boolean,
        rxCharacteristicPresent: Boolean,
        notificationsEnabled: Boolean,
        connectionStillUp: Boolean,
        helloAttempted: Boolean
    ): String? = when {
        !gattNonNull -> "BluetoothGatt is null"
        !connectionStillUp -> "connection is not connected"
        !notificationsEnabled -> "notifications are not enabled"
        !rxCharacteristicPresent -> "RX characteristic is null"
        helloAttempted -> "HELLO already attempted for this connection"
        else -> null
    }

    fun shouldTriggerHello(
        gattNonNull: Boolean,
        rxCharacteristicPresent: Boolean,
        notificationsEnabled: Boolean,
        connectionStillUp: Boolean,
        helloAttempted: Boolean
    ): Boolean = helloSkipReason(
        gattNonNull = gattNonNull,
        rxCharacteristicPresent = rxCharacteristicPresent,
        notificationsEnabled = notificationsEnabled,
        connectionStillUp = connectionStillUp,
        helloAttempted = helloAttempted
    ) == null

    fun shouldTriggerHello(
        notificationsEnabled: Boolean,
        helloAttempted: Boolean,
        rxCharacteristicPresent: Boolean
    ): Boolean = shouldTriggerHello(
        gattNonNull = true,
        rxCharacteristicPresent = rxCharacteristicPresent,
        notificationsEnabled = notificationsEnabled,
        connectionStillUp = true,
        helloAttempted = helloAttempted
    )

    fun isConnectionStillUp(state: BleGattClientState): Boolean {
        return state == BleGattClientState.CONNECTED ||
            state == BleGattClientState.SERVICES_DISCOVERED ||
            state == BleGattClientState.NOTIFICATIONS_ENABLED
    }

    fun afterNotificationsEnabled(): BleHandshakeState = BleHandshakeState.WAITING

    fun afterHelloWriteAccepted(writeAccepted: Boolean): BleHandshakeState {
        return if (writeAccepted) {
            BleHandshakeState.WAITING
        } else {
            BleHandshakeState.FAILED
        }
    }

    fun afterHelloWriteCallback(success: Boolean): BleHandshakeState {
        return if (success) {
            BleHandshakeState.HELLO_SENT
        } else {
            BleHandshakeState.FAILED
        }
    }

    fun afterAckReceived(current: BleHandshakeState): BleHandshakeState {
        return if (
            current == BleHandshakeState.HELLO_SENT ||
            current == BleHandshakeState.WAITING
        ) {
            BleHandshakeState.ACK_RECEIVED
        } else {
            current
        }
    }

    fun afterHandshakeSuccess(): BleHandshakeState = BleHandshakeState.SUCCESS

    fun afterDisconnect(): BleHandshakeState = BleHandshakeState.NOT_STARTED

    fun parsedType(parsed: ParsedHandshake): String = when (parsed) {
        is ParsedHandshake.Hello -> "HELLO"
        is ParsedHandshake.Ack -> "ACK"
        ParsedHandshake.Invalid -> "UNKNOWN"
    }
}

