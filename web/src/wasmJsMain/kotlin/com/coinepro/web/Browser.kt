@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/*
 * Everything this page asks of the browser, in one file and in plain JavaScript expressions.
 *
 * Deliberately no `kotlinx-browser` and no JSON library: the terminal needs a fetch, a parse, the
 * address bar, a visibility flag and one stored id, and each of those is one line of the platform's
 * own API. A dependency for five lines is five lines nobody can read in the bundle.
 */

// ── Network ────────────────────────────────────────────────────────────────────────────────────

private fun fetchTextJs(url: String, clientId: String, done: (String?) -> Unit): Unit = js(
    """fetch(url, { headers: { 'X-Client-Id': clientId }, cache: 'no-store' })
        .then(function (r) { return r.ok ? r.text() : null; })
        .then(function (t) { done(t); }, function () { done(null); })""",
)

/** The body of a GET, or null for anything that is not a 2xx — a refusal, a timeout, no network. */
suspend fun fetchText(url: String): String? = suspendCancellableCoroutine { continuation ->
    fetchTextJs(url, ClientId.value) { text -> if (continuation.isActive) continuation.resume(text) }
}

private fun fetchBytesJs(url: String, done: (JsAny?) -> Unit): Unit = js(
    """fetch(url)
        .then(function (r) { return r.ok ? r.arrayBuffer() : null; })
        .then(function (b) { done(b ? new Int8Array(b) : null); }, function () { done(null); })""",
)

private fun byteLength(array: JsAny): Int = js("array.length")

private fun byteAt(array: JsAny, index: Int): Byte = js("array[index]")

/** A file of the bundle, as bytes. The fonts. */
suspend fun fetchBytes(url: String): ByteArray? {
    val array = suspendCancellableCoroutine<JsAny?> { continuation ->
        fetchBytesJs(url) { bytes -> if (continuation.isActive) continuation.resume(bytes) }
    } ?: return null
    return ByteArray(byteLength(array)) { byteAt(array, it) }
}

// ── JSON ───────────────────────────────────────────────────────────────────────────────────────

/** `JSON.parse`, answering null instead of throwing. A relay body is data from the network. */
fun parseJson(text: String): JsAny? = js("(function () { try { return JSON.parse(text); } catch (e) { return null; } })()")

fun jsonArray(node: JsAny, key: String): JsAny? = js("(node && Array.isArray(node[key])) ? node[key] : null")

fun jsonLength(array: JsAny): Int = js("array.length")

fun jsonItem(array: JsAny, index: Int): JsAny = js("array[index]")

/** A number field, or NaN when it is absent or not a number. */
fun jsonNumber(node: JsAny, key: String): Double = js("(node && typeof node[key] === 'number') ? node[key] : NaN")

fun jsonString(node: JsAny, key: String): String? = js("(node && typeof node[key] === 'string') ? node[key] : null")

fun jsonBoolean(node: JsAny, key: String): Boolean = js("!!(node && node[key] === true)")

/** An ISO-8601 moment in epoch seconds, or NaN. The forex candle route stamps its bars this way. */
fun isoToEpochSeconds(iso: String): Double = js("Date.parse(iso) / 1000")

/**
 * The price of one symbol out of a snapshot, found in JavaScript rather than walked from Kotlin.
 *
 * The crypto snapshot carries every instrument the venue quotes — 862 rows on 2026-09-23 — and the
 * terminal wants one of them every two seconds. Crossing the Wasm boundary 862 times to find it
 * would be most of the frame.
 */
fun priceIn(rows: JsAny, symbolKey: String, priceKey: String, symbol: String): Double = js(
    """(function () {
        for (var i = 0; i < rows.length; i++) {
            var row = rows[i];
            if (row && row[symbolKey] === symbol && typeof row[priceKey] === 'number') return row[priceKey];
        }
        return NaN;
    })()""",
)

// ── Page ───────────────────────────────────────────────────────────────────────────────────────

fun pagePath(): String = js("window.location.pathname")

fun replacePagePath(path: String): Unit = js("window.history.replaceState(null, '', path)")

/** Whether nobody is looking. A background tab spends a real reader's rate-limit budget for nothing. */
fun pageHidden(): Boolean = js("document.visibilityState === 'hidden'")

fun setDocumentLanguage(tag: String, rtl: Boolean): Unit =
    js("(function () { document.documentElement.lang = tag; document.documentElement.dir = rtl ? 'rtl' : 'ltr'; })()")

fun setDocumentTitle(title: String): Unit = js("(function () { document.title = title; })()")

/** Takes the «loading» note off the page once the first frame is drawn. */
fun hideSplash(): Unit = js("(function () { var s = document.getElementById('splash'); if (s) s.remove(); })()")

private fun storedJs(key: String): String? = js("(function () { try { return localStorage.getItem(key); } catch (e) { return null; } })()")

private fun storeJs(key: String, value: String): Unit = js("(function () { try { localStorage.setItem(key, value); } catch (e) {} })()")

private fun randomIdJs(): String = js(
    "(window.crypto && crypto.randomUUID) ? crypto.randomUUID() : ('c' + Math.random().toString(36).slice(2) + Date.now().toString(36))",
)

/** A small preference that survives a reload, or null. Private windows and blocked storage give null. */
fun stored(key: String): String? = storedJs(key)

fun store(key: String, value: String) = storeJs(key, value)

/**
 * One random id per browser, sent as `X-Client-Id` (docs/web/SERVER.md §6).
 *
 * The relay's rate buckets are keyed on address *and* client, because Iranian CGNAT puts a great
 * many readers behind one address; without this they would share one reader's budget. It
 * identifies nobody — it is random, it is never tied to anything, and clearing site data replaces
 * it.
 */
object ClientId {
    val value: String by lazy {
        stored(KEY) ?: randomIdJs().also { store(KEY, it) }
    }

    private const val KEY = "pc_client_id"
}
