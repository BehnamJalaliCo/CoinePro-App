package com.coinepro.core.chartevents

import com.coinepro.core.marketintel.MarketRelevance

/**
 * Which chart a news item or a release belongs to.
 *
 * The reading has to go both ways or half the feature is missing: an event marked on a chart is
 * worth little if a reader who found the same story in the news list cannot get from it to the
 * candle it happened on.
 *
 * ### The feed names a market, not an instrument
 *
 * Both documents tag a row with `gold`, `silver` or `crypto` — a market — and a chart route needs a
 * symbol. There is no lossless conversion, so this makes the one choice it can defend: each market
 * gets its reference instrument, the one both catalogues carry and the one a reader who taps
 * «نمایش روی نمودار» on a gold story expects to land on. It is stated here, in one place, so that
 * choice is visible rather than scattered through two screens.
 *
 * A row tagged with nothing is general market news and maps to no instrument at all — and then the
 * entry is not offered, rather than dropping the reader onto an arbitrary chart and implying the
 * story was about it.
 */
object ChartEventSymbols {

    /** Gold's reference instrument on CoinePro-FX. */
    const val GOLD_SYMBOL: String = "XAUUSD"

    /** Silver's. */
    const val SILVER_SYMBOL: String = "XAGUSD"

    /** Crypto's reference instrument on TradeYar — the pair the catalogue is built around. */
    const val CRYPTO_SYMBOL: String = "BTCUSDT"

    /**
     * The instrument to open, or null when the row names no market.
     *
     * Gold before silver before crypto where a row is tagged with several, which is arbitrary only
     * in the sense that any total order would be: what matters is that it is fixed, so the same
     * story opens the same chart every time rather than following set iteration order.
     */
    fun symbolFor(relevance: Set<MarketRelevance>): String? = when {
        MarketRelevance.GOLD in relevance -> GOLD_SYMBOL
        MarketRelevance.SILVER in relevance -> SILVER_SYMBOL
        MarketRelevance.CRYPTO in relevance -> CRYPTO_SYMBOL
        else -> null
    }
}
