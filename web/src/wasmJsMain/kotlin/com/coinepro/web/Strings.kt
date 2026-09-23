package com.coinepro.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.coinepro.core.chart.formatFixed

/*
 * The phone's strings in the browser: `strings/en.json` and `strings/fa.json`, exported at build
 * time from every module's `values/` and `values-fa/` by `exportTerminalStrings`. A key here is the
 * key in `R.string`, so a screen ported from the phone keeps its words by keeping its keys.
 */

/** One language's table. */
class StringTable(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>)

object Strings {
    /** Persian or English — the page's one language switch. State, so the whole page redraws. */
    var persian: Boolean by mutableStateOf(true)

    private var english = StringTable(emptyMap(), emptyMap())
    private var farsi = StringTable(emptyMap(), emptyMap())

    val table: StringTable get() = if (persian) farsi else english

    /** Loads both tables. False when either failed, which the page reports rather than hides. */
    suspend fun load(): Boolean {
        val en = fetchText("strings/en.json")?.let(::parseTable)
        val fa = fetchText("strings/fa.json")?.let(::parseTable)
        if (en != null) english = en
        if (fa != null) farsi = fa
        return en != null && fa != null
    }

    /** The key in the page's language, falling back to English and then to the key itself. */
    fun get(key: String): String = table.strings[key] ?: english.strings[key] ?: key

    fun plural(key: String, count: Int): String {
        val forms = table.plurals[key] ?: english.plurals[key] ?: return key
        // Persian has one form in CLDR and Android's `other` covers it; English has one/other.
        val form = if (count == 1) forms["one"] ?: forms["other"] else forms["other"] ?: forms["one"]
        return form ?: key
    }

    private fun parseTable(text: String): StringTable? {
        val root = parseJson(text) ?: return null
        return StringTable(objectOfStrings(root, "strings"), objectOfObjects(root, "plurals"))
    }
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun keysOf(node: JsAny): JsAny = js("Object.keys(node)")

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun childOf(node: JsAny, key: String): JsAny? = js("(node && typeof node[key] === 'object') ? node[key] : null")

private fun objectOfStrings(root: JsAny, name: String): Map<String, String> {
    val node = childOf(root, name) ?: return emptyMap()
    val keys = keysOf(node)
    val out = HashMap<String, String>(jsonLength(keys) * 2)
    for (index in 0 until jsonLength(keys)) {
        val key = jsonStringAt(keys, index)
        jsonString(node, key)?.let { out[key] = it }
    }
    return out
}

private fun objectOfObjects(root: JsAny, name: String): Map<String, Map<String, String>> {
    val node = childOf(root, name) ?: return emptyMap()
    val keys = keysOf(node)
    return (0 until jsonLength(keys)).associate { index ->
        val key = jsonStringAt(keys, index)
        key to (childOf(node, key)?.let { inner ->
            val innerKeys = keysOf(inner)
            (0 until jsonLength(innerKeys)).associate { k ->
                val form = jsonStringAt(innerKeys, k)
                form to (jsonString(inner, form) ?: "")
            }
        } ?: emptyMap())
    }
}

/**
 * Android's `getString(id, args)` for the subset its strings use: `%s`, `%d`, `%1$s`, `%2$d`,
 * `%.1f`, `%%`. Latin digits always — a count that must read in Persian digits is formatted by
 * the caller before it arrives, exactly as on the phone.
 */
fun formatAndroid(template: String, vararg args: Any?): String {
    val out = StringBuilder(template.length + 16)
    var next = 0
    var i = 0
    while (i < template.length) {
        val c = template[i]
        if (c != '%' || i + 1 >= template.length) { out.append(c); i++; continue }
        if (template[i + 1] == '%') { out.append('%'); i += 2; continue }
        var j = i + 1
        var position: Int? = null
        val digitsStart = j
        while (j < template.length && template[j].isDigit()) j++
        if (j < template.length && template[j] == '$' && j > digitsStart) {
            position = template.substring(digitsStart, j).toInt() - 1
            j++
        } else {
            j = i + 1
        }
        var precision: Int? = null
        if (j < template.length && template[j] == '.') {
            val start = j + 1
            j = start
            while (j < template.length && template[j].isDigit()) j++
            precision = template.substring(start, j).toIntOrNull()
        }
        if (j >= template.length) { out.append(template.substring(i)); break }
        val conversion = template[j]
        val arg = args.getOrNull(position ?: next++)
        out.append(
            when (conversion) {
                'f' -> formatFixed((arg as? Number)?.toDouble() ?: 0.0, precision ?: 6)
                'd' -> (arg as? Number)?.toLong()?.toString() ?: arg.toString()
                else -> arg?.toString() ?: ""
            },
        )
        i = j + 1
    }
    return out.toString()
}

/** `stringResource(R.string.key)`, in the browser. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun str(key: String): String {
    // A read of the language state, so a switch redraws every caller.
    Strings.persian
    return Strings.get(key)
}

/** `stringResource(R.string.key, args)`. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun str(key: String, vararg args: Any?): String = formatAndroid(str(key), *args)

/** `pluralStringResource(R.plurals.key, count, args)`. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun plural(key: String, count: Int, vararg args: Any?): String {
    Strings.persian
    return formatAndroid(Strings.plural(key, count), *args)
}

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
internal fun objectOfStringsPublic(root: JsAny, name: String): Map<String, String> = objectOfStrings(root, name)
