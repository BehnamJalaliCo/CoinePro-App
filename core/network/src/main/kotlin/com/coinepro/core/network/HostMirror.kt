package com.coinepro.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * A second road to a host the reader's network will not let them reach (5.27.0).
 *
 * The forex platform sits behind Cloudflare, and from inside Iran without a VPN its address often
 * does not answer: the watchlist's forex rows stayed dashes while the crypto rows, served from
 * `tradeyar.trade-future.ir`, filled. That host *is* reachable, so it carries a path — `/fx/` —
 * that its own web server hands on to the forex platform. This interceptor is the phone's half.
 *
 * ### How it chooses
 *
 * The primary is tried first, with a short connect timeout, because it is the shorter road when it
 * works. A failure *before the request left the phone* — no address, no route, a refused or reset
 * handshake, a connect that timed out — sends the same request down the mirror, and the mirror is
 * then used straight away for [stickyMillis], so one slow probe is paid once and not on every call.
 * [MirrorMemory] keeps that choice across launches.
 *
 * A request that may have reached the server is never sent twice unless it is a `GET`: a payment
 * claim that timed out reading its answer is not retried down another road.
 *
 * ### When the mirror is not there
 *
 * A 404 or a 5xx from the mirror is a mirror that is not set up or not working. The preference is
 * dropped, so the next call tries the primary again rather than six hours of answers from a road
 * that leads nowhere.
 */
class HostMirror(
    /** `host` to the mirror's base, e.g. `coineprofx.com` → `https://tradeyar.trade-future.ir/fx/`. */
    private val mirrors: Map<String, HttpUrl>,
    private val memory: MirrorMemory = MirrorMemory.InProcess(),
    private val now: () -> Long = System::currentTimeMillis,
    private val probeConnectMillis: Int = PROBE_CONNECT_MILLIS,
    private val stickyMillis: Long = STICKY_MILLIS,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val mirror = mirrors[host] ?: return chain.proceed(request)
        if (memory.mirrorUntil(host) > now()) return throughMirror(chain, request, mirror, host)
        return try {
            chain.withConnectTimeout(probeConnectMillis, TimeUnit.MILLISECONDS).proceed(request)
        } catch (error: IOException) {
            if (chain.call().isCanceled() || !beforeSend(error, request)) throw error
            memory.useMirror(host, now() + stickyMillis)
            throughMirror(chain, request, mirror, host)
        }
    }

    private fun throughMirror(chain: Interceptor.Chain, request: Request, mirror: HttpUrl, host: String): Response {
        val response = chain.proceed(request.newBuilder().url(rewrite(request.url, mirror)).build())
        if (response.code == 404 || response.code >= 500) memory.useMirror(host, 0L)
        return response
    }

    companion object {
        /** Long enough to fail fast on a filtered address, short enough for a slow honest link. */
        const val PROBE_CONNECT_MILLIS = 6_000

        /** How long a working mirror is used before the primary is tried again. */
        const val STICKY_MILLIS = 6L * 60 * 60 * 1000

        /** `https://coineprofx.com/api/user/me?a=b` → `https://tradeyar.trade-future.ir/fx/api/user/me?a=b`. */
        fun rewrite(url: HttpUrl, mirror: HttpUrl): HttpUrl {
            val builder = mirror.newBuilder()
            val segments = url.encodedPathSegments.filter { it.isNotEmpty() }
            // The mirror's base ends in `/`, which OkHttp keeps as one empty last segment.
            if (mirror.encodedPathSegments.lastOrNull() == "") builder.removePathSegment(mirror.pathSize - 1)
            segments.forEach(builder::addEncodedPathSegment)
            if (url.encodedPath.endsWith("/") && segments.isNotEmpty()) builder.addPathSegment("")
            return builder.encodedQuery(url.encodedQuery).build()
        }

        /**
         * `coineprofx.com=https://tradeyar.trade-future.ir/fx/;…`, as the build carries it. A line
         * that does not read that way is refused loudly, for `parsePins`'s reason.
         */
        fun parse(raw: String?): Map<String, HttpUrl> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(';').map(String::trim).filter(String::isNotEmpty).associate { entry ->
                val host = entry.substringBefore('=', "").trim()
                val url = entry.substringAfter('=', "").trim().toHttpUrlOrNull()
                require(host.isNotEmpty() && url != null && url.isHttps) {
                    "A host mirror must read host=https://…, not '$entry'."
                }
                host to url
            }
        }

        private fun beforeSend(error: IOException, request: Request): Boolean {
            if (request.method == "GET") return true
            return error is UnknownHostException ||
                error is ConnectException ||
                error is NoRouteToHostException ||
                error is SSLException ||
                (error is SocketTimeoutException && error.message.orEmpty().contains("connect", ignoreCase = true))
        }
    }
}

/** Where [HostMirror] remembers that a host is being reached the long way round. */
interface MirrorMemory {
    /** Epoch millis until which [host] goes through its mirror; 0 when it does not. */
    fun mirrorUntil(host: String): Long

    fun useMirror(host: String, untilEpochMillis: Long)

    /** For tests and for a build with nowhere to persist it. */
    class InProcess : MirrorMemory {
        private val until = ConcurrentHashMap<String, Long>()
        override fun mirrorUntil(host: String): Long = until[host] ?: 0L
        override fun useMirror(host: String, untilEpochMillis: Long) {
            until[host] = untilEpochMillis
        }
    }
}
