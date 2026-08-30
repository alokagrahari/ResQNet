package com.resqnet.mesh

import com.resqnet.mesh.transport.MockNetwork
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive simulation tests for [MeshEngine].
 * All 7 required scenarios plus additional edge cases.
 *
 * Uses [MockNetwork] — zero BLE dependency.
 */
class MeshEngineTest {

    private lateinit var network: MockNetwork

    @Before
    fun setUp() {
        network = MockNetwork()
    }

    // =========================================================================
    // TEST 1: Packet creation
    // =========================================================================

    @Test
    fun `test1 - packet creation has correct initial values`() {
        val engineA = network.createTransportAndEngine("NODE_A")

        val packet = engineA.createPacket("NODE_C", "Hello")

        assertTrue("messageId should be non-blank", packet.messageId.isNotBlank())
        assertEquals("NODE_A", packet.sourceNodeId)
        assertEquals("NODE_A", packet.senderNodeId)
        assertEquals("NODE_C", packet.destinationNodeId)
        assertEquals("Hello", packet.payload)
        assertEquals(5, packet.ttl)
        assertEquals(0, packet.hopCount)
    }

    // =========================================================================
    // TEST 2: A → B
    // =========================================================================

    @Test
    fun `test2 - A sends to B and B receives`() {
        val engineA = network.createTransportAndEngine("NODE_A")
        val engineB = network.createTransportAndEngine("NODE_B")
        network.addLink("NODE_A", "NODE_B")

        var receivedPacket: Packet? = null
        engineB.onMessageReceived = { receivedPacket = it }

        engineA.sendMessage("NODE_B", "Hello B")

        assertNotNull("B should have received the packet", receivedPacket)
        assertEquals("NODE_A", receivedPacket!!.sourceNodeId)
        assertEquals("Hello B", receivedPacket!!.payload)
        assertEquals("NODE_A", receivedPacket!!.senderNodeId)
    }

    // =========================================================================
    // TEST 3: A → B → C  (TTL and hop count verification)
    // =========================================================================

    @Test
    fun `test3 - A to B to C with correct TTL and hop count`() {
        val engineA = network.createTransportAndEngine("NODE_A")
        val engineB = network.createTransportAndEngine("NODE_B")
        val engineC = network.createTransportAndEngine("NODE_C")

        network.addLink("NODE_A", "NODE_B")
        network.addLink("NODE_B", "NODE_C")

        var packetAtC: Packet? = null
        engineC.onMessageReceived = { packetAtC = it }

        // Track what B forwards
        var forwardedByB: Packet? = null
        engineB.onMessageForwarded = { forwardedByB = it }

        var packetAtB: Packet? = null
        engineB.onMessageReceived = { packetAtB = it }

        val sentPacket = engineA.sendMessage("NODE_C", "Hello C")

        // A starts with TTL=5, hop=0
        assertEquals(5, sentPacket.ttl)
        assertEquals(0, sentPacket.hopCount)

        // B forwarded with TTL=4, hop=1
        assertNotNull("B should have forwarded the packet", forwardedByB)
        assertEquals(4, forwardedByB!!.ttl)
        assertEquals(1, forwardedByB!!.hopCount)
        assertEquals("NODE_B", forwardedByB!!.senderNodeId)

        // C receives what B forwarded: TTL=4, hop=1
        assertNotNull("C should have received the packet", packetAtC)
        assertEquals("NODE_A", packetAtC!!.sourceNodeId)
        assertEquals("NODE_B", packetAtC!!.senderNodeId)
        assertEquals("Hello C", packetAtC!!.payload)
        assertEquals(4, packetAtC!!.ttl)
        assertEquals(1, packetAtC!!.hopCount)
        assertNull("B must not deliver a packet destined for C", packetAtB)
    }

    // =========================================================================
    // TEST 4: Duplicate packet
    // =========================================================================

    @Test
    fun `test4 - duplicate packet is dropped`() {
        val engineB = network.createTransportAndEngine("NODE_B")

        var receiveCount = 0
        engineB.onMessageReceived = { receiveCount++ }

        val packet = Packet(
            messageId = "MSG001",
            sourceNodeId = "NODE_A",
            senderNodeId = "NODE_A",
            destinationNodeId = "NODE_B",
            payload = "Hello",
            ttl = 5,
            hopCount = 0
        )

        // First time: processed
        engineB.receivePacket(packet)
        assertEquals("First packet should be processed", 1, receiveCount)

        // Second time: dropped (duplicate)
        engineB.receivePacket(packet)
        assertEquals("Duplicate packet should be dropped", 1, receiveCount)
    }

    // =========================================================================
    // TEST 5: TTL expiration
    // =========================================================================

    @Test
    fun `test5 - TTL expired packet is not forwarded`() {
        val engineA = network.createTransportAndEngine("NODE_A", defaultTtl = 1)
        val engineB = network.createTransportAndEngine("NODE_B")
        val engineC = network.createTransportAndEngine("NODE_C")

        network.addLink("NODE_A", "NODE_B")
        network.addLink("NODE_B", "NODE_C")

        var receivedAtC = false
        engineC.onMessageReceived = { receivedAtC = true }

        engineA.sendMessage("NODE_C", "Hello C")

        // B receives the packet with TTL=1, but cannot forward (TTL would become 0)
        assertFalse("C should NOT receive — TTL expired at B", receivedAtC)
    }

    // =========================================================================
    // TEST 6: Malformed packet (no crash)
    // =========================================================================

    @Test
    fun `test6 - malformed packets are rejected without crashing`() {
        val engineA = network.createTransportAndEngine("NODE_A")

        val malformed = listOf(
            Packet("", "A", "A", "B", "Hello", 5, 0),        // blank messageId
            Packet("MSG", "", "A", "B", "Hello", 5, 0),      // blank sourceNodeId
            Packet("MSG", "A", "", "B", "Hello", 5, 0),      // blank senderNodeId
            Packet("MSG", "A", "A", "B", "", 5, 0),          // blank payload
            Packet("MSG", "A", "A", "B", "Hello", 0, 0),     // zero TTL
            Packet("MSG", "A", "A", "B", "Hello", -1, 0),    // negative TTL
            Packet("MSG", "A", "A", "B", "Hello", 5, -1),    // negative hopCount
            Packet("   ", "A", "A", "B", "Hello", 5, 0),     // whitespace messageId
        )

        // None of these should throw exceptions
        for (packet in malformed) {
            engineA.receivePacket(packet)
        }

        // If we get here, no crash occurred
        assertTrue("All malformed packets handled without crash", true)
    }

    // =========================================================================
    // TEST 7: Diamond topology — multiple paths, process once
    // =========================================================================

    @Test
    fun `test7 - diamond topology processes message only once at D`() {
        //        B
        //       / \
        //      A   D
        //       \ /
        //        C

        val engineA = network.createTransportAndEngine("NODE_A")
        val engineB = network.createTransportAndEngine("NODE_B")
        val engineC = network.createTransportAndEngine("NODE_C")
        val engineD = network.createTransportAndEngine("NODE_D")

        network.addLink("NODE_A", "NODE_B")
        network.addLink("NODE_A", "NODE_C")
        network.addLink("NODE_B", "NODE_D")
        network.addLink("NODE_C", "NODE_D")

        var receiveCountAtD = 0
        engineD.onMessageReceived = { receiveCountAtD++ }

        engineA.sendMessage("NODE_D", "Hello D")

        assertEquals("D should process the message exactly once", 1, receiveCountAtD)
    }

    // =========================================================================
    // Additional tests
    // =========================================================================

    @Test
    fun `message IDs are unique across multiple packets`() {
        val engineA = network.createTransportAndEngine("NODE_A")
        val ids = (1..100).map { engineA.createPacket("NODE_B", "msg$it").messageId }.toSet()
        assertEquals("All 100 message IDs should be unique", 100, ids.size)
    }

    @Test
    fun `node identity generates UUID format`() {
        val identity = NodeIdentity()
        assertTrue("Node ID should not be blank", identity.nodeId.isNotBlank())
        val uuidRegex = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        assertTrue("Node ID should be UUID format", identity.nodeId.matches(uuidRegex))
    }

    @Test
    fun `two NodeIdentity instances have different IDs`() {
        val id1 = NodeIdentity()
        val id2 = NodeIdentity()
        assertNotEquals(id1.nodeId, id2.nodeId)
    }

    @Test
    fun `packet not bounced back to sender`() {
        // A ↔ B only
        val engineA = network.createTransportAndEngine("NODE_A")
        val engineB = network.createTransportAndEngine("NODE_B")
        network.addLink("NODE_A", "NODE_B")

        var receiveCountAtA = 0
        engineA.onMessageReceived = { receiveCountAtA++ }

        engineA.sendMessage("NODE_C", "Looking for C")

        // B receives from A, has only peer A, but should NOT forward back
        // A already marked as seen, so even if forwarded back, A drops as duplicate
        assertEquals("A should not process its own message back", 0, receiveCountAtA)
    }

    @Test
    fun `configurable default TTL`() {
        val engine = network.createTransportAndEngine("NODE_A", defaultTtl = 10)
        val packet = engine.createPacket("NODE_B", "Hello")
        assertEquals(10, packet.ttl)
    }

    @Test
    fun `broadcast reaches all peers`() {
        //   B
        //  /
        // A - C
        //  \
        //   D
        val engineA = network.createTransportAndEngine("NODE_A")
        val engineB = network.createTransportAndEngine("NODE_B")
        val engineC = network.createTransportAndEngine("NODE_C")
        val engineD = network.createTransportAndEngine("NODE_D")

        network.addLink("NODE_A", "NODE_B")
        network.addLink("NODE_A", "NODE_C")
        network.addLink("NODE_A", "NODE_D")

        var receivedB = false
        var receivedC = false
        var receivedD = false
        engineB.onMessageReceived = { receivedB = true }
        engineC.onMessageReceived = { receivedC = true }
        engineD.onMessageReceived = { receivedD = true }

        engineA.sendMessage("*", "Broadcast!")

        assertTrue("B should receive broadcast", receivedB)
        assertTrue("C should receive broadcast", receivedC)
        assertTrue("D should receive broadcast", receivedD)
    }

    @Test
    fun `longer chain A to B to C to D`() {
        val engineA = network.createTransportAndEngine("NODE_A")
        val engineB = network.createTransportAndEngine("NODE_B")
        val engineC = network.createTransportAndEngine("NODE_C")
        val engineD = network.createTransportAndEngine("NODE_D")

        network.addLink("NODE_A", "NODE_B")
        network.addLink("NODE_B", "NODE_C")
        network.addLink("NODE_C", "NODE_D")

        var packetAtD: Packet? = null
        engineD.onMessageReceived = { packetAtD = it }

        engineA.sendMessage("NODE_D", "Hello D")

        assertNotNull("D should receive the packet", packetAtD)
        assertEquals("NODE_A", packetAtD!!.sourceNodeId)
        // After 3 hops (A→B, B→C, C→D): TTL=5-2=3, hop=0+2=2
        // D receives what C forwarded: TTL=3, hop=2
        assertEquals(3, packetAtD!!.ttl)
        assertEquals(2, packetAtD!!.hopCount)
        assertEquals("NODE_C", packetAtD!!.senderNodeId)
    }

    @Test
    fun `seen cache size grows with received messages`() {
        val engineA = network.createTransportAndEngine("NODE_A")
        val engineB = network.createTransportAndEngine("NODE_B")
        network.addLink("NODE_A", "NODE_B")

        assertEquals(0, engineB.getSeenCacheSize())

        engineA.sendMessage("NODE_B", "msg1")
        assertEquals(1, engineB.getSeenCacheSize())

        engineA.sendMessage("NODE_B", "msg2")
        assertEquals(2, engineB.getSeenCacheSize())
    }
}
