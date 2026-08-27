package com.coinepro.core.guest

import com.coinepro.core.common.AppResult
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.symbols.SymbolArtwork
import com.coinepro.core.symbols.SymbolClassifier

/**
 * The public feed, wearing the interfaces the signed-in app already uses.
 *
 * This is the piece that makes the guest experience the *same* app rather than a smaller one made
 * of different parts. The markets list, the search screen, the chart and every sparkline are built
 * against [MarketCatalogGateway] and [CandleGateway]; give a guest an implementation of those two
 * that reads TradeYar's public routes and the whole surface works, unchanged, with no `if (guest)`
 * anywhere inside it.
 *
 * Two adapters, and nothing else — deliberately. Everything a guest cannot have (signals, the AI,
 * a balance, an order) is a *different* gateway with no public counterpart, so the boundary is
 * drawn by which interfaces exist here rather than by a flag some screen has to remember to read.
 */
class GuestMarketCatalogGateway(
    private val gateway: GuestGateway,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : MarketCatalogGateway {

    /**
     * Everything the public feed quotes.
     *
     * The empty symbol list is what asks for the whole universe — several hundred rows — and it is
     * the right request exactly once, here, because this *is* the catalogue. Every other caller
     * names what is on screen.
     */
    override suspend fun load(): MarketCatalog {
        val prices = when (val result = gateway.prices(emptyList())) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> throw IllegalStateException(result.message)
        }
        val at = nowMillis()
        val quotes = prices.quotes.associate { quote ->
            quote.symbol to MarketQuote(
                instrument = Instrument(
                    symbol = quote.symbol,
                    displayName = quote.symbol,
                    marketType = MarketType.CRYPTO,
                ),
                price = quote.price,
                changePercent = quote.changePercent24h,
                timestampEpochMillis = at,
                source = QuoteSource.LBANK,
                // The server's own verdict on the whole snapshot, applied per row. It has no
                // per-symbol freshness to give, and inventing one would be a claim it never made.
                isStale = prices.stale,
            )
        }
        return MarketCatalog(
            markets = prices.quotes.map(GuestQuote::symbol)
                .filterNot(SymbolClassifier::isNoise)
                .map(SymbolClassifier::classify)
                // The same rule the signed-in catalogue follows, and it has to be the same rule: a
                // market with no artwork is not listed. A guest seeing lettered grey discs beside
                // real logos is the first impression this app gets, once.
                .filter(SymbolArtwork::covers),
            quotes = quotes,
            serverTimeEpochMillis = prices.ageMillis?.let { at - it },
        )
    }
}

/**
 * Public candles, in the signed-in chart's own contract.
 *
 * The route caps a request at 500 and the cap is applied here rather than discovered at the server,
 * which is the gateway's own note and the reason [CandleGateway.DEFAULT_LIMIT] is safe to pass.
 *
 * **Paging backwards is not offered by this route.** [before] is therefore honoured by refusing to
 * pretend: a request for an earlier page returns an empty one with `hasMore = false`, so the chart
 * stops asking rather than looping on a page it keeps being handed. A guest gets the most recent
 * few hundred bars on any timeframe, which is a chart; what they do not get is ten years of
 * history, which is what an account is for.
 */
class GuestCandleGateway(private val gateway: GuestGateway) : CandleGateway {

    override suspend fun load(
        symbol: String,
        timeframe: Timeframe,
        limit: Int,
        before: Long?,
    ): CandlePage {
        if (before != null) {
            return CandlePage(
                symbol = symbol,
                timeframe = timeframe,
                candles = emptyList(),
                hasMore = false,
                limitMax = MAX_LIMIT,
            )
        }
        val result = gateway.candles(
            symbol = symbol,
            timeframe = timeframe.wire,
            limit = limit.coerceIn(1, MAX_LIMIT),
        )
        val candles = when (result) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> throw IllegalStateException(result.message)
        }
        val bars = candles.candles.map { candle ->
            OhlcBar(
                t = candle.timeSeconds,
                o = candle.open,
                h = candle.high,
                l = candle.low,
                c = candle.close,
                v = candle.volume ?: 0.0,
                closed = candle.closed,
            )
        }
        return CandlePage(
            symbol = symbol,
            // The server's normalised label where it gave one, so a cache keyed on the answer and
            // one keyed on the request cannot disagree.
            timeframe = Timeframe.of(candles.timeframe) ?: timeframe,
            candles = bars,
            oldest = bars.firstOrNull()?.t,
            hasMore = false,
            limitMax = MAX_LIMIT,
        )
    }

    private companion object {
        /** The public route's own ceiling — half the signed-in one. See [GuestGateway.candles]. */
        const val MAX_LIMIT = 500
    }
}
