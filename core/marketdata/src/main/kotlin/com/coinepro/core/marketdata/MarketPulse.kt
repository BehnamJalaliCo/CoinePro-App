package com.coinepro.core.marketdata

/**
 * **The four figures at the top of the markets screen** (run ΤΦΥ, U3).
 *
 * CoinMarketCap's pulse row, and the reason it is the first thing on that screen is that it answers
 * «how is the market» before a reader has scrolled anything: one number for size, one for activity,
 * one for concentration, one for mood.
 *
 * ### Every field is nullable, and three of them are null today
 *
 * This is the honest part and the brief asks for exactly it: «any value that cannot be computed
 * renders as «—» and the missing input is named in BLOCKED.md». What this app has is one day's
 * ticker table per venue — a last price, a change and a turnover per symbol — and that is enough
 * for **one** of the four.
 *
 * * [turnover24h] — **computed**. The sum of what changed hands across the venue's own book. It is
 *   the venue's, not the world's, and [turnoverIsVenueOnly] says so rather than letting a reader
 *   read it as global volume.
 * * [marketCap] — **not computable.** Market capitalisation is price × circulating supply and
 *   nothing in either backend serves a supply figure. A number here derived from turnover would be
 *   a fabrication with a familiar name on it.
 * * [bitcoinDominance] — **not computable**, and for the same reason: it is one market cap over the
 *   sum of all of them.
 * * [fearGreed] — **not served.** There is a published index by that name, it is somebody else's
 *   number, and `MarketMood`'s own note says why this app will not compute one and call it that: a
 *   reader who has seen the real index would compare the two and find them disagreeing.
 *
 * So the row draws four cells, one of them with a figure in it and three with «—», and every dash
 * is a fact about this app's inputs rather than a loading state. `docs/runs/RUN_TFY/BLOCKED.md`
 * names what each needs.
 *
 * ### Why the dashes ship rather than the cells being hidden
 *
 * Because a row that appeared a field at a time as backends grew would be a row whose shape changed
 * under a reader, and because «we do not have this» is worth saying on a screen whose whole subject
 * is figures. A cell that is absent teaches nothing; a cell that says «—» and opens an explanation
 * teaches exactly one thing.
 */
data class MarketPulse(
    /** Total market capitalisation, in quote currency. Null — see the class note. */
    val marketCap: Double? = null,
    /** Turnover across the venue's book in the last day, in quote currency. */
    val turnover24h: Double? = null,
    /** Bitcoin's share of total capitalisation, 0..100. Null — see the class note. */
    val bitcoinDominance: Double? = null,
    /** The published fear-and-greed reading, 0..100. Null — see the class note. */
    val fearGreed: Int? = null,
    /**
     * The share of the board that is up today, 0..100 — what this app *can* measure of the mood.
     *
     * Carried beside [fearGreed] rather than instead of it. They are different numbers and the row
     * does not pretend otherwise: the gauge is the index and reads «—», and this is printed under
     * its own name. See `MarketMood`.
     */
    val breadth: Int? = null,
    /** How many markets the figures were taken over. Zero before any table has arrived. */
    val markets: Int = 0,
) {

    /**
     * Whether [turnover24h] is one venue's book rather than the whole market.
     *
     * Always true today, and it is not a placeholder: this app reads one venue at a time and the
     * sum of what *it* traded is a real, useful number as long as nothing calls it global volume.
     */
    val turnoverIsVenueOnly: Boolean get() = turnover24h != null

    /** Whether there is anything at all worth drawing. */
    val isEmpty: Boolean
        get() = marketCap == null && turnover24h == null && bitcoinDominance == null &&
            fearGreed == null && breadth == null

    companion object {

        /**
         * What the day's table can say.
         *
         * Turnover is summed over the markets that reported one — a market with no figure is left
         * out of the sum rather than counted as zero, because zero is a claim that nothing traded.
         * Breadth is counted the same way `MarketMood` counts it, over the markets whose change the
         * feed actually sent.
         */
        fun of(table: MarketTickerTable): MarketPulse {
            val rows = table.tickers.values
            if (rows.isEmpty()) return MarketPulse()
            val turnover = rows.mapNotNull { it.turnover24h?.takeIf { value -> value.isFinite() && value > 0 } }
            val changes = rows.mapNotNull { it.changePercent24h?.takeIf { value -> value.isFinite() } }
            return MarketPulse(
                // Left null on purpose. See the class note: neither backend serves a supply figure,
                // and a capitalisation derived from turnover would be a fabrication.
                marketCap = null,
                turnover24h = turnover.sum().takeIf { turnover.isNotEmpty() },
                bitcoinDominance = null,
                fearGreed = null,
                breadth = changes
                    .takeIf { it.isNotEmpty() }
                    ?.let { list -> (list.count { it > 0 } * 100.0 / list.size).toInt() },
                markets = rows.size,
            )
        }
    }
}
