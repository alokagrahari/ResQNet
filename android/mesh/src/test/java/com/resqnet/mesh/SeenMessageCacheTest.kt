package com.resqnet.mesh

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SeenMessageCache].
 */
class SeenMessageCacheTest {

    private lateinit var cache: SeenMessageCache

    @Before
    fun setUp() {
        cache = SeenMessageCache()
    }

    @Test
    fun `new message is not seen`() {
        assertFalse(cache.hasSeen("MSG001"))
    }

    @Test
    fun `marked message is seen`() {
        cache.markSeen("MSG001")
        assertTrue(cache.hasSeen("MSG001"))
    }

    @Test
    fun `different messages tracked independently`() {
        cache.markSeen("MSG001")
        assertTrue(cache.hasSeen("MSG001"))
        assertFalse(cache.hasSeen("MSG002"))
    }

    @Test
    fun `multiple messages can be tracked`() {
        cache.markSeen("MSG001")
        cache.markSeen("MSG002")
        cache.markSeen("MSG003")
        assertTrue(cache.hasSeen("MSG001"))
        assertTrue(cache.hasSeen("MSG002"))
        assertTrue(cache.hasSeen("MSG003"))
        assertFalse(cache.hasSeen("MSG004"))
    }

    @Test
    fun `clear removes all entries`() {
        cache.markSeen("MSG001")
        cache.markSeen("MSG002")
        assertEquals(2, cache.size())

        cache.clear()

        assertEquals(0, cache.size())
        assertFalse(cache.hasSeen("MSG001"))
        assertFalse(cache.hasSeen("MSG002"))
    }

    @Test
    fun `size returns correct count`() {
        assertEquals(0, cache.size())
        cache.markSeen("MSG001")
        assertEquals(1, cache.size())
        cache.markSeen("MSG002")
        assertEquals(2, cache.size())
    }

    @Test
    fun `marking same message twice does not increase size`() {
        cache.markSeen("MSG001")
        cache.markSeen("MSG001")
        assertEquals(1, cache.size())
    }
}
