package com.resqnet.mesh

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests verifying that generated message IDs conform to UUID format,
 * not just uniqueness.
 */
class MessageIdGeneratorTest {

    private val uuidRegex = Regex(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    )

    @Test
    fun `generated ID matches UUID format`() {
        val id = MessageIdGenerator.generate()
        assertTrue(
            "Message ID '$id' should match UUID format",
            id.matches(uuidRegex)
        )
    }

    @Test
    fun `multiple generated IDs all match UUID format`() {
        val ids = (1..50).map { MessageIdGenerator.generate() }
        for (id in ids) {
            assertTrue(
                "Message ID '$id' should match UUID format",
                id.matches(uuidRegex)
            )
        }
    }

    @Test
    fun `generated IDs are unique`() {
        val ids = (1..100).map { MessageIdGenerator.generate() }.toSet()
        assertEquals("All 100 message IDs should be unique", 100, ids.size)
    }

    @Test
    fun `generated ID is non-blank`() {
        val id = MessageIdGenerator.generate()
        assertTrue("Message ID should not be blank", id.isNotBlank())
    }

    @Test
    fun `generated ID has correct length for UUID`() {
        // UUID string format: 8-4-4-4-12 = 36 characters including hyphens
        val id = MessageIdGenerator.generate()
        assertEquals("UUID should be 36 characters", 36, id.length)
    }

    @Test
    fun `generated ID contains only valid UUID characters`() {
        val id = MessageIdGenerator.generate()
        val validChars = "0123456789abcdef-"
        for (ch in id) {
            assertTrue(
                "Character '$ch' should be a valid UUID character",
                ch in validChars
            )
        }
    }

    @Test
    fun `packet messageId from engine is UUID format`() {
        // Verify that MeshEngine.createPacket produces UUID-format messageIds
        val transport = object : com.resqnet.mesh.transport.Transport {
            override fun send(peer: Peer, packet: Packet) {}
            override fun getPeers(): List<Peer> = emptyList()
        }
        val engine = MeshEngine("NODE_TEST", transport)
        val packet = engine.createPacket("NODE_B", "Hello")

        assertTrue(
            "Packet messageId '${packet.messageId}' should be UUID format",
            packet.messageId.matches(uuidRegex)
        )
    }
}
