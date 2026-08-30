package com.coinepro.core.marketdata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Every bar this app has been given for a series, kept so the reader's window **grows**.
 *
 * ### Why this is not [CandleCache]
 *
 * [CandleCache] answers one question — "draw something true before the network answers" — and it is
 * trimmed to [CandleCache.KEEP_BARS] on every write for exactly that reason: it holds a chart, not
 * a history. That trim is also why paging back has never accumulated. A reader who spent a minute
 * dragging a five-minute chart back through a fortnight closed the app and got a fortnight less
 * chart the next morning, because the only thing kept was the newest two thousand bars. The work
 * was thrown away, and the next session paid for it again.
 *
 * An archive is the other half. It is written to by the same loads, it is never trimmed back to a
 * screenful, and it is read from before the network on a page-back — so the second walk through a
 * week costs nothing, and the depth a reader has accumulated is theirs from then on.
 *
 * ### What it is bounded by, and why a phone survives it
 *
 * Two ceilings, and both are real rather than decorative:
 *
 *  * [MAX_BARS_PER_SERIES] — fifty thousand bars for one symbol at one interval. That is the
 *    ceiling the owner asked for and it is what the paging in [fillHistory] stops at.
 *  * [MAX_BARS_TOTAL] — a quarter of a million bars across every series together, which is roughly
 *    [MAX_BARS_TOTAL] × [BYTES_PER_BAR] ≈ 16 MB of rows. Reached by evicting whole series, least
 *    recently read first, because half a series is worse than none: a chart drawn from a history
 *    with a hole in the middle is a chart with a gap nobody can explain.
 *
 * The per-series ceiling alone is not a bound. A reader who opens forty symbols on four intervals
 * has a hundred and sixty series, and forty thousand bars each is eight million rows — half a
 * gigabyte, on a phone, for charts nobody is looking at. The total is the bound that matters, and
 * it is the one with a test.
 */
interface CandleArchive {

    /**
     * A window of the archive, oldest first, or empty.
     *
     * [before] selects the [limit] bars that open strictly before it — the same promise the gateway
     * makes, so a page-back reads the same shape from disk as from the network and a caller does
     * not care which answered. Null asks for the newest [limit].
     *
     * Never throws and never blocks on a network, for the same reason [CandleCache.read] does not:
     * a store that can fail a chart open turns a slow path into a broken one.
     */
    suspend fun read(
        symbol: String,
        interval: ChartInterval,
        limit: Int = READ_LIMIT,
        before: Long? = null,
    ): List<OhlcBar>

    /**
     * Merge bars in, and answer how many of them were **new**.
     *
     * The count is not bookkeeping. It is how [fillHistory] tells a venue that has run out of
     * history from one that is ignoring `before` and handing back the same page forever — TradeYar's
     * public route does exactly that — and without it a backward fill against that route is an
     * infinite loop of identical requests.
     */
    suspend fun write(symbol: String, interval: ChartInterval, bars: List<OhlcBar>): Int

    /** What is held for one series: how many bars, and the ends of them. Null when nothing is. */
    suspend fun span(symbol: String, interval: ChartInterval): ArchiveSpan?

    /** Everything, for every series. A diagnostic action and a reset, not part of any load. */
    suspend fun clear()

    companion object {
        /**
         * The ceiling for one series: fifty thousand bars.
         *
         * The number the owner asked for, and it is capacity rather than a promise about what
         * exists — see [fillHistory], which says which of the two ways it stopped. At one minute a
         * bar it is thirty-four days; at a day a bar it is longer than either venue has been
         * trading. It is also about 3.2 MB of rows for a single series, which is why the total
         * below exists.
         */
        const val MAX_BARS_PER_SERIES = 50_000

        /**
         * The ceiling across every series: a quarter of a million bars, about 16 MB.
         *
         * Chosen against the phone this app is actually used on rather than against a number that
         * sounds generous. A mid-range Android in Iran ships 64 GB with a great deal of it already
         * spent, and an app that quietly grows to half a gigabyte of candles is one that gets
         * uninstalled for a reason nobody ever writes in a review.
         */
        const val MAX_BARS_TOTAL = 250_000

        /**
         * What one archived bar costs on disk, near enough to reason with.
         *
         * A row is a symbol, an interval, six numbers and a stamp; in SQLite with its index that is
         * in the neighbourhood of sixty-four bytes. Deliberately an estimate and named as one: the
         * point is to be able to say "sixteen megabytes" out loud and be right to the order of
         * magnitude, not to predict a page count.
         */
        const val BYTES_PER_BAR = 64

        /** What a plain read hands back when the caller does not say. One page of chart. */
        const val READ_LIMIT = CandleGateway.DEFAULT_LIMIT

        /** Roughly what [bars] rows occupy. See [BYTES_PER_BAR] for how rough. */
        fun estimatedBytes(bars: Int): Long = bars.toLong() * BYTES_PER_BAR
    }
}

/**
 * What the archive holds for one series.
 *
 * [oldest] is the one a page-back needs and the one a reader is told about; [count] is what the
 * ceiling is measured against. Both are read together because every caller that wants one wants the
 * other, and two round trips to a database to answer one question is two chances to see the store
 * mid-write.
 */
data class ArchiveSpan(val count: Int, val oldest: Long, val newest: Long)

/**
 * No archive at all.
 *
 * The default wherever an archive has not been wired — a preview, a test double, the guest surface
 * before it is given one — and it is a working answer rather than a hole: every read is empty,
 * every write is dropped, and the chart falls back to the network exactly as it did before this
 * existed.
 */
object NoOpCandleArchive : CandleArchive {
    override suspend fun read(symbol: String, interval: ChartInterval, limit: Int, before: Long?): List<OhlcBar> =
        emptyList()

    override suspend fun write(symbol: String, interval: ChartInterval, bars: List<OhlcBar>): Int = 0

    override suspend fun span(symbol: String, interval: ChartInterval): ArchiveSpan? = null

    override suspend fun clear() = Unit
}

/**
 * An archive in memory, bounded exactly as a durable one is.
 *
 * It exists for two reasons and neither is "somewhere to put the interface". It is what the paging
 * and the bounds are tested against without a database, and it is the honest fallback on a build
 * with no persistence wired: a session's worth of accumulated history is still much better than
 * re-fetching a week every time the reader drags left, and it costs the same bounded memory the
 * durable one costs in rows.
 *
 * Guarded by a [Mutex] rather than a synchronised block: every method here is already suspending,
 * the chart writes from one coroutine while a fill writes from another, and a lock that parks a
 * coroutine is the one that does not park the thread the frame is drawn on.
 */
class InMemoryCandleArchive(
    private val maxBarsPerSeries: Int = CandleArchive.MAX_BARS_PER_SERIES,
    private val maxBarsTotal: Int = CandleArchive.MAX_BARS_TOTAL,
) : CandleArchive {

    // Access-ordered, so `keys.first()` is genuinely the least recently used series and eviction
    // does not throw away the chart the reader is looking at. A plain map would evict by insertion
    // order, which is "the first symbol you opened today" — usually the one you are still watching.
    private val series = LinkedHashMap<String, MutableList<OhlcBar>>(16, 0.75f, true)

    private val lock = Mutex()

    override suspend fun read(
        symbol: String,
        interval: ChartInterval,
        limit: Int,
        before: Long?,
    ): List<OhlcBar> = lock.withLock {
        val held = series[key(symbol, interval)] ?: return emptyList()
        val window = if (before == null) held else held.filter { it.t < before }
        val count = limit.coerceAtLeast(1)
        // The newest `count` of the window, still oldest first. Reading from the far end is what
        // both callers want: the chart wants the live edge, and a page-back wants the bars nearest
        // the hole it is filling rather than the oldest ones in the store.
        if (window.size <= count) window.toList() else window.subList(window.size - count, window.size).toList()
    }

    override suspend fun write(symbol: String, interval: ChartInterval, bars: List<OhlcBar>): Int {
        if (bars.isEmpty()) return 0
        return lock.withLock {
            val held = series.getOrPut(key(symbol, interval)) { ArrayList() }
            val known = held.mapTo(HashSet(held.size)) { it.t }
            // Only finite bars, and only ones not already held. A `NaN` high in an archive is worse
            // than in a cache: it collapses the price axis on every open from now on, long after
            // the response that produced it is forgotten, and nothing on screen says why.
            val fresh = bars.filter { it.t > 0L && it.isFinite() && known.add(it.t) }
            if (fresh.isEmpty()) return@withLock 0
            held += fresh
            held.sortBy { it.t }
            // The oldest go first at the ceiling, because the ceiling is only ever reached by
            // walking backwards and the reader is walking away from them.
            if (held.size > maxBarsPerSeries) {
                held.subList(0, held.size - maxBarsPerSeries).clear()
            }
            evictToTotal()
            fresh.size
        }
    }

    override suspend fun span(symbol: String, interval: ChartInterval): ArchiveSpan? = lock.withLock {
        val held = series[key(symbol, interval)]?.takeIf { it.isNotEmpty() } ?: return null
        ArchiveSpan(count = held.size, oldest = held.first().t, newest = held.last().t)
    }

    override suspend fun clear() {
        lock.withLock { series.clear() }
    }

    /** How many bars are held across every series, which is what [maxBarsTotal] bounds. */
    suspend fun totalBars(): Int = lock.withLock { series.values.sumOf { it.size } }

    /**
     * Drop whole series, least recently read first, until the total fits.
     *
     * Whole ones, never a slice off each: a series trimmed in the middle draws as a chart with a
     * gap the reader cannot account for, and a gap in a candle series is supposed to mean the
     * market was shut.
     */
    private fun evictToTotal() {
        var total = series.values.sumOf { it.size }
        while (total > maxBarsTotal && series.size > 1) {
            val oldestKey = series.keys.first()
            total -= series.remove(oldestKey)?.size ?: 0
        }
    }

    private fun key(symbol: String, interval: ChartInterval): String =
        symbol.uppercase() + "@" + interval.wire
}

/**
 * Whether every price on this bar is a number.
 *
 * `NaN` and the infinities arrive from a feed that divided by something, and one of them anywhere
 * in a series rescales the whole price axis to nothing. Volume is not checked here: it is genuinely
 * absent on the MT5 feed and a missing volume is not a reason to throw away a good price.
 */
private fun OhlcBar.isFinite(): Boolean = o.isFinite() && h.isFinite() && l.isFinite() && c.isFinite()
