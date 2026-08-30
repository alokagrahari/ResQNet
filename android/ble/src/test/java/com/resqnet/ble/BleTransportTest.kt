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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BleTransportTest {

    private class RecordingOutbound : BleTransport.Outbound {
        val sent = mutableListOf<String>()
        val sentTo = mutableListOf<String>()
        override fun sendTo(nodeId: String, encoded: String): Boolean {
            sentTo += nodeId
            sent += encoded
            return true
        }
    }

    private fun packet(
        id: String = "MSG001",
        from: String = "NODE_A",
        to: String = "NODE_B",
        payload: String = "hello",
        ttl: Int = 5,
        hopCount: Int = 0,
        sender: String = from
    ) = Packet(
        messageId = id,
        sourceNodeId = from,
        senderNodeId = sender,
        destinationNodeId = to,
        payload = payload,
        ttl = ttl,
        hopCount = hopCount
    )

    private lateinit var originalLog: (String, String) -> Unit
    private val dropLogs = mutableListOf<String>()
    private val forwardLogs = mutableListOf<String>()

    @Before
    fun setUp() {
        originalLog = MeshLogger.logOutput
        dropLogs.clear()
        forwardLogs.clear()
        MeshLogger.logOutput = { tag, message ->
            if (tag == "DROP") dropLogs += message
            if (tag == "FORWARD") forwardLogs += message
        }
    }

    @After
    fun tearDown() {
        MeshLogger.logOutput = originalLog
    }

    @Test
    fun send_encodesPacketOntoGattPayload() {
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound)
        val packet = packet()
        transport.send(Peer("NODE_B"), packet)
        assertEquals(1, outbound.sent.size)
        assertEquals(listOf("NODE_B"), outbound.sentTo)
        assertEquals(packet, PacketCodec.decode(outbound.sent.single()))
        assertTrue(outbound.sent.single().startsWith("MESH:v1|"))
    }

    @Test
    fun ingest_deliversMeshPacketAndIgnoresPingData() {
        val transport = BleTransport(RecordingOutbound())
        var received: Packet? = null
        transport.onPacketReceived = { received = it }

        transport.ingestRaw("PING:abc123")
        assertNull(received)
        transport.ingestRaw("DATA:test001:Hello from ResQNet")
        assertNull(received)
        transport.ingestRaw("HELLO:n1a2b3c4")
        assertNull(received)

        val packet = packet()
        transport.ingestRaw(PacketCodec.encode(packet))
        assertEquals(packet, received)
    }

    @Test
    fun ingest_registersSenderAsConnectedPeer() {
        val transport = BleTransport(RecordingOutbound())
        assertTrue(transport.getPeers().isEmpty())
        transport.ingestRaw(PacketCodec.encode(packet(from = "n682a9752")))
        assertEquals(listOf(Peer("n682a9752")), transport.getPeers())
    }

    @Test
    fun connectedPeer_becomesEligible() {
        val transport = BleTransport(RecordingOutbound())
        assertTrue(transport.getPeers().isEmpty())
        transport.addPeer("NODE_B")
        assertEquals(listOf(Peer("NODE_B")), transport.getPeers())
    }

    @Test
    fun disconnectedPeer_becomesIneligible() {
        val transport = BleTransport(RecordingOutbound())
        transport.addPeer("NODE_B")
        transport.removePeer("NODE_B")
        assertTrue(transport.getPeers().isEmpty())
    }

    @Test
    fun getPeers_tracksHandshakeNodeIds() {
        val transport = BleTransport(RecordingOutbound())
        assertTrue(transport.getPeers().isEmpty())
        transport.addPeer("NODE_B")
        assertEquals(listOf(Peer("NODE_B")), transport.getPeers())
        transport.removePeer("NODE_B")
        assertTrue(transport.getPeers().isEmpty())
    }

    @Test
    fun aToB_packetTransferThroughMeshEngine() {
        val outboundA = RecordingOutbound()
        val transportA = BleTransport(outboundA)
        val transportB = BleTransport(RecordingOutbound())
        val engineA = MeshEngine("NODE_A", transportA)
        val engineB = MeshEngine("NODE_B", transportB)
        transportA.addPeer("NODE_B")
        transportB.addPeer("NODE_A")
        transportB.onPacketReceived = { engineB.receivePacket(it) }

        var atB: Packet? = null
        engineB.onMessageReceived = { atB = it }

        engineA.sendMessage("NODE_B", "hello-mesh")
        assertEquals(1, outboundA.sent.size)
        assertEquals(listOf("NODE_B"), outboundA.sentTo)
        transportB.ingestRaw(outboundA.sent.single())

        assertEquals("hello-mesh", atB?.payload)
        assertEquals("NODE_A", atB?.sourceNodeId)
        assertEquals("NODE_B", atB?.destinationNodeId)
        assertEquals(0, atB?.hopCount)
        assertEquals(5, atB?.ttl)
    }

    @Test
    fun previousHopExcluded_otherConnectedPeerSelected() {
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound)
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")
        transport.onPacketReceived = { engine.receivePacket(it) }

        var forwarded: Packet? = null
        engine.onMessageForwarded = { forwarded = it }

        transport.ingestRaw(
            PacketCodec.encode(
                packet(from = "NODE_A", to = "*", sender = "NODE_A")
            )
        )

        assertEquals(listOf("NODE_C"), outbound.sentTo)
        assertFalse(outbound.sentTo.contains("NODE_A"))
        assertEquals(4, forwarded?.ttl)
        assertEquals(1, forwarded?.hopCount)
        assertEquals("NODE_B", forwarded?.senderNodeId)
        assertTrue(forwardLogs.any { it.contains("NODE_B -> NODE_C") })
    }

    @Test
    fun noEligiblePeers_producesExistingDrop() {
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound)
        val engine = MeshEngine("NODE_B", transport)
        transport.onPacketReceived = { engine.receivePacket(it) }

        transport.ingestRaw(
            PacketCodec.encode(packet(from = "NODE_A", to = "NODE_C"))
        )

        assertTrue(outbound.sent.isEmpty())
        assertTrue(
            dropLogs.any { it.contains("no eligible peers to forward to") }
        )
    }
}
