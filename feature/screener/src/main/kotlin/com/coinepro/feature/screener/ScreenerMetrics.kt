package com.coinepro.feature.screener

import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.feature.screener.model.ScreenerRow
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a market's price and its recent bars into the row a filter can read.
 *
 * ### The snapshot does not carry any of this
 *
 * Both backends answer the snapshot endpoint with a symbol, a price, a bid, an ask and a timestamp.
 * There is no day's high, no low, no volume and no change percent on the wire — the app's own
 * `MarketQuote.changePercent` is null on every quote either feed has ever sent. So a screener that
 * filters on the day's move has to derive it, and the only source is the market's own bars.
 *
 * That is the constraint the whole feature is shaped around, and it is worth stating plainly rather
 * than discovering: a filter on price is free, and a filter on anything else costs one candle
 * request per market.
 *
 * ### The live price wins over the bar's close, and widens the bar
 *
 * The last daily bar is open — its close is the price as of whenever the page was served, and the
 * live socket is ahead of it. Taking the bar's close would show a reader a change percent that
 * disagrees with the price on the row above it, which is the single most obvious way for a market
 * table to look broken.
 *
 * For the same reason the high and the low are widened by the live price rather than taken from the
 * bar alone. A market trading above its recorded high is not at a negative distance from it; it is
 * making a new high, and «فاصله از سقف» must read zero rather than a minus sign.
 */
object ScreenerMetrics {

    /**
     * One row, from whatever is known about a market.
     *
     * [bars] may be empty, and that is the ordinary state of a row that has not been resolved yet:
     * the result carries the price and nothing else, every derived figure is null, and every
     * threshold on those figures declines it. See [ScreenerRow] for why null rather than zero.
     *
     * @param bars oldest first, as every gateway in this app returns them.
     * @param indicatorKeys the readings this screen's filters actually need. Computing anything
     *   else would be arithmetic over several hundred bars per market that nothing will read —
     *   which on a catalogue of a thousand is the difference between a screener and a stalled
     *   phone.
     */
    fun rowOf(
        meta: SymbolMeta,
        quote: MarketQuote?,
        bars: List<OhlcBar> = emptyList(),
        indicatorKeys: Set<String> = emptySet(),
    ): ScreenerRow {
        val last = bars.lastOrNull()
        val price = quote?.price?.takeIf { it.isFinite() && it > 0.0 } ?: last?.c
        val market = quote?.instrument?.marketType?.name ?: marketOf(meta)

        if (last == null) {
            return ScreenerRow(meta = meta, price = price, market = market)
        }

        val high = price?.let { max(last.h, it) } ?: last.h
        val low = price?.let { min(last.l, it) } ?: last.l
        // The close before the one in progress. Its absence — a market with exactly one bar of
        // history — falls back to the current bar's own open, which is what "today's move" means
        // when there is no yesterday to compare against.
        val reference = bars.getOrNull(bars.size - 2)?.c ?: last.o
        val at = price ?: last.c
        val changeAbsolute = (at - reference).takeIf { it.isFinite() }
        val changePercent = if (reference > 0.0) changeAbsolute?.div(reference)?.times(100.0) else null

        // Zero is what the MT5 side sends when it has no volume to report, and it is not a claim
        // that nothing traded. Treated as unknown, so «حجم» sorts it to the end rather than
        // presenting the entire forex catalogue as the quietest markets of the day.
        val volume = last.v.takeIf { it.isFinite() && it > 0.0 }
        val typical = (last.h + last.l + last.c) / 3.0
        val quoteVolume = volume?.takeIf { typical > 0.0 }?.times(typical)

        return ScreenerRow(
            meta = meta,
            price = price,
            changePercent = changePercent?.takeIf(Double::isFinite),
            changeAbsolute = changeAbsolute,
            volume = volume,
            quoteVolume = quoteVolume,
            high = high,
            low = low,
            indicators = if (indicatorKeys.isEmpty()) {
                emptyMap()
            } else {
                ScreenerIndicators.computeAll(indicatorKeys, bars)
            },
            market = market,
        )
    }

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
