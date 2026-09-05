package com.coinepro.core.common

/**
 * Where a backend's *static files* live, given the address of its *API*.
 *
 * ### The bug this exists to close
 *
 * CoinePro-FX's API base is `https://coineprofx.com/api/` — the `/api` is part of the address
 * because that deployment serves its routes as `user/…` under that prefix. Its static files are not
 * under it. They are at the site root:
 *
 * ```
 * https://coineprofx.com/assets/logo/XAUUSD.webp       200  image/webp
 * https://coineprofx.com/api/assets/logo/XAUUSD.webp   404  application/json
 * ```
 *
 * Both measured on 2026-09-05, the day the server shipped the route. The app was building the
 * second — `BuildConfig.API_BASE_URL.trimEnd('/') + "/assets/logo/…"` — so every symbol the
 * vendored artwork does not draw fetched a JSON 404, decoded nothing, and fell back to the
 * monogram. Silently, and for as long as the code had existed: a 404 on an image is not an error
 * anybody sees, it is an image that does not appear, on symbols nobody had drawn artwork for
 * anyway. The server building the files correctly is what made it visible.
 *
 * ### Why the origin and not "strip /api"
 *
 * Because the next deployment will have a different prefix and the same static root. A path is a
 * guess about one server; the origin is what "the site this API belongs to" actually means, and it
 * is right for both bases this app carries — TradeYar's is the bare host and comes back unchanged.
 *
 * Returns an empty string for anything that is not an absolute `http(s)` address, which is what a
 * build with no base URL configured has. Callers draw nothing rather than fetching from a
 * half-formed address.
 */
object SiteAssets {

    /** `https://coineprofx.com/api/` → `https://coineprofx.com`. No trailing slash. */
    fun originOf(baseUrl: String?): String {
        val trimmed = baseUrl?.trim().orEmpty()
        val scheme = SCHEMES.firstOrNull { trimmed.startsWith(it, ignoreCase = true) } ?: return ""
        val afterScheme = trimmed.substring(scheme.length)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return ""
        return trimmed.substring(0, scheme.length) + authority
    }

    /**
     * One static file on that site, by path.
     *
     * [path] is written without a leading slash at every call site, the way the routes in this app
     * are, and a leading one is tolerated rather than doubled — `//assets/…` is a protocol-relative
     * URL to some servers and a plain 404 to others, and neither is worth risking over a slash.
     */
    fun url(baseUrl: String?, path: String): String? {
        val origin = originOf(baseUrl).takeIf { it.isNotEmpty() } ?: return null
        return origin + "/" + path.trimStart('/')
    }

    private val SCHEMES = listOf("https://", "http://")
}
