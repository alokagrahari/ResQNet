package com.resqnet.mesh

import com.resqnet.mesh.transport.Transport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MeshEngine eligible-peer selection against a BLE-style connected-peer registry.
 * Destination need not be known; only the previous hop (and self) are excluded.
 */
class MeshForwardingTest {

    private class RegistryTransport : Transport {
        private val peerIds = linkedSetOf<String>()
        val sentTo = mutableListOf<String>()
        val sentPackets = mutableListOf<Packet>()

        fun addPeer(nodeId: String) {
            peerIds += nodeId
        }

        fun removePeer(nodeId: String) {
            peerIds -= nodeId
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
        source: String = "NODE_A",
        sender: String = source,
        dest: String = "*",
        ttl: Int = 5,
        hopCount: Int = 0
    ) = Packet(
        messageId = id,
        sourceNodeId = source,
        senderNodeId = sender,
        destinationNodeId = dest,
        payload = "hello-mesh",
        ttl = ttl,
        hopCount = hopCount
    )

    @Test
    fun connectedPeer_becomesEligible() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_C")

        engine.sendMessage("*", "hello-mesh")

        assertEquals(listOf("NODE_C"), transport.sentTo)
    }

    @Test
    fun disconnectedPeer_becomesIneligible() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_C")
        transport.removePeer("NODE_C")

        engine.sendMessage("*", "hello-mesh")

        assertTrue(transport.sentTo.isEmpty())
    }

    @Test
    fun previousHop_isExcluded() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")

        engine.receivePacket(packet(sender = "NODE_A"))

        assertEquals(listOf("NODE_C"), transport.sentTo)
    }

    @Test
    fun anotherConnectedPeer_isSelected() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")
        transport.addPeer("NODE_D")

        var forwarded: Packet? = null
        engine.onMessageForwarded = { forwarded = it }

        engine.receivePacket(packet(sender = "NODE_A", dest = "NODE_Z"))

        assertEquals(listOf("NODE_C", "NODE_D"), transport.sentTo)
        assertEquals(4, forwarded?.ttl)
        assertEquals(1, forwarded?.hopCount)
        assertEquals("NODE_B", forwarded?.senderNodeId)
    }

    @Test
    fun noPeers_producesExistingDrop() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)

        engine.receivePacket(packet(sender = "NODE_A", dest = "NODE_C"))

        assertTrue(transport.sentTo.isEmpty())
        assertTrue(
            dropLogs.any { it.contains("no eligible peers to forward to") }
        )
    }

    @Test
    fun ttlReachesZero_doesNotForward() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")

        engine.receivePacket(packet(sender = "NODE_A", ttl = 1))

        assertTrue(transport.sentTo.isEmpty())
        assertTrue(dropLogs.any { it.contains("TTL expired") })
    }

    @Test
    fun duplicateMessage_isNotForwarded() {
        val transport = RegistryTransport()
        val engine = MeshEngine("NODE_B", transport)
        transport.addPeer("NODE_A")
        transport.addPeer("NODE_C")

        val first = packet(id = "DUP-1", sender = "NODE_A")
        engine.receivePacket(first)
        assertEquals(1, transport.sentTo.size)

        engine.receivePacket(first)
        assertEquals(1, transport.sentTo.size)
        assertTrue(dropLogs.any { it.contains("duplicate") })
    }
}
