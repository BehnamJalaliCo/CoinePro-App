@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package okhttp3

import com.coinepro.web.net.FetchBody
import com.coinepro.web.net.FormPart
import com.coinepro.web.net.WebRoutes
import com.coinepro.web.net.browserFetch
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/*
 * OkHttp, for the browser: the same types the phone's gateways and interceptors are written against,
 * over `fetch`. One difference the phone's code never sees: an interceptor's `intercept` and a
 * chain's `proceed` suspend, because a page cannot block — the build marks the phone's
 * `override fun intercept` as `suspend` when it compiles it here (web/build.gradle.kts).
 */

// ── Media types and bodies ─────────────────────────────────────────────────────────────────────

class MediaType private constructor(private val text: String) {
    val type: String get() = text.substringBefore('/')
    val subtype: String get() = text.substringAfter('/').substringBefore(';')
    fun charset(fallback: Any? = null): Any? = fallback
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is MediaType && other.text == text
    override fun hashCode(): Int = text.hashCode()

    companion object {
        fun String.toMediaType(): MediaType = MediaType(this)
        fun String.toMediaTypeOrNull(): MediaType? = if ('/' in this) MediaType(this) else null
        fun get(text: String): MediaType = MediaType(text)
        fun parse(text: String): MediaType? = text.toMediaTypeOrNull()
    }
}

abstract class RequestBody {
    abstract fun contentType(): MediaType?
    open fun contentLength(): Long = -1
    internal abstract fun toFetch(): FetchBody

    companion object {
        fun String.toRequestBody(contentType: MediaType? = null): RequestBody = BytesBody(encodeToByteArray(), contentType)
        fun ByteArray.toRequestBody(contentType: MediaType? = null, offset: Int = 0, byteCount: Int = size): RequestBody =
            BytesBody(copyOfRange(offset, offset + byteCount), contentType)
        fun create(contentType: MediaType?, content: String): RequestBody = BytesBody(content.encodeToByteArray(), contentType)
        fun create(contentType: MediaType?, content: ByteArray): RequestBody = BytesBody(content, contentType)
        fun create(content: String, contentType: MediaType?): RequestBody = BytesBody(content.encodeToByteArray(), contentType)
    }
}

internal class BytesBody(val bytes: ByteArray, private val type: MediaType?) : RequestBody() {
    override fun contentType(): MediaType? = type
    override fun contentLength(): Long = bytes.size.toLong()
    override fun toFetch(): FetchBody = FetchBody.Bytes(bytes)
}

class MultipartBody private constructor(val parts: List<Part>, private val type: MediaType) : RequestBody() {
    override fun contentType(): MediaType = type
    override fun toFetch(): FetchBody = FetchBody.Form(parts.map { it.toForm() })

    class Part private constructor(val name: String, val filename: String?, val body: RequestBody) {
        internal fun toForm(): FormPart {
            val bytes = (body as? BytesBody)?.bytes ?: ByteArray(0)
            return if (filename == null) FormPart(name, bytes.decodeToString(), null, "text/plain", null)
            else FormPart(name, null, bytes, body.contentType()?.toString() ?: "application/octet-stream", filename)
        }

        companion object {
            fun createFormData(name: String, value: String): Part = Part(name, null, BytesBody(value.encodeToByteArray(), null))
            fun createFormData(name: String, filename: String?, body: RequestBody): Part = Part(name, filename ?: "file", body)
        }
    }

    class Builder(private val boundary: String = "web") {
        private val parts = ArrayList<Part>()
        private var type: MediaType = FORM
        fun setType(type: MediaType): Builder = apply { this.type = type }
        fun addPart(part: Part): Builder = apply { parts += part }
        fun addFormDataPart(name: String, value: String): Builder = apply { parts += Part.createFormData(name, value) }
        fun addFormDataPart(name: String, filename: String?, body: RequestBody): Builder = apply { parts += Part.createFormData(name, filename, body) }
        fun build(): MultipartBody = MultipartBody(parts.toList(), type)
    }

    companion object {
        val FORM: MediaType = with(MediaType) { "multipart/form-data".toMediaType() }
        val MIXED: MediaType = with(MediaType) { "multipart/mixed".toMediaType() }
    }
}

class FormBody private constructor(private val pairs: List<Pair<String, String>>) : RequestBody() {
    override fun contentType(): MediaType = with(MediaType) { "application/x-www-form-urlencoded".toMediaType() }
    override fun toFetch(): FetchBody = FetchBody.Bytes(
        pairs.joinToString("&") { java.net.URLEncoder.encode(it.first, "UTF-8") + "=" + java.net.URLEncoder.encode(it.second, "UTF-8") }
            .encodeToByteArray(),
    )
    class Builder {
        private val pairs = ArrayList<Pair<String, String>>()
        fun add(name: String, value: String): Builder = apply { pairs += name to value }
        fun build(): FormBody = FormBody(pairs.toList())
    }
}

abstract class ResponseBody : java.io.Closeable {
    abstract fun contentType(): MediaType?
    abstract fun contentLength(): Long
    abstract fun bytes(): ByteArray
    fun string(): String = bytes().decodeToString()
    fun byteStream(): java.io.InputStream = java.io.ByteArrayInputStream(bytes())
    fun charStream(): java.io.Reader = java.io.StringReader(string())
    override fun close() {}

    companion object {
        fun String.toResponseBody(contentType: MediaType? = null): ResponseBody = BytesResponseBody(encodeToByteArray(), contentType)
        fun ByteArray.toResponseBody(contentType: MediaType? = null): ResponseBody = BytesResponseBody(this, contentType)
        fun create(contentType: MediaType?, content: String): ResponseBody = BytesResponseBody(content.encodeToByteArray(), contentType)
    }
}

internal class BytesResponseBody(private val data: ByteArray, private val type: MediaType?) : ResponseBody() {
    override fun contentType(): MediaType? = type
    override fun contentLength(): Long = data.size.toLong()
    override fun bytes(): ByteArray = data
}

// ── URLs and headers ───────────────────────────────────────────────────────────────────────────

class HttpUrl private constructor(private val text: String) {
    val scheme: String get() = text.substringBefore("://")
    val host: String get() = text.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
    val port: Int get() = text.substringAfter("://").substringBefore('/').substringAfter(':', "").toIntOrNull() ?: if (isHttps) 443 else 80
    val isHttps: Boolean get() = scheme == "https"
    val encodedPath: String get() = "/" + text.substringAfter("://").substringAfter('/', "").substringBefore('?').substringBefore('#')
    val pathSegments: List<String> get() = encodedPath.removePrefix("/").split('/').map { java.net.URLDecoder.decode(it, "UTF-8") }
    val encodedQuery: String? get() = text.substringAfter('?', "").substringBefore('#').takeIf { '?' in text }
    val query: String? get() = encodedQuery?.let { java.net.URLDecoder.decode(it, "UTF-8") }
    val queryParameterNames: Set<String> get() = queryPairs().map { it.first }.toCollection(LinkedHashSet())
    fun queryParameter(name: String): String? = queryPairs().firstOrNull { it.first == name }?.second
    fun queryParameterValues(name: String): List<String?> = queryPairs().filter { it.first == name }.map { it.second }
    private fun queryPairs(): List<Pair<String, String>> = encodedQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map {
        java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
    }
    fun resolve(link: String): HttpUrl? = runCatching { HttpUrl(java.net.URI(text).resolve(link).toString()) }.getOrNull()
    fun newBuilder(): Builder = Builder(text)
    fun toUrl(): java.net.URL = java.net.URL(text)
    fun toUri(): java.net.URI = java.net.URI(text)
    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is HttpUrl && other.text == text
    override fun hashCode(): Int = text.hashCode()

    class Builder internal constructor(private var base: String) {
        constructor() : this("")
        private val extra = ArrayList<Pair<String, String?>>()
        private val segments = ArrayList<String>()
        fun scheme(scheme: String): Builder = apply { base = "$scheme://" + base.substringAfter("://", "") }
        fun host(host: String): Builder = apply { base = base.substringBefore("://", "https") + "://" + host }
        fun addPathSegment(segment: String): Builder = apply { segments += java.net.URLEncoder.encode(segment, "UTF-8") }
        fun addPathSegments(path: String): Builder = apply { segments += path.trim('/') }
        fun addEncodedPathSegments(path: String): Builder = apply { segments += path.trim('/') }
        fun addQueryParameter(name: String, value: String?): Builder = apply { extra += name to value }
        fun addEncodedQueryParameter(name: String, value: String?): Builder = apply { extra += name to value }
        fun setQueryParameter(name: String, value: String?): Builder = apply { extra.removeAll { it.first == name }; extra += name to value }
        fun build(): HttpUrl {
            var url = base
            if (segments.isNotEmpty()) {
                val path = url.substringBefore('?')
                val q = url.substringAfter('?', "")
                url = path.trimEnd('/') + "/" + segments.joinToString("/") + if (q.isNotEmpty()) "?$q" else ""
            }
            if (extra.isNotEmpty()) {
                val encoded = extra.joinToString("&") { (k, v) ->
                    java.net.URLEncoder.encode(k, "UTF-8") + (v?.let { "=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
                }
                url += (if ('?' in url) "&" else "?") + encoded
            }
            return HttpUrl(url)
        }
    }

    companion object {
        fun String.toHttpUrl(): HttpUrl = toHttpUrlOrNull() ?: throw IllegalArgumentException("Expected URL scheme 'http' or 'https' but was '$this'")
        fun String.toHttpUrlOrNull(): HttpUrl? = if (startsWith("http://") || startsWith("https://")) HttpUrl(this) else null
        fun get(url: String): HttpUrl = url.toHttpUrl()
        fun parse(url: String): HttpUrl? = url.toHttpUrlOrNull()
    }
}

class Headers private constructor(private val pairs: List<Pair<String, String>>) : Iterable<Pair<String, String>> {
    operator fun get(name: String): String? = pairs.lastOrNull { it.first.equals(name, true) }?.second
    fun values(name: String): List<String> = pairs.filter { it.first.equals(name, true) }.map { it.second }
    fun names(): Set<String> = pairs.map { it.first }.toCollection(LinkedHashSet())
    val size: Int get() = pairs.size
    fun name(index: Int): String = pairs[index].first
    fun value(index: Int): String = pairs[index].second
    fun toMultimap(): Map<String, List<String>> = pairs.groupBy({ it.first.lowercase() }, { it.second })
    fun newBuilder(): Builder = Builder().also { b -> pairs.forEach { b.add(it.first, it.second) } }
    internal fun list(): List<Pair<String, String>> = pairs
    override fun iterator(): Iterator<Pair<String, String>> = pairs.iterator()

    class Builder {
        private val pairs = ArrayList<Pair<String, String>>()
        fun add(name: String, value: String): Builder = apply { pairs += name to value }
        fun set(name: String, value: String): Builder = apply { removeAll(name); pairs += name to value }
        fun removeAll(name: String): Builder = apply { pairs.removeAll { it.first.equals(name, true) } }
        operator fun get(name: String): String? = pairs.lastOrNull { it.first.equals(name, true) }?.second
        fun build(): Headers = Headers(pairs.toList())
    }

    companion object {
        fun headersOf(vararg namesAndValues: String): Headers =
            Headers(namesAndValues.toList().chunked(2).map { it[0] to it[1] })
        fun Map<String, String>.toHeaders(): Headers = Headers(entries.map { it.key to it.value })
    }
}

// ── Requests and responses ─────────────────────────────────────────────────────────────────────

class Request private constructor(
    val url: HttpUrl,
    val method: String,
    val headers: Headers,
    val body: RequestBody?,
    private val tags: Map<Any, Any?>,
) {
    fun header(name: String): String? = headers[name]
    fun headers(name: String): List<String> = headers.values(name)
    fun newBuilder(): Builder = Builder(this)
    fun tag(): Any? = tags[Any::class]
    fun <T : Any> tag(type: kotlin.reflect.KClass<T>): T? = @Suppress("UNCHECKED_CAST") (tags[type] as T?)
    val isHttps: Boolean get() = url.isHttps
    override fun toString(): String = "Request{method=$method, url=$url}"

    class Builder() {
        private var url: HttpUrl? = null
        private var method = "GET"
        private var headers = Headers.Builder()
        private var body: RequestBody? = null
        private val tags = LinkedHashMap<Any, Any?>()

        internal constructor(request: Request) : this() {
            url = request.url; method = request.method; headers = request.headers.newBuilder(); body = request.body
            tags.putAll(request.tags)
        }

        fun url(url: String): Builder = apply {
            val fixed = when {
                url.startsWith("ws:", true) -> "http:" + url.substring(3)
                url.startsWith("wss:", true) -> "https:" + url.substring(4)
                else -> url
            }
            this.url = with(HttpUrl) { fixed.toHttpUrl() }
        }
        fun url(url: HttpUrl): Builder = apply { this.url = url }
        fun url(url: java.net.URL): Builder = url(url.toString())
        fun header(name: String, value: String): Builder = apply { headers.set(name, value) }
        fun addHeader(name: String, value: String): Builder = apply { headers.add(name, value) }
        fun removeHeader(name: String): Builder = apply { headers.removeAll(name) }
        fun headers(headers: Headers): Builder = apply { this.headers = headers.newBuilder() }
        fun get(): Builder = method("GET", null)
        fun head(): Builder = method("HEAD", null)
        fun post(body: RequestBody): Builder = method("POST", body)
        fun put(body: RequestBody): Builder = method("PUT", body)
        fun patch(body: RequestBody): Builder = method("PATCH", body)
        fun delete(body: RequestBody? = null): Builder = method("DELETE", body)
        fun method(method: String, body: RequestBody?): Builder = apply { this.method = method; this.body = body }
        fun tag(tag: Any?): Builder = apply { tags[Any::class] = tag }
        fun <T : Any> tag(type: kotlin.reflect.KClass<in T>, tag: T?): Builder = apply { tags[type] = tag }
        fun cacheControl(control: CacheControl): Builder = this
        fun build(): Request = Request(url ?: throw IllegalStateException("url == null"), method, headers.build(), body, tags.toMap())
    }
}

class CacheControl private constructor() {
    class Builder {
        fun noCache(): Builder = this
        fun noStore(): Builder = this
        fun maxAge(age: Int, unit: TimeUnit): Builder = this
        fun build(): CacheControl = CacheControl()
    }
    companion object {
        val FORCE_NETWORK = CacheControl()
        val FORCE_CACHE = CacheControl()
    }
}

enum class Protocol { HTTP_1_0, HTTP_1_1, HTTP_2, H2_PRIOR_KNOWLEDGE, QUIC }

class Response private constructor(
    val request: Request,
    val code: Int,
    val message: String,
    val headers: Headers,
    val body: ResponseBody?,
    val protocol: Protocol,
    val sentRequestAtMillis: Long,
    val receivedResponseAtMillis: Long,
) : java.io.Closeable {
    val isSuccessful: Boolean get() = code in 200..299
    val isRedirect: Boolean get() = code in 300..399
    val networkResponse: Response? get() = this
    val cacheResponse: Response? get() = null
    val priorResponse: Response? get() = null
    fun header(name: String, defaultValue: String? = null): String? = headers[name] ?: defaultValue
    fun headers(name: String): List<String> = headers.values(name)
    fun peekBody(byteCount: Long): ResponseBody = body ?: with(ResponseBody) { "".toResponseBody() }
    fun newBuilder(): Builder = Builder(this)
    override fun close() {}
    override fun toString(): String = "Response{protocol=$protocol, code=$code, message=$message, url=${request.url}}"

    class Builder() {
        private var request: Request? = null
        private var code = -1
        private var message = ""
        private var headers = Headers.Builder()
        private var body: ResponseBody? = null
        private var protocol = Protocol.HTTP_1_1
        private var sent = 0L
        private var received = 0L

        internal constructor(response: Response) : this() {
            request = response.request; code = response.code; message = response.message
            headers = response.headers.newBuilder(); body = response.body; protocol = response.protocol
            sent = response.sentRequestAtMillis; received = response.receivedResponseAtMillis
        }

        fun request(request: Request): Builder = apply { this.request = request }
        fun code(code: Int): Builder = apply { this.code = code }
        fun message(message: String): Builder = apply { this.message = message }
        fun protocol(protocol: Protocol): Builder = apply { this.protocol = protocol }
        fun header(name: String, value: String): Builder = apply { headers.set(name, value) }
        fun addHeader(name: String, value: String): Builder = apply { headers.add(name, value) }
        fun headers(headers: Headers): Builder = apply { this.headers = headers.newBuilder() }
        fun body(body: ResponseBody?): Builder = apply { this.body = body }
        fun sentRequestAtMillis(at: Long): Builder = apply { sent = at }
        fun receivedResponseAtMillis(at: Long): Builder = apply { received = at }
        fun build(): Response = Response(request!!, code, message, headers.build(), body, protocol, sent, received)
    }
}

// ── Interceptors, calls, the client ────────────────────────────────────────────────────────────

fun interface Interceptor {
    suspend fun intercept(chain: Chain): Response

    interface Chain {
        fun request(): Request
        suspend fun proceed(request: Request): Response
        fun call(): Call
        fun connectTimeoutMillis(): Int = 15_000
        fun readTimeoutMillis(): Int = 30_000
        fun writeTimeoutMillis(): Int = 30_000
    }

    companion object {
        inline operator fun invoke(crossinline block: suspend (chain: Chain) -> Response): Interceptor =
            Interceptor { block(it) }
    }
}

interface Callback {
    fun onFailure(call: Call, e: IOException)
    fun onResponse(call: Call, response: Response)
}

interface Call {
    fun request(): Request
    /** The phone's blocking `execute`, which a page cannot do; the build rewrites it to [await]. */
    suspend fun await(): Response
    fun enqueue(responseCallback: Callback)
    fun cancel()
    fun isCanceled(): Boolean
    fun isExecuted(): Boolean
    fun clone(): Call
}

class CertificatePinner private constructor() {
    class Builder {
        fun add(pattern: String, vararg pins: String): Builder = this
        fun build(): CertificatePinner = CertificatePinner()
    }
    companion object {
        /** The browser checks certificates itself; a page cannot pin, and does not pretend to. */
        val DEFAULT: CertificatePinner = CertificatePinner()
        fun pin(certificate: Any?): String = ""
    }
}

class Dispatcher {
    var maxRequests: Int = 64
    var maxRequestsPerHost: Int = 5
    fun cancelAll() {}
}

class ConnectionPool(maxIdleConnections: Int = 5, keepAliveDuration: Long = 5, timeUnit: TimeUnit = TimeUnit.MINUTES) {
    fun evictAll() {}
}

class OkHttpClient internal constructor(builder: Builder) {
    constructor() : this(Builder())

    val interceptors: List<Interceptor> = builder.interceptors.toList()
    val networkInterceptors: List<Interceptor> = builder.networkInterceptors.toList()
    val callTimeoutMillis: Int = builder.callTimeout
    val connectTimeoutMillis: Int = builder.connectTimeout
    val readTimeoutMillis: Int = builder.readTimeout
    val writeTimeoutMillis: Int = builder.writeTimeout
    val pingIntervalMillis: Int = builder.pingInterval
    val dispatcher: Dispatcher = builder.dispatcher
    val connectionPool: ConnectionPool = ConnectionPool()
    val certificatePinner: CertificatePinner = CertificatePinner.DEFAULT

    fun newBuilder(): Builder = Builder(this)
    fun newCall(request: Request): Call = RealCall(this, request)
    fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket = BrowserWebSocket.open(request, listener)

    class Builder() {
        internal val interceptors = ArrayList<Interceptor>()
        internal val networkInterceptors = ArrayList<Interceptor>()
        internal var callTimeout = 0
        internal var connectTimeout = 15_000
        internal var readTimeout = 30_000
        internal var writeTimeout = 30_000
        internal var pingInterval = 0
        internal var dispatcher = Dispatcher()

        internal constructor(client: OkHttpClient) : this() {
            interceptors += client.interceptors; networkInterceptors += client.networkInterceptors
            callTimeout = client.callTimeoutMillis; connectTimeout = client.connectTimeoutMillis
            readTimeout = client.readTimeoutMillis; writeTimeout = client.writeTimeoutMillis
            pingInterval = client.pingIntervalMillis; dispatcher = client.dispatcher
        }

        private fun ms(amount: Long, unit: TimeUnit) = unit.toMillis(amount).toInt()
        fun connectTimeout(timeout: Long, unit: TimeUnit): Builder = apply { connectTimeout = ms(timeout, unit) }
        fun readTimeout(timeout: Long, unit: TimeUnit): Builder = apply { readTimeout = ms(timeout, unit) }
        fun writeTimeout(timeout: Long, unit: TimeUnit): Builder = apply { writeTimeout = ms(timeout, unit) }
        fun callTimeout(timeout: Long, unit: TimeUnit): Builder = apply { callTimeout = ms(timeout, unit) }
        fun pingInterval(interval: Long, unit: TimeUnit): Builder = apply { pingInterval = ms(interval, unit) }
        fun connectTimeout(timeout: java.time.Duration): Builder = apply { connectTimeout = timeout.toMillis().toInt() }
        fun readTimeout(timeout: java.time.Duration): Builder = apply { readTimeout = timeout.toMillis().toInt() }
        fun writeTimeout(timeout: java.time.Duration): Builder = apply { writeTimeout = timeout.toMillis().toInt() }
        fun callTimeout(timeout: java.time.Duration): Builder = apply { callTimeout = timeout.toMillis().toInt() }
        fun addInterceptor(interceptor: Interceptor): Builder = apply { interceptors += interceptor }
        fun addNetworkInterceptor(interceptor: Interceptor): Builder = apply { networkInterceptors += interceptor }
        fun certificatePinner(pinner: CertificatePinner): Builder = this
        fun retryOnConnectionFailure(retry: Boolean): Builder = this
        fun followRedirects(follow: Boolean): Builder = this
        fun followSslRedirects(follow: Boolean): Builder = this
        fun dispatcher(dispatcher: Dispatcher): Builder = apply { this.dispatcher = dispatcher }
        fun connectionPool(pool: ConnectionPool): Builder = this
        fun cache(cache: Any?): Builder = this
        fun protocols(protocols: List<Protocol>): Builder = this
        fun build(): OkHttpClient = OkHttpClient(this)
    }
}

private val callScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal class RealCall(private val client: OkHttpClient, private val original: Request) : Call {
    private var job: Job? = null
    private var canceled = false
    private var executed = false

    override fun request(): Request = original
    override fun isCanceled(): Boolean = canceled
    override fun isExecuted(): Boolean = executed
    override fun clone(): Call = RealCall(client, original)
    override fun cancel() { canceled = true; job?.cancel() }

    override suspend fun await(): Response {
        executed = true
        val chain = RealChain(this, client, client.interceptors + client.networkInterceptors, 0, original)
        return chain.proceed(original)
    }

    override fun enqueue(responseCallback: Callback) {
        job = callScope.launch {
            val response = try {
                await()
            } catch (e: IOException) {
                responseCallback.onFailure(this@RealCall, e); return@launch
            } catch (e: kotlinx.coroutines.CancellationException) {
                responseCallback.onFailure(this@RealCall, IOException("Canceled")); return@launch
            } catch (e: Throwable) {
                responseCallback.onFailure(this@RealCall, IOException(e.message, e)); return@launch
            }
            responseCallback.onResponse(this@RealCall, response)
        }
    }
}

internal class RealChain(
    private val call: RealCall,
    private val client: OkHttpClient,
    private val interceptors: List<Interceptor>,
    private val index: Int,
    private val request: Request,
) : Interceptor.Chain {
    override fun request(): Request = request
    override fun call(): Call = call
    override suspend fun proceed(request: Request): Response {
        if (index < interceptors.size) {
            return interceptors[index].intercept(RealChain(call, client, interceptors, index + 1, request))
        }
        return transport(client, request)
    }
}

/** The last link of every chain: the request, mapped onto the relay, sent by `fetch`. */
internal suspend fun transport(client: OkHttpClient, request: Request): Response {
    val sent = com.coinepro.web.jvm.nowMillisJs().toLong()
    val timeout = if (client.callTimeoutMillis > 0) client.callTimeoutMillis else client.readTimeoutMillis + client.connectTimeoutMillis
    val result = browserFetch(
        url = WebRoutes.map(request.url.toString()),
        method = request.method,
        headers = request.headers.list() + listOfNotNull(
            request.body?.contentType()?.let { type -> "Content-Type".takeIf { request.header(it) == null }?.let { it to type.toString() } },
        ),
        body = request.body?.toFetch(),
        timeoutMs = timeout,
    )
    if (result.status < 0) throw IOException(result.statusText.ifBlank { "Network unreachable" })
    val headers = Headers.Builder().also { b -> result.headers.forEach { b.add(it.first, it.second) } }.build()
    val type = headers["Content-Type"]?.let { with(MediaType) { it.toMediaTypeOrNull() } }
    return Response.Builder()
        .request(request)
        .code(result.status)
        .message(result.statusText)
        .headers(headers)
        .body(with(ResponseBody) { result.body.toResponseBody(type) })
        .sentRequestAtMillis(sent)
        .receivedResponseAtMillis(com.coinepro.web.jvm.nowMillisJs().toLong())
        .build()
}
