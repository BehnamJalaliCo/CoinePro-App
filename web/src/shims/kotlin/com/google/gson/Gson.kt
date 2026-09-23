@file:Suppress("unused", "UNCHECKED_CAST")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.google.gson

import com.coinepro.web.wire.WireAdapters
import kotlin.reflect.KClass

/*
 * Gson, for the browser: the same tree types (`JsonObject`, `JsonArray`, `JsonPrimitive`), the same
 * `JsonParser`, and `Gson.fromJson`/`toJson` backed by adapters generated at build time from the
 * phone's own DTO classes (`generateWebWire`), because a Wasm binary has no reflection to read a
 * class's fields. The field-naming rule is the phone's: `@SerializedName` first (with its
 * alternates), otherwise `LOWER_CASE_WITH_UNDERSCORES`, which is how `NetworkFactory` configures Gson.
 */

open class JsonParseException(message: String?, cause: Throwable? = null) : RuntimeException(message, cause)
class JsonSyntaxException(message: String?, cause: Throwable? = null) : JsonParseException(message, cause)
class JsonIOException(message: String?) : JsonParseException(message)

@kotlinx.serialization.Serializable(with = com.coinepro.web.wire.GsonElementSerializer::class)
abstract class JsonElement {
    open val isJsonObject: Boolean get() = this is JsonObject
    open val isJsonArray: Boolean get() = this is JsonArray
    open val isJsonPrimitive: Boolean get() = this is JsonPrimitive
    open val isJsonNull: Boolean get() = this is JsonNull
    open val asJsonObject: JsonObject get() = this as? JsonObject ?: throw IllegalStateException("Not a JSON Object: $this")
    open val asJsonArray: JsonArray get() = this as? JsonArray ?: throw IllegalStateException("Not a JSON Array: $this")
    open val asJsonPrimitive: JsonPrimitive get() = this as? JsonPrimitive ?: throw IllegalStateException("Not a JSON Primitive: $this")
    open val asJsonNull: JsonNull get() = this as? JsonNull ?: throw IllegalStateException("Not a JSON Null: $this")
    open val asString: String get() = throw UnsupportedOperationException(javaClassName())
    open val asDouble: Double get() = throw UnsupportedOperationException(javaClassName())
    open val asFloat: Float get() = asDouble.toFloat()
    open val asLong: Long get() = asDouble.toLong()
    open val asInt: Int get() = asDouble.toInt()
    open val asBoolean: Boolean get() = throw UnsupportedOperationException(javaClassName())
    open val asNumber: Number get() = asDouble
    open val asBigDecimal: java.math.BigDecimal get() = java.math.BigDecimal(asString)
    fun getAsJsonObject(): JsonObject = asJsonObject
    fun getAsJsonArray(): JsonArray = asJsonArray
    fun getAsString(): String = asString
    fun getAsDouble(): Double = asDouble
    fun getAsLong(): Long = asLong
    fun getAsInt(): Int = asInt
    fun getAsBoolean(): Boolean = asBoolean
    abstract fun deepCopy(): JsonElement
    private fun javaClassName(): String = this::class.simpleName ?: "JsonElement"
    override fun toString(): String = JsonWriter.write(this)
}

object JsonNull : JsonElement() {
    val INSTANCE: JsonNull get() = this
    override fun deepCopy(): JsonElement = this
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = 0
}

@kotlinx.serialization.Serializable(with = com.coinepro.web.wire.GsonPrimitiveSerializer::class)
class JsonPrimitive private constructor(private val value: Any) : JsonElement() {
    constructor(v: String) : this(v as Any)
    constructor(v: Number) : this(v as Any)
    constructor(v: Boolean) : this(v as Any)
    constructor(v: Char) : this(v.toString() as Any)
    val isString: Boolean get() = value is String
    val isNumber: Boolean get() = value is Number
    val isBoolean: Boolean get() = value is Boolean
    override val asString: String get() = when (value) {
        is Double -> if (value == kotlin.math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
        else -> value.toString()
    }
    override val asDouble: Double get() = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: throw NumberFormatException("For input string: \"$value\"")
        is Boolean -> throw NumberFormatException("boolean")
        else -> throw NumberFormatException(value.toString())
    }
    override val asLong: Long get() = (value as? String)?.toLongOrNull() ?: asDouble.toLong()
    override val asInt: Int get() = (value as? String)?.toIntOrNull() ?: asDouble.toInt()
    override val asBoolean: Boolean get() = when (value) {
        is Boolean -> value
        is String -> value.toBoolean()
        else -> false
    }
    override val asNumber: Number get() = value as? Number ?: asDouble
    val raw: Any get() = value
    override fun deepCopy(): JsonElement = this
    override fun equals(other: Any?): Boolean = other is JsonPrimitive && (other.value == value ||
        (other.value is Number && value is Number && (other.value as Number).toDouble() == (value as Number).toDouble()))
    override fun hashCode(): Int = if (value is Number) value.toDouble().hashCode() else value.hashCode()
}

@kotlinx.serialization.Serializable(with = com.coinepro.web.wire.GsonArraySerializer::class)
class JsonArray() : JsonElement(), Iterable<JsonElement> {
    constructor(capacity: Int) : this()
    internal val items = ArrayList<JsonElement>()
    fun add(e: JsonElement?) { items.add(e ?: JsonNull) }
    fun add(s: String?) { items.add(s?.let(::JsonPrimitive) ?: JsonNull) }
    fun add(n: Number?) { items.add(n?.let(::JsonPrimitive) ?: JsonNull) }
    fun add(b: Boolean?) { items.add(b?.let(::JsonPrimitive) ?: JsonNull) }
    fun addAll(other: JsonArray) { items.addAll(other.items) }
    operator fun get(index: Int): JsonElement = items[index]
    fun set(index: Int, e: JsonElement): JsonElement = items.set(index, e)
    fun remove(index: Int): JsonElement = items.removeAt(index)
    fun size(): Int = items.size
    val isEmpty: Boolean get() = items.isEmpty()
    fun asList(): MutableList<JsonElement> = items
    override fun iterator(): Iterator<JsonElement> = items.iterator()
    override fun deepCopy(): JsonElement = JsonArray().also { a -> items.forEach { a.add(it.deepCopy()) } }
    override val asString: String get() = if (items.size == 1) items[0].asString else throw IllegalStateException("Array")
    override val asDouble: Double get() = if (items.size == 1) items[0].asDouble else throw IllegalStateException("Array")
    override val asBoolean: Boolean get() = if (items.size == 1) items[0].asBoolean else throw IllegalStateException("Array")
    override fun equals(other: Any?): Boolean = other is JsonArray && other.items == items
    override fun hashCode(): Int = items.hashCode()
}

@kotlinx.serialization.Serializable(with = com.coinepro.web.wire.GsonObjectSerializer::class)
class JsonObject : JsonElement() {
    internal val members = LinkedHashMap<String, JsonElement>()
    fun add(key: String, e: JsonElement?) { members[key] = e ?: JsonNull }
    fun addProperty(key: String, v: String?) { members[key] = v?.let(::JsonPrimitive) ?: JsonNull }
    fun addProperty(key: String, v: Number?) { members[key] = v?.let(::JsonPrimitive) ?: JsonNull }
    fun addProperty(key: String, v: Boolean?) { members[key] = v?.let(::JsonPrimitive) ?: JsonNull }
    fun addProperty(key: String, v: Char?) { members[key] = v?.let(::JsonPrimitive) ?: JsonNull }
    fun remove(key: String): JsonElement? = members.remove(key)
    fun has(key: String): Boolean = key in members
    operator fun get(key: String): JsonElement? = members[key]
    fun getAsJsonObject(key: String): JsonObject? = members[key] as? JsonObject
    fun getAsJsonArray(key: String): JsonArray? = members[key] as? JsonArray
    fun getAsJsonPrimitive(key: String): JsonPrimitive? = members[key] as? JsonPrimitive
    fun keySet(): Set<String> = members.keys
    fun entrySet(): Set<Map.Entry<String, JsonElement>> = members.entries
    fun size(): Int = members.size
    val isEmpty: Boolean get() = members.isEmpty()
    fun asMap(): MutableMap<String, JsonElement> = members
    override fun deepCopy(): JsonElement = JsonObject().also { o -> members.forEach { (k, v) -> o.add(k, v.deepCopy()) } }
    override fun equals(other: Any?): Boolean = other is JsonObject && other.members == members
    override fun hashCode(): Int = members.hashCode()
}

// ── Parsing and writing ────────────────────────────────────────────────────────────────────────

private fun parseJs(text: String): JsAny? = js("(function () { try { return JSON.parse(text); } catch (e) { return undefined; } })()")
private fun isValidJs(text: String): Boolean = js("(function () { try { JSON.parse(text); return true; } catch (e) { return false; } })()")
private fun kindOf(v: JsAny?): Int = js("v === null || v === undefined ? 0 : (typeof v === 'boolean' ? 1 : (typeof v === 'number' ? 2 : (typeof v === 'string' ? 3 : (Array.isArray(v) ? 4 : 5))))")
private fun asBool(v: JsAny?): Boolean = js("v")
private fun asNum(v: JsAny?): Double = js("v")
private fun asStr(v: JsAny?): String = js("v")
private fun arrLen(v: JsAny?): Int = js("v.length")
private fun arrAt(v: JsAny?, i: Int): JsAny? = js("v[i]")
private fun keys(v: JsAny?): JsAny = js("Object.keys(v)")
private fun prop(v: JsAny?, k: String): JsAny? = js("v[k]")
private fun keyAt(a: JsAny, i: Int): String = js("a[i]")
private fun numberSourceIsInteger(v: JsAny?): Boolean = js("Number.isInteger(v)")

internal fun treeOf(v: JsAny?): JsonElement = when (kindOf(v)) {
    0 -> JsonNull
    1 -> JsonPrimitive(asBool(v))
    2 -> asNum(v).let { d -> if (numberSourceIsInteger(v) && kotlin.math.abs(d) < 9.007199254740991E15) JsonPrimitive(d.toLong()) else JsonPrimitive(d) }
    3 -> JsonPrimitive(asStr(v))
    4 -> JsonArray().also { a -> for (i in 0 until arrLen(v)) a.add(treeOf(arrAt(v, i))) }
    else -> JsonObject().also { o ->
        val k = keys(v)
        for (i in 0 until arrLen(k)) { val key = keyAt(k, i); o.add(key, treeOf(prop(v, key))) }
    }
}

object JsonParser {
    fun parseString(json: String): JsonElement {
        if (json.isBlank()) return JsonNull
        if (!isValidJs(json)) throw JsonSyntaxException("Malformed JSON")
        return treeOf(parseJs(json))
    }
    fun parseReader(reader: java.io.Reader): JsonElement = parseString(reader.readText())
    @Deprecated("Gson API") fun parse(json: String): JsonElement = parseString(json)
}

class JsonParserInstance { fun parse(json: String): JsonElement = JsonParser.parseString(json) }

internal object JsonWriter {
    fun write(e: JsonElement): String = StringBuilder().also { write(e, it) }.toString()
    private fun write(e: JsonElement, sb: StringBuilder) {
        when (e) {
            is JsonNull -> sb.append("null")
            is JsonPrimitive -> when (val raw = e.raw) {
                is String -> quote(raw, sb)
                is Boolean -> sb.append(raw)
                is Double -> if (raw == kotlin.math.floor(raw) && !raw.isInfinite() && kotlin.math.abs(raw) < 1e15) sb.append(raw.toLong()) else sb.append(raw)
                is Float -> sb.append(raw.toDouble())
                else -> sb.append(raw.toString())
            }
            is JsonArray -> { sb.append('['); e.items.forEachIndexed { i, x -> if (i > 0) sb.append(','); write(x, sb) }; sb.append(']') }
            is JsonObject -> {
                sb.append('{')
                var first = true
                e.members.forEach { (k, v) -> if (!first) sb.append(','); first = false; quote(k, sb); sb.append(':'); write(v, sb) }
                sb.append('}')
            }
        }
    }
    fun quote(s: String, sb: StringBuilder) {
        sb.append('"')
        s.forEach { c ->
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 || c == ' ' || c == ' ' -> sb.append("\\u" + c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}

// ── Gson itself ────────────────────────────────────────────────────────────────────────────────

enum class FieldNamingPolicy { IDENTITY, UPPER_CAMEL_CASE, LOWER_CASE_WITH_UNDERSCORES, LOWER_CASE_WITH_DASHES, UPPER_CASE_WITH_UNDERSCORES }

class GsonBuilder {
    private var policy = FieldNamingPolicy.IDENTITY
    private var serializeNulls = false
    fun setFieldNamingPolicy(p: FieldNamingPolicy): GsonBuilder = apply { policy = p }
    fun serializeNulls(): GsonBuilder = apply { serializeNulls = true }
    fun setLenient(): GsonBuilder = this
    fun disableHtmlEscaping(): GsonBuilder = this
    fun setPrettyPrinting(): GsonBuilder = this
    fun registerTypeAdapter(type: Any, adapter: Any): GsonBuilder = this
    fun registerTypeAdapterFactory(factory: Any): GsonBuilder = this
    fun excludeFieldsWithoutExposeAnnotation(): GsonBuilder = this
    fun create(): Gson = Gson(policy, serializeNulls)
}

/**
 * Decoding and encoding through the generated adapters. The naming policy is carried for the
 * record; the adapters bake the phone's policy in, because the phone has exactly one.
 */
class Gson internal constructor(val policy: FieldNamingPolicy, private val serializeNulls: Boolean) {
    constructor() : this(FieldNamingPolicy.IDENTITY, false)

    fun <T : Any> fromJson(json: String?, type: KClass<T>): T? = fromJson(json?.let { JsonParser.parseString(it) }, type)

    fun <T : Any> fromJson(json: JsonElement?, type: KClass<T>): T? {
        if (json == null || json is JsonNull) return null
        return try {
            WireAdapters.decode(type, json, policy)
        } catch (e: JsonParseException) {
            throw e
        } catch (e: Exception) {
            throw JsonSyntaxException(e.message, e)
        }
    }

    fun <T> fromJson(json: String?, type: java.lang.reflect.Type): T? = fromJson(json?.let { JsonParser.parseString(it) }, type)

    fun <T> fromJson(json: JsonElement?, type: java.lang.reflect.Type): T? {
        if (json == null || json is JsonNull) return null
        return WireAdapters.decodeType(type, json, policy) as T?
    }

    fun toJson(value: Any?): String = JsonWriter.write(toJsonTree(value))

    fun toJsonTree(value: Any?): JsonElement = WireAdapters.encodeAny(value, policy)
}
