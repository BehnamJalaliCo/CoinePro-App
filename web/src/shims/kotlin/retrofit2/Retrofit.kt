@file:Suppress("unused", "UNCHECKED_CAST")

package retrofit2

import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody

/*
 * Retrofit, for the browser. The phone's service interfaces are implemented at build time
 * (`shareSources` in web/build.gradle.kts writes a `…Web` class beside each one and turns
 * `retrofit.create(Api::class.java)` into `ApiWeb(retrofit)`), and those implementations call
 * [execute] below: the same base URL, path, query, headers and body the phone would send, through
 * the same `OkHttpClient` and the same interceptors.
 */

class Retrofit private constructor(val baseUrl: HttpUrl, val client: OkHttpClient) {
    fun baseUrl(): HttpUrl = baseUrl
    fun callFactory(): OkHttpClient = client
    fun newBuilder(): Builder = Builder().baseUrl(baseUrl).client(client)

    /** Never reached: the build replaces every `create` with the generated implementation. */
    fun <T : Any> create(service: kotlin.reflect.KClass<T>): T =
        throw UnsupportedOperationException("${service.simpleName} has no browser implementation")

    class Builder {
        private var base: HttpUrl? = null
        private var client: OkHttpClient? = null
        fun baseUrl(url: String): Builder = apply { base = with(HttpUrl) { url.toHttpUrl() } }
        fun baseUrl(url: HttpUrl): Builder = apply { base = url }
        fun client(client: OkHttpClient): Builder = apply { this.client = client }
        fun callFactory(factory: OkHttpClient): Builder = apply { client = factory }
        fun addConverterFactory(factory: Any): Builder = this
        fun addCallAdapterFactory(factory: Any): Builder = this
        fun build(): Retrofit = Retrofit(base ?: throw IllegalStateException("Base URL required."), client ?: OkHttpClient())
    }

    /** One call of a generated service method. */
    suspend fun execute(
        method: String,
        path: String?,
        url: String?,
        pathParams: List<Pair<String, Any?>> = emptyList(),
        query: List<Pair<String, Any?>> = emptyList(),
        headers: List<Pair<String, Any?>> = emptyList(),
        body: RequestBody? = null,
    ): okhttp3.Response {
        var relative = url ?: path ?: ""
        pathParams.forEach { (name, value) ->
            relative = relative.replace("{$name}", java.net.URLEncoder.encode(value.toString(), "UTF-8").replace("+", "%20"))
        }
        val resolved = resolve(relative)
        val builder = resolved.newBuilder()
        query.forEach { (name, value) ->
            when (value) {
                null -> {}
                is Iterable<*> -> value.forEach { v -> if (v != null) builder.addQueryParameter(name, v.toString()) }
                else -> builder.addQueryParameter(name, value.toString())
            }
        }
        val request = Request.Builder().url(builder.build())
        headers.forEach { (name, value) -> if (value != null) request.addHeader(name, value.toString()) }
        val needsBody = method == "POST" || method == "PUT" || method == "PATCH"
        request.method(method, body ?: if (needsBody) "".toRequestBody(null) else null)
        return client.newCall(request.build()).await()
    }

    /** A plain-typed service method's result: the decoded body, or `HttpException` as Retrofit throws. */
    inline fun <reified T> body(response: okhttp3.Response): T {
        if (!response.isSuccessful) {
            throw HttpException(Response.error<Any>(response.body ?: "".toResponseBody(), response))
        }
        return decodeBody(response)
    }

    /** A `Response<T>` service method's result: never throws on an HTTP status, as on the phone. */
    inline fun <reified T> wrapped(response: okhttp3.Response): Response<T> =
        if (response.isSuccessful) Response.success(decodeBody<T>(response), response)
        else Response.error(response.body ?: "".toResponseBody(), response)

    private fun resolve(relative: String): HttpUrl {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return with(HttpUrl) { relative.toHttpUrl() }
        val base = baseUrl.toString()
        if (relative.startsWith("/")) {
            val origin = base.substringBefore("://") + "://" + base.substringAfter("://").substringBefore('/')
            return with(HttpUrl) { (origin + relative).toHttpUrl() }
        }
        val dir = base.substringBefore('?').let { if (it.endsWith('/')) it else it.substringBeforeLast('/') + "/" }
        return with(HttpUrl) { (dir + relative).toHttpUrl() }
    }
}

/** A JSON `@Body`, as the phone's Gson converter would write it. */
fun jsonBody(json: String): RequestBody = json.toRequestBody("application/json; charset=UTF-8".toMediaType())

class Response<T> private constructor(private val raw: okhttp3.Response, private val body: T?, private val errorBody: ResponseBody?) {
    fun raw(): okhttp3.Response = raw
    fun code(): Int = raw.code
    fun message(): String = raw.message
    fun headers(): okhttp3.Headers = raw.headers
    val isSuccessful: Boolean get() = raw.isSuccessful
    fun body(): T? = body
    fun errorBody(): ResponseBody? = errorBody
    override fun toString(): String = raw.toString()

    companion object {
        fun <T> success(body: T?, raw: okhttp3.Response): Response<T> = Response(raw, body, null)
        fun <T> success(body: T?): Response<T> = Response(
            okhttp3.Response.Builder().code(200).message("OK")
                .request(Request.Builder().url("http://localhost/").build()).build(),
            body, null,
        )
        fun <T> error(body: ResponseBody, raw: okhttp3.Response): Response<T> = Response(raw, null, body)
        fun <T> error(code: Int, body: ResponseBody): Response<T> = Response(
            okhttp3.Response.Builder().code(code).message("Response.error()")
                .request(Request.Builder().url("http://localhost/").build()).build(),
            null, body,
        )
    }
}

open class HttpException(private val response: Response<*>?) : RuntimeException("HTTP ${response?.code()} ${response?.message()}") {
    fun code(): Int = response?.code() ?: -1
    override val message: String get() = response?.message() ?: ""
    fun message(): String = message
    fun response(): Response<*>? = response
}


inline fun <reified T> decodeBody(response: okhttp3.Response): T = when (T::class) {
    Unit::class -> Unit as T
    ResponseBody::class -> (response.body ?: "".toResponseBody()) as T
    else -> try {
        com.coinepro.web.wire.decodeWire<T>(response.body?.string().orEmpty())
    } catch (e: kotlinx.serialization.SerializationException) {
        throw com.google.gson.JsonSyntaxException(e.message, e)
    } catch (e: IllegalArgumentException) {
        throw com.google.gson.JsonSyntaxException(e.message, e)
    }
}

