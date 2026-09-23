@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package okhttp3

import com.coinepro.web.net.WebRoutes
import okio.ByteString

interface WebSocket {
    fun request(): Request
    fun queueSize(): Long
    fun send(text: String): Boolean
    fun send(bytes: ByteString): Boolean
    fun close(code: Int, reason: String?): Boolean
    fun cancel()

    fun interface Factory {
        fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket
    }
}

abstract class WebSocketListener {
    open fun onOpen(webSocket: WebSocket, response: Response) {}
    open fun onMessage(webSocket: WebSocket, text: String) {}
    open fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
    open fun onClosing(webSocket: WebSocket, code: Int, reason: String) {}
    open fun onClosed(webSocket: WebSocket, code: Int, reason: String) {}
    open fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
}

private fun openJs(
    url: String,
    onOpen: () -> Unit,
    onText: (String) -> Unit,
    onClose: (Int, String) -> Unit,
    onError: () -> Unit,
): JsAny = js(
    """(function () {
        var ws = new WebSocket(url);
        ws.onopen = function () { onOpen(); };
        ws.onmessage = function (e) {
            if (typeof e.data === 'string') { onText(e.data); }
            else if (e.data && e.data.text) { e.data.text().then(function (t) { onText(t); }); }
        };
        ws.onclose = function (e) { onClose(e.code || 1005, e.reason || ''); };
        ws.onerror = function () { onError(); };
        return ws;
    })()""",
)

private fun sendJs(ws: JsAny, text: String): Boolean = js("(function(){ try { if (ws.readyState !== 1) return false; ws.send(text); return true; } catch (e) { return false; } })()")
private fun closeJs(ws: JsAny, code: Int, reason: String): Unit = js("(function(){ try { ws.close(code, reason); } catch (e) { try { ws.close(); } catch (e2) {} } })()")
private fun bufferedJs(ws: JsAny): Double = js("ws.bufferedAmount || 0")

/** A browser `WebSocket` behind OkHttp's interface — opened through the relay like every call. */
internal class BrowserWebSocket private constructor(private val request: Request, private val listener: WebSocketListener) : WebSocket {
    private var socket: JsAny? = null
    private var failed = false
    private var closed = false

    override fun request(): Request = request
    override fun queueSize(): Long = socket?.let { bufferedJs(it).toLong() } ?: 0L
    override fun send(text: String): Boolean = socket?.let { sendJs(it, text) } ?: false
    override fun send(bytes: ByteString): Boolean = send(bytes.utf8())
    override fun close(code: Int, reason: String?): Boolean {
        val s = socket ?: return false
        closeJs(s, code, reason ?: "")
        return true
    }
    override fun cancel() { socket?.let { closeJs(it, 1000, "") } }

    private fun response(code: Int) = Response.Builder().request(request).code(code).message("").build()

    companion object {
        fun open(request: Request, listener: WebSocketListener): WebSocket {
            val ws = BrowserWebSocket(request, listener)
            val url = WebRoutes.mapSocket(
                request.url.toString().replaceFirst("https://", "wss://").replaceFirst("http://", "ws://"),
            )
            ws.socket = openJs(
                url,
                onOpen = { listener.onOpen(ws, ws.response(101)) },
                onText = { listener.onMessage(ws, it) },
                onClose = { code, reason ->
                    if (!ws.failed && !ws.closed) {
                        ws.closed = true
                        if (code == 1006) listener.onFailure(ws, java.io.IOException("Socket closed abnormally"), null)
                        else { listener.onClosing(ws, code, reason); listener.onClosed(ws, code, reason) }
                    }
                },
                onError = {
                    if (!ws.failed && !ws.closed) {
                        ws.failed = true
                        listener.onFailure(ws, java.io.IOException("Socket failed"), null)
                    }
                },
            )
            return ws
        }
    }
}
