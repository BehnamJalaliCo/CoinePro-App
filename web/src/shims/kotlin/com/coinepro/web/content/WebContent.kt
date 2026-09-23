@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.content

import com.coinepro.web.net.jsBytesPublic
import com.coinepro.web.net.toJsBytesPublic

/*
 * Files a reader hands the page, and files the page hands back.
 *
 * On the phone a picked file is a `content://` URI the app reads through `ContentResolver`, and a
 * saved one is a URI the system's "save as" produced. Here a picked file's bytes are held under a
 * `webcontent://` URI for the page's lifetime, and writing to a `webdownload://` URI saves the bytes
 * as a download under the name the reader was offered — the browser's own "save as".
 */
object WebContent {
    private val picked = HashMap<String, Pair<String, ByteArray>>()
    private var next = 0

    fun hold(name: String, type: String, bytes: ByteArray): String {
        val id = "webcontent://picked/${next++}/" + name.replace('/', '_')
        picked[id] = type to bytes
        return id
    }

    fun bytes(uri: String): ByteArray? = picked[uri]?.second
    fun type(uri: String): String? = picked[uri]?.first ?: uri.substringAfter("?type=", "").ifEmpty { null }

    fun downloadUri(name: String, type: String): String = "webdownload://save/${next++}/$name?type=$type"

    /** Saves [bytes] as a download, named as the URI says. */
    fun save(uri: String, bytes: ByteArray) {
        val name = uri.substringAfterLast('/').substringBefore("?type=")
        val type = uri.substringAfter("?type=", "application/octet-stream")
        downloadJs(bytes.toJsBytesPublic(), name, type)
    }

    /** Opens the browser's file chooser; [done] gets null when the reader cancels. */
    fun choose(accept: String, done: (String?) -> Unit) {
        chooseJs(accept) { name, type, data ->
            done(if (data == null) null else hold(name ?: "file", type ?: "application/octet-stream", jsBytesPublic(data)))
        }
    }
}

private fun downloadJs(bytes: JsAny, name: String, type: String): Unit = js(
    """(function () {
        var url = URL.createObjectURL(new Blob([bytes], { type: type }));
        var a = document.createElement('a'); a.href = url; a.download = name;
        document.body.appendChild(a); a.click(); a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 30000);
    })()""",
)

private fun chooseJs(accept: String, done: (String?, String?, JsAny?) -> Unit): Unit = js(
    """(function () {
        var input = document.createElement('input');
        input.type = 'file'; if (accept) input.accept = accept; input.style.display = 'none';
        var settled = false;
        input.onchange = function () {
            var f = input.files && input.files[0];
            settled = true;
            if (!f) { done(null, null, null); input.remove(); return; }
            f.arrayBuffer().then(function (b) { done(f.name, f.type, new Int8Array(b)); input.remove(); },
                                 function () { done(null, null, null); input.remove(); });
        };
        input.addEventListener('cancel', function () { if (!settled) { settled = true; done(null, null, null); input.remove(); } });
        document.body.appendChild(input);
        input.click();
    })()""",
)
