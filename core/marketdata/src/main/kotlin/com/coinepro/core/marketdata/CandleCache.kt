package com.coinepro.core.marketdata

/**
 * The bars this app already has, so a chart has something true to draw before it asks for anything.
 *
 * ### Why it exists
 *
 * "The chart won't come up" is the single loudest complaint about every app in this category, and
 * in Persian-language reviews it is **19.3%** of negative chart mentions — larger than the next
 * category by two and a half times. The observed threshold in those reviews is sharp: an instant
 * chart is the stated baseline, three seconds is *noticed*, five seconds reliably produces a one-
 * or two-star review, and a blank chart on returning from the background is what makes people stop
 * opening the app.
 *
 * None of that is fixed by a faster request. It is fixed by not needing one to draw.
 *
 * ### What a cached series is and is not
 *
 * It is **the last thing that was true**, not a guess. Every bar in it was served by the backend;
 * the only thing that has changed is that time has passed. So it is drawn immediately, and the
 * network answer replaces it the moment it lands.
 *
 * It is *not* a substitute for the fetch, and it is never used to answer "what is the price now" —
 * `MarketDataController` owns that, and the chart's own last-price tag reads the live series once
 * it arrives. A cached chart with a live price on it is the correct combination; a cached price
 * presented as live is not.
 */
interface CandleCache {

    /**
     * The newest bars held for one series, oldest first, or empty.
     *
     * Never throws and never blocks on a network. A cache that can fail a chart open is worse than
     * no cache: it turns a slow path into a broken one.
     */
    suspend fun read(symbol: String, timeframe: Timeframe, limit: Int = READ_LIMIT): List<OhlcBar>

    /** Merge a freshly fetched page in, and trim the series back to [KEEP_BARS]. */
    suspend fun write(symbol: String, timeframe: Timeframe, bars: List<OhlcBar>)

    /**
     * The same read, for a series the reader may have made up.
     *
     * ### Why the interval and not the timeframe is the real key
     *
     * A cache entry is identified by the series it holds, and a custom interval is a series no
     * [Timeframe] names. Keying on an enum forces every custom interval to borrow a preset's slot,
     * and two of them then overwrite each other: a reader who flips between 205 minutes and 137
     * minutes gets whichever was written last, drawn under the other one's label. Worse, a bare
     * minute count could land on a preset's slot and a 5-minute custom would silently replace the
     * cached `M5`. So the key is [ChartInterval.wire] — `"M5"` for the preset, `"205"` for the
     * custom — which cannot collide because no preset's spelling is a bare number.
     *
     * The default here routes a preset to the [Timeframe] method and declines a custom interval,
     * because an implementation that only knows how to key on an enum genuinely has nowhere to put
     * one, and answering with another series' bars would be worse than answering with none.
     * `RoomCandleCache`, in `core:database`, overrides both and keys on the wire string.
     */
    suspend fun read(symbol: String, interval: ChartInterval, limit: Int = READ_LIMIT): List<OhlcBar> =
        (interval as? ChartInterval.Preset)?.let { read(symbol, it.timeframe, limit) } ?: emptyList()

    /** The interval-keyed write. See [read] for why the wire spelling is the key and not the enum. */
    suspend fun write(symbol: String, interval: ChartInterval, bars: List<OhlcBar>) {
        val preset = interval as? ChartInterval.Preset ?: return
        write(symbol, preset.timeframe, bars)
    }

    /** Everything. What a sign-out does — see the note in [NoOpCandleCache]. */
    suspend fun clear()

    companion object {
        /**
         * How many bars a chart open reads back.
         *
         * Three hundred, which is [CandleGateway.DEFAULT_LIMIT] — the same window the first fetch
         * asks for, so the cached chart and the fetched one are the same shape and the swap is
         * invisible. Reading more would draw bars the reader would have to pan to reach.
         */
        const val READ_LIMIT = CandleGateway.DEFAULT_LIMIT

        /**
         * How many bars are kept per series.
         *
         * Two thousand. Enough that paging back through history stays cached — which is the second
         * place a reader waits — and far below the point where the table is worth worrying about:
         * two thousand rows of nine numbers is a few hundred kilobytes per series, and the trim
         * runs on every write so it cannot accumulate.
         *
         * TradingView's own paid tiers hold 5,000 to 40,000 intraday bars. This is a phone cache,
         * not a research archive, and the honest ceiling is much lower.
         */
        const val KEEP_BARS = 2_000
    }
}

/**
 * No cache at all.
 *
 * The default in every test and preview, and the correct behaviour in exactly one production case:
 * there is deliberately **no** cache clearing on sign-out. A candle is a public fact about a
 * market — the price of gold at ten o'clock is not the reader's private data, and throwing it away
 * would slow the next chart open to protect nothing.
 */
object NoOpCandleCache : CandleCache {
    override suspend fun read(symbol: String, timeframe: Timeframe, limit: Int): List<OhlcBar> = emptyList()
    override suspend fun write(symbol: String, timeframe: Timeframe, bars: List<OhlcBar>) = Unit
    override suspend fun read(symbol: String, interval: ChartInterval, limit: Int): List<OhlcBar> = emptyList()
    override suspend fun write(symbol: String, interval: ChartInterval, bars: List<OhlcBar>) = Unit
    override suspend fun clear() = Unit
}
