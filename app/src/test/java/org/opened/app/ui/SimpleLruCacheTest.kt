package org.opened.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.opened.app.ui.components.SimpleLruCache

class SimpleLruCacheTest {

    @Test
    fun testEvictionPolicy() {
        val cache = SimpleLruCache<String, String>(2)
        cache.put("a", "1")
        cache.put("b", "2")

        assertEquals("1", cache.get("a"))
        // Adding "c" should evict "b" because "a" was accessed most recently
        cache.put("c", "3")

        assertEquals("1", cache.get("a"))
        assertNull(cache.get("b"))
        assertEquals("3", cache.get("c"))
    }

    @Test
    fun testGetOrPut() {
        val cache = SimpleLruCache<String, Int>(3)
        var computeCount = 0

        val val1 = cache.getOrPut("key") {
            computeCount++
            42
        }
        val val2 = cache.getOrPut("key") {
            computeCount++
            99
        }

        assertEquals(42, val1)
        assertEquals(42, val2)
        assertEquals(1, computeCount)
    }
}
