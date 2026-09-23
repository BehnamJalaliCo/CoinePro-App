@file:Suppress("unused", "UNUSED_PARAMETER")

package android.util

class DisplayMetrics { val density: Float = 1f; val widthPixels: Int = 1280; val heightPixels: Int = 800; val densityDpi: Int = 160; val scaledDensity: Float = 1f }

object Log {
    fun d(tag: String?, msg: String, tr: Throwable? = null): Int = 0
    fun i(tag: String?, msg: String, tr: Throwable? = null): Int = 0
    fun w(tag: String?, msg: String, tr: Throwable? = null): Int = 0
    fun w(tag: String?, tr: Throwable?): Int = 0
    fun e(tag: String?, msg: String, tr: Throwable? = null): Int = 0
    fun v(tag: String?, msg: String, tr: Throwable? = null): Int = 0
    fun isLoggable(tag: String?, level: Int): Boolean = false
    const val DEBUG = 3
}

open class LruCache<K : Any, V : Any>(private val maxSize: Int) {
    private val map = LinkedHashMap<K, V>()
    private var size = 0
    protected open fun sizeOf(key: K, value: V): Int = 1
    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}
    fun get(key: K): V? = map.remove(key)?.also { map[key] = it }
    fun put(key: K, value: V): V? {
        val previous = map.remove(key)
        if (previous != null) size -= sizeOf(key, previous)
        map[key] = value
        size += sizeOf(key, value)
        while (size > maxSize && map.isNotEmpty()) {
            val eldest = map.keys.first()
            val gone = map.remove(eldest)!!
            size -= sizeOf(eldest, gone)
            entryRemoved(true, eldest, gone, null)
        }
        return previous
    }
    fun remove(key: K): V? = map.remove(key)?.also { size -= sizeOf(key, it) }
    fun evictAll() { map.clear(); size = 0 }
    fun size(): Int = size
    fun maxSize(): Int = maxSize
    fun snapshot(): Map<K, V> = LinkedHashMap(map)
}

object Base64 {
    const val DEFAULT = 0
    const val NO_WRAP = 2
    const val URL_SAFE = 8
    const val NO_PADDING = 1
    fun encodeToString(input: ByteArray, flags: Int): String {
        val encoder = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        return (if (flags and NO_PADDING != 0) encoder.withoutPadding() else encoder).encodeToString(input)
    }
    fun decode(str: String, flags: Int): ByteArray = java.util.Base64.getDecoder().decode(str)
    fun encode(input: ByteArray, flags: Int): ByteArray = encodeToString(input, flags).encodeToByteArray()
}

class Patterns {
    companion object {
        val EMAIL_ADDRESS: Regex = Regex("[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+")
        val WEB_URL: Regex = Regex("https?://\\S+")
    }
}

fun Regex.matcher(input: CharSequence): RegexMatcherCompat = RegexMatcherCompat(this, input)
class RegexMatcherCompat(private val regex: Regex, private val input: CharSequence) { fun matches(): Boolean = regex.matches(input) }

class Size(val width: Int, val height: Int)
class SizeF(val width: Float, val height: Float)
class TypedValue

class Rational(val numerator: Int, val denominator: Int) { fun toFloat(): Float = numerator.toFloat() / denominator }
