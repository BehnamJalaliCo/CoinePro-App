@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.net

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/*
 * The one door out of the page: `fetch`, through the pro-chart.com relay.
 *
 * The phone talks to TradeYar and CoinePro-FX directly. A page cannot — neither backend answers a
 * cross-origin request from pro-chart.com — so every URL the phone's code builds is first mapped by
 * [WebRoutes] onto the relay, which is the page's own origin. What the phone's code sees is the same
 * `okhttp3.Response` it always saw.
 */

class FetchResult(val status: Int, val statusText: String, val headers: List<Pair<String, String>>, val body: ByteArray, val url: String)

private fun newHeadersJs(): JsAny = js("new Headers()")
private fun appendHeaderJs(h: JsAny, name: String, value: String): Unit = js("(function(){ try { h.append(name, value); } catch (e) {} })()")
private fun bytesToJs(size: Int): JsAny = js("new Uint8Array(size)")
private fun setByteJs(a: JsAny, i: Int, b: Byte): Unit = js("a[i] = b")
private fun newFormDataJs(): JsAny = js("new FormData()")
private fun formAppendTextJs(f: JsAny, name: String, value: String): Unit = js("f.append(name, value)")
private fun formAppendFileJs(f: JsAny, name: String, bytes: JsAny, type: String, filename: String): Unit =
    js("f.append(name, new Blob([bytes], { type: type }), filename)")

private fun fetchJs(
    url: String,
    method: String,
    headers: JsAny,
    body: JsAny?,
    timeoutMs: Int,
    done: (Int, String, JsAny?, JsAny?, String) -> Unit,
): JsAny = js(
    """(function () {
        var ctl = (typeof AbortController !== 'undefined') ? new AbortController() : null;
        var timer = (ctl && timeoutMs > 0) ? setTimeout(function () { ctl.abort(); }, timeoutMs) : null;
        var init = { method: method, headers: headers, cache: 'no-store', credentials: 'same-origin' };
        if (body !== null && body !== undefined) init.body = body;
        if (ctl) init.signal = ctl.signal;
        fetch(url, init).then(function (r) {
            var hs = [];
            r.headers.forEach(function (v, k) { hs.push(k); hs.push(v); });
            return r.arrayBuffer().then(function (b) {
                if (timer) clearTimeout(timer);
                done(r.status, r.statusText || '', hs, new Int8Array(b), r.url || url);
            });
        }).catch(function (e) {
            if (timer) clearTimeout(timer);
            done(-1, String(e && e.message || e), null, null, url);
        });
        return ctl;
    })()""",
)

private fun abortJs(ctl: JsAny?): Unit = js("(function(){ if (ctl) ctl.abort(); })()")
private fun lengthJs(a: JsAny): Int = js("a.length")
private fun byteAtJs(a: JsAny, i: Int): Byte = js("a[i]")
private fun stringAtJs(a: JsAny, i: Int): String = js("String(a[i])")

internal fun ByteArray.toJsBytes(): JsAny {
    val out = bytesToJs(size)
    for (i in indices) setByteJs(out, i, this[i])
    return out
}

internal fun jsBytes(a: JsAny): ByteArray {
    val n = lengthJs(a)
    return ByteArray(n) { byteAtJs(a, it) }
}

/** A part of a multipart form: text, or a file with its bytes. */
class FormPart(val name: String, val value: String?, val bytes: ByteArray?, val contentType: String, val filename: String?)

sealed interface FetchBody {
    class Bytes(val bytes: ByteArray) : FetchBody
    class Form(val parts: List<FormPart>) : FetchBody
}

/** One HTTP exchange. A failure to connect answers status -1 rather than throwing. */
suspend fun browserFetch(
    url: String,
    method: String,
    headers: List<Pair<String, String>>,
    body: FetchBody?,
    timeoutMs: Int,
): FetchResult = suspendCancellableCoroutine { continuation ->
    val h = newHeadersJs()
    headers.forEach { (k, v) ->
        // The browser sets these itself and refuses to be told.
        if (!k.equals("User-Agent", true) && !k.equals("Content-Length", true) && !k.equals("Host", true) &&
            !(body is FetchBody.Form && k.equals("Content-Type", true))
        ) appendHeaderJs(h, k, v)
    }
    appendHeaderJs(h, "X-Client-Id", com.coinepro.web.net.ClientIdentity.value)
    val jsBody: JsAny? = when (body) {
        null -> null
        is FetchBody.Bytes -> body.bytes.toJsBytes()
        is FetchBody.Form -> newFormDataJs().also { form ->
            body.parts.forEach { part ->
                if (part.bytes != null) formAppendFileJs(form, part.name, part.bytes.toJsBytes(), part.contentType, part.filename ?: "file")
                else formAppendTextJs(form, part.name, part.value ?: "")
            }
        }
    }
    val controller = fetchJs(url, method, h, jsBody, timeoutMs) { status, text, hs, bytes, finalUrl ->
        if (!continuation.isActive) return@fetchJs
        val pairs = if (hs == null) emptyList() else (0 until lengthJs(hs) / 2).map { stringAtJs(hs, it * 2) to stringAtJs(hs, it * 2 + 1) }
        continuation.resume(FetchResult(status, text, pairs, bytes?.let(::jsBytes) ?: ByteArray(0), finalUrl))
    }
    continuation.invokeOnCancellation { abortJs(controller) }
}

private fun storedJs(key: String): String? = js("(function () { try { return localStorage.getItem(key); } catch (e) { return null; } })()")
private fun storeJs(key: String, value: String): Unit = js("(function () { try { localStorage.setItem(key, value); } catch (e) {} })()")
private fun randomIdJs(): String = js(
    "(window.crypto && crypto.randomUUID) ? crypto.randomUUID() : ('c' + Math.random().toString(36).slice(2) + Date.now().toString(36))",
)

/** The same `pc_client_id` the terminal sends (docs/web/SERVER.md §6). */
object ClientIdentity {
    val value: String by lazy { storedJs("pc_client_id") ?: randomIdJs().also { storeJs("pc_client_id", it) } }
}

fun ByteArray.toJsBytesPublic(): JsAny = toJsBytes()
fun jsBytesPublic(a: JsAny): ByteArray = jsBytes(a)
