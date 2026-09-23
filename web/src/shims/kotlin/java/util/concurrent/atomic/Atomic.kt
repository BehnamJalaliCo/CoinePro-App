@file:Suppress("unused")

package java.util.concurrent.atomic

/** The browser has one thread, so these are plain holders with the JDK's method names. */
class AtomicReference<V>(private var value: V) {
    fun get(): V = value
    fun set(v: V) { value = v }
    fun getAndSet(v: V): V = value.also { value = v }
    fun compareAndSet(expect: V, update: V): Boolean = if (value === expect || value == expect) { value = update; true } else false
    fun updateAndGet(f: (V) -> V): V = f(value).also { value = it }
    fun getAndUpdate(f: (V) -> V): V = value.also { value = f(it) }
    override fun toString(): String = value.toString()
}

class AtomicLong(private var value: Long = 0) {
    fun get(): Long = value
    fun set(v: Long) { value = v }
    fun incrementAndGet(): Long = ++value
    fun decrementAndGet(): Long = --value
    fun getAndIncrement(): Long = value++
    fun getAndDecrement(): Long = value--
    fun addAndGet(d: Long): Long { value += d; return value }
    fun getAndAdd(d: Long): Long = value.also { value += d }
    fun getAndSet(v: Long): Long = value.also { value = v }
    fun compareAndSet(expect: Long, update: Long): Boolean = if (value == expect) { value = update; true } else false
    fun updateAndGet(f: (Long) -> Long): Long = f(value).also { value = it }
    override fun toString(): String = value.toString()
}

class AtomicInteger(private var value: Int = 0) {
    fun get(): Int = value
    fun set(v: Int) { value = v }
    fun incrementAndGet(): Int = ++value
    fun decrementAndGet(): Int = --value
    fun getAndIncrement(): Int = value++
    fun getAndDecrement(): Int = value--
    fun addAndGet(d: Int): Int { value += d; return value }
    fun getAndSet(v: Int): Int = value.also { value = v }
    fun compareAndSet(expect: Int, update: Int): Boolean = if (value == expect) { value = update; true } else false
    override fun toString(): String = value.toString()
}

class AtomicBoolean(private var value: Boolean = false) {
    fun get(): Boolean = value
    fun set(v: Boolean) { value = v }
    fun getAndSet(v: Boolean): Boolean = value.also { value = v }
    fun compareAndSet(expect: Boolean, update: Boolean): Boolean = if (value == expect) { value = update; true } else false
    override fun toString(): String = value.toString()
}
