package com.coinepro.app.alerts

import com.coinepro.core.common.BrandConfig

/**
 * Where a fired alert's notification takes the reader.
 *
 * ### The whole product thesis is in this one link
 *
 * An alert fires, the chart opens **on that symbol at that timeframe with the drawings intact**,
 * and twenty seconds later the reader has decided. Cold start to decision is the number this app is
 * measured by, and the notification is the only step of it the app controls.
 *
 * It used to be `coinepro://activity`, which opens the activity list. That is the screen for
 * everything the app has ever told anybody — signals, copy trades, news — and the alert that just
 * fired is one row of it. From there the reader still has to find the symbol, open its chart and
 * wait for it to load, at four in the morning, having been woken specifically so that they could
 * look at it. The in-app toast already offered «باز کردن نمودار»; the cold-start case, which is the
 * one that matters because the reader was not holding the phone, was served worse than the case
 * where they were.
 *
 * ### Why `market` rather than a new host
 *
 * `coinepro://market/<ticker>` is already declared in the manifest, already shape-checked by
 * `parseCoineProDeepLink`, and already navigates to the chart — the home-screen widget's rows use
 * it. A second host doing the same thing would be a second thing to keep verified, and a scheme
 * nobody verifies is not a place to add surface.
 *
 * ### The timeframe rides as a query, and is correct even where nothing reads it
 *
 * `SymbolChartStateStore` restores the timeframe, the indicators and the drawings the reader last
 * had on that symbol, so a link carrying the symbol alone already lands on the right bar. The
 * timeframe is carried anyway because "the right bar" and "the bar this alert was evaluated on" are
 * not always the same thing — an alert with `AlertFrequency.ONCE_PER_BAR_CLOSE` is decided on a
 * specific interval, and if the reader has since left that symbol's chart on another one, the app
 * should be able to say which. `parseCoineProDeepLink` reads it into `CoineProDeepLink.Market` and
 * `MainActivity` sets the timeframe before the symbol, in the same frame — the chart's launch effect
 * is keyed on the symbol, so a timeframe written after it would arrive a recomposition late and be
 * applied to the *next* link instead of this one.
 *
 * ### Pure, and no `android.net.Uri`
 *
 * `Uri.encode` is an Android static, and this module's unit tests run with
 * `isReturnDefaultValues = true` — so in a test it returns null rather than throwing, and a link
 * built out of it would silently become the string `"coinepro://market/null"`. Every notification
 * would then open nothing, and no test could see it. The encoder here is eight lines and runs on a
 * JVM.
 */
object AlertDeepLink {

    /** The custom scheme. Unverified, which is why nothing carrying a credential uses it. */
    const val SCHEME = BrandConfig.SCHEME

    /** The host that means "a market's chart". Declared in the manifest; do not rename it. */
    const val CHART_HOST = "market"

    /** The query parameter carrying the bar the alert was evaluated on. */
    const val TIMEFRAME_QUERY = "tf"

    /**
     * The link for one symbol's chart, with the bar it was decided on where there is one.
     *
     * The ticker is percent-encoded because a pair is written with a slash — `XAU/USD` — and an
     * unencoded slash is two path segments, which `parseCoineProDeepLink` refuses outright: it
     * takes a *single* segment, deliberately, so that nothing can smuggle a path through this host.
     *
     * A blank timeframe leaves the query off rather than writing `?tf=`. An empty parameter and an
     * absent one should not be two spellings of the same thing.
     */
    fun chart(symbol: String, timeframe: String? = null): String {
        val ticker = encode(symbol.trim().uppercase())
        val bar = timeframe?.trim()?.takeIf(String::isNotEmpty)
        val base = "$SCHEME://$CHART_HOST/$ticker"
        return if (bar == null) base else "$base?$TIMEFRAME_QUERY=" + encode(bar)
    }

    /**
     * Percent-encoding for one path segment or query value.
     *
     * The unreserved set of RFC 3986 and nothing else. Encoding more than is strictly required is
     * the safe direction here: an over-encoded byte decodes back to itself, while a character left
     * raw can change what the URI *means* — a slash becomes a path boundary, a question mark starts
     * a query, and either turns a link to gold into a link to nothing.
     */
    private fun encode(raw: String): String = buildString(raw.length) {
        raw.toByteArray(Charsets.UTF_8).forEach { byte ->
            val character = byte.toInt().toChar()
            if (character.isLetterOrDigit() && character.code < 128 || character in UNRESERVED) {
                append(character)
            } else {
                append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
            }
        }
    }

    private const val UNRESERVED = "-._~"
    private const val HEX = "0123456789ABCDEF"
}
