package com.coinepro.feature.screener

import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.feature.screener.model.ScreenerRow
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a market's price, the day's table and its recent bars into the row a filter can read.
 *
 * ### The snapshot carries none of this, and for one platform that used to be the end of it
 *
 * Both backends answer the snapshot endpoint with a symbol, a price, a bid, an ask and a timestamp.
 * There is no day's high, no low, no volume and no change percent on the wire — the app's own
 * `MarketQuote.changePercent` is null on every quote either feed has ever sent. So a screener that
 * filters on the day's move has to get it from somewhere else, and for a long time the only
 * somewhere else was the market's own bars: one candle request per market, which is what made a
 * filter on price free and a filter on anything else expensive.
 *
 * TradeYar has since built the day's table — every market's open, high, low, change, volume and
 * turnover in a single request, cached five seconds at the server. Where that answers, the figures
 * below are the venue's own and the whole table costs one call. CoinePro-FX has no equivalent
 * route, so gold and silver still come from their bars and the per-market cost is still real there.
 * Both paths are live at once and the screener runs a mixed catalogue, which is why every figure
 * below picks a source rather than assuming one.
 *
 * ### The venue wins wherever both can answer
 *
 * Not a performance choice: the venue's rolling twenty-four hours is what its own site prints and
 * what a reader will hold this table against, while a change derived from two daily closes is
 * measured from a midnight the venue does not use. The two disagree by a few tenths on a quiet day
 * and by more on a loud one. `feature/heatmap` states the same rule in the same words and reached
 * it first.
 *
 * ### The live price wins over both, and widens the day's range
 *
 * The last daily bar is open and the venue's table is up to five seconds old; the live socket is
 * ahead of either. Taking anything else would show a reader a change percent that disagrees with the
 * price on the row beside it, which is the single most obvious way for a market table to look
 * broken.
 *
 * For the same reason the high and the low are widened by the live price rather than taken from the
 * source alone. A market trading above its recorded high is not at a negative distance from it; it
 * is making a new high, and «فاصله از سقف» must read zero rather than a minus sign.
 */
object ScreenerMetrics {

    /**
     * One row, from whatever is known about a market.
     *
     * [ticker] and [bars] may both be absent, and that is the ordinary state of a row that has not
     * been resolved yet: the result carries the price and nothing else, every derived figure is
     * null, and every threshold on those figures declines it. See [ScreenerRow] for why null rather
     * than zero.
     *
     * @param ticker the day's figures for this market, where the platform serves them. Every field
     *   on it but the price is nullable and the server omits rather than zeroes, so an absent field
     *   here falls through to the bar rather than becoming a figure of zero.
     * @param bars oldest first, as every gateway in this app returns them.
     * @param indicators readings already computed for this market, keyed by
     *   [com.coinepro.feature.screener.model.ScreenerIndicatorId.normalisedKey].
     *
     * The readings are passed **in** rather than computed here, and that is not a style preference.
     * Reducing one indicator is arithmetic over several hundred bars; a scan is four hundred markets
     * times however many conditions the screen carries, and this function is called from the
     * controller on every rebuild — a filter chip, a quote tick, a ticker poll. Computing inside it
     * put that work on whichever thread the caller happened to be on, which on this screen is the
     * one drawing the table. [ScreenerController] now computes them on a background dispatcher, once
     * per (symbol, indicator, period) for the life of a scan, and hands the answers here.
     */
    fun rowOf(
        meta: SymbolMeta,
        quote: MarketQuote?,
        bars: List<OhlcBar> = emptyList(),
        ticker: MarketTicker? = null,
        indicators: Map<String, Double> = emptyMap(),
    ): ScreenerRow {
        val last = bars.lastOrNull()
        val price = quote?.price?.positive()
            ?: ticker?.last?.positive()
            ?: last?.c
        val market = quote?.instrument?.marketType?.name ?: marketOf(meta)

        if (last == null && ticker == null) {
            return ScreenerRow(meta = meta, price = price, market = market, indicators = indicators)
        }

        // Widened by the live price in both directions, and null where neither source reported a
        // range at all. That guard is load-bearing: without it a market that arrived with a price
        // and nothing else would take that price as both its high and its low, which is a range of
        // exactly zero — not "unknown" but the specific claim that the price has not moved all day.
        val reportedHigh = (ticker?.high24h ?: last?.h)?.takeIf(Double::isFinite)
        val reportedLow = (ticker?.low24h ?: last?.l)?.takeIf(Double::isFinite)
        val high = reportedHigh?.let { top -> price?.let { max(top, it) } ?: top }
        val low = reportedLow?.let { bottom -> price?.let { min(bottom, it) } ?: bottom }

        val at = price ?: last?.c
        val venueChange = ticker?.changePercent24h?.takeIf(Double::isFinite)
        // The close before the one in progress. Its absence — a market with exactly one bar of
        // history — falls back to the current bar's own open, which is what "today's move" means
        // when there is no yesterday to compare against.
        val barReference = (bars.getOrNull(bars.size - 2)?.c ?: last?.o)?.takeIf { it > 0.0 }
        // The change is taken as a pair from one source or the other and never half from each. A
        // reader who subtracts «تغییر مطلق» from the price expects to land on the number
        // «تغییر روزانه» is measured from, and a percentage over the venue's rolling day beside an
        // absolute over the daily bar's would not agree with itself in the one place a screener is
        // checked. So where the venue answered the percentage, the absolute is measured from the
        // venue's own reference or left unknown — never borrowed from the bar.
        val reference = if (venueChange != null) ticker?.open24h?.positive() else barReference
        val changeAbsolute = reference?.let { from -> at?.minus(from) }?.takeIf(Double::isFinite)
        val changePercent = venueChange
            ?: reference?.let { from -> changeAbsolute?.div(from)?.times(100.0) }?.takeIf(Double::isFinite)

        // Zero is what the MT5 side sends when it has no volume to report, and it is not a claim
        // that nothing traded. Treated as unknown, so «حجم» sorts it to the end rather than
        // presenting the entire forex catalogue as the quietest markets of the day.
        val barVolume = last?.v?.positive()
        val typical = last?.let { (it.h + it.l + it.c) / 3.0 }?.positive()
        // Turnover falls back to the bar's own quantity at the bar's own typical price, and never
        // to the venue's base volume. The two are different quantities: «ارزش معاملات» across a
        // mixed list is money moved, and filling it from a count of coins would rank a cheap token
        // above Bitcoin. The relay this route replaced has exactly that bug in the field it calls
        // `volume24h`, and said so.
        val volume = ticker?.volume24h?.positive() ?: barVolume
        val quoteVolume = ticker?.turnover24h?.positive()
            ?: typical?.let { atTypical -> barVolume?.times(atTypical) }

        return ScreenerRow(
            meta = meta,
            price = price,
            changePercent = changePercent,
            changeAbsolute = changeAbsolute,
            volume = volume,
            quoteVolume = quoteVolume,
            high = high,
            low = low,
            indicators = indicators,
            market = market,
            // A row the day's table answered for is read, even where the answer was a price and
            // nothing else — see [ScreenerRow.resolved] for why that is not the same question as
            // "does this row have figures on it".
            resolved = ticker != null || (high != null && low != null),
        )
    }

    /** A figure is a figure only if it is finite and above zero. Used often enough to name. */
    private fun Double.positive(): Double? = takeIf { it.isFinite() && it > 0.0 }

    /**
     * Which backend quotes a market, for a row that has no live quote to ask.
     *
     * Derived from the asset class rather than guessed: TradeYar carries the coins and CoinePro-FX
     * carries everything else, which is the split `MarketDataSymbols` makes and the reason it
     * refuses to offer a combined list.
     */
    private fun marketOf(meta: SymbolMeta): String =
        if (meta.category == SymbolCategory.CRYPTO) MarketType.CRYPTO.name else MarketType.FOREX.name
}
