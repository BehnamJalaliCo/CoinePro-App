package com.coinepro.core.marketdata

import kotlin.math.abs

/**
 * **حال‌وهوای بازار** — what the whole board did today, in one strip (run Ω4).
 *
 * ### Why breadth and not a fear-and-greed index
 *
 * The brief asks for «fear/greed». There is a published index by that name, it is somebody else's
 * number, it covers crypto only, and no backend this app talks to serves it. Shipping a figure
 * *called* fear-and-greed that this app computed itself would be putting a familiar name on an
 * unfamiliar number, which is the one thing a market screen must never do — a reader who has seen
 * the real index would compare the two and find them disagreeing, and would be right to.
 *
 * So this is **breadth**: the share of the board that is up today. It is the oldest sentiment
 * measure there is, it is computed from the same table the rows are drawn from, and it is named for
 * what it is. Where the real index can be licensed the seam is one field — see `docs/backend/FEEDS.md`.
 *
 * ### Why «unusual» is «busy and moving» and not «unusual for this market»
 *
 * «Unusual volume» properly means today's turnover against *this instrument's own* recent normal,
 * and that needs a history of daily turnover per symbol which neither backend serves and which the
 * app does not keep. What is in hand is one day's table, so the honest version of the question is
 * «which markets are in the top of the board on turnover *and* on movement» — busy and going
 * somewhere — and that is what [unusual] is, named in the UI for what it measures rather than for
 * what it approximates.
 *
 * ### Graceful in parts
 *
 * Every field is independently nullable or empty, because the table arrives in pieces and one
 * platform (CoinePro-FX) has no such route at all. A strip with three of its four parts is a strip
 * with three parts; a strip that waits for all four is a blank row on the screen a reader opens
 * first.
 */
data class MarketMood(
    /**
     * The share of quoted markets that are up today, 0..100. Null before any figure has arrived.
     *
     * Counted over the markets whose change the feed actually sent. A market with no figure is not a
     * flat one and must not be counted as neither up nor down in a denominator — that would drag
     * every reading towards fifty in exactly the state where the table is half-loaded.
     */
    val breadth: Int? = null,
    val advancing: Int = 0,
    val declining: Int = 0,
    /** The biggest movers today, by the size of the move, whichever way it went. Up to three. */
    val movers: List<MarketTicker> = emptyList(),
    /** Busy *and* moving — see the class note on what this does and does not measure. Up to three. */
    val unusual: List<MarketTicker> = emptyList(),
) {
    /** Whether there is anything at all worth drawing. */
    val isEmpty: Boolean get() = breadth == null && movers.isEmpty() && unusual.isEmpty()

    /**
     * Which way the board is leaning, as a word rather than a number.
     *
     * Three bands and deliberately coarse, for the reason [com.coinepro.core.marketdata.MarketMood]
     * does not call itself an index: breadth of 54 % against 46 % is a board doing nothing, and a
     * strip that said «صعودی» at 54 would be reporting noise as news.
     */
    val lean: MarketLean
        get() = when {
            breadth == null -> MarketLean.UNKNOWN
            breadth >= LEANING -> MarketLean.UP
            breadth <= 100 - LEANING -> MarketLean.DOWN
            else -> MarketLean.MIXED
        }

    companion object {
        /**
         * How far past half the board has to be before the strip names a direction.
         *
         * Sixty-forty. Below that the two sides are within the range an ordinary session produces
         * with nothing behind it, and the honest word for it is «مختلط».
         */
        const val LEANING = 60

        /** How many markets each list carries. Three is a strip; six is a screen. */
        const val LISTED = 3

        /**
         * Read the board.
         *
         * Pure and synchronous over a table the store already holds, so this is a `remember` at the
         * call site rather than anything asynchronous. Nothing here fetches.
         *
         * @param covered the symbols the app has artwork for. The strip is a list of markets like
         *   any other and the same rule applies: no symbol without artwork reaches a list, so the
         *   filter is here rather than at the row. Empty means «do not filter», for a caller that
         *   has already filtered.
         */
        fun of(table: MarketTickerTable, covered: Set<String> = emptySet()): MarketMood {
            val tickers = table.tickers.values
                .filter { covered.isEmpty() || it.symbol.uppercase() in covered }
            if (tickers.isEmpty()) return MarketMood()

            val moved = tickers.mapNotNull { ticker -> ticker.changePercent24h?.let { ticker to it } }
            val advancing = moved.count { it.second > 0.0 }
            val declining = moved.count { it.second < 0.0 }
            // Counted over the ones that went somewhere. A board where four hundred markets are
            // exactly flat is a board whose feed has not updated, and dividing by it would report
            // that as perfect balance rather than as no data.
            val decided = advancing + declining
            val breadth = if (decided > 0) (advancing * 100.0 / decided).toInt() else null

            val movers = moved
                .sortedByDescending { abs(it.second) }
                .take(LISTED)
                .map { it.first }

            // «Busy and moving»: the intersection of the top of the board on turnover and the top on
            // movement. An intersection rather than a score, because a weighted score would need a
            // weight nobody can defend and would produce a different list on each poll as the two
            // rankings shuffled against each other.
            val byTurnover = tickers
                .mapNotNull { ticker -> ticker.turnover24h?.let { ticker to it } }
                .sortedByDescending { it.second }
                .take(BUSY_DEPTH)
                .map { it.first.symbol.uppercase() }
                .toSet()
            val unusual = moved
                .filter { it.first.symbol.uppercase() in byTurnover }
                .sortedByDescending { abs(it.second) }
                .take(LISTED)
                .map { it.first }

            return MarketMood(
                breadth = breadth,
                advancing = advancing,
                declining = declining,
                movers = movers,
                unusual = unusual,
            )
        }

        /**
         * How deep into the turnover ranking «busy» reaches.
         *
         * Twenty markets. On a board of four hundred that is the top five per cent, which is the
         * band a reader would recognise as «where the money is»; at a hundred it stops meaning
         * anything and the intersection becomes «the top movers again».
         */
        private const val BUSY_DEPTH = 20
    }
}

/** Which way the board is leaning, in words. See [MarketMood.lean]. */
enum class MarketLean { UP, DOWN, MIXED, UNKNOWN }
