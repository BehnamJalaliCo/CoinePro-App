@file:Suppress("UNCHECKED_CAST")
@file:OptIn(kotlinx.serialization.InternalSerializationApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)

package com.coinepro.web.wire

import com.google.gson.FieldNamingPolicy
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import java.lang.reflect.WireType
import kotlin.reflect.KClass
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.serializerOrNull
import kotlinx.serialization.json.JsonArray as KxArray
import kotlinx.serialization.json.JsonElement as KxElement
import kotlinx.serialization.json.JsonNull as KxNull
import kotlinx.serialization.json.JsonObject as KxObject
import kotlinx.serialization.json.JsonPrimitive as KxPrimitive

/*
 * How the phone's wire classes cross the network in a browser.
 *
 * On the phone Gson reads a class's fields by reflection. A Wasm binary has none, so the build
 * (`shareSources` in web/build.gradle.kts) marks every class the phone decodes or encodes with
 * `@Serializable`, turns `@SerializedName` into `@SerialName`/`@JsonNames` and gives every other
 * field its `LOWER_CASE_WITH_UNDERSCORES` name with the field's own name as an alternate — the
 * phone's `NetworkFactory` policy, which is the only one it configures. What Gson forgives, this is
 * configured to forgive too: unknown keys, quoted numbers, a missing or null field.
 */

val WireJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
}

// ── Gson tree ↔ kotlinx tree ───────────────────────────────────────────────────────────────────

fun toKx(e: JsonElement?): KxElement = when (e) {
    null, is JsonNull -> KxNull
    is JsonPrimitive -> when (val raw = e.raw) {
        is String -> KxPrimitive(raw)
        is Boolean -> KxPrimitive(raw)
        is Number -> KxPrimitive(raw)
        else -> KxPrimitive(raw.toString())
    }
    is JsonArray -> KxArray(e.map(::toKx))
    is JsonObject -> KxObject(e.entrySet().associate { it.key to toKx(it.value) })
    else -> KxNull
}

fun fromKx(e: KxElement): JsonElement = when (e) {
    is KxNull -> JsonNull
    is KxPrimitive -> when {
        e.isString -> JsonPrimitive(e.content)
        e.booleanOrNull != null -> JsonPrimitive(e.booleanOrNull!!)
        else -> e.content.toLongOrNull()?.let { JsonPrimitive(it) } ?: e.content.toDoubleOrNull()?.let { JsonPrimitive(it) }
            ?: JsonPrimitive(e.content)
    }
    is KxArray -> JsonArray().also { a -> e.forEach { a.add(fromKx(it)) } }
    is KxObject -> JsonObject().also { o -> e.forEach { (k, v) -> o.add(k, fromKx(v)) } }
}

abstract class GsonTreeSerializer<T : JsonElement>(name: String) : KSerializer<T> {
    override val descriptor: SerialDescriptor = SerialDescriptor(name, KxElement.serializer().descriptor)
    protected abstract fun cast(e: JsonElement): T
    override fun deserialize(decoder: Decoder): T = cast(fromKx((decoder as JsonDecoder).decodeJsonElement()))
    override fun serialize(encoder: Encoder, value: T) = (encoder as JsonEncoder).encodeJsonElement(toKx(value))
}

object GsonElementSerializer : GsonTreeSerializer<JsonElement>("com.google.gson.JsonElement") {
    override fun cast(e: JsonElement): JsonElement = e
}

object GsonObjectSerializer : GsonTreeSerializer<JsonObject>("com.google.gson.JsonObject") {
    override fun cast(e: JsonElement): JsonObject = e as? JsonObject ?: throw JsonSyntaxException("Expected an object")
}

object GsonArraySerializer : GsonTreeSerializer<JsonArray>("com.google.gson.JsonArray") {
    override fun cast(e: JsonElement): JsonArray = e as? JsonArray ?: throw JsonSyntaxException("Expected an array")
}

object GsonPrimitiveSerializer : GsonTreeSerializer<JsonPrimitive>("com.google.gson.JsonPrimitive") {
    override fun cast(e: JsonElement): JsonPrimitive = e as? JsonPrimitive ?: throw JsonSyntaxException("Expected a primitive")
}

// ── Decoding and encoding by class, for `Gson` ─────────────────────────────────────────────────

object WireAdapters {
    fun <T : Any> decode(type: KClass<T>, json: JsonElement, policy: FieldNamingPolicy): T {
        val value: Any = when (type) {
            JsonElement::class -> json
            JsonObject::class -> json.asJsonObject
            JsonArray::class -> json.asJsonArray
            JsonPrimitive::class -> json.asJsonPrimitive
            String::class -> json.asString
            Int::class -> json.asInt
            Long::class -> json.asLong
            Double::class -> json.asDouble
            Float::class -> json.asFloat
            Boolean::class -> json.asBoolean
            else -> {
                val serializer = type.serializerOrNull()
                    ?: throw JsonParseException("No wire adapter for ${type.simpleName}: add it to the shared DTO set")
                WireJson.decodeFromJsonElement(serializer, toKx(json))
            }
        }
        return value as T
    }

    fun decodeType(type: java.lang.reflect.Type, json: JsonElement, policy: FieldNamingPolicy): Any? {
        if (json is JsonNull) return null
        val wire = type as? WireType ?: throw JsonParseException("Unsupported type $type")
        return when (wire.raw) {
            List::class, MutableList::class, ArrayList::class, Collection::class, Iterable::class ->
                json.asJsonArray.map { decodeType(wire.arguments.first(), it, policy) }
            Set::class, MutableSet::class, HashSet::class, LinkedHashSet::class ->
                json.asJsonArray.map { decodeType(wire.arguments.first(), it, policy) }.toCollection(LinkedHashSet())
            Map::class, MutableMap::class, HashMap::class, LinkedHashMap::class ->
                json.asJsonObject.entrySet().associate { (k, v) -> k to decodeType(wire.arguments[1], v, policy) }
            else -> decode(wire.raw as KClass<Any>, json, policy)
        }
    }

    fun encodeAny(value: Any?, policy: FieldNamingPolicy): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Char -> JsonPrimitive(value.toString())
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject().also { o ->
            value.forEach { (k, v) -> if (v != null) o.add(k.toString(), encodeAny(v, policy)) }
        }
        is Iterable<*> -> JsonArray().also { a -> value.forEach { a.add(encodeAny(it, policy)) } }
        is Array<*> -> JsonArray().also { a -> value.forEach { a.add(encodeAny(it, policy)) } }
        is IntArray -> JsonArray().also { a -> value.forEach { a.add(it) } }
        is LongArray -> JsonArray().also { a -> value.forEach { a.add(it) } }
        is DoubleArray -> JsonArray().also { a -> value.forEach { a.add(it) } }
        else -> {
            val serializer = (value::class as KClass<Any>).serializerOrNull()
            when {
                serializer != null -> fromKx(WireJson.encodeToJsonElement(serializer, value))
                value is Enum<*> -> JsonPrimitive(value.name)
                else -> throw JsonParseException("No wire adapter for ${value::class.simpleName}: add it to the shared DTO set")
            }
        }
    }
}

/** The body of a response as the declared type — what the generated Retrofit services call. */
inline fun <reified T> decodeWire(text: String): T {
    val serializer = kotlinx.serialization.serializer<T>()
    return WireJson.decodeFromString(serializer, text.ifBlank { "null" })
}

/** A request body as JSON — `@Body` in the generated services. */
inline fun <reified T> encodeWire(value: T): String =
    if (value is JsonElement) value.toString() else WireJson.encodeToString(kotlinx.serialization.serializer<T>(), value)

/**
 * A field the phone declares as `Any?`: Gson fills it with what the JSON holds — a map, a list, a
 * double, a string, a boolean — and so does this.
 */
object AnyValueSerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = SerialDescriptor("kotlin.Any", KxElement.serializer().descriptor)
    override fun deserialize(decoder: Decoder): Any? = plain((decoder as JsonDecoder).decodeJsonElement())
    override fun serialize(encoder: Encoder, value: Any?) =
        (encoder as JsonEncoder).encodeJsonElement(toKx(WireAdapters.encodeAny(value, FieldNamingPolicy.IDENTITY)))

    private fun plain(e: KxElement): Any? = when (e) {
        is KxNull -> null
        is KxPrimitive -> if (e.isString) e.content else e.booleanOrNull ?: e.content.toDoubleOrNull() ?: e.content
        is KxArray -> e.map(::plain)
        is KxObject -> e.mapValues { plain(it.value) }
    }
}
