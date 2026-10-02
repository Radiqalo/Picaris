package io.github.radiqalo.picaris

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleFlightCacheTest {
    @Test
    fun concurrentLoadsShareOneRequestAndSuccessfulValue() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val cache = SingleFlightCache<String, Int>(scope)
                val releaseLoad = CompletableDeferred<Int>()
                var loadCount = 0

                val first =
                    cache.get("key") {
                        loadCount++
                        releaseLoad.await()
                    }
                val second =
                    cache.get("key") {
                        loadCount++
                        99
                    }

                assertSame(first, second)
                releaseLoad.complete(7)
                assertEquals(7, first.await())
                assertEquals(7, cache.get("key") { 99 }.await())
                assertEquals(1, loadCount)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun failedLoadIsRemovedSoNextCallCanRetry() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val cache = SingleFlightCache<String, Int>(scope)
                val failed = cache.get("key") { error("load failed") }
                val result = runCatching { failed.await() }

                assertEquals("load failed", result.exceptionOrNull()?.message)
                assertEquals(8, cache.get("key") { 8 }.await())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun leastRecentlyUsedEntryIsEvictedAtCapacity() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val cache = SingleFlightCache<Int, Int>(scope, capacity = 2)
                val first = cache.get(1) { 1 }
                cache.get(2) { 2 }
                assertSame(first, cache.get(1) { 10 })

                cache.get(3) { 3 }.await()
                assertEquals(20, cache.get(2) { 20 }.await())
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun boundedLruCacheEvictsOldestAndRetainsCachedNull() {
        val cache = BoundedLruCache<String, String?>(capacity = 2)
        cache["nullable"] = null
        cache["other"] = "value"
        assertNull(cache.getOrPut("nullable") { "unexpected" })
        assertTrue(cache.containsKey("nullable"))

        assertNull(cache["nullable"])
        cache["new"] = "entry"

        assertNull(cache["other"])
        assertEquals("entry", cache["new"])
    }
}
