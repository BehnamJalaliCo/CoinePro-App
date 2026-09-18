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
    /**
     * How many names the venue actually returned, before this app dropped a single one (run Ξ,
     * item 20).
     *
     * The count matters because the brief's question — «does the app render every symbol the venue
     * serves?» — has two failure modes that look identical from the screen: a client that drops
     * markets, and a backend that serves few. Without this number the two are indistinguishable
     * and the wrong one gets fixed. With it, `markets.size` against [served] says exactly which:
     * equal, less the noise rows, means the client is keeping everything it is given, and a short
     * list is then a short answer.
     *
     * Defaulted to the list's own size so the two dozen fakes in the test tree, and the guest
     * gateway that builds its catalogue rather than fetching one, do not have to answer a question
     * they have no information about.
     */
    val served: Int = markets.size,
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
 * * **CoinePro-FX** was asked for the same thing and answers the bare call — but **not with the
 *   full set**, and that is measured rather than assumed. On 2026-09-18 `GET api/ws/snapshot` with
 *   no `symbols` returned **17 symbols and neither XAUUSD nor XAGUSD**: the majors, three indices
 *   and crude. The same host's `api/public/prices/live` returns 19, and the two extra are exactly
 *   gold and silver.
 *
 *   So on the forex side this is **not** discovery of the whole universe, and the consequence is
 *   not cosmetic: the metals are the reason this platform is in the product at all, and
 *   `ForexSignalScope` narrows the forex signal list to gold. A reader can be shown a gold *call*
 *   and then find no gold *market* to open. `docs/runs/RUN_ALEF/BLOCKED.md §א20` is the ask to
 *   that backend's team; nothing in this app can fix it, because a symbol the feed does not list
 *   is a symbol the app must not invent.
 *
 * Neither catalogue is a constant this app carries, and that stays the point: a market either
 * backend adds shows up without an app release. What this note no longer claims is that both
 * backends answer the bare call the same way. They do not.
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
                // **Everything the venue serves, drawn one way or the other** (run ΤΦΥ F1, and run
                // Ξ item 20).
                //
                // This filter used to be the rule that a market the app had no artwork for was a
                // market it did not list, and the argument was that a grey disc with a "D" in it
                // does not read as «this is DOGE» beside forty real logos. The measurement
                // overturned it: LBank quotes 1 333 tether pairs and this repository holds a mark
                // for 178, so the rule was not trimming a long tail — it was hiding seven markets
                // in eight in a product whose subject is the list of markets. `SymbolArtwork.lists`
                // now answers true for everything, the monogram carries the ones with no mark, and
                // `ARTWORK_GATES_LISTING` is the one word that would put the old rule back.
                .filter(SymbolArtwork::lists),
            quotes = quotes,
            serverTimeEpochMillis = response.serverTimeMs,
            // Before anything above ran. See [MarketCatalog.served].
            served = response.prices.keys.size,
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
