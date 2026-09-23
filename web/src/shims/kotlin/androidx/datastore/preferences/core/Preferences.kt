@file:Suppress("UNCHECKED_CAST", "unused")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * Preferences DataStore, for the browser: the same keys and the same `edit {}`, kept in the page's
 * `localStorage` under `datastore:<name>` — one JSON object per store, each value with its type.
 * What the phone keeps on its disk, the browser keeps for this site; clearing site data clears it,
 * as uninstalling clears the phone's.
 */

abstract class Preferences internal constructor() {
    class Key<T> internal constructor(val name: String, internal val kind: String) {
        infix fun to(value: T): Pair<T> = Pair(this, value)
        override fun equals(other: Any?): Boolean = other is Key<*> && other.name == name
        override fun hashCode(): Int = name.hashCode()
        override fun toString(): String = name
    }

    class Pair<T> internal constructor(internal val key: Key<T>, internal val value: T)

    abstract operator fun <T> contains(key: Key<T>): Boolean
    abstract operator fun <T> get(key: Key<T>): T?
    abstract fun asMap(): Map<Key<*>, Any>
    fun toMutablePreferences(): MutablePreferences = MutablePreferences(LinkedHashMap(asMap()))
    fun toPreferences(): Preferences = MutablePreferences(LinkedHashMap(asMap()), frozen = true)
}

class MutablePreferences internal constructor(
    internal val map: MutableMap<Key<*>, Any> = LinkedHashMap(),
    private val frozen: Boolean = false,
) : Preferences() {
    override fun <T> contains(key: Key<T>): Boolean = map.containsKey(key)
    override fun <T> get(key: Key<T>): T? = map[key] as T?
    override fun asMap(): Map<Key<*>, Any> = map.toMap()
    operator fun <T> set(key: Key<T>, value: T) {
        check(!frozen) { "Do mutate preferences once returned to DataStore." }
        if (value == null) map.remove(key) else map[key] = (if (value is Set<*>) value.toSet() else value) as Any
    }
    operator fun plusAssign(prefs: Preferences) { map.putAll(prefs.asMap()) }
    operator fun plusAssign(pair: Pair<*>) { putAll(pair) }
    operator fun minusAssign(key: Key<*>) { remove(key) }
    fun putAll(vararg pairs: Pair<*>) { pairs.forEach { map[it.key] = it.value as Any } }
    fun <T> remove(key: Key<T>): T = map.remove(key) as T
    fun clear() = map.clear()
    override fun equals(other: Any?): Boolean = other is MutablePreferences && other.map == map
    override fun hashCode(): Int = map.hashCode()
    override fun toString(): String = map.entries.joinToString(", ", "{", "}") { "${it.key.name}=${it.value}" }
}

fun stringPreferencesKey(name: String): Preferences.Key<String> = Preferences.Key(name, "s")
fun intPreferencesKey(name: String): Preferences.Key<Int> = Preferences.Key(name, "i")
fun longPreferencesKey(name: String): Preferences.Key<Long> = Preferences.Key(name, "l")
fun doublePreferencesKey(name: String): Preferences.Key<Double> = Preferences.Key(name, "d")
fun floatPreferencesKey(name: String): Preferences.Key<Float> = Preferences.Key(name, "f")
fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = Preferences.Key(name, "b")
fun stringSetPreferencesKey(name: String): Preferences.Key<Set<String>> = Preferences.Key(name, "ss")
fun byteArrayPreferencesKey(name: String): Preferences.Key<ByteArray> = Preferences.Key(name, "ba")

fun emptyPreferences(): Preferences = MutablePreferences(frozen = true)
fun preferencesOf(vararg pairs: Preferences.Pair<*>): Preferences = mutablePreferencesOf(*pairs).toPreferences()
fun mutablePreferencesOf(vararg pairs: Preferences.Pair<*>): MutablePreferences = MutablePreferences().also { it.putAll(*pairs) }

suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences =
    updateData { it.toMutablePreferences().apply { transform(this) }.toPreferences() }

private fun readJs(key: String): String? = js("(function () { try { return localStorage.getItem(key); } catch (e) { return null; } })()")
private fun writeJs(key: String, value: String): Unit = js("(function () { try { localStorage.setItem(key, value); } catch (e) {} })()")

/** One named store, alive for the page. The same name is the same store, as on the phone. */
class LocalStoragePreferences private constructor(private val name: String) : DataStore<Preferences> {
    private val state = MutableStateFlow(load())
    private val lock = Mutex()
    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = lock.withLock {
        val next = transform(state.value).toPreferences()
        if (next != state.value) {
            state.value = next
            save(next)
        }
        next
    }

    private fun storageKey() = "datastore:$name"

    private fun load(): Preferences {
        val raw = readJs(storageKey()) ?: return emptyPreferences()
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyPreferences()
        val out = MutablePreferences()
        for ((key, entry) in root.entrySet()) {
            val o = entry as? JsonObject ?: continue
            val kind = o.get("t")?.asString ?: continue
            val v = o.get("v") ?: continue
            runCatching {
                val value: Any = when (kind) {
                    "s" -> v.asString
                    "i" -> v.asInt
                    "l" -> v.asLong
                    "d" -> v.asDouble
                    "f" -> v.asFloat
                    "b" -> v.asBoolean
                    "ss" -> v.asJsonArray.map { it.asString }.toSet()
                    "ba" -> java.util.Base64.getDecoder().decode(v.asString)
                    else -> return@runCatching
                }
                out.map[Preferences.Key<Any>(key, kind)] = value
            }
        }
        return out.toPreferences()
    }

    private fun save(prefs: Preferences) {
        val root = JsonObject()
        prefs.asMap().forEach { (key, value) ->
            val o = JsonObject()
            o.addProperty("t", key.kind)
            when (value) {
                is String -> o.addProperty("v", value)
                is Number -> o.addProperty("v", value)
                is Boolean -> o.addProperty("v", value)
                is Set<*> -> o.add("v", JsonArray().also { a -> value.forEach { a.add(it.toString()) } })
                is ByteArray -> o.addProperty("v", java.util.Base64.getEncoder().encodeToString(value))
                else -> o.add("v", JsonPrimitive(value.toString()))
            }
            root.add(key.name, o)
        }
        writeJs(storageKey(), root.toString())
    }

    companion object {
        private val stores = HashMap<String, LocalStoragePreferences>()
        fun named(name: String): LocalStoragePreferences = stores.getOrPut(name) { LocalStoragePreferences(name) }
    }
}

object PreferenceDataStoreFactory {
    fun create(
        corruptionHandler: Any? = null,
        migrations: List<Any> = emptyList(),
        scope: Any? = null,
        produceFile: () -> java.io.File,
    ): DataStore<Preferences> = LocalStoragePreferences.named(produceFile().name.removeSuffix(".preferences_pb"))
}
