package com.coinepro.core.database

import com.coinepro.core.marketdata.CandleCache
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe

/**
 * [CandleCache] over the Room table.
 *
 * Every method swallows its failures. That is deliberate and it is the whole contract: a cache
 * that can fail a chart open turns a slow path into a broken one, which is strictly worse than
 * having no cache. A disk error here means the reader waits for the network — the behaviour they
 * had before this existed — and nothing else.
 *
 * ### One key, two ways in
 *
 * The [Timeframe] pair and the [ChartInterval] pair are not two caches. Both reduce to the same
 * column, because `Timeframe.wire` and `ChartInterval.Preset(it).wire` are the same string by
 * construction: a chart opened on `H4` before this class knew about intervals finds its own bars
 * again afterwards, and a caller that has a preset in hand does not have to wrap it to hit the
 * cache. What the interval pair adds is the ability to store a series no preset names.
 */
class RoomCandleCache(
    private val dao: CandleCacheDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CandleCache {

    override suspend fun read(symbol: String, timeframe: Timeframe, limit: Int): List<OhlcBar> =
        readWire(symbol, timeframe.wire, limit)

    override suspend fun read(symbol: String, interval: ChartInterval, limit: Int): List<OhlcBar> =
        readWire(symbol, interval.wire, limit)

    override suspend fun write(symbol: String, timeframe: Timeframe, bars: List<OhlcBar>) =
        writeWire(symbol, timeframe.wire, bars)

    override suspend fun write(symbol: String, interval: ChartInterval, bars: List<OhlcBar>) =
        writeWire(symbol, interval.wire, bars)

    override suspend fun clear() {
        runCatching { dao.clearAll() }
    }

    private suspend fun readWire(symbol: String, intervalWire: String, limit: Int): List<OhlcBar> =
        runCatching {
            dao.newest(symbol.uppercase(), intervalWire, limit.coerceAtLeast(1))
                // Read newest-first so the index does the work, handed back oldest-first because
                // that is the order every consumer of a series expects.
                .asReversed()
                .map { OhlcBar(t = it.t, o = it.o, h = it.h, l = it.l, c = it.c, v = it.v) }
        }.getOrDefault(emptyList())

    private suspend fun writeWire(symbol: String, intervalWire: String, bars: List<OhlcBar>) {
        if (bars.isEmpty()) return
        val key = symbol.uppercase()
        val at = nowMillis()
        runCatching {
            dao.replace(
                candles = bars.mapNotNull { bar -> bar.toEntity(key, intervalWire, at) },
                symbol = key,
                intervalWire = intervalWire,
                keep = CandleCache.KEEP_BARS,
            )
        }
    }
}

/**
 * A wire bar as a cache row, or null if it is not worth keeping.
 *
 * A bar with a non-positive timestamp or a non-finite price is dropped rather than stored. The
 * point is not tidiness: a `NaN` high in a cached series collapses the whole price axis the moment
 * it is drawn, and it would do so on *every* subsequent open, silently, long after the bad
 * response that produced it was forgotten.
 */
private fun OhlcBar.toEntity(symbol: String, intervalWire: String, at: Long): CachedCandleEntity? {
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
        // Volume is optional on one of the two feeds, and a non-finite one is not a reason to drop
        // an otherwise good bar — the chart already hides the volume pane when the feed sends none.
        v = if (v.isFinite()) v else 0.0,
        cachedAtEpochMillis = at,
    )
}
