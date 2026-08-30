package com.resqnet.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BleInboundClassifierTest {

    private val mesh =
        "MESH:v1|0ea557d5-5f42-4a13-b726-111cc80f157c|n682a9752|n682a9752|n5bf766be|5|0|hello-mesh"

    @Test
    fun meshV1_isMeshEvenThoughTestProtocolMarksInvalid() {
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse(mesh))
        assertEquals(BleInboundClassifier.Kind.MESH, BleInboundClassifier.classify(mesh))
        assertEquals("MESH", BleInboundClassifier.typeLabel(mesh))
        assertNotEquals(BleInboundClassifier.Kind.UNKNOWN, BleInboundClassifier.classify(mesh))
    }

    @Test
    fun helloAckPingPongData_keepExistingTypes() {
        assertEquals(BleInboundClassifier.Kind.HELLO, BleInboundClassifier.classify("HELLO:n682a9752"))
        assertEquals(BleInboundClassifier.Kind.ACK, BleInboundClassifier.classify("ACK:n5bf766be"))
        assertEquals(BleInboundClassifier.Kind.PING, BleInboundClassifier.classify("PING:abc123"))
        assertEquals(BleInboundClassifier.Kind.PONG, BleInboundClassifier.classify("PONG:abc123"))
        assertEquals(
            BleInboundClassifier.Kind.DATA,
            BleInboundClassifier.classify("DATA:test001:Hello from ResQNet")
        )
    }

    @Test
    fun unknownAndEmpty() {
        assertEquals(BleInboundClassifier.Kind.UNKNOWN, BleInboundClassifier.classify("NOPE"))
        assertEquals(BleInboundClassifier.Kind.UNKNOWN, BleInboundClassifier.classify(""))
        assertEquals(BleInboundClassifier.Kind.UNKNOWN, BleInboundClassifier.classify(null))
    }
}
