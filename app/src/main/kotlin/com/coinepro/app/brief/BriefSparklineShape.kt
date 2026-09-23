package com.coinepro.app.brief

import com.coinepro.core.chart.CandleSeries

/**
 * The sparkline's geometry, with no canvas anywhere near it.
 *
 * ### Why this is a separate object rather than four lines inside the painter
 *
 * The same argument `priceAlertChannelId` makes in `NotificationChannels`, and it was reached the
 * same way. Everything a sparkline can get *wrong* is in here — whether to draw at all, which way
 * the night went, where each point lands when the price span is zero — and every one of those is a
 * pure function of a list of numbers. Left inside the painter they could only be exercised by
 * rasterising a bitmap, which on this project means Robolectric with `@GraphicsMode(NATIVE)`: the
 * legacy graphics shadows rasterise nothing, so a colour assertion passes against an empty image,
 * and the native mode loads a graphics library **outside the JVM heap** that took the `:app` suite
 * past the container's memory and had it killed with SIGKILL rather than a test failure.
 *
 * So the decisions are here, under an ordinary unit test, and what is left in [BriefSparkline] is
 * three paint calls with nothing to decide.
 */
object BriefSparklineShape {

    /** A point in the box, both axes 0..1, with y already flipped so 0 is the top. */
    data class Point(val x: Float, val y: Float)

    /**
     * The line, or null where there is not enough of one to draw.
     *
     * Two closes is the floor: one point is a dot, and a dot in a notification reads as a broken
     * image rather than as a market that only printed once.
     */
    data class Shape(
        val points: List<Point>,
        /**
         * Whether the night ended above where it started — **first against last**.
         *
         * Not «the last bar was green». A night that fell all the way and bounced on the final bar
         * is a night that fell, and the sentence printed directly above the picture says so;
         * colouring by the last candle would contradict it on exactly the mornings a reader looks
         * hardest.
         */
        val rising: Boolean,
    )

    /**
     * The closes of the last [bars] bars, normalised into the unit box.
     *
     * A price span of zero — a perfectly flat night — is centred rather than divided by, and it
     * still draws: flat is a fact about the market, and refusing to render it would look like a
     * failure to fetch. Non-finite closes are dropped rather than plotted, for the same reason a
     * drawing with no price is left out of the alert picker: a point at zero in a picture of prices
     * reads as a fault in the feed.
     */
    fun of(series: CandleSeries?, bars: Int = BARS): Shape? {
        val closes = series?.let { source ->
            val from = maxOf(0, source.size - bars)
            (from until source.size).mapNotNull { index -> source[index].c.takeIf(Double::isFinite) }
        }.orEmpty()
        return ofCloses(closes)
    }

    /**
     * The same normalisation, from closes a caller already has — run Τ2, B8.
     *
     * The picture-in-picture window publishes a tail of closes rather than a whole series, because
     * a series cannot survive the composition it came from and the window outlives one. It gets
     * this rather than a second copy of the arithmetic: two places deciding «where does this point
     * land when the span is zero» is two places that can disagree, and this one is tested.
     *
     * Non-finite values are dropped here too, and for the same reason: a point at zero in a picture
     * of prices reads as a fault in the feed.
     */
    fun ofCloses(values: List<Double>): Shape? {
        val closes = values.filter(Double::isFinite)
        if (closes.size < 2) return null

        val low = closes.min()
        val span = closes.max() - low
        val step = 1f / (closes.size - 1).toFloat()
        return Shape(
            points = closes.mapIndexed { index, close ->
                val ratio = if (span == 0.0) 0.5f else ((close - low) / span).toFloat()
                // Price up: a canvas grows downwards and a chart does not, and this is the one
                // place the two conventions are reconciled. A sketch drawn upside down would be
                // worse than none — a reader would take it for a different night.
                Point(x = index * step, y = 1f - ratio)
            },
            rising = closes.last() >= closes.first(),
        )
    }

    /**
     * How many bars the picture carries.
     *
     * Twenty-four hourly closes: the night the brief is about, and no more. A week of bars in a
     * notification-sized image would compress exactly the hours being reported into a few pixels.
     */
    const val BARS = 24
}
