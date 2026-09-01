package com.coinepro.core.chart

/**
 * One bar. The field names are the wire's, deliberately.
 *
 * Both backends send `{t,o,h,l,c,v}` — CoinePro-FX from its `candles` table, TradeYar from its
 * LBank cache — and renaming them here would mean a translation layer that exists only so this file
 * could read `open` instead of `o`. In a type that appears a thousand times per screen and is read
 * mostly inside loops of price arithmetic, the short names are also what the arithmetic looks like
 * on paper.
 *
 * [t] is the bar's **open** time in unix seconds, not milliseconds and not its close. Everything
 * that plots, pans or crosshairs depends on that being one convention, and the two feeds already
 * agree on it.
 *
 * [v] is nullable because it is genuinely absent on the MT5 side. Zero would be a fabricated
 * number, and a volume pane drawn from fabricated zeros looks like a market with no participants
 * rather than like a feed that does not report volume.
 */
data class Candle(
    val t: Long,
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Double? = null,
) {
    /** Whether the bar closed above its open. A doji counts as up, the way every terminal draws it. */
    val up: Boolean get() = c >= o

    /** The bar's range. Used everywhere a "typical move" is needed. */
    val range: Double get() = h - l

    /** The mid, which several indicators and every non-standard chart type are built on. */
    val mid: Double get() = (h + l) / 2

    /** `(h + l + c) / 3` — the "typical price" of CCI, VWAP and the volume-weighted family. */
    val typical: Double get() = (h + l + c) / 3
}

/**
 * A run of bars, plus the columns the indicator library actually wants.
 *
 * Indicators are written against parallel arrays rather than a list of objects, because that is what
 * the arithmetic is: a rolling mean reads one column, and walking a list of six-field objects to
 * pull one field out is both slower and harder to read. The columns are computed once here rather
 * than at every call site.
 *
 * The bars are required to be in ascending time order, which is the order both feeds send and the
 * order every calculation below assumes. It is checked once, on construction, rather than defended
 * against in fifty loops: a reversed series does not throw anywhere, it just draws a chart that is
 * subtly and consistently wrong.
 */
class CandleSeries(val bars: List<Candle>) {

    init {
        for (index in 1 until bars.size) {
            require(bars[index].t >= bars[index - 1].t) {
                "Candles must be in ascending time order; bar $index goes backwards"
            }
        }
    }

    val size: Int get() = bars.size
    val isEmpty: Boolean get() = bars.isEmpty()

    val time: LongArray by lazy { LongArray(bars.size) { bars[it].t } }
    val open: DoubleArray by lazy { DoubleArray(bars.size) { bars[it].o } }
    val high: DoubleArray by lazy { DoubleArray(bars.size) { bars[it].h } }
    val low: DoubleArray by lazy { DoubleArray(bars.size) { bars[it].l } }
    val close: DoubleArray by lazy { DoubleArray(bars.size) { bars[it].c } }

    /** Volume with absent entries as zero — for the panes that must draw *something* per bar. */
    val volume: DoubleArray by lazy { DoubleArray(bars.size) { bars[it].v ?: 0.0 } }

    /** Whether any bar reports volume at all. False on the MT5 feed, and the pane hides itself. */
    val hasVolume: Boolean by lazy { bars.any { it.v != null && it.v > 0 } }

    operator fun get(index: Int): Candle = bars[index]

    /**
     * Build every column now, on the thread this is called from, and return the same series.
     *
     * The columns are lazy so that a series built only to be cached or counted never pays for
     * them. The cost of that laziness is *where* they get paid for: the first read of `close` on a
     * fresh series is the first indicator or the first draw, and both of those run on the main
     * thread. On a twenty-thousand-bar minute chart that is six array builds and twenty thousand
     * `any` steps in the middle of a frame — a visible hitch at exactly the moment the reader is
     * looking. The controller calls this from a background dispatcher before it publishes, so the
     * main thread finds the columns already there.
     */
    fun warm(): CandleSeries = apply {
        time; open; high; low; close; volume; hasVolume
    }

    companion object {
        val EMPTY = CandleSeries(emptyList())
    }
}
