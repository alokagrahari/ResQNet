package com.resqnet.mesh

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safety tests for [SeenMessageCache].
 * Verifies correct behavior under concurrent access from multiple threads.
 */
class SeenMessageCacheConcurrencyTest {

    private lateinit var cache: SeenMessageCache

    @Before
    fun setUp() {
        cache = SeenMessageCache()
    }

    @Test
    fun `concurrent markSeen on distinct IDs does not crash`() {
        val threadCount = 20
        val messagesPerThread = 100
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        val threads = (1..threadCount).map { threadIdx ->
            Thread {
                try {
                    barrier.await() // all threads start simultaneously
                    for (i in 1..messagesPerThread) {
                        val id = "T${threadIdx}_MSG$i"
                        cache.markSeen(id)
                        cache.hasSeen(id)
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals("No thread should have thrown an exception", 0, errors.get())
        assertEquals(
            "All distinct IDs should be in the cache",
            threadCount * messagesPerThread,
            cache.size()
        )
    }

    @Test
    fun `concurrent markSeen on same IDs does not inflate size`() {
        val threadCount = 20
        val sharedIds = (1..50).map { "SHARED_MSG_$it" }
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        val threads = (1..threadCount).map {
            Thread {
                try {
                    barrier.await()
                    for (id in sharedIds) {
                        cache.markSeen(id)
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals("No errors during concurrent access", 0, errors.get())
        assertEquals(
            "Duplicate marking from multiple threads must not inflate size",
            sharedIds.size,
            cache.size()
        )
        // Every shared ID should be visible
        for (id in sharedIds) {
            assertTrue("$id should be seen after concurrent marking", cache.hasSeen(id))
        }
    }

    @Test
    fun `concurrent hasSeen and markSeen interleaved do not crash or lose data`() {
        val threadCount = 20
        val idsPerThread = 50
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)
        // Track which IDs each thread marked
        val markedIds = ConcurrentHashMap.newKeySet<String>()

        val threads = (1..threadCount).map { threadIdx ->
            Thread {
                try {
                    barrier.await()
                    for (i in 1..idsPerThread) {
                        val id = "MIX_MSG_$i" // overlapping IDs across threads
                        if (!cache.hasSeen(id)) {
                            cache.markSeen(id)
                            markedIds.add(id)
                        }
                        // Also do a read after write
                        cache.hasSeen(id)
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals("No errors during interleaved access", 0, errors.get())
        // All IDs that were marked should be present
        for (id in markedIds) {
            assertTrue("$id should be seen", cache.hasSeen(id))
        }
        // Size should equal the number of unique IDs (1..50)
        assertEquals("Cache size should match unique IDs", idsPerThread, cache.size())
    }

    @Test
    fun `concurrent markSeen and clear do not crash`() {
        val threadCount = 10
        val iterations = 200
        val barrier = CyclicBarrier(threadCount + 1) // +1 for clearer thread
        val latch = CountDownLatch(threadCount + 1)
        val errors = AtomicInteger(0)

        // Writer threads
        val writers = (1..threadCount).map { threadIdx ->
            Thread {
                try {
                    barrier.await()
                    for (i in 1..iterations) {
                        cache.markSeen("CLEAR_T${threadIdx}_$i")
                        cache.hasSeen("CLEAR_T${threadIdx}_$i")
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        // Clearer thread
        val clearer = Thread {
            try {
                barrier.await()
                for (i in 1..10) {
                    Thread.sleep(1)
                    cache.clear()
                }
            } catch (e: Exception) {
                errors.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }

        writers.forEach { it.start() }
        clearer.start()
        latch.await()

        assertEquals("No errors during concurrent mark+clear", 0, errors.get())
        // Cache state is indeterminate (clears happened), but it must not crash
        // and size() must return a non-negative value
        assertTrue("Size must be non-negative", cache.size() >= 0)
    }

    @Test
    fun `high-contention concurrent reads and writes are consistent`() {
        val threadCount = 15
        val barrier = CyclicBarrier(threadCount)
        val latch = CountDownLatch(threadCount)
        val errors = AtomicInteger(0)

        // Pre-mark some IDs
        val preMarked = (1..100).map { "PRE_$it" }
        preMarked.forEach { cache.markSeen(it) }

        val threads = (1..threadCount).map {
            Thread {
                try {
                    barrier.await()
                    // Read pre-marked IDs — must always return true
                    for (id in preMarked) {
                        if (!cache.hasSeen(id)) {
                            errors.incrementAndGet()
                        }
                    }
                    // Write new IDs concurrently
                    for (i in 1..50) {
                        cache.markSeen("NEW_CONTENTION_$i")
                    }
                    // Verify new IDs are visible
                    for (i in 1..50) {
                        if (!cache.hasSeen("NEW_CONTENTION_$i")) {
                            errors.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        threads.forEach { it.start() }
        latch.await()

        assertEquals("No consistency errors under high contention", 0, errors.get())
        assertEquals(
            "Pre-marked + new unique IDs",
            100 + 50,
            cache.size()
        )
    }
}
