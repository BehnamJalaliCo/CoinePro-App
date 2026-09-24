package com.coinepro.core.chart

/**
 * What TradingView's «Chart settings» dialog holds that this chart did not already keep somewhere
 * else (5.16.0).
 *
 * The dialog has six tabs — Symbol, Status line, Scales, Canvas, Trading, Events — and most of what
 * they set already had a home here: the scale mode, the axis side and the decimals on the viewport,
 * the colours in a colour template, the event kinds in their own store. This is the rest, the
 * switches a reader flips once and expects to find flipped on every chart: whether bars are coloured
 * against the previous close, whether bodies and wicks are drawn, which parts of the status line
 * show, the grid's two directions, the margins above and below the price, and the trading chrome.
 *
 * Global rather than per symbol, as TradingView's are: a reader who hides the wicks hides them on
 * everything. Every default is TradingView's own, so a reader who never opens the dialog sees
 * exactly the chart this was before it existed.
 */
data class ChartAppearance(
    // ── Symbol ──────────────────────────────────────────────────────────────────────────────────
    /** `barColorsOnPrevClose`: a bar is green when it closed above the previous close. */
    val colourOnPreviousClose: Boolean = false,
    /** `drawBody`. Off leaves the wicks, which is how some readers read ranges. */
    val bodies: Boolean = true,
    /** `drawWick`. */
    val wicks: Boolean = true,
    // ── Status line ─────────────────────────────────────────────────────────────────────────────
    /** The instrument's mark beside its name. */
    val legendLogo: Boolean = true,
    /** Open, high, low and close. */
    val legendOhlc: Boolean = true,
    /** The change from the previous close, in price and percent. */
    val legendChange: Boolean = true,
    /** The studies' rows under the price line. */
    val legendIndicators: Boolean = true,
    // ── Scales ──────────────────────────────────────────────────────────────────────────────────
    /** The dotted line at the last price and its tag. */
    val lastPriceLine: Boolean = true,
    /** The countdown to the bar's close, under the price tag. */
    val countdown: Boolean = true,
    /** The line at the previous session's close. */
    val previousCloseLine: Boolean = true,
    // ── Canvas ──────────────────────────────────────────────────────────────────────────────────
    val gridVertical: Boolean = true,
    val gridHorizontal: Boolean = true,
    /** The mark in the corner of the pane. */
    val watermark: Boolean = true,
    /** TradingView's crosshair «Magnet»: the horizontal line on the bar's close (5.16.1). */
    val crosshairMagnet: Boolean = false,
    /** Air over the highest price, percent of the plot's height. TradingView's 10. */
    val topMarginPercent: Int = DEFAULT_TOP_MARGIN,
    /** Air under the lowest, percent. TradingView's 8. */
    val bottomMarginPercent: Int = DEFAULT_BOTTOM_MARGIN,
    // ── Trading ─────────────────────────────────────────────────────────────────────────────────
    /** The quote chip in the plot's top corner, which opens the scale settings. */
    val quoteChip: Boolean = true,
    /** The trade ring under the live bar. */
    val tradeRing: Boolean = true,
    /** The volume columns along the foot of the price. */
    val volume: Boolean = true,
) {
    /** [topMarginPercent] as the viewport wants it. */
    val topMargin: Double get() = topMarginPercent.coerceIn(0, MAX_MARGIN) / 100.0

    /** [bottomMarginPercent] as the viewport wants it. */
    val bottomMargin: Double get() = bottomMarginPercent.coerceIn(0, MAX_MARGIN) / 100.0

    /** One line of `key=value;` pairs, only what differs from the default — see [decode]. */
    fun encode(): String {
        val base = ChartAppearance()
        val out = StringBuilder()
        fun put(key: String, value: Any, default: Any) {
            if (value != default) out.append(key).append('=').append(value).append(';')
        }
        put("prevclose", colourOnPreviousClose, base.colourOnPreviousClose)
        put("bodies", bodies, base.bodies)
        put("wicks", wicks, base.wicks)
        put("logo", legendLogo, base.legendLogo)
        put("ohlc", legendOhlc, base.legendOhlc)
        put("change", legendChange, base.legendChange)
        put("studies", legendIndicators, base.legendIndicators)
        put("last", lastPriceLine, base.lastPriceLine)
        put("countdown", countdown, base.countdown)
        put("prevline", previousCloseLine, base.previousCloseLine)
        put("gridv", gridVertical, base.gridVertical)
        put("gridh", gridHorizontal, base.gridHorizontal)
        put("watermark", watermark, base.watermark)
        put("crossmagnet", crosshairMagnet, base.crosshairMagnet)
        put("top", topMarginPercent, base.topMarginPercent)
        put("bottom", bottomMarginPercent, base.bottomMarginPercent)
        put("quote", quoteChip, base.quoteChip)
        put("ring", tradeRing, base.tradeRing)
        put("volume", volume, base.volume)
        return out.toString()
    }

    companion object {
        const val DEFAULT_TOP_MARGIN = 10
        const val DEFAULT_BOTTOM_MARGIN = 8

        /** Past this the two margins leave the bars no room. TradingView caps each field alike. */
        const val MAX_MARGIN = 40

        /**
         * Reads [encode]'s line. An unknown key or a malformed value is skipped rather than
         * failing the whole line, so a setting added later never costs a reader the ones they had.
         */
        fun decode(line: String?): ChartAppearance {
            var a = ChartAppearance()
            if (line.isNullOrBlank()) return a
            for (pair in line.split(';')) {
                val key = pair.substringBefore('=', "")
                val value = pair.substringAfter('=', "")
                val flag = value.toBooleanStrictOrNull()
                val number = value.toIntOrNull()
                a = when (key) {
                    "prevclose" -> flag?.let { a.copy(colourOnPreviousClose = it) }
                    "bodies" -> flag?.let { a.copy(bodies = it) }
                    "wicks" -> flag?.let { a.copy(wicks = it) }
                    "logo" -> flag?.let { a.copy(legendLogo = it) }
                    "ohlc" -> flag?.let { a.copy(legendOhlc = it) }
                    "change" -> flag?.let { a.copy(legendChange = it) }
                    "studies" -> flag?.let { a.copy(legendIndicators = it) }
                    "last" -> flag?.let { a.copy(lastPriceLine = it) }
                    "countdown" -> flag?.let { a.copy(countdown = it) }
                    "prevline" -> flag?.let { a.copy(previousCloseLine = it) }
                    "gridv" -> flag?.let { a.copy(gridVertical = it) }
                    "gridh" -> flag?.let { a.copy(gridHorizontal = it) }
                    "watermark" -> flag?.let { a.copy(watermark = it) }
                    "crossmagnet" -> flag?.let { a.copy(crosshairMagnet = it) }
                    "top" -> number?.let { a.copy(topMarginPercent = it.coerceIn(0, MAX_MARGIN)) }
                    "bottom" -> number?.let { a.copy(bottomMarginPercent = it.coerceIn(0, MAX_MARGIN)) }
                    "quote" -> flag?.let { a.copy(quoteChip = it) }
                    "ring" -> flag?.let { a.copy(tradeRing = it) }
                    "volume" -> flag?.let { a.copy(volume = it) }
                    else -> null
                } ?: a
            }
            return a
        }
    }
}
