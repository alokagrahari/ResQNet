package com.resqnet.storage.api

import com.resqnet.storage.sampleEmergency
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HttpEmergencyApiTest {

    @Test
    fun `request json includes all six identity fields from the stored record`() {
        val record = sampleEmergency("TEST-123")
        val json = emergencyRequestJson(record)
        assertEquals("TEST-123", json.getString("messageId"))
        assertEquals("NODE_A", json.getString("sourceNodeId"))
        assertEquals("Medical", json.getString("type"))
        assertEquals(28.6139, json.getDouble("latitude"), 0.0001)
        assertEquals(77.2090, json.getDouble("longitude"), 0.0001)
        assertEquals(formatEmergencyTimestampUtc(record.timestamp), json.getString("timestamp"))
        assertEquals("2023-11-14T22:13:20.000Z", json.getString("timestamp"))
    }

    @Test
    fun `uploadEmergency posts all six fields to POST api emergency`() = runTest {
        val capturedBody = AtomicReference("")
        val latch = CountDownLatch(1)
        val server = ServerSocket(0)
        val port = server.localPort
        thread(start = true, isDaemon = true) {
            server.use { listener ->
                listener.accept().use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    var contentLength = 0
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toInt()
                        }
                        if (line.isEmpty()) {
                            break
                        }
                    }
                    val body = CharArray(contentLength)
                    var offset = 0
                    while (offset < contentLength) {
                        val read = reader.read(body, offset, contentLength - offset)
                        if (read < 0) {
                            break
                        }
                        offset += read
                    }
                    capturedBody.set(String(body, 0, offset))
                    val response =
                        "HTTP/1.1 201 Created\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}"
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    latch.countDown()
                }
            }
        }

        val api = HttpEmergencyApi("http://127.0.0.1:$port")
        val record = sampleEmergency("TEST-123")
        val result = api.uploadEmergency(record)
        assertTrue(result.isSuccess)
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        val json = JSONObject(capturedBody.get())
        assertEquals("TEST-123", json.getString("messageId"))
        assertEquals("NODE_A", json.getString("sourceNodeId"))
        assertEquals("Medical", json.getString("type"))
        assertEquals(28.6139, json.getDouble("latitude"), 0.0001)
        assertEquals(77.2090, json.getDouble("longitude"), 0.0001)
        assertEquals(formatEmergencyTimestampUtc(record.timestamp), json.getString("timestamp"))
    }
}
