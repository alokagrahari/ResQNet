package com.resqnet.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketCodecTest {

    private val sample = Packet(
        messageId = "MSG001",
        sourceNodeId = "NODE_A",
        senderNodeId = "NODE_A",
        destinationNodeId = "NODE_B",
        payload = "Hello from ResQNet",
        ttl = 5,
        hopCount = 0
    )

    private val physicalRx =
        "MESH:v1|0ea557d5-5f42-4a13-b726-111cc80f157c|n682a9752|n682a9752|n5bf766be|5|0|hello-mesh"

    @Test
    fun encodeThenDecode_roundTripsPacket() {
        val encoded = PacketCodec.encode(sample)
        val decoded = PacketCodec.decode(encoded)
        assertEquals(sample, decoded)
    }

    @Test
    fun payloadMayContainPipesAndColons() {
        val packet = sample.copy(payload = """{"type":"Medical","note":"a|b:c"}""")
        assertEquals(packet, PacketCodec.decode(PacketCodec.encode(packet)))
    }

    @Test
    fun emergencyGpsPayload_remainsCompatibleOpaqueField() {
        val payload =
            "EMERGENCY:SOS|TYPE=MEDICAL|PRIORITY=CRITICAL|LAT=12.971600|LON=77.594600|MSG=Immediate assistance required"
        val packet = sample.copy(payload = payload)
        val decoded = PacketCodec.decode(PacketCodec.encode(packet))
        assertEquals(payload, decoded!!.payload)
        assertEquals(sample.messageId, decoded.messageId)
        assertEquals(5, decoded.ttl)
        assertEquals(0, decoded.hopCount)
    }

    @Test
    fun helloAckPingData_areNotMeshPackets() {
        assertNull(PacketCodec.decode("HELLO:n1a2b3c4"))
        assertNull(PacketCodec.decode("ACK:n9z8y7x6"))
        assertNull(PacketCodec.decode("PING:test123"))
        assertNull(PacketCodec.decode("PONG:test123"))
        assertNull(PacketCodec.decode("DATA:test001:Hello from ResQNet"))
        assertFalse(PacketCodec.isMeshPayload("HELLO:n1a2b3c4"))
        assertFalse(PacketCodec.isMeshPayload("DATA:test001:Hello from ResQNet"))
    }

    @Test
    fun meshPrefix_isDetected() {
        val encoded = PacketCodec.encode(sample)
        assertTrue(encoded.startsWith("MESH:v1|"))
        assertTrue(PacketCodec.isMeshPayload(encoded))
    }

    @Test
    fun malformedAndEmpty_returnNull() {
        assertNull(PacketCodec.decode(null))
        assertNull(PacketCodec.decode(""))
        assertNull(PacketCodec.decode("MESH:"))
        assertNull(PacketCodec.decode("MESH:v1|only|five|fields|here"))
        assertNull(PacketCodec.decode("MESH:v2|MSG001|A|A|B|5|0|hi"))
        assertNull(PacketCodec.decode("MESH:v1|MSG001|A|A|B|x|0|hi"))
    }

    @Test
    fun physicalBlePayload_matchesExistingPacketModel() {
        val decoded = PacketCodec.decode(physicalRx)
        assertNotNull(decoded)
        assertEquals("0ea557d5-5f42-4a13-b726-111cc80f157c", decoded!!.messageId)
        assertEquals("n682a9752", decoded.sourceNodeId)
        assertEquals("n682a9752", decoded.senderNodeId)
        assertEquals("n5bf766be", decoded.destinationNodeId)
        assertEquals(5, decoded.ttl)
        assertEquals(0, decoded.hopCount)
        assertEquals("hello-mesh", decoded.payload)
    }

    @Test
    fun decodeResult_reportsFailureReasonForMalformedMesh() {
        val failed = PacketCodec.decodeResult("MESH:v1|only|three")
        assertTrue(failed is PacketCodec.DecodeResult.Failed)
        val reason = (failed as PacketCodec.DecodeResult.Failed).reason
        assertTrue(reason.contains("expected 8 fields"))
        assertEquals(
            PacketCodec.DecodeResult.Ignored,
            PacketCodec.decodeResult("HELLO:n682a9752")
        )
    }
}
