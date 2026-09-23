@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.net

/**
 * Where a URL the phone would call is fetched from in a browser.
 *
 * 1. The relay's own named routes (docs/web/SERVER.md §4) where the phone's call is exactly what the
 *    relay already fronts — prices, candles, headlines, the public track record, community,
 *    membership. These work today.
 * 2. Everything else on the two backends goes through the relay's passthrough: `/up/tradeyar/…` and
 *    `/up/coineprofx/…`, the same path and query, the same method, headers and body
 *    (docs/web/SERVER.md §4.9). The relay adds nothing and keeps nothing.
 * 3. Any other host is fetched as it is: a public API that answers cross-origin reads (LBank's,
 *    the app-update manifest) needs no relay, and one that does not fails exactly as a network
 *    failure does on the phone.
 */
private fun pageOriginJs(): String = js("window.location.origin")
private fun encodeUriComponentJs(value: String): String = js("encodeURIComponent(value)")

object WebRoutes {
    const val TRADEYAR = "https://tradeyar.trade-future.ir/"
    const val COINEPRO_FX = "https://coineprofx.com/"

    val origin: String by lazy { pageOriginJs() }

    fun map(url: String): String {
        val (base, key) = when {
            url.startsWith(TRADEYAR) -> TRADEYAR to "tradeyar"
            url.startsWith(COINEPRO_FX) -> COINEPRO_FX to "coineprofx"
            url.startsWith("https://www.coineprofx.com/") -> "https://www.coineprofx.com/" to "coineprofx"
            else -> return url
        }
        val rest = url.removePrefix(base)
        val path = rest.substringBefore('?')
        val query = rest.substringAfter('?', "")
        named(key, path, query)?.let { return origin + it }
        return "$origin/up/$key/$rest"
    }

    private fun named(key: String, path: String, query: String): String? {
        fun q(extra: String = query) = if (extra.isEmpty()) "" else "?$extra"
        if (key == "tradeyar") {
            when (path) {
                "api/v1/public/prices" -> return "/api/crypto/prices" + q()
                "api/v1/news/list" -> return "/api/news" + q()
                "api/demo/signals" -> return "/api/track-record" + q()
                "api/v1/public/community" -> return "/api/community" + q()
                "api/v1/public/membership" -> return "/api/membership" + q()
            }
            if (path.startsWith("api/v1/public/candles/")) {
                val symbol = path.removePrefix("api/v1/public/candles/")
                return "/api/crypto/candles" + q(listOf("symbol=$symbol", query).filter { it.isNotEmpty() }.joinToString("&"))
            }
        } else {
            when (path) {
                "api/public/prices/live" -> return "/api/fx/prices" + q()
                "api/public/prices/series" -> return "/api/fx/candles" + q()
                "api/public/signals/showcase", "public/signals/showcase" -> return "/api/fx/showcase" + q()
            }
        }
        return null
    }

    /**
     * Where a picture the phone would load is loaded from.
     *
     * The two backends' pictures go the way their JSON does. Any other host — a headline's photo on
     * the publisher's site — is read through the relay's image route (docs/web/SERVER.md §4.13),
     * because a page may show another site's picture but may not read its bytes, and Coil draws from
     * bytes. The phone reads them directly; this is the same picture by the only road a page has.
     */
    fun mapImage(url: String): String {
        val mapped = map(url)
        if (mapped != url || url.startsWith(origin)) return mapped
        if (!url.startsWith("https://") && !url.startsWith("http://")) return url
        return "$origin/api/img?url=" + encodeUriComponentJs(url)
    }

    /** The socket URL a `wss://` the phone would open is reached at. */
    fun mapSocket(url: String): String {
        val https = url.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://")
        val mapped = map(https)
        if (mapped == https) return url
        return mapped.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
    }
}
