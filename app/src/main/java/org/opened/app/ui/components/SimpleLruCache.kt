package org.opened.app.ui.components

/**
 * Thread-safe, lightweight, pure-Kotlin LRU Cache with zero Android framework dependencies.
 * Ensures fast lookups and low memory footprint in both Android ART runtime and JVM unit test environments.
 */
class SimpleLruCache<K : Any, V : Any>(private val maxSize: Int) {
    init {
        require(maxSize > 0) { "maxSize must be > 0" }
    }

    private val lock = Any()
    private val map = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxSize
        }
    }

    fun get(key: K): V? = synchronized(lock) {
        map[key]
    }

    fun put(key: K, value: V): V? = synchronized(lock) {
        map.put(key, value)
    }

    inline fun getOrPut(key: K, defaultValue: () -> V): V {
        get(key)?.let { return it }
        val value = defaultValue()
        put(key, value)
        return value
    }

    fun remove(key: K): V? = synchronized(lock) {
        map.remove(key)
    }

    fun clear() = synchronized(lock) {
        map.clear()
    }

    fun size(): Int = synchronized(lock) {
        map.size
    }
}
