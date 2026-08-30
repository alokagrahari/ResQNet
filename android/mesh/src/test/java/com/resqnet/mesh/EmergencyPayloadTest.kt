package com.resqnet.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyPayloadTest {

    @Test
    fun encodeIncludesLatitudeAndLongitude() {
        val payload = EmergencyPayload.encode(28.6139, 77.209)
        assertTrue(payload.startsWith("EMERGENCY:SOS|"))
        assertTrue(payload.contains("TYPE=MEDICAL"))
        assertTrue(payload.contains("PRIORITY=CRITICAL"))
        assertTrue(payload.contains("LAT=28.613900"))
        assertTrue(payload.contains("LON=77.209000"))
        assertTrue(payload.contains("MSG=Immediate assistance required"))
    }

    @Test
    fun parseLatitude() {
        val sos = EmergencyPayload.parse(
            "EMERGENCY:SOS|TYPE=MEDICAL|PRIORITY=CRITICAL|LAT=19.076090|LON=72.877426|MSG=Immediate assistance required"
        )
        assertNotNull(sos)
        assertEquals(19.076090, sos!!.latitude!!, 0.000001)
        assertTrue(sos.latitudeValid)
    }

    @Test
    fun parseLongitude() {
        val sos = EmergencyPayload.parse(
            "EMERGENCY:SOS|TYPE=MEDICAL|PRIORITY=CRITICAL|LAT=19.076090|LON=72.877426|MSG=Immediate assistance required"
        )
        assertNotNull(sos)
        assertEquals(72.877426, sos!!.longitude!!, 0.000001)
        assertTrue(sos.longitudeValid)
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        val outOfRange = EmergencyPayload.parse(
            "EMERGENCY:SOS|TYPE=MEDICAL|PRIORITY=CRITICAL|LAT=99.000000|LON=181.000000|MSG=help"
        )
        assertNotNull(outOfRange)
        assertFalse(outOfRange!!.latitudeValid)
        assertFalse(outOfRange.longitudeValid)
        assertNull(outOfRange.latitude)
        assertNull(outOfRange.longitude)
        assertFalse(outOfRange.hasValidCoordinates)

        val nonNumeric = EmergencyPayload.parse(
            "EMERGENCY:SOS|TYPE=MEDICAL|PRIORITY=CRITICAL|LAT=abc|LON=xyz|MSG=help"
        )
        assertNotNull(nonNumeric)
        assertFalse(nonNumeric!!.latitudeValid)
        assertFalse(nonNumeric.longitudeValid)
        assertFalse(nonNumeric.hasValidCoordinates)
    }

    @Test
    fun missingCoordinatesAreDetected() {
        val missing = EmergencyPayload.parse(
            "EMERGENCY:SOS|TYPE=MEDICAL|PRIORITY=CRITICAL|MSG=Immediate assistance required"
        )
        assertNotNull(missing)
        assertFalse(missing!!.hasLatitude)
        assertFalse(missing.hasLongitude)
        assertFalse(missing.hasValidCoordinates)
        assertNull(missing.latitude)
        assertNull(missing.longitude)
        assertEquals("MEDICAL", missing.type)
        assertEquals("CRITICAL", missing.priority)
    }

    @Test
    fun normalMeshPayloadIsNotEmergency() {
        assertNull(EmergencyPayload.parse("hello-mesh"))
        assertNull(EmergencyPayload.parse("""{"type":"SOS","latitude":1.0,"longitude":2.0}"""))
        assertNull(EmergencyPayload.parse("DATA:test001:Hello from ResQNet"))
    }

    @Test
    fun emergencyPayloadRoundTripsInsideUnchangedMeshV1() {
        val payload = EmergencyPayload.encode(12.9716, 77.5946)
        val packet = Packet(
            messageId = "MSG-GPS",
            sourceNodeId = "nA",
            senderNodeId = "nA",
            destinationNodeId = "nC",
            payload = payload,
            ttl = 5,
            hopCount = 0
        )
        val decoded = PacketCodec.decode(PacketCodec.encode(packet))
        assertEquals(packet, decoded)
        val sos = EmergencyPayload.parse(decoded!!.payload)
        assertNotNull(sos)
        assertEquals(12.9716, sos!!.latitude!!, 0.000001)
        assertEquals(77.5946, sos.longitude!!, 0.000001)
        assertEquals("MEDICAL", sos.type)
        assertEquals("CRITICAL", sos.priority)
        assertEquals("Immediate assistance required", sos.message)
    }

    @Test
    fun emergencyMeshPacketExceedsOldMtuAndFitsRequestedMtu() {
        val payload = EmergencyPayload.encode(12.9716, 77.5946)
        val packet = Packet(
            messageId = "0ea557d5-5f42-4a13-b726-111cc80f157c",
            sourceNodeId = "na4a9da10",
            senderNodeId = "na4a9da10",
            destinationNodeId = "nba6f8491",
            payload = payload,
            ttl = 5,
            hopCount = 0
        )
        val bytes = PacketCodec.encode(packet).toByteArray(Charsets.UTF_8).size
        assertTrue("emergency packet should exceed MTU 185 payload window", bytes > 182)
        assertTrue("emergency packet should fit MTU 517 payload window", bytes <= 514)
    }

    @Test
    fun helloMeshStillDecodesAsOpaquePayload() {
        val packet = Packet(
            messageId = "0ea557d5-5f42-4a13-b726-111cc80f157c",
            sourceNodeId = "n682a9752",
            senderNodeId = "n682a9752",
            destinationNodeId = "n5bf766be",
            payload = "hello-mesh",
            ttl = 5,
            hopCount = 0
        )
        val decoded = PacketCodec.decode(PacketCodec.encode(packet))
        assertEquals("hello-mesh", decoded!!.payload)
        assertNull(EmergencyPayload.parse(decoded.payload))
    }
}
