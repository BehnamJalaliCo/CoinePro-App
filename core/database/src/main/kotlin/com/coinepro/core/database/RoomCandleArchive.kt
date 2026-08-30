package com.coinepro.core.database

import com.coinepro.core.marketdata.ArchiveSpan
import com.coinepro.core.marketdata.CandleArchive
import com.coinepro.core.marketdata.CandleCache
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.OhlcBar

/**
 * [CandleArchive] over the same Room table [RoomCandleCache] reads.
 *
 * ### Why this is a second class and not a second interface on the first one
 *
 * `CandleCache.write` and `CandleArchive.write` have the same parameters and different return
 * types — the archive answers how many bars were *new*, which is the signal `fillHistory` uses to
 * tell a venue that has run out of history from one that is ignoring `before` and handing back the
 * same page for ever. Kotlin cannot resolve one function against both, so one object cannot be
 * both. That is a fact about the language rather than a design, but the split it forces is honest:
 * these are two different jobs over one table.
 *
 * ### One table, two bounds
 *
 * The cache trims to [CandleCache.KEEP_BARS] so a chart opens instantly on a few hundred bars. The
 * archive trims to [CandleArchive.MAX_BARS_PER_SERIES], which is why history paged back now
 * survives the night — under the cache's bound alone, a fortnight of dragging was discarded at two
 * thousand bars and paid for again the next morning. Whichever writes last sets the ceiling, so
 * the archive's writes are what make the depth accumulate and the cache's are simply cheaper.
 *
 * Every method swallows its failures, exactly as the cache does and for the same reason: a store
 * that can fail a chart open turns a slow path into a broken one.
 */
class RoomCandleArchive(
    private val dao: CandleCacheDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CandleArchive {

    override suspend fun read(
        symbol: String,
        interval: ChartInterval,
        limit: Int,
        before: Long?,
    ): List<OhlcBar> = runCatching {
        val key = symbol.uppercase()
        val count = limit.coerceAtLeast(1)
        val rows = if (before == null) {
            dao.newest(key, interval.wire, count)
        } else {
            dao.before(key, interval.wire, before, count)
        }
        // Read newest-first so the index does the work, handed back oldest-first because that is
        // the order every consumer of a series expects.
        rows.asReversed().map { OhlcBar(t = it.t, o = it.o, h = it.h, l = it.l, c = it.c, v = it.v) }
    }.getOrDefault(emptyList())

    override suspend fun write(
        symbol: String,
        interval: ChartInterval,
        bars: List<OhlcBar>,
    ): Int = runCatching {
        if (bars.isEmpty()) return@runCatching 0
        val key = symbol.uppercase()
        // Counted against what is held *before* the insert, rather than inferred from row counts
        // after it: the insert replaces on conflict, so a page the caller already had would
        // otherwise read as progress and a fill against a route that ignores `before` would never
        // stop.
        val held = dao.span(key, interval.wire)
        val fresh = if (held == null || held.count == 0) {
            bars.size
        } else {
            bars.count { it.t < held.oldest || it.t > held.newest }
        }
        val at = nowMillis()
        dao.upsert(bars.mapNotNull { it.toArchiveEntity(key, interval.wire, at) })
        dao.trim(key, interval.wire, CandleArchive.MAX_BARS_PER_SERIES)
        evictIfOverBound()
        fresh
    }.getOrDefault(0)

    override suspend fun span(symbol: String, interval: ChartInterval): ArchiveSpan? = runCatching {
        dao.span(symbol.uppercase(), interval.wire)
            ?.takeIf { it.count > 0 }
            ?.let { ArchiveSpan(count = it.count, oldest = it.oldest, newest = it.newest) }
    }.getOrNull()

    override suspend fun clear() {
        runCatching { dao.clearAll() }
    }

    /**
     * Evict whole series, least recently written first, until the table is under its total bound.
     *
     * Whole series rather than the oldest rows across the table, because a series with a hole
     * punched in the middle of it draws as a market that was shut for a fortnight — which is a lie
     * the reader has no way to see through, and worse than having lost the series outright.
     */
    private suspend fun evictIfOverBound() {
        var total = dao.totalBars()
        if (total <= CandleArchive.MAX_BARS_TOTAL) return
        for (series in dao.seriesByAge()) {
            if (total <= CandleArchive.MAX_BARS_TOTAL) return
            dao.clearSeries(series.symbol, series.intervalWire)
            total -= series.bars
        }
    }
}

/**
 * A wire bar as a row, or null if it is not worth keeping.
 *
 * The same rule the cache applies, and for the same reason: a `NaN` high in a stored series
 * collapses the whole price axis the moment it is drawn, and would do so on every subsequent open,
 * silently, long after the bad response that produced it was forgotten.
 */
private fun OhlcBar.toArchiveEntity(
    symbol: String,
    intervalWire: String,
    at: Long,
): CachedCandleEntity? {
    if (t <= 0L) return null
    if (!o.isFinite() || !h.isFinite() || !l.isFinite() || !c.isFinite()) return null
    return CachedCandleEntity(
        symbol = symbol,
        interval = intervalWire,
        t = t,
        o = o,
        h = h,
        l = l,
        c = c,
        v = if (v.isFinite()) v else 0.0,
        cachedAtEpochMillis = at,
    )
}
