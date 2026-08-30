package com.resqnet.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleHandshakeProtocolTest {

    @Test
    fun validHello_parsing() {
        val parsed = BleHandshakeProtocol.parse("HELLO:n1a2b3c4")
        assertEquals(ParsedHandshake.Hello("n1a2b3c4"), parsed)
    }

    @Test
    fun validAck_parsing() {
        val parsed = BleHandshakeProtocol.parse("ACK:n9z8y7x6")
        assertEquals(ParsedHandshake.Ack("n9z8y7x6"), parsed)
    }

    @Test
    fun invalidMessage() {
        assertEquals(ParsedHandshake.Invalid, BleHandshakeProtocol.parse("PING"))
        assertEquals(ParsedHandshake.Invalid, BleHandshakeProtocol.parse("HELLO"))
        assertEquals(ParsedHandshake.Invalid, BleHandshakeProtocol.parse("ACK"))
        assertEquals(ParsedHandshake.Invalid, BleHandshakeProtocol.parse("hi:there"))
    }

    @Test
    fun emptyMessage() {
        assertEquals(ParsedHandshake.Invalid, BleHandshakeProtocol.parse(""))
        assertEquals(ParsedHandshake.Invalid, BleHandshakeProtocol.parse(null))
        assertEquals("", BleHandshakeProtocol.decodeUtf8(ByteArray(0)))
        assertEquals("", BleHandshakeProtocol.decodeUtf8(null))
    }

    @Test
    fun helloWithNodeId() {
        val encoded = BleHandshakeProtocol.hello("phoneA")
        assertEquals("HELLO:phoneA", encoded)
        val parsed = BleHandshakeProtocol.parse(encoded)
        assertEquals(ParsedHandshake.Hello("phoneA"), parsed)
        assertEquals(
            ParsedHandshake.Invalid,
            BleHandshakeProtocol.parse("HELLO:")
        )
    }

    @Test
    fun ackWithNodeId() {
        val encoded = BleHandshakeProtocol.ack("phoneB")
        assertEquals("ACK:phoneB", encoded)
        val parsed = BleHandshakeProtocol.parse(encoded)
        assertEquals(ParsedHandshake.Ack("phoneB"), parsed)
        assertEquals(
            ParsedHandshake.Invalid,
            BleHandshakeProtocol.parse("ACK:")
        )
    }

    @Test
    fun duplicateHelloHandling() {
        val guard = HelloDuplicateGuard()
        assertTrue(guard.shouldAck("AA:BB:CC:DD:EE:FF"))
        assertFalse(guard.shouldAck("AA:BB:CC:DD:EE:FF"))
        assertTrue(guard.hasAcknowledged("AA:BB:CC:DD:EE:FF"))
        assertTrue(guard.shouldAck("11:22:33:44:55:66"))
        guard.onDisconnected("AA:BB:CC:DD:EE:FF")
        assertFalse(guard.hasAcknowledged("AA:BB:CC:DD:EE:FF"))
        assertTrue(guard.shouldAck("AA:BB:CC:DD:EE:FF"))
        guard.clear()
        assertTrue(guard.shouldAck("11:22:33:44:55:66"))
    }

    @Test
    fun handshakeUiLabels() {
        assertEquals("Waiting", BleHandshakeState.WAITING.uiLabel())
        assertEquals("HELLO sent", BleHandshakeState.HELLO_SENT.uiLabel())
        assertEquals("ACK received", BleHandshakeState.ACK_RECEIVED.uiLabel())
        assertEquals("HELLO/ACK SUCCESS", BleHandshakeState.SUCCESS.uiLabel())
        assertEquals("Failed", BleHandshakeState.FAILED.uiLabel())
    }

    @Test
    fun decodeUtf8_helloBytes() {
        val text = BleHandshakeProtocol.decodeUtf8(
            "HELLO:node1".toByteArray(Charsets.UTF_8)
        )
        assertEquals("HELLO:node1", text)
        assertEquals(
            ParsedHandshake.Hello("node1"),
            BleHandshakeProtocol.parse(text)
        )
    }

    @Test
    fun helloTrigger_afterNotificationSuccess() {
        assertEquals(
            null,
            BleHandshakeMachine.helloSkipReason(
                gattNonNull = true,
                rxCharacteristicPresent = true,
                notificationsEnabled = true,
                connectionStillUp = true,
                helloAttempted = false
            )
        )
        assertTrue(
            BleHandshakeMachine.shouldTriggerHello(
                gattNonNull = true,
                rxCharacteristicPresent = true,
                notificationsEnabled = true,
                connectionStillUp = true,
                helloAttempted = false
            )
        )
        assertTrue(
            BleHandshakeMachine.isConnectionStillUp(
                BleGattClientState.NOTIFICATIONS_ENABLED
            )
        )
    }

    @Test
    fun helloNotTriggeredTwice() {
        assertEquals(
            "HELLO already attempted for this connection",
            BleHandshakeMachine.helloSkipReason(
                gattNonNull = true,
                rxCharacteristicPresent = true,
                notificationsEnabled = true,
                connectionStillUp = true,
                helloAttempted = true
            )
        )
    }

    @Test
    fun helloNotTriggered_missingRx() {
        assertEquals(
            "RX characteristic is null",
            BleHandshakeMachine.helloSkipReason(
                gattNonNull = true,
                rxCharacteristicPresent = false,
                notificationsEnabled = true,
                connectionStillUp = true,
                helloAttempted = false
            )
        )
    }

    @Test
    fun helloNotTriggered_notificationsDisabled() {
        assertEquals(
            "notifications are not enabled",
            BleHandshakeMachine.helloSkipReason(
                gattNonNull = true,
                rxCharacteristicPresent = true,
                notificationsEnabled = false,
                connectionStillUp = true,
                helloAttempted = false
            )
        )
    }

    @Test
    fun helloWriteFailure_setsFailed() {
        assertEquals(
            BleHandshakeState.FAILED,
            BleHandshakeMachine.afterHelloWriteAccepted(false)
        )
        assertEquals(
            BleHandshakeState.FAILED,
            BleHandshakeMachine.afterHelloWriteCallback(false)
        )
    }

    @Test
    fun helloSuccessfulWriteCallback_setsHelloSent() {
        assertEquals(
            BleHandshakeState.HELLO_SENT,
            BleHandshakeMachine.afterHelloWriteCallback(true)
        )
    }

    @Test
    fun disconnectResetsHelloState() {
        var helloAttempted = true
        assertEquals(
            BleHandshakeState.NOT_STARTED,
            BleHandshakeMachine.afterDisconnect()
        )
        helloAttempted = false
        assertTrue(
            BleHandshakeMachine.shouldTriggerHello(
                notificationsEnabled = true,
                helloAttempted = helloAttempted,
                rxCharacteristicPresent = true
            )
        )
        assertFalse(
            BleHandshakeMachine.isConnectionStillUp(BleGattClientState.DISCONNECTED)
        )
    }

    @Test
    fun handshakeStateTransitions_helloThenAck() {
        assertEquals(
            BleHandshakeState.WAITING,
            BleHandshakeMachine.afterNotificationsEnabled()
        )
        assertEquals(
            BleHandshakeState.WAITING,
            BleHandshakeMachine.afterHelloWriteAccepted(true)
        )
        assertEquals(
            BleHandshakeState.FAILED,
            BleHandshakeMachine.afterHelloWriteAccepted(false)
        )
        assertEquals(
            BleHandshakeState.HELLO_SENT,
            BleHandshakeMachine.afterHelloWriteCallback(true)
        )
        assertEquals(
            BleHandshakeState.FAILED,
            BleHandshakeMachine.afterHelloWriteCallback(false)
        )
        assertEquals(
            BleHandshakeState.ACK_RECEIVED,
            BleHandshakeMachine.afterAckReceived(BleHandshakeState.HELLO_SENT)
        )
        assertEquals(
            BleHandshakeState.SUCCESS,
            BleHandshakeMachine.afterHandshakeSuccess()
        )
        assertEquals(
            BleHandshakeState.NOT_STARTED,
            BleHandshakeMachine.afterDisconnect()
        )
    }

    @Test
    fun parsedType_helloAckUnknown() {
        assertEquals(
            "HELLO",
            BleHandshakeMachine.parsedType(ParsedHandshake.Hello("a"))
        )
        assertEquals(
            "ACK",
            BleHandshakeMachine.parsedType(ParsedHandshake.Ack("b"))
        )
        assertEquals(
            "UNKNOWN",
            BleHandshakeMachine.parsedType(ParsedHandshake.Invalid)
        )
    }

    @Test
    fun helloAttempt_resetsOnDisconnectOnly() {
        var helloAttempted = false
        assertTrue(
            BleHandshakeMachine.shouldTriggerHello(
                notificationsEnabled = true,
                helloAttempted = helloAttempted,
                rxCharacteristicPresent = true
            )
        )
        helloAttempted = true
        assertFalse(
            BleHandshakeMachine.shouldTriggerHello(
                notificationsEnabled = true,
                helloAttempted = helloAttempted,
                rxCharacteristicPresent = true
            )
        )
        helloAttempted = false
        assertEquals(BleHandshakeState.NOT_STARTED, BleHandshakeMachine.afterDisconnect())
        assertTrue(
            BleHandshakeMachine.shouldTriggerHello(
                notificationsEnabled = true,
                helloAttempted = helloAttempted,
                rxCharacteristicPresent = true
            )
        )
    }
}
