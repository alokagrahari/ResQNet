package com.resqnet.mesh

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [PacketValidator].
 */
class PacketValidatorTest {

    private fun validPacket() = Packet(
        messageId = "MSG001",
        sourceNodeId = "NODE_A",
        senderNodeId = "NODE_A",
        destinationNodeId = "NODE_C",
        payload = "Hello",
        ttl = 5,
        hopCount = 0
    )

    @Test
    fun `valid packet passes validation`() {
        val result = PacketValidator.validate(validPacket())
        assertTrue(result.isValid)
    }

    @Test
    fun `blank messageId fails validation`() {
        val result = PacketValidator.validate(validPacket().copy(messageId = ""))
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("messageId"))
    }

    @Test
    fun `whitespace-only messageId fails validation`() {
        val result = PacketValidator.validate(validPacket().copy(messageId = "   "))
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("messageId"))
    }

    @Test
    fun `blank sourceNodeId fails validation`() {
        val result = PacketValidator.validate(validPacket().copy(sourceNodeId = ""))
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("sourceNodeId"))
    }

    @Test
    fun `blank senderNodeId fails validation`() {
        val result = PacketValidator.validate(validPacket().copy(senderNodeId = ""))
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("senderNodeId"))
    }

    @Test
    fun `blank payload fails validation`() {
        val result = PacketValidator.validate(validPacket().copy(payload = ""))
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("payload"))
    }

    @Test
    fun `zero TTL fails validation`() {
        val result = PacketValidator.validate(validPacket().copy(ttl = 0))
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("TTL"))
    }

    @Test
    fun `negative TTL fails validation`() {
        val result = PacketValidator.validate(validPacket().copy(ttl = -1))
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("TTL"))
    }

    @Test
    fun `negative hopCount fails validation`() {
        val result = PacketValidator.validate(validPacket().copy(hopCount = -1))
        assertFalse(result.isValid)
        assertTrue(result.reason.contains("hopCount"))
    }

    @Test
    fun `TTL of 1 passes validation`() {
        val result = PacketValidator.validate(validPacket().copy(ttl = 1))
        assertTrue(result.isValid)
    }

    @Test
    fun `hopCount of 0 passes validation`() {
        val result = PacketValidator.validate(validPacket().copy(hopCount = 0))
        assertTrue(result.isValid)
    }

    @Test
    fun `large TTL passes validation`() {
        val result = PacketValidator.validate(validPacket().copy(ttl = 100))
        assertTrue(result.isValid)
    }
}
