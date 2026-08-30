package com.resqnet.ble

import com.resqnet.mesh.MeshEngine
import com.resqnet.mesh.Packet
import com.resqnet.mesh.PacketCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleMeshAdapterTest {

    /**
     * Payload captured from the physical GATT RX log.
     * Must keep matching [PacketCodec] — do not invent a second format.
     */
    private val physicalRx =
        "MESH:v1|0ea557d5-5f42-4a13-b726-111cc80f157c|n682a9752|n682a9752|n5bf766be|5|0|hello-mesh"

    @Test
    fun physicalPayload_decodesToExistingPacketModel() {
        val packet = PacketCodec.decode(physicalRx)
        assertEquals(
            Packet(
                messageId = "0ea557d5-5f42-4a13-b726-111cc80f157c",
                sourceNodeId = "n682a9752",
                senderNodeId = "n682a9752",
                destinationNodeId = "n5bf766be",
                payload = "hello-mesh",
                ttl = 5,
                hopCount = 0
            ),
            packet
        )
    }

    @Test
    fun meshPayload_logsRxDecodeAndHandoff_thenCallsMeshEngine() {
        val logs = mutableListOf<String>()
        var handedOff: Packet? = null
        val engine = MeshEngine("n5bf766be", RecordingTransport())
        var delivered: Packet? = null
        engine.onMessageReceived = { delivered = it }

        val adapter = BleMeshAdapter(
            onHandoff = { packet ->
                handedOff = packet
                engine.receivePacket(packet)
            },
            log = { logs += it }
        )

        adapter.ingest(physicalRx, fromPeer = "n682a9752")

        assertEquals("BLE MESH RX FROM PEER=n682a9752", logs[0])
        assertEquals("BLE MESH DECODE SUCCESS", logs[1])
        assertEquals(
            "BLE MESH HANDOFF TO MESH=0ea557d5-5f42-4a13-b726-111cc80f157c",
            logs[2]
        )
        assertEquals("hello-mesh", handedOff?.payload)
        assertEquals("hello-mesh", delivered?.payload)
        assertEquals("n682a9752", delivered?.sourceNodeId)
    }

    @Test
    fun helloAckPingData_areIgnored() {
        val logs = mutableListOf<String>()
        var handedOff: Packet? = null
        val adapter = BleMeshAdapter(
            onHandoff = { handedOff = it },
            log = { logs += it }
        )
        adapter.ingest("HELLO:n682a9752")
        adapter.ingest("ACK:n5bf766be")
        adapter.ingest("PING:abc")
        adapter.ingest("PONG:abc")
        adapter.ingest("DATA:test001:Hello from ResQNet")
        assertTrue(logs.isEmpty())
        assertNull(handedOff)
    }

    @Test
    fun malformedMesh_logsDecodeFailed() {
        val logs = mutableListOf<String>()
        var handedOff: Packet? = null
        val adapter = BleMeshAdapter(
            onHandoff = { handedOff = it },
            log = { logs += it }
        )
        adapter.ingest("MESH:v1|only|three")
        assertEquals("BLE MESH RX", logs[0])
        assertTrue(logs[1].startsWith("BLE MESH DECODE FAILED:"))
        assertNull(handedOff)
    }

    private class RecordingTransport : com.resqnet.mesh.transport.Transport {
        override fun send(peer: com.resqnet.mesh.Peer, packet: Packet) {}
        override fun getPeers(): List<com.resqnet.mesh.Peer> = emptyList()
    }
}
