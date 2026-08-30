package com.resqnet.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleTestMessageProtocolTest {

    @Test
    fun pingEncoding() {
        assertEquals("PING:test123", BleTestMessageProtocol.ping("test123"))
    }

    @Test
    fun pongEncoding() {
        assertEquals("PONG:test123", BleTestMessageProtocol.pong("test123"))
    }

    @Test
    fun pingParsing() {
        assertEquals(
            ParsedTestMessage.Ping("abc123"),
            BleTestMessageProtocol.parse("PING:abc123")
        )
    }

    @Test
    fun pongParsing() {
        assertEquals(
            ParsedTestMessage.Pong("abc123"),
            BleTestMessageProtocol.parse("PONG:abc123")
        )
    }

    @Test
    fun dataEncoding() {
        assertEquals(
            "DATA:test001:Hello from ResQNet",
            BleTestMessageProtocol.data("test001", "Hello from ResQNet")
        )
    }

    @Test
    fun dataParsing() {
        val parsed = BleTestMessageProtocol.parse("DATA:test001:Hello from ResQNet")
        assertEquals(
            ParsedTestMessage.Data("test001", "Hello from ResQNet"),
            parsed
        )
        val data = parsed as ParsedTestMessage.Data
        assertEquals("DATA", BleTestMessageProtocol.parsedType(data))
        assertEquals("test001", data.messageId)
        assertEquals("Hello from ResQNet", data.payload)
    }

    @Test
    fun emptyMessage() {
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse(""))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse(null))
    }

    @Test
    fun malformedMessage() {
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("PING"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("PONG"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("DATA"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("DATA:onlyId"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("HELLO:node1"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("ACK:node1"))
    }

    @Test
    fun missingMessageId() {
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("PING:"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("PONG:"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("DATA:"))
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse("DATA::payload"))
    }

    @Test
    fun payloadContainingSpaces() {
        val parsed = BleTestMessageProtocol.parse("DATA:id1:Hello from ResQNet")
        assertEquals(
            ParsedTestMessage.Data("id1", "Hello from ResQNet"),
            parsed
        )
    }

    @Test
    fun meshV1_isRecognizedAsMeshTypeWithoutChangingPingDataHandling() {
        val raw =
            "MESH:v1|0ea557d5-5f42-4a13-b726-111cc80f157c|n682a9752|n682a9752|n5bf766be|5|0|hello-mesh"
        assertEquals(ParsedTestMessage.Invalid, BleTestMessageProtocol.parse(raw))
        assertEquals(
            "MESH",
            BleTestMessageProtocol.parsedType(raw, ParsedTestMessage.Invalid)
        )
        assertEquals("UNKNOWN", BleTestMessageProtocol.parsedType(ParsedTestMessage.Invalid))
        assertEquals(
            "PING",
            BleTestMessageProtocol.parsedType(
                "PING:abc123",
                ParsedTestMessage.Ping("abc123")
            )
        )
    }

    @Test
    fun payloadContainingColonCharacters() {
        val parsed = BleTestMessageProtocol.parse("DATA:id2:a:b:c")
        assertEquals(
            ParsedTestMessage.Data("id2", "a:b:c"),
            parsed
        )
        assertEquals(
            "DATA:id2:a:b:c",
            BleTestMessageProtocol.data("id2", "a:b:c")
        )
    }

    @Test
    fun pingToPong_preservesMessageId() {
        val ping = BleTestMessageProtocol.parse("PING:abc123") as ParsedTestMessage.Ping
        val response = BleTestMessageProtocol.responseFor(ping)
        assertEquals("PONG:abc123", response)
        val pong = BleTestMessageProtocol.parse(response) as ParsedTestMessage.Pong
        assertEquals("abc123", ping.messageId)
        assertEquals(ping.messageId, pong.messageId)
    }

    @Test
    fun pongMustNotGenerateAnotherResponse() {
        val pong = ParsedTestMessage.Pong("abc123")
        val data = ParsedTestMessage.Data("id", "Hello from ResQNet")
        assertTrue(
            BleTestMessageProtocol.shouldGenerateResponse(
                ParsedTestMessage.Ping("abc123")
            )
        )
        assertFalse(BleTestMessageProtocol.shouldGenerateResponse(pong))
        assertFalse(BleTestMessageProtocol.shouldGenerateResponse(data))
        assertFalse(
            BleTestMessageProtocol.shouldGenerateResponse(ParsedTestMessage.Invalid)
        )
        assertNull(BleTestMessageProtocol.responseFor(pong))
        assertNull(BleTestMessageProtocol.responseFor(data))
        assertNull(BleTestMessageProtocol.responseFor(ParsedTestMessage.Invalid))
    }
}
