package io.github.radiqalo.picaris

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import java.util.LinkedHashMap

/** Shares in-flight loads, drops failed loads, and bounds retained results by LRU. */
internal class SingleFlightCache<K : Any, V : Any>(
    private val scope: CoroutineScope,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val entries = LinkedHashMap<K, Deferred<V>>(capacity, 0.75f, true)

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun get(
        key: K,
        load: suspend () -> V,
    ): Deferred<V> {
        entries[key]?.let { cached ->
            if (!cached.isCancelled) return cached
            entries.remove(key)
        }

        val request = scope.async { load() }
        entries[key] = request
        request.invokeOnCompletion { cause ->
            if (cause != null) {
                synchronized(this) {
                    if (entries[key] === request) entries.remove(key)
                }
            }
        }
        while (entries.size > capacity) {
            entries.entries.iterator().run {
                next()
                remove()
            }
        }
        return request
    }

    @Synchronized
    fun remove(key: K) {
        entries.remove(key)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Synchronized
    fun completed(key: K): V? =
        entries[key]
            ?.takeIf { it.isCompleted && !it.isCancelled }
            ?.let { runCatching { it.getCompleted() }.getOrNull() }

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }
}

/** LRU-bounded storage for completed values and reusable Flow instances. */
internal class BoundedLruCache<K : Any, V>(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private val entries = LinkedHashMap<K, V>(capacity, 0.75f, true)

    init {
        require(capacity > 0)
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    operator fun set(
        key: K,
        value: V,
    ) {
        entries[key] = value
        while (entries.size > capacity) {
            entries.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    @Synchronized
    fun containsKey(key: K): Boolean = entries.containsKey(key)

    @Synchronized
    fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V =
        if (entries.containsKey(key)) {
            entries.getValue(key)
        } else {
            defaultValue().also { set(key, it) }
        }

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
