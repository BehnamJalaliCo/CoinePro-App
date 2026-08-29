package com.coinepro.core.aisignal

import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.symbols.SymbolArtwork
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.symbols.SymbolMatch
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolRanking
import com.coinepro.core.symbols.SymbolSearch

/** Where the list a reader is choosing from actually came from. Shown, because it changes what it means. */
enum class AiSymbolOrigin {
    /** The server stated its own list alongside the quota. Nothing outside it can be asked for. */
    SERVER,

    /** Everything the platform quotes, discovered from the snapshot endpoint. */
    CATALOGUE,

    /** Neither had landed, so the hand-written first screenful is standing in. */
    FALLBACK,
}

/**
 * What the AI screen may ask about, and where that list came from.
 *
 * ### What this replaces
 *
 * `AiSignalProductScope.symbolsFor` was the whole menu: eight coins, or two metals. The app knows
 * 441 crypto markets and the whole MT5 forex universe — `MarketCatalogGateway` discovers them from
 * the same snapshot endpoint the markets screen uses, and `core:symbols` already classifies, ranks
 * and fuzzy-matches them. The AI screen was the one surface that could not reach any of it.
 *
 * ### The precedence, and why it is not negotiable
 *
 * 1. **A list the server stated wins.** CoinePro-FX sends `symbols` with its quota. A picker
 *    offering something outside it produces exactly the 422 this work started from, and this time
 *    the app would have been told and ignored it.
 * 2. **Otherwise the catalogue**, which is discovery rather than a constant, so a market a backend
 *    adds is askable without an app release.
 * 3. **Otherwise the fallback**, which exists only so that the first paint of the screen is not
 *    empty and so that a catalogue that never loads still leaves the screen usable.
 *
 * When the server states a list *and* the catalogue has loaded, the catalogue supplies the metadata
 * — Persian name, category, base/quote split — for the symbols on the server's list, and any name
 * the server allows that the catalogue has never heard of is still offered, classified from its
 * ticker. The server's list decides membership; the catalogue only decides how a member is drawn.
 *
 * `SymbolArtwork.covers` filters throughout, at the catalogue and at the server list alike: a symbol
 * with no artwork is a blank square or a lettered disc in a list of real logos, and this app does
 * not ship those.
 */
data class AiSymbolUniverse(
    val markets: List<SymbolMeta>,
    val origin: AiSymbolOrigin,
    val loading: Boolean = false,
) {
    val size: Int get() = markets.size

    /** The categories actually present, in the enum's own order, so the filter row has no dead chips. */
    val categories: List<SymbolCategory>
        get() = SymbolCategory.entries.filter { category -> markets.any { it.category == category } }

    /** Ranked search over the universe. Empty [query] is a browse list, not an empty one. */
    fun search(query: String, category: SymbolCategory? = null): List<SymbolMatch> =
        SymbolSearch.search(markets, query, category)

    /** Whether [symbol] is something this universe will let a reader ask about. */
    fun allows(symbol: String): Boolean {
        val normalized = AiSignalProductScope.normalizeSymbol(symbol) ?: return false
        return markets.any { it.symbol == normalized }
    }

    companion object {
        val EMPTY = AiSymbolUniverse(markets = emptyList(), origin = AiSymbolOrigin.FALLBACK)

        /** The hand-written first screenful for [platform], before anything has loaded. */
        fun fallback(platform: MarketPlatform): AiSymbolUniverse = AiSymbolUniverse(
            markets = AiSignalProductScope.symbolsFor(platform).toMarkets(),
            origin = AiSymbolOrigin.FALLBACK,
        )

        /**
         * Applies the precedence above.
         *
         * @param stated what the server said it accepts, or empty where it said nothing. Empty is
         *   silence, never a refusal of everything — TradeYar sends no list at all.
         */
        fun resolve(
            platform: MarketPlatform,
            stated: List<String>,
            catalogue: List<SymbolMeta>,
            loading: Boolean = false,
        ): AiSymbolUniverse {
            val known = catalogue.associateBy { it.symbol }
            if (stated.isNotEmpty()) {
                val allowed = stated
                    .mapNotNull(AiSignalProductScope::normalizeSymbol)
                    .distinct()
                    .map { known[it] ?: SymbolClassifier.classify(it) }
                    .filter(SymbolArtwork::covers)
                if (allowed.isNotEmpty()) {
                    return AiSymbolUniverse(allowed.ranked(), AiSymbolOrigin.SERVER, loading)
                }
            }
            if (catalogue.isNotEmpty()) {
                return AiSymbolUniverse(catalogue.ranked(), AiSymbolOrigin.CATALOGUE, loading)
            }
            return AiSymbolUniverse(
                markets = AiSignalProductScope.symbolsFor(platform).toMarkets(),
                origin = AiSymbolOrigin.FALLBACK,
                loading = loading,
            )
        }

        private fun List<String>.toMarkets(): List<SymbolMeta> = mapNotNull { raw ->
            AiSignalProductScope.normalizeSymbol(raw)?.let(SymbolClassifier::classify)
        }.filter(SymbolArtwork::covers).ranked()

        /**
         * Popular first, then by liquidity.
         *
         * The same ordering the markets screen browses in, and for the same reason: the alphabet is
         * what made the old list feel random, with `AAVEUSDT` permanently at the top of a list
         * somebody opened looking for bitcoin.
         */
        private fun List<SymbolMeta>.ranked(): List<SymbolMeta> = sortedWith(
            compareByDescending<SymbolMeta> { it.popular }
                .thenBy { SymbolRanking.rank(it) },
        )
    }
}

/**
 * Everything a platform will quote, for the AI picker.
 *
 * An interface rather than the gateway itself so the controller can be tested without a Retrofit
 * instance, and so a platform that never gains a discovery endpoint can be given a source that
 * simply returns nothing rather than a controller that has to know about that.
 */
fun interface AiSymbolCatalog {
    suspend fun markets(): List<SymbolMeta>
}

/**
 * The AI picker's universe, read from the same catalogue the markets screen searches.
 *
 * One discovery call for the whole app rather than a second list the AI screen maintains: the
 * failure mode of two lists is the one this app already had, where the AI screen offered ten
 * markets and the search screen offered four hundred and neither could explain the difference.
 */
class MarketCatalogAiSymbolCatalog(private val gateway: MarketCatalogGateway) : AiSymbolCatalog {
    override suspend fun markets(): List<SymbolMeta> = gateway.load().markets
}
