package com.resqnet.mesh

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the [Packet] data class.
 */
class PacketTest {

    @Test
    fun `packet creation has correct fields`() {
        val packet = Packet(
            messageId = "MSG001",
            sourceNodeId = "NODE_A",
            senderNodeId = "NODE_A",
            destinationNodeId = "NODE_C",
            payload = "Hello",
            ttl = 5,
            hopCount = 0
        )

        assertEquals("MSG001", packet.messageId)
        assertEquals("NODE_A", packet.sourceNodeId)
        assertEquals("NODE_A", packet.senderNodeId)
        assertEquals("NODE_C", packet.destinationNodeId)
        assertEquals("Hello", packet.payload)
        assertEquals(5, packet.ttl)
        assertEquals(0, packet.hopCount)
    }

    @Test
    fun `prepareForForward decrements TTL and increments hop count`() {
        val original = Packet("MSG001", "NODE_A", "NODE_A", "NODE_C", "Hello", 5, 0)

        val forwarded = original.prepareForForward("NODE_B")

        assertEquals(4, forwarded.ttl)
        assertEquals(1, forwarded.hopCount)
        assertEquals("NODE_B", forwarded.senderNodeId)
        assertEquals("NODE_A", forwarded.sourceNodeId) // source stays same
        assertEquals("MSG001", forwarded.messageId)     // messageId stays same
    }

    @Test
    fun `prepareForForward preserves destination and payload`() {
        val original = Packet("MSG001", "NODE_A", "NODE_A", "NODE_C", "Hello", 5, 0)
        val forwarded = original.prepareForForward("NODE_B")

        assertEquals("NODE_C", forwarded.destinationNodeId)
        assertEquals("Hello", forwarded.payload)
    }

    @Test
    fun `packet data class has proper equality`() {
        val p1 = Packet("MSG001", "A", "A", "C", "Hello", 5, 0)
        val p2 = Packet("MSG001", "A", "A", "C", "Hello", 5, 0)
        assertEquals(p1, p2)
    }

    @Test
    fun `packets with different fields are not equal`() {
        val p1 = Packet("MSG001", "A", "A", "C", "Hello", 5, 0)
        val p2 = Packet("MSG002", "A", "A", "C", "Hello", 5, 0)
        assertNotEquals(p1, p2)
    }

    @Test
    fun `multiple prepareForForward calls chain correctly`() {
        val original = Packet("MSG001", "NODE_A", "NODE_A", "NODE_D", "Data", 5, 0)

        val atB = original.prepareForForward("NODE_B")
        assertEquals(4, atB.ttl)
        assertEquals(1, atB.hopCount)
        assertEquals("NODE_B", atB.senderNodeId)

        val atC = atB.prepareForForward("NODE_C")
        assertEquals(3, atC.ttl)
        assertEquals(2, atC.hopCount)
        assertEquals("NODE_C", atC.senderNodeId)

        // Source never changes
        assertEquals("NODE_A", atC.sourceNodeId)
    }
}
