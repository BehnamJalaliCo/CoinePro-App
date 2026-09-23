@file:Suppress("unused")

package org.json

import com.google.gson.JsonArray as GArray
import com.google.gson.JsonElement as GElement
import com.google.gson.JsonNull as GNull
import com.google.gson.JsonObject as GObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive as GPrimitive

/** Android's `org.json`, over the same tree the Gson stand-in keeps. */
class JSONException(message: String?) : Exception(message)

private fun wrap(e: GElement?): Any? = when (e) {
    null, is GNull -> JSONObject.NULL
    is GObject -> JSONObject(e)
    is GArray -> JSONArray(e)
    is GPrimitive -> when {
        e.isBoolean -> e.asBoolean
        e.isNumber -> e.raw as Number
        else -> e.asString
    }
    else -> null
}

private fun unwrap(v: Any?): GElement = when (v) {
    null, JSONObject.NULL -> GNull
    is JSONObject -> v.tree
    is JSONArray -> v.tree
    is String -> GPrimitive(v)
    is Number -> GPrimitive(v)
    is Boolean -> GPrimitive(v)
    is Map<*, *> -> objectOf(v)
    is Collection<*> -> arrayOf(v)
    else -> GPrimitive(v.toString())
}

private fun parsed(text: String): GElement? = try { JsonParser.parseString(text) } catch (e: Exception) { null }
private fun parsedObject(text: String): GObject = parsed(text) as? GObject ?: throw JSONException("Value is not a JSON object")
private fun parsedArray(text: String): GArray = parsed(text) as? GArray ?: throw JSONException("Value is not a JSON array")
private fun objectOf(map: Map<*, *>): GObject = GObject().also { o -> map.forEach { (k, v) -> o.add(k.toString(), unwrap(v)) } }
private fun arrayOf(items: Collection<*>): GArray = GArray().also { a -> items.forEach { a.add(unwrap(it)) } }

class JSONObject internal constructor(internal val tree: GObject) {
    constructor() : this(GObject())
    constructor(text: String) : this(parsedObject(text))
    constructor(map: Map<*, *>) : this(objectOf(map))

    fun put(name: String, value: Any?): JSONObject = apply { tree.add(name, unwrap(value)) }
    fun putOpt(name: String, value: Any?): JSONObject = apply { if (value != null) put(name, value) }
    fun has(name: String): Boolean = tree.has(name)
    fun isNull(name: String): Boolean = tree.get(name).let { it == null || it is GNull }
    fun get(name: String): Any = wrap(tree.get(name) ?: throw JSONException("No value for $name"))!!
    fun opt(name: String): Any? = tree.get(name)?.let(::wrap)
    fun getString(name: String): String = (tree.get(name) ?: throw JSONException("No value for $name")).let { if (it is GPrimitive) it.asString else it.toString() }
    fun optString(name: String, fallback: String = ""): String = tree.get(name)?.takeIf { it !is GNull }?.let { if (it is GPrimitive) it.asString else it.toString() } ?: fallback
    fun getInt(name: String): Int = (tree.get(name) ?: throw JSONException("No value for $name")).asInt
    fun optInt(name: String, fallback: Int = 0): Int = runCatching { tree.get(name)!!.asInt }.getOrDefault(fallback)
    fun getLong(name: String): Long = (tree.get(name) ?: throw JSONException("No value for $name")).asLong
    fun optLong(name: String, fallback: Long = 0L): Long = runCatching { tree.get(name)!!.asLong }.getOrDefault(fallback)
    fun getDouble(name: String): Double = (tree.get(name) ?: throw JSONException("No value for $name")).asDouble
    fun optDouble(name: String, fallback: Double = Double.NaN): Double = runCatching { tree.get(name)!!.asDouble }.getOrDefault(fallback)
    fun getBoolean(name: String): Boolean = (tree.get(name) ?: throw JSONException("No value for $name")).asBoolean
    fun optBoolean(name: String, fallback: Boolean = false): Boolean = runCatching { tree.get(name)!!.asBoolean }.getOrDefault(fallback)
    fun getJSONObject(name: String): JSONObject = (tree.get(name) as? GObject)?.let(::JSONObject) ?: throw JSONException("Not an object: $name")
    fun optJSONObject(name: String): JSONObject? = (tree.get(name) as? GObject)?.let(::JSONObject)
    fun getJSONArray(name: String): JSONArray = (tree.get(name) as? GArray)?.let(::JSONArray) ?: throw JSONException("Not an array: $name")
    fun optJSONArray(name: String): JSONArray? = (tree.get(name) as? GArray)?.let(::JSONArray)
    fun keys(): Iterator<String> = tree.keySet().toList().iterator()
    fun names(): JSONArray? = if (tree.size() == 0) null else JSONArray(tree.keySet().toList())
    fun length(): Int = tree.size()
    fun remove(name: String): Any? = tree.remove(name)?.let(::wrap)
    override fun toString(): String = tree.toString()
    fun toString(indent: Int): String = tree.toString()

    companion object {
        val NULL: Any = object { override fun toString() = "null" }
        fun quote(s: String?): String = GPrimitive(s ?: "").toString()
    }
}

class JSONArray internal constructor(internal val tree: GArray) {
    constructor() : this(GArray())
    constructor(text: String) : this(parsedArray(text))
    constructor(items: Collection<*>) : this(arrayOf(items))

    fun length(): Int = tree.size()
    fun put(value: Any?): JSONArray = apply { tree.add(unwrap(value)) }
    fun get(index: Int): Any = wrap(tree[index])!!
    fun opt(index: Int): Any? = if (index in 0 until tree.size()) wrap(tree[index]) else null
    fun getString(index: Int): String = tree[index].let { if (it is GPrimitive) it.asString else it.toString() }
    fun optString(index: Int, fallback: String = ""): String = runCatching { getString(index) }.getOrDefault(fallback)
    fun getInt(index: Int): Int = tree[index].asInt
    fun getLong(index: Int): Long = tree[index].asLong
    fun getDouble(index: Int): Double = tree[index].asDouble
    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = runCatching { tree[index].asDouble }.getOrDefault(fallback)
    fun getBoolean(index: Int): Boolean = tree[index].asBoolean
    fun getJSONObject(index: Int): JSONObject = (tree[index] as? GObject)?.let(::JSONObject) ?: throw JSONException("Not an object at $index")
    fun optJSONObject(index: Int): JSONObject? = (tree.asList().getOrNull(index) as? GObject)?.let(::JSONObject)
    fun getJSONArray(index: Int): JSONArray = (tree[index] as? GArray)?.let(::JSONArray) ?: throw JSONException("Not an array at $index")
    override fun toString(): String = tree.toString()
}
