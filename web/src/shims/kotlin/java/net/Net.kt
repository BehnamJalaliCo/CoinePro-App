@file:Suppress("unused")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package java.net

private fun encodeJs(s: String): String = js("encodeURIComponent(s).replace(/%20/g, '+')")
private fun decodeJs(s: String): String = js("(function () { try { return decodeURIComponent(s.replace(/\\+/g, ' ')); } catch (e) { return s; } })()")

object URLEncoder {
    fun encode(s: String, charset: String): String = encodeJs(s)
    fun encode(s: String, charset: Any): String = encodeJs(s)
}

object URLDecoder {
    fun decode(s: String, charset: String): String = decodeJs(s)
    fun decode(s: String, charset: Any): String = decodeJs(s)
}

open class SocketTimeoutException(message: String? = null) : java.io.IOException(message)
open class UnknownHostException(message: String? = null) : java.io.IOException(message)
open class ConnectException(message: String? = null) : java.io.IOException(message)

class URISyntaxException(input: String, reason: String) : Exception("$reason: $input")

/** A parsed URI: scheme, host, port, path, query, fragment — enough for the app's link checks. */
class URI(private val text: String) {
    val scheme: String?
    val host: String?
    val port: Int
    val path: String?
    val query: String?
    val fragment: String?
    val rawQuery: String? get() = query
    val rawPath: String? get() = path
    val authority: String? get() = host?.let { if (port >= 0) "$it:$port" else it }

    init {
        var rest = text
        fragment = rest.substringAfter('#', "").ifEmpty { null }
        rest = rest.substringBefore('#')
        query = rest.substringAfter('?', "").ifEmpty { null }
        rest = rest.substringBefore('?')
        val schemeEnd = rest.indexOf(':')
        if (schemeEnd > 0 && rest.substring(0, schemeEnd).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) {
            scheme = rest.substring(0, schemeEnd)
            rest = rest.substring(schemeEnd + 1)
        } else scheme = null
        if (rest.startsWith("//")) {
            val auth = rest.substring(2).substringBefore('/')
            rest = rest.substring(2 + auth.length)
            val hostPort = auth.substringAfter('@')
            if (hostPort.contains(':')) {
                host = hostPort.substringBefore(':'); port = hostPort.substringAfter(':').toIntOrNull() ?: -1
            } else { host = hostPort.ifEmpty { null }; port = -1 }
        } else { host = null; port = -1 }
        path = rest
    }

    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is URI && other.text == text
    override fun hashCode(): Int = text.hashCode()
    fun resolve(s: String): URI = URI(if (s.contains("://")) s else "${scheme}://${authority}${if (s.startsWith("/")) s else "/$s"}")

    companion object {
        fun create(s: String): URI = URI(s)
    }
}

class URL(private val text: String) {
    private val uri = URI(text)
    val host: String? get() = uri.host
    val protocol: String? get() = uri.scheme
    val path: String? get() = uri.path
    val query: String? get() = uri.query
    override fun toString(): String = text
    fun toURI(): URI = uri
}
