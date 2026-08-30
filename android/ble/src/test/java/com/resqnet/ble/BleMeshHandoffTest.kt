package com.resqnet.ble

import com.resqnet.mesh.MeshEngine
import com.resqnet.mesh.MeshLogger
import com.resqnet.mesh.Packet
import com.resqnet.mesh.PacketCodec
import com.resqnet.mesh.Peer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GATT notification / RX write → classifier → BleTransport → MeshEngine.
 * Proves MESH:v1 is handed off even though [BleTestMessageProtocol] marks it Invalid.
 */
class BleMeshHandoffTest {

    private class RecordingOutbound : BleTransport.Outbound {
        val sentTo = mutableListOf<String>()
        val sent = mutableListOf<String>()
        override fun sendTo(nodeId: String, encoded: String): Boolean {
            sentTo += nodeId
            sent += encoded
            return true
        }
    }

    private lateinit var originalLog: (String, String) -> Unit
    private val logTags = mutableListOf<String>()
    private val dropLogs = mutableListOf<String>()

    @Before
    fun setUp() {
        originalLog = MeshLogger.logOutput
        logTags.clear()
        dropLogs.clear()
        MeshLogger.logOutput = { tag, message ->
            logTags += tag
            if (tag == "DROP") dropLogs += message
        }
    }

    @After
    fun tearDown() {
        MeshLogger.logOutput = originalLog
    }

    private fun packet(
        id: String = "MSG-HANDOFF",
        from: String = "NODE_A",
        sender: String = from,
        to: String = "NODE_C",
        payload: String = "hello-mesh",
        ttl: Int = 5,
        hopCount: Int = 0
    ) = Packet(
        messageId = id,
        sourceNodeId = from,
        senderNodeId = sender,
        destinationNodeId = to,
        payload = payload,
        ttl = ttl,
        hopCount = hopCount
    )

    /**
     * Same branches as [BleGattClient] notification and [BleGattServer] RX:
     * MESH is ingested; HELLO/ACK/PING/PONG/DATA are not treated as mesh.
     */
    private fun simulateGattInbound(
        transport: BleTransport,
        raw: String,
        fromAddress: String
    ) {
        when (BleInboundClassifier.classify(raw)) {
            BleInboundClassifier.Kind.MESH -> transport.ingestRaw(raw, fromAddress = fromAddress)
            else -> {
                // HELLO/ACK/PING/PONG/DATA stay on their existing handlers.
            }
        }
    }

    @Test
    fun meshNotification_isHandedToMeshEngine() {
        val connections = BleConnectionManager()
        connections.registerPeer("AA:01")
        connections.bindNodeId("AA:01", "NODE_A")
        val transport = BleTransport(RecordingOutbound(), connections)
        val engine = MeshEngine("NODE_C", transport)
        var received: Packet? = null
        transport.onPacketReceived = { engine.receivePacket(it) }
        engine.onMessageReceived = { received = it }

        val raw = PacketCodec.encode(packet(to = "NODE_C"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse(raw))
        assertEquals(BleInboundClassifier.Kind.MESH, BleInboundClassifier.classify(raw))

        simulateGattInbound(transport, raw, "AA:01")

        assertEquals("hello-mesh", received?.payload)
        assertEquals("MSG-HANDOFF", received?.messageId)
        assertTrue(logTags.contains("RECEIVE"))
        assertTrue(logTags.contains("VALIDATE"))
        assertTrue(logTags.contains("DEDUP"))
        assertTrue(logTags.contains("DEST"))
        assertTrue(logTags.contains("DELIVERED"))
    }

    @Test
    fun meshFromPeerA_isAssociatedWithPeerA() {
        val connections = BleConnectionManager()
        val peerA = connections.registerPeer("AA:01")
        connections.bindNodeId("AA:01", "NODE_A")
        val transport = BleTransport(RecordingOutbound(), connections)
        var handed: Packet? = null
        transport.onPacketReceived = { handed = it }

        simulateGattInbound(
            transport,
            PacketCodec.encode(packet(from = "NODE_A", to = "NODE_C")),
            "AA:01"
        )

        assertEquals("NODE_A", handed?.senderNodeId)
        assertSame(peerA, connections.getPeer("AA:01"))
        assertEquals("NODE_A", connections.getPeer("AA:01")?.nodeId)
        assertSame(peerA, connections.getPeerByNodeId("NODE_A"))
        assertEquals(1, connections.peerCount())
    }

    @Test
    fun destinationNode_deliversAndDoesNotForward() {
        val connections = BleConnectionManager()
        connections.registerLogicalPeer("NODE_A")
        connections.registerLogicalPeer("NODE_D")
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound, connections)
        val engine = MeshEngine("NODE_C", transport)
        var delivered: Packet? = null
        var forwarded: Packet? = null
        transport.onPacketReceived = { engine.receivePacket(it) }
        engine.onMessageReceived = { delivered = it }
        engine.onMessageForwarded = { forwarded = it }

        simulateGattInbound(
            transport,
            PacketCodec.encode(packet(from = "NODE_B", sender = "NODE_B", to = "NODE_C")),
            "BB:02"
        )

        assertEquals("hello-mesh", delivered?.payload)
        assertNull(forwarded)
        assertTrue(outbound.sentTo.isEmpty())
    }

    @Test
    fun relay_forwardsToAnotherConnectedPeer() {
        val connections = BleConnectionManager()
        connections.registerLogicalPeer("NODE_A")
        connections.registerLogicalPeer("NODE_C")
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound, connections)
        val engine = MeshEngine("NODE_B", transport)
        var delivered: Packet? = null
        transport.onPacketReceived = { engine.receivePacket(it) }
        engine.onMessageReceived = { delivered = it }

        simulateGattInbound(
            transport,
            PacketCodec.encode(packet(from = "NODE_A", sender = "NODE_A", to = "NODE_C")),
            "AA:01"
        )

        assertNull(delivered)
        assertEquals(listOf("NODE_C"), outbound.sentTo)
        assertFalse(outbound.sentTo.contains("NODE_A"))
    }

    @Test
    fun duplicate_isDropped() {
        val connections = BleConnectionManager()
        connections.registerLogicalPeer("NODE_A")
        connections.registerLogicalPeer("NODE_C")
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound, connections)
        val engine = MeshEngine("NODE_B", transport)
        transport.onPacketReceived = { engine.receivePacket(it) }

        val raw = PacketCodec.encode(packet(id = "DUP-1", to = "NODE_C"))
        simulateGattInbound(transport, raw, "AA:01")
        assertEquals(1, outbound.sentTo.size)

        simulateGattInbound(transport, raw, "AA:01")
        assertEquals(1, outbound.sentTo.size)
        assertTrue(dropLogs.any { it.contains("duplicate") })
    }

    @Test
    fun ttlZero_isDropped() {
        val connections = BleConnectionManager()
        connections.registerLogicalPeer("NODE_A")
        connections.registerLogicalPeer("NODE_C")
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound, connections)
        val engine = MeshEngine("NODE_B", transport)
        var delivered: Packet? = null
        transport.onPacketReceived = { engine.receivePacket(it) }
        engine.onMessageReceived = { delivered = it }

        simulateGattInbound(
            transport,
            PacketCodec.encode(packet(to = "NODE_C", ttl = 0)),
            "AA:01"
        )

        assertNull(delivered)
        assertTrue(outbound.sentTo.isEmpty())
        assertTrue(dropLogs.any { it.contains("validation failed") })
    }

    @Test
    fun helloAndAck_areNotIngestedAsMesh() {
        val transport = BleTransport(RecordingOutbound())
        var received: Packet? = null
        transport.onPacketReceived = { received = it }

        simulateGattInbound(transport, "HELLO:n682a9752", "AA:01")
        simulateGattInbound(transport, "ACK:n5bf766be", "AA:01")
        simulateGattInbound(transport, "PING:abc123", "AA:01")
        simulateGattInbound(transport, "PONG:abc123", "AA:01")
        simulateGattInbound(transport, "DATA:test001:Hello from ResQNet", "AA:01")

        assertNull(received)
        assertEquals(BleInboundClassifier.Kind.HELLO, BleInboundClassifier.classify("HELLO:n682a9752"))
        assertEquals(BleInboundClassifier.Kind.ACK, BleInboundClassifier.classify("ACK:n5bf766be"))
        assertEquals(
            ParsedHandshake.Hello("n682a9752"),
            BleHandshakeProtocol.parse("HELLO:n682a9752")
        )
        assertEquals(
            ParsedHandshake.Ack("n5bf766be"),
            BleHandshakeProtocol.parse("ACK:n5bf766be")
        )
    }

    @Test
    fun twoConnectedPeers_remainIndependentOnTargetedSend() {
        val connections = BleConnectionManager()
        connections.registerPeer("AA:01")
        connections.bindNodeId("AA:01", "NODE_A")
        connections.registerPeer("CC:03")
        connections.bindNodeId("CC:03", "NODE_C")
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound, connections)

        transport.send(Peer("NODE_A"), packet(id = "TO-A", to = "NODE_A"))
        assertEquals(listOf("NODE_A"), outbound.sentTo)

        transport.send(Peer("NODE_C"), packet(id = "TO-C", to = "NODE_C"))
        assertEquals(listOf("NODE_A", "NODE_C"), outbound.sentTo)
        assertEquals(2, connections.peerCount())
        assertFalse(connections.getPeer("AA:01") === connections.getPeer("CC:03"))
    }
}
