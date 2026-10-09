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
 * On a phone in Iran's time zone the mirror goes first (5.27.2): 5.27.0 tried the primary first and
 * still drew dashes, because a filtered Cloudflare address there tends to take the connection and
 * then stall the handshake until the call's own ceiling cancels it — and a cancelled call has no
 * time left for a second road. Elsewhere the primary goes first. Either way the first road runs on
 * short clocks, and a failure *before the request left the phone*, or an HTML wall in place of an
 * answer (Cloudflare's block page, the mirror's server with nothing behind it), sends the same
 * request down the other road. Whichever road worked is remembered for [stickyMillis] in
 * [MirrorMemory], across launches.
 *
 * A request that may have reached the server is never sent twice unless it is a `GET`: a payment
 * claim that timed out reading its answer is not retried down another road. CoinePro-FX's own
 * answers — JSON, a 404 for a market with no data among them — are answers, and never switch roads;
 * 5.27.0 dropped the mirror on any 404 and so flapped back to the closed road every few requests.
 */
class HostMirror(
    /** `host` to the mirror's base, e.g. `coineprofx.com` → `https://tradeyar.trade-future.ir/fx/`. */
    private val mirrors: Map<String, HttpUrl>,
    private val memory: MirrorMemory = MirrorMemory.InProcess(),
    private val now: () -> Long = System::currentTimeMillis,
    private val probeConnectMillis: Int = PROBE_CONNECT_MILLIS,
    private val stickyMillis: Long = STICKY_MILLIS,
    /**
     * Whether to take the mirror first (5.27.2) — true on a phone set to Iran's time zone, where the
     * primary is the road that is usually closed. The primary is then the fallback, so a reader on a
     * VPN, or one whose network lets Cloudflare through, loses nothing.
     */
    private val preferMirror: () -> Boolean = { false },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val mirror = mirrors[host] ?: return chain.proceed(request)
        val viaMirror = request.newBuilder().url(rewrite(request.url, mirror)).build()
        val mirrorFirst = preferMirror() || memory.mirrorUntil(host) > now()
        val (first, second) = if (mirrorFirst) viaMirror to request else request to viaMirror
        val response = try {
            // Short clocks on the first road, so a filtered address — one that takes the connection
            // and then never finishes the handshake — fails here rather than at the call's own
            // thirty-second ceiling, which cancels the call and leaves no time for the second road.
            chain.withConnectTimeout(probeConnectMillis, TimeUnit.MILLISECONDS)
                .withReadTimeout(PROBE_READ_MILLIS, TimeUnit.MILLISECONDS)
                .withWriteTimeout(PROBE_READ_MILLIS, TimeUnit.MILLISECONDS)
                .proceed(first)
        } catch (error: IOException) {
            if (chain.call().isCanceled() || !beforeSend(error, request)) throw error
            remember(host, wentToMirror = !mirrorFirst)
            return chain.proceed(second)
        }
        if (!refused(response, request.method)) {
            // The road that answered is the one to keep: a reader whose primary answered is not
            // held on the mirror, and one whose mirror answered stays there.
            if (mirrorFirst && !preferMirror()) memory.useMirror(host, now() + stickyMillis)
            return response
        }
        response.close()
        remember(host, wentToMirror = !mirrorFirst)
        return chain.proceed(second)
    }

    private fun remember(host: String, wentToMirror: Boolean) {
        memory.useMirror(host, if (wentToMirror) now() + stickyMillis else 0L)
    }

    companion object {
        /** Long enough to fail fast on a filtered address, short enough for a slow honest link. */
        const val PROBE_CONNECT_MILLIS = 6_000

        /** The first road's read and write clocks; see [intercept]. */
        const val PROBE_READ_MILLIS = 10_000

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

        /**
         * A response that came from the road rather than from CoinePro-FX: Cloudflare's own block or
         * error page on the primary, or the mirror's web server with nothing behind it. Both are HTML;
         * CoinePro-FX answers JSON, so its own 404 for a market with no data is an answer, not a wall.
         * A 5xx is retried down the other road only for a `GET`, which the origin may have seen.
         */
        internal fun refused(response: Response, method: String): Boolean {
            val html = response.header("Content-Type").orEmpty().contains("text/html", ignoreCase = true)
            if (!html) return false
            return when (response.code) {
                403, 404, 451 -> true
                in 500..599 -> method == "GET" || response.code in 520..530
                else -> false
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
