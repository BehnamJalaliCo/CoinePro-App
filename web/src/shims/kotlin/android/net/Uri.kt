@file:Suppress("unused")

package android.net

/** `android.net.Uri`, over `java.net.URI` — parse, read the parts, build, encode. */
class Uri private constructor(private val text: String) {
    private val parsed = java.net.URI(text)
    val scheme: String? get() = parsed.scheme
    val host: String? get() = parsed.host
    val port: Int get() = parsed.port
    val path: String? get() = parsed.path
    val query: String? get() = parsed.query?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    val encodedQuery: String? get() = parsed.query
    val fragment: String? get() = parsed.fragment
    val authority: String? get() = parsed.authority
    val lastPathSegment: String? get() = pathSegments.lastOrNull()
    val pathSegments: List<String> get() = (parsed.path ?: "").split('/').filter { it.isNotEmpty() }
    val queryParameterNames: Set<String> get() = queryPairs().map { it.first }.toSet()
    val isAbsolute: Boolean get() = scheme != null
    fun getQueryParameter(key: String): String? = queryPairs().firstOrNull { it.first == key }?.second
    fun getQueryParameters(key: String): List<String> = queryPairs().filter { it.first == key }.map { it.second }
    fun getBooleanQueryParameter(key: String, fallback: Boolean): Boolean = getQueryParameter(key)?.let { it != "false" && it != "0" } ?: fallback
    private fun queryPairs(): List<Pair<String, String>> = (parsed.query ?: "").split('&').filter { it.isNotEmpty() }.map {
        java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }
    fun buildUpon(): Builder = Builder().also { it.base = text }
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is Uri && other.text == text
    override fun hashCode(): Int = text.hashCode()

    class Builder {
        internal var base: String = ""
        private var scheme: String? = null
        private var authority: String? = null
        private val segments = ArrayList<String>()
        private var path: String? = null
        private val query = ArrayList<Pair<String, String>>()
        private var fragment: String? = null
        fun scheme(s: String?): Builder = apply { scheme = s }
        fun authority(a: String?): Builder = apply { authority = a }
        fun path(p: String?): Builder = apply { path = p; segments.clear() }
        fun appendPath(p: String): Builder = apply { segments += p }
        fun encodedPath(p: String?): Builder = path(p)
        fun appendEncodedPath(p: String): Builder = apply { segments += p }
        fun appendQueryParameter(k: String, v: String?): Builder = apply { query += k to (v ?: "") }
        fun clearQuery(): Builder = apply { query.clear() }
        fun fragment(f: String?): Builder = apply { fragment = f }
        fun build(): Uri {
            if (scheme == null && authority == null && base.isNotEmpty()) {
                val existing = base
                val sep = if ('?' in existing) '&' else '?'
                val q = query.joinToString("&") { "${encode(it.first)}=${encode(it.second)}" }
                return Uri(existing + (if (segments.isNotEmpty()) "/" + segments.joinToString("/") { encode(it) } else "") + (if (q.isNotEmpty()) "$sep$q" else ""))
            }
            val sb = StringBuilder()
            scheme?.let { sb.append(it).append(':') }
            authority?.let { sb.append("//").append(it) }
            path?.let { sb.append(if (it.startsWith("/") || authority == null) it else "/$it") }
            segments.forEach { sb.append('/').append(encode(it)) }
            if (query.isNotEmpty()) sb.append('?').append(query.joinToString("&") { "${encode(it.first)}=${encode(it.second)}" })
            fragment?.let { sb.append('#').append(it) }
            return Uri(sb.toString())
        }
    }

    companion object {
        val EMPTY = Uri("")
        fun parse(s: String): Uri = Uri(s)
        fun encode(s: String?): String = java.net.URLEncoder.encode(s ?: "", "UTF-8").replace("+", "%20")
        fun encode(s: String?, allow: String?): String = encode(s)
        fun decode(s: String?): String = java.net.URLDecoder.decode(s ?: "", "UTF-8")
        fun fromParts(scheme: String, ssp: String, fragment: String?): Uri = Uri("$scheme:$ssp" + (fragment?.let { "#$it" } ?: ""))
        fun fromFile(file: java.io.File): Uri = Uri("file://${file.path}")
    }
}

