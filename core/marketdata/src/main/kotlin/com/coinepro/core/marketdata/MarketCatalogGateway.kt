package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.symbols.SymbolMeta
import retrofit2.Retrofit

/**
 * Every market a platform quotes, with a price for each.
 *
 * [markets] is the searchable universe and [quotes] is what it is worth right now. They are one
 * object because they arrive in one response: the snapshot endpoint *is* the catalogue, which is
 * convenient and also the only discovery mechanism either backend offers today.
 */
data class MarketCatalog(
    val markets: List<SymbolMeta>,
    val quotes: Map<String, MarketQuote>,
    val serverTimeEpochMillis: Long?,
)

/**
 * Discovers what a platform actually quotes, rather than being told.
 *
 * The app used to ship the answer as a constant — eight symbols, hand-written. Anything a backend
 * added was invisible until somebody edited the app and shipped a release, which is the wrong shape
 * for a product whose whole subject is a list of markets that changes.
 *
 * The two platforms are not equally cooperative about this, and the difference is worth stating
 * plainly rather than hiding behind a common interface:
 *
 * * **TradeYar** answers `GET ws/snapshot` with no `symbols` parameter by returning everything in
 *   scope. That is real discovery: the crypto universe is whatever LBank is quoting today.
 * * **CoinePro-FX** has no such mode. Its snapshot defaults to gold and silver, caps a request at
 *   twenty symbols, and rejects any symbol outside `settings.SYMBOLS` with `unsupported_symbol` —
 *   and it publishes no endpoint that returns that set. So the forex catalogue is still the list
 *   this app carries, and it stays that way until the server offers one. `docs/` records the ask.
 */
interface MarketCatalogGateway {
    suspend fun load(): MarketCatalog
}

class NetworkMarketCatalogGateway private constructor(
    private val api: MarketDataApi,
    private val platform: MarketPlatform,
    private val path: String,
    private val nowMillis: () -> Long,
) : MarketCatalogGateway {

    override suspend fun load(): MarketCatalog {
        val requested = when (platform) {
            // Omitted, which is what asks for the whole universe.
            MarketPlatform.TRADEYAR -> null
            MarketPlatform.COINEPRO_FX -> MarketDataSymbols.forex.joinToString(",")
        }
        val response = api.snapshot(path, requested)
        val quotes = response.prices.values
            .mapNotNull { it.toDomain(nowMillis(), platform) }
            .associateBy { it.instrument.symbol }
        return MarketCatalog(
            // Classified from the response's own keys rather than from the quotes, so a market the
            // feed listed but had no price for is still searchable. A price is a thing a market has,
            // not what makes it one.
            markets = response.prices.keys
                .filterNot(SymbolClassifier::isNoise)
                .map(SymbolClassifier::classify),
            quotes = quotes,
            serverTimeEpochMillis = response.serverTimeMs,
        )
    }

    companion object {
        fun create(
            retrofit: Retrofit,
            platform: MarketPlatform,
            nowMillis: () -> Long = System::currentTimeMillis,
        ): NetworkMarketCatalogGateway = NetworkMarketCatalogGateway(
            api = retrofit.create(MarketDataApi::class.java),
            platform = platform,
            path = platform.snapshotPath(),
            nowMillis = nowMillis,
        )
    }
}
