package com.resqnet.ble

import com.resqnet.mesh.MeshEngine
import com.resqnet.mesh.MeshLogger
import com.resqnet.mesh.Packet
import com.resqnet.mesh.Peer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BleConnectionManagerTest {

    private lateinit var originalLog: (String, String) -> Unit
    private val dropLogs = mutableListOf<String>()

    @Before
    fun setUp() {
        originalLog = MeshLogger.logOutput
        dropLogs.clear()
        MeshLogger.logOutput = { tag, message ->
            if (tag == "DROP") dropLogs += message
        }
    }

    @After
    fun tearDown() {
        MeshLogger.logOutput = originalLog
    }

    private fun packet(
        id: String = "MSG001",
        source: String = "nAAAA1111",
        sender: String = source,
        dest: String = "*",
        ttl: Int = 5
    ) = Packet(
        messageId = id,
        sourceNodeId = source,
        senderNodeId = sender,
        destinationNodeId = dest,
        payload = "hello-mesh",
        ttl = ttl,
        hopCount = 0
    )

    @Test
    fun onePeerRegistration() {
        val manager = BleConnectionManager()
        val peer = manager.registerPeer("AA:AA:AA:AA:AA:01")
        manager.bindNodeId(peer.address, "nAAAA1111")
        assertEquals(1, manager.peerCount())
        assertEquals(listOf("nAAAA1111"), manager.nodeIds())
    }

    @Test
    fun twoSimultaneousPeers() {
        val manager = BleConnectionManager()
        manager.registerPeer("AA:AA:AA:AA:AA:01")
        manager.bindNodeId("AA:AA:AA:AA:AA:01", "nAAAA1111")
        manager.registerPeer("CC:CC:CC:CC:CC:03")
        manager.bindNodeId("CC:CC:CC:CC:CC:03", "nCCCC3333")
        assertEquals(2, manager.peerCount())
        assertEquals(
            setOf("nAAAA1111", "nCCCC3333"),
            manager.nodeIds().toSet()
        )
        val first = manager.getPeer("AA:AA:AA:AA:AA:01")
        val second = manager.getPeer("CC:CC:CC:CC:CC:03")
        assertFalse(first === second)
        assertNull(first?.gatt)
        assertNull(second?.gatt)
    }

    @Test
    fun threeSimultaneousPeers() {
        val manager = BleConnectionManager()
        manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nA")
        manager.registerPeer("BB:02")
        manager.bindNodeId("BB:02", "nB")
        manager.registerPeer("CC:03")
        manager.bindNodeId("CC:03", "nC")
        assertEquals(3, manager.peerCount())
        assertEquals(setOf("nA", "nB", "nC"), manager.nodeIds().toSet())
    }

    @Test
    fun duplicatePeerRegistration_doesNotReplace() {
        val manager = BleConnectionManager()
        val first = manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nAAAA1111")
        val second = manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nAAAA1111")
        assertSame(first, second)
        assertEquals(1, manager.peerCount())
        assertEquals("nAAAA1111", second.nodeId)
    }

    @Test
    fun updateLiveLink_mutatesSamePeerAndLookupReturnsIt() {
        val manager = BleConnectionManager()
        val first = manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nAAAA1111")
        val updated = manager.updateLiveLink(
            "AA:01",
            notificationsEnabled = true,
            mtu = 517
        )
        assertSame(first, updated)
        assertEquals(517, first.negotiatedMtu)
        assertTrue(first.notificationsEnabled)
        assertSame(first, manager.getPeerByNodeId("nAAAA1111"))
        assertNull(manager.updateLiveLink("missing", mtu = 185))
    }

    @Test
    fun peerLookupByMac() {
        val manager = BleConnectionManager()
        manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nAAAA1111")
        manager.registerPeer("CC:03")
        manager.bindNodeId("CC:03", "nCCCC3333")
        assertEquals("nAAAA1111", manager.getPeer("AA:01")?.nodeId)
        assertEquals("nCCCC3333", manager.getPeer("CC:03")?.nodeId)
        assertNull(manager.getPeer("FF:FF"))
    }

    @Test
    fun peerLookupByNodeId() {
        val manager = BleConnectionManager()
        manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nAAAA1111")
        manager.registerPeer("CC:03")
        manager.bindNodeId("CC:03", "nCCCC3333")
        assertEquals("AA:01", manager.getPeerByNodeId("nAAAA1111")?.address)
        assertEquals("CC:03", manager.getPeerByNodeId("nCCCC3333")?.address)
        assertNull(manager.getPeerByNodeId("nZZZZ"))
    }

    @Test
    fun disconnectOnePeer_otherRemains() {
        val manager = BleConnectionManager()
        manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nAAAA1111")
        manager.registerPeer("CC:03")
        manager.bindNodeId("CC:03", "nCCCC3333")
        manager.registerPeer("DD:04")
        manager.bindNodeId("DD:04", "nDDDD4444")
        manager.removePeer("AA:01")
        assertEquals(2, manager.peerCount())
        assertNull(manager.getPeer("AA:01"))
        assertEquals(
            setOf("nCCCC3333", "nDDDD4444"),
            manager.nodeIds().toSet()
        )
    }

    @Test
    fun targetedSendToPeerA() {
        val manager = BleConnectionManager()
        manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nAAAA1111")
        manager.registerPeer("CC:03")
        manager.bindNodeId("CC:03", "nCCCC3333")
        assertTrue(manager.sendToPeer("nAAAA1111", "MESH:v1|a|nA|nA|*|5|0|hi"))
        assertEquals(listOf("nAAAA1111"), manager.recordedSends().map { it.first })
    }

    @Test
    fun targetedSendToPeerC() {
        val manager = BleConnectionManager()
        manager.registerPeer("AA:01")
        manager.bindNodeId("AA:01", "nAAAA1111")
        manager.registerPeer("CC:03")
        manager.bindNodeId("CC:03", "nCCCC3333")
        assertTrue(manager.sendToPeer("nCCCC3333", "MESH:v1|c|nC|nC|*|5|0|hi"))
        assertEquals(listOf("nCCCC3333"), manager.recordedSends().map { it.first })
        assertFalse(manager.recordedSends().any { it.first == "nAAAA1111" })
    }

    @Test
    fun previousHopExclusion() {
        val manager = BleConnectionManager()
        manager.registerLogicalPeer("nAAAA1111")
        manager.registerLogicalPeer("nCCCC3333")
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound, manager)
        val engine = MeshEngine("nBBBB2222", transport)
        engine.receivePacket(packet(sender = "nAAAA1111"))
        assertEquals(listOf("nCCCC3333"), outbound.sentTo)
    }

    @Test
    fun multipleEligibleForwardingPeers() {
        val manager = BleConnectionManager()
        manager.registerLogicalPeer("nAAAA1111")
        manager.registerLogicalPeer("nCCCC3333")
        manager.registerLogicalPeer("nDDDD4444")
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound, manager)
        val engine = MeshEngine("nBBBB2222", transport)
        engine.receivePacket(packet(sender = "nAAAA1111", dest = "nZZZZ"))
        assertEquals(
            setOf("nCCCC3333", "nDDDD4444"),
            outbound.sentTo.toSet()
        )
        assertFalse(outbound.sentTo.contains("nAAAA1111"))
    }

    @Test
    fun emptyEligiblePeerList() {
        val manager = BleConnectionManager()
        val outbound = RecordingOutbound()
        val transport = BleTransport(outbound, manager)
        val engine = MeshEngine("nBBBB2222", transport)
        engine.receivePacket(packet(sender = "nAAAA1111", dest = "nCCCC3333"))
        assertTrue(outbound.sentTo.isEmpty())
        assertTrue(dropLogs.any { it.contains("no eligible peers to forward to") })
    }

    @Test
    fun notificationStatePerPeer() {
        val manager = BleConnectionManager()
        val a = manager.registerPeer("AA:01")
        val c = manager.registerPeer("CC:03")
        a.notificationsEnabled = true
        c.notificationsEnabled = false
        assertTrue(manager.getPeer("AA:01")!!.notificationsEnabled)
        assertFalse(manager.getPeer("CC:03")!!.notificationsEnabled)
        c.notificationsEnabled = true
        assertTrue(manager.getPeer("CC:03")!!.notificationsEnabled)
        assertTrue(manager.getPeer("AA:01")!!.notificationsEnabled)
    }

    @Test
    fun helloStatePerPeer() {
        val manager = BleConnectionManager()
        val a = manager.registerPeer("AA:01")
        val c = manager.registerPeer("CC:03")
        a.handshakeState = BleHandshakeState.HELLO_SENT
        c.handshakeState = BleHandshakeState.SUCCESS
        assertEquals(BleHandshakeState.HELLO_SENT, manager.getPeer("AA:01")?.handshakeState)
        assertEquals(BleHandshakeState.SUCCESS, manager.getPeer("CC:03")?.handshakeState)
        a.handshakeState = BleHandshakeState.SUCCESS
        assertEquals(BleHandshakeState.SUCCESS, manager.getPeer("AA:01")?.handshakeState)
        assertEquals(BleHandshakeState.SUCCESS, manager.getPeer("CC:03")?.handshakeState)
    }

    @Test
    fun meshPeers_areNodeIdPeers() {
        val manager = BleConnectionManager()
        manager.registerPeer("AA:01")
        assertTrue(manager.meshPeers().isEmpty())
        manager.bindNodeId("AA:01", "nAAAA1111")
        assertEquals(listOf(Peer("nAAAA1111")), manager.meshPeers())
    }

    private class RecordingOutbound : BleTransport.Outbound {
        val sentTo = mutableListOf<String>()
        override fun sendTo(nodeId: String, encoded: String): Boolean {
            sentTo += nodeId
            return true
        }
    }
}
