package com.resqnet.mesh

import com.resqnet.mesh.transport.Transport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Destination-aware deliver vs forward:
 * - dest = this node → deliver, do not forward
 * - dest = another node → forward, do not deliver
 * - dest = `*` → deliver and forward
 */
class MeshDestinationTest {

    private class RegistryTransport : Transport {
        private val peerIds = linkedSetOf<String>()
        val sentTo = mutableListOf<String>()
        val sentPackets = mutableListOf<Packet>()

        fun addPeer(nodeId: String) {
            peerIds += nodeId
        }

        override fun send(peer: Peer, packet: Packet) {
            sentTo += peer.nodeId
            sentPackets += packet
        }

        override fun getPeers(): List<Peer> = peerIds.map { Peer(it) }
    }

    private lateinit var originalLog: (String, String) -> Unit
    private val dropLogs = mutableListOf<String>()

    @Before
    fun setUp() {
        originalLog = MeshLogger.logOutput
        dropLogs.clear()
        MeshLogger.logOutput = { tag, message ->
            if (tag == "DROP") {
                dropLogs += message
            }
        }
    }

    @After
    fun tearDown() {
        MeshLogger.logOutput = originalLog
    }

    private fun packet(
        id: String = "MSG001",
        dest: String,
        sender: String = "NODE_A",
        ttl: Int = 5
    ) = Packet(
        messageId = id,
        sourceNodeId = "NODE_A",
        senderNodeId = sender,
        destinationNodeId = dest,
        payload = "hello-mesh",
        ttl = ttl,
        hopCount = 0
    )

    @Test
    fun destinationB_deliversAndDoesNotForward() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")

        var delivered: Packet? = null
        var forwarded: Packet? = null
        engine.onMessageReceived = { delivered = it }
        engine.onMessageForwarded = { forwarded = it }

        engine.receivePacket(packet(dest = "NODE_B"))

        assertEquals("hello-mesh", delivered?.payload)
        assertEquals("NODE_B", delivered?.destinationNodeId)
        assertNull(forwarded)
        assertTrue(transport.sentTo.isEmpty())
    }

    @Test
    fun destinationC_forwardsWithoutDelivering() {
        val transport = RegistryTransport()
        val engineB = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")

        var deliveredAtB: Packet? = null
        var forwarded: Packet? = null
        engineB.onMessageReceived = { deliveredAtB = it }
        engineB.onMessageForwarded = { forwarded = it }

        engineB.receivePacket(packet(dest = "NODE_C"))

        assertNull(deliveredAtB)
        assertEquals(listOf("NODE_C"), transport.sentTo)
        assertEquals(4, forwarded?.ttl)
        assertEquals(1, forwarded?.hopCount)
        assertEquals("NODE_B", forwarded?.senderNodeId)
        assertEquals("NODE_C", forwarded?.destinationNodeId)
        assertEquals("NODE_A", forwarded?.sourceNodeId)
    }

    @Test
    fun destinationC_atC_deliversAndStopsForwarding() {
        val transport = RegistryTransport()
        val engineC = MeshEngine("NODE_C", transport)
        transport.addPeer("NODE_B")
        transport.addPeer("NODE_D")

        var delivered: Packet? = null
        var forwarded: Packet? = null
        engineC.onMessageReceived = { delivered = it }
        engineC.onMessageForwarded = { forwarded = it }

        engineC.receivePacket(
            packet(dest = "NODE_C", sender = "NODE_B").copy(
                senderNodeId = "NODE_B",
                ttl = 4,
                hopCount = 1
            )
        )

        assertEquals("hello-mesh", delivered?.payload)
        assertEquals("NODE_C", delivered?.destinationNodeId)
        assertNull(forwarded)
        assertTrue(transport.sentTo.isEmpty())
    }

    @Test
    fun broadcastStar_deliversAndForwards() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")

        var delivered: Packet? = null
        engine.onMessageReceived = { delivered = it }

        engine.receivePacket(packet(dest = "*"))

        assertEquals("hello-mesh", delivered?.payload)
        assertEquals(listOf("NODE_C"), transport.sentTo)
    }

    @Test
    fun ttlZero_doesNotDeliverOrForward() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")

        var delivered: Packet? = null
        engine.onMessageReceived = { delivered = it }

        engine.receivePacket(packet(dest = "NODE_C", ttl = 0))

        assertNull(delivered)
        assertTrue(transport.sentTo.isEmpty())
        assertTrue(dropLogs.any { it.contains("validation failed") })
    }

    @Test
    fun duplicate_isNotForwardedOrDeliveredTwice() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")

        var deliverCount = 0
        engine.onMessageReceived = { deliverCount++ }

        val first = packet(id = "DUP-DEST", dest = "NODE_C")
        engine.receivePacket(first)
        assertEquals(1, transport.sentTo.size)
        assertEquals(0, deliverCount)

        engine.receivePacket(first)
        assertEquals(1, transport.sentTo.size)
        assertEquals(0, deliverCount)
        assertTrue(dropLogs.any { it.contains("duplicate") })
    }

    @Test
    fun noEligiblePeer_producesExistingDrop() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")

        var delivered: Packet? = null
        engine.onMessageReceived = { delivered = it }

        engine.receivePacket(packet(dest = "NODE_C"))

        assertNull(delivered)
        assertTrue(transport.sentTo.isEmpty())
        assertTrue(
            dropLogs.any { it.contains("no eligible peers to forward to") }
        )
    }
}
