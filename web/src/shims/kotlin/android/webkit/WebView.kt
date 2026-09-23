@file:Suppress("unused", "UNUSED_PARAMETER")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package android.webkit

import android.content.Context
import android.net.Uri
import android.view.View

private fun iframeJs(): JsAny = js("(function(){ var f = document.createElement('iframe'); f.style.border = '0'; f.setAttribute('allow', 'clipboard-read; clipboard-write'); f.setAttribute('referrerpolicy', 'no-referrer'); return f; })()")
private fun setSrcJs(f: JsAny, url: String): Unit = js("f.src = url")
private fun onLoadJs(f: JsAny, done: () -> Unit): Unit = js("f.addEventListener('load', function(){ done(); })")
private fun onErrorJs(f: JsAny, done: () -> Unit): Unit = js("f.addEventListener('error', function(){ done(); })")
private fun removeJs(f: JsAny): Unit = js("(function(){ if (f.parentNode) f.parentNode.removeChild(f); })()")
private fun backgroundJs(f: JsAny, css: String): Unit = js("f.style.background = css")

/**
 * A WebView, in a page: an `<iframe>` laid over the canvas where the phone would put the view.
 * The page it shows keeps its own history, which a parent page cannot read or walk, so
 * [canGoBack] is false and back leaves the screen — the one behaviour that differs, and the
 * reason is the browser's cross-origin rule, not a choice here.
 */
class WebView(context: Context) : View(context) {
    private val frame: JsAny = iframeJs()
    override val element: JsAny get() = frame
    val settings: WebSettings = WebSettings()
    var webViewClient: WebViewClient = WebViewClient()
    var url: String? = null
        private set

    init {
        onLoadJs(frame) { url?.let { webViewClient.onPageFinished(this, it) } }
        onErrorJs(frame) {
            webViewClient.onReceivedError(this, WebResourceRequest(Uri.parse(url ?: ""), true), WebResourceError(-1, "failed"))
        }
    }

    fun loadUrl(url: String) { this.url = url; setSrcJs(frame, url) }
    fun loadUrl(url: String, headers: Map<String, String>) = loadUrl(url)
    fun reload() { url?.let { setSrcJs(frame, it) } }
    fun canGoBack(): Boolean = false
    fun goBack() {}
    fun stopLoading() {}
    fun destroy() { removeJs(frame) }
    fun evaluateJavascript(script: String, callback: ((String) -> Unit)?) { callback?.invoke("null") }
    fun addJavascriptInterface(target: Any, name: String) {}
    override fun setBackgroundColor(color: Int) { if (color == 0) backgroundJs(frame, "transparent") }
}

@Target(AnnotationTarget.FUNCTION)
annotation class JavascriptInterface

class WebSettings {
    var javaScriptEnabled = true
    var domStorageEnabled = true
    var databaseEnabled = true
    var loadWithOverviewMode = false
    var useWideViewPort = true
    var builtInZoomControls = false
    var displayZoomControls = false
    var allowFileAccess = false
    var allowContentAccess = false
    var mixedContentMode = MIXED_CONTENT_NEVER_ALLOW
    var userAgentString: String? = null
    var mediaPlaybackRequiresUserGesture = true
    fun setSupportZoom(support: Boolean) {}
    companion object {
        const val MIXED_CONTENT_NEVER_ALLOW = 1
        const val MIXED_CONTENT_ALWAYS_ALLOW = 0
    }
}

open class WebViewClient {
    open fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {}
    open fun onPageFinished(view: WebView, url: String) {}
    open fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {}
    open fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
}

open class WebChromeClient

class WebResourceRequest(val url: Uri, val isForMainFrame: Boolean) {
    val method: String get() = "GET"
    val requestHeaders: Map<String, String> get() = emptyMap()
}

class WebResourceError(val errorCode: Int, val description: CharSequence)

object CookieManager {
    fun getInstance(): CookieManager = this
    fun setAcceptCookie(accept: Boolean) {}
    fun removeAllCookies(callback: ((Boolean) -> Unit)?) { callback?.invoke(true) }
    fun flush() {}
}
