package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.symbols.SymbolArtwork
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
 * * **CoinePro-FX** now does the same. It used to default to gold and silver, cap a request at
 *   twenty, and fail the whole request on one symbol outside `settings.SYMBOLS`; asked for a
 *   discovery mode, its team made the bare call return the full set, moved the cap onto explicit
 *   lists only, and added an `unsupported` field so one bad name no longer costs the response.
 *
 * So both platforms are asked the same way now, and neither catalogue is a constant this app
 * carries. That is the whole point: a market either backend adds shows up without an app release.
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
        // Omitted on both, which is what asks for the whole universe. Naming a list would cap
        // the answer at whatever this app happened to know when it shipped.
        val requested: String? = null
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
                .map(SymbolClassifier::classify)
                // A market the app cannot draw is a market it does not list. The lettered token —
                // a grey disc with a "D" in it — does not read as "this is DOGE" beside forty real
                // logos; it reads as a broken image, and a screenful of them reads as a broken app.
                // Leaving out the long tail nobody asked for costs less than presenting it badly.
                .filter(SymbolArtwork::covers),
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
