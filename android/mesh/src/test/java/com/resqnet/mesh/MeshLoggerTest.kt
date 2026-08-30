package com.resqnet.mesh

import com.resqnet.mesh.transport.MockNetwork
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests verifying that [MeshLogger] emits structured log events
 * for all important mesh operations.
 *
 * Uses the pluggable [MeshLogger.logOutput] to capture log entries
 * without any Android or BLE dependency.
 */
class MeshLoggerTest {

    /** Captured log entries as (tag, message) pairs. */
    private val logEntries = mutableListOf<Pair<String, String>>()

    /** Preserve original logger to restore after each test. */
    private lateinit var originalLogOutput: (String, String) -> Unit

    @Before
    fun setUp() {
        originalLogOutput = MeshLogger.logOutput
        logEntries.clear()
        MeshLogger.logOutput = { tag, message ->
            synchronized(logEntries) {
                logEntries.add(tag to message)
            }
        }
    }

    @After
    fun tearDown() {
        MeshLogger.logOutput = originalLogOutput
    }

    // =========================================================================
    // Direct MeshLogger unit tests
    // =========================================================================

    @Test
    fun `receive logs correct tag and includes messageId and sender`() {
        MeshLogger.receive("MSG001", "NODE_A")

        assertEquals(1, logEntries.size)
        assertEquals("RECEIVE", logEntries[0].first)
        assertTrue("Should contain messageId", logEntries[0].second.contains("MSG001"))
        assertTrue("Should contain sender", logEntries[0].second.contains("NODE_A"))
    }

    @Test
    fun `validatePass logs VALIDATE tag`() {
        MeshLogger.validatePass("MSG001")

        assertEquals("VALIDATE", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("PASS"))
        assertTrue(logEntries[0].second.contains("MSG001"))
    }

    @Test
    fun `validateFail logs VALIDATE tag with reason`() {
        MeshLogger.validateFail("MSG001", "payload is blank")

        assertEquals("VALIDATE", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("FAIL"))
        assertTrue(logEntries[0].second.contains("payload is blank"))
    }

    @Test
    fun `dedupNew logs DEDUP tag with NEW`() {
        MeshLogger.dedupNew("MSG001")

        assertEquals("DEDUP", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("NEW"))
    }

    @Test
    fun `dedupDuplicate logs DEDUP tag with DUPLICATE`() {
        MeshLogger.dedupDuplicate("MSG001")

        assertEquals("DEDUP", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("DUPLICATE"))
    }

    @Test
    fun `ttlUpdate logs TTL tag with old and new values`() {
        MeshLogger.ttlUpdate("MSG001", 5, 4)

        assertEquals("TTL", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("5"))
        assertTrue(logEntries[0].second.contains("4"))
    }

    @Test
    fun `ttlExpired logs TTL tag with EXPIRED`() {
        MeshLogger.ttlExpired("MSG001")

        assertEquals("TTL", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("EXPIRED"))
    }

    @Test
    fun `hopUpdate logs HOP tag with old and new values`() {
        MeshLogger.hopUpdate("MSG001", 0, 1)

        assertEquals("HOP", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("0"))
        assertTrue(logEntries[0].second.contains("1"))
    }

    @Test
    fun `forward logs FORWARD tag with from and to nodes`() {
        MeshLogger.forward("MSG001", "NODE_B", "NODE_C")

        assertEquals("FORWARD", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("NODE_B"))
        assertTrue(logEntries[0].second.contains("NODE_C"))
    }

    @Test
    fun `send logs SEND tag`() {
        MeshLogger.send("MSG001")

        assertEquals("SEND", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("MSG001"))
    }

    @Test
    fun `drop logs DROP tag with reason`() {
        MeshLogger.drop("MSG001", "TTL expired")

        assertEquals("DROP", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("TTL expired"))
    }

    @Test
    fun `delivered logs DELIVERED tag with node`() {
        MeshLogger.delivered("MSG001", "NODE_C")

        assertEquals("DELIVERED", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("NODE_C"))
    }

    @Test
    fun `destination logs DEST tag with dest and decision`() {
        MeshLogger.destination("MSG001", "NODE_C", "FORWARD")

        assertEquals("DEST", logEntries[0].first)
        assertTrue(logEntries[0].second.contains("NODE_C"))
        assertTrue(logEntries[0].second.contains("FORWARD"))
    }

    // =========================================================================
    // Integration: verify MeshEngine emits correct log sequence
    // =========================================================================

    @Test
    fun `successful receive pipeline logs RECEIVE, VALIDATE, DEDUP, TTL, HOP, FORWARD`() {
        val network = MockNetwork()
        val engineA = network.createTransportAndEngine("NODE_A")
        val engineB = network.createTransportAndEngine("NODE_B")
        val engineC = network.createTransportAndEngine("NODE_C")
        network.addLink("NODE_A", "NODE_B")
        network.addLink("NODE_B", "NODE_C")

        logEntries.clear() // clear setup noise
        engineA.sendMessage("NODE_C", "Hello C")

        val tags = logEntries.map { it.first }

        // Verify the pipeline tags appear in order for A's send and B's receive
        assertTrue("Should contain SEND", tags.contains("SEND"))
        assertTrue("Should contain RECEIVE", tags.contains("RECEIVE"))
        assertTrue("Should contain VALIDATE", tags.contains("VALIDATE"))
        assertTrue("Should contain DEDUP", tags.contains("DEDUP"))
        assertTrue("Should contain TTL", tags.contains("TTL"))
        assertTrue("Should contain HOP", tags.contains("HOP"))
        assertTrue("Should contain FORWARD", tags.contains("FORWARD"))
        assertTrue("Should contain DELIVERED", tags.contains("DELIVERED"))
        assertTrue("Should contain DEST", tags.contains("DEST"))

        // SEND should come before RECEIVE
        val sendIdx = tags.indexOf("SEND")
        val receiveIdx = tags.indexOf("RECEIVE")
        assertTrue("SEND should come before RECEIVE", sendIdx < receiveIdx)
    }

    @Test
    fun `duplicate packet logs RECEIVE, VALIDATE, DEDUP DUPLICATE, DROP`() {
        val network = MockNetwork()
        val engineB = network.createTransportAndEngine("NODE_B")

        val packet = Packet("MSG001", "NODE_A", "NODE_A", "NODE_B", "Hello", 5, 0)

        // First receive
        engineB.receivePacket(packet)
        logEntries.clear()

        // Second receive — should be duplicate
        engineB.receivePacket(packet)

        val tags = logEntries.map { it.first }
        assertEquals("RECEIVE", tags[0])
        assertEquals("VALIDATE", tags[1])
        assertEquals("DEDUP", tags[2])
        assertEquals("DROP", tags[3])

        // Verify DEDUP message mentions DUPLICATE
        assertTrue(
            "DEDUP log should mention DUPLICATE",
            logEntries[2].second.contains("DUPLICATE")
        )
    }

    @Test
    fun `malformed packet logs RECEIVE, VALIDATE FAIL, DROP`() {
        val network = MockNetwork()
        val engine = network.createTransportAndEngine("NODE_A")

        logEntries.clear()
        engine.receivePacket(Packet("", "A", "A", "B", "Hello", 5, 0))

        val tags = logEntries.map { it.first }
        assertEquals("RECEIVE", tags[0])
        assertEquals("VALIDATE", tags[1])
        assertEquals("DROP", tags[2])

        // VALIDATE should mention FAIL
        assertTrue(logEntries[1].second.contains("FAIL"))
    }

    @Test
    fun `TTL expired packet logs TTL EXPIRED and DROP`() {
        val network = MockNetwork()
        val engineA = network.createTransportAndEngine("NODE_A", defaultTtl = 1)
        val engineB = network.createTransportAndEngine("NODE_B")
        val engineC = network.createTransportAndEngine("NODE_C")
        network.addLink("NODE_A", "NODE_B")
        network.addLink("NODE_B", "NODE_C")

        logEntries.clear()
        engineA.sendMessage("NODE_C", "Hello C")

        // Find the TTL EXPIRED log from B's processing
        val ttlLogs = logEntries.filter { it.first == "TTL" }
        assertTrue(
            "Should have a TTL EXPIRED log",
            ttlLogs.any { it.second.contains("EXPIRED") }
        )

        val dropLogs = logEntries.filter { it.first == "DROP" }
        assertTrue(
            "Should have a DROP log for TTL",
            dropLogs.any { it.second.contains("TTL expired") }
        )
    }
}
