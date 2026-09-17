package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.symbols.SymbolStatus
import com.coinepro.core.symbols.SymbolUniverse
import com.coinepro.core.symbols.UniverseSymbol
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Every market a venue lists, with the listing's own facts on it.
 *
 * ### Why this is separate from [MarketCatalogGateway]
 *
 * The catalogue is the **snapshot**: symbols and prices, together, because that is how the snapshot
 * endpoint answers. It is discovery by side effect — the universe is whatever had a price when you
 * asked — and it carries nothing about the listing itself.
 *
 * A markets screen needs the listing. A rank column needs turnover, a filter sheet needs the venue
 * and the type, an order ticket needs the tick size and the minimum quantity, and a delisted market
 * needs to say so rather than simply stopping. None of that is in a price.
 *
 * ### What answers today
 *
 * **Nothing.** `GET v1/symbols` is not served by either backend — CoinePro-FX returns its web page
 * for that path and TradeYar redirects it to a login. So [load] falls through to the snapshot and
 * then to the bundled table, which is the arrangement `docs/runs/RUN_TFY/BLOCKED.md` records: the
 * client is written to the shape the brief specifies, and the day the endpoint exists the app reads
 * it without another release.
 *
 * The fall-through is not a hack around a missing endpoint. Even with `v1/symbols` live it is the
 * right shape: a venue that is slow, rate-limited or mid-deploy must not empty the markets screen.
 */
interface SymbolUniverseGateway {
    suspend fun load(): List<UniverseSymbol>
}

/**
 * The bundled table alone, for a build with no venue of its own to ask.
 *
 * The guest shell reads public routes and has no authenticated universe behind them. Handing it
 * this rather than null is what makes the markets tab the same screen for a stranger as for a
 * member: three hundred markets they can search, chart and read about, with prices on whichever of
 * them the public feed is carrying.
 */
object BundledSymbolUniverseGateway : SymbolUniverseGateway {
    override suspend fun load(): List<UniverseSymbol> = SymbolUniverse.bundled()
}

/**
 * One row of `v1/symbols`, exactly as the brief specifies it.
 *
 * Every field but `id` is optional, and that is deliberate rather than defensive: a venue that
 * knows a tick size should send one and a venue that does not should be readable anyway. A missing
 * field becomes a null the screen can say «—» to, never a zero it would draw as a fact.
 */
internal data class UniverseSymbolDto(
    val id: String? = null,
    val base: String? = null,
    val quote: String? = null,
    val venue: String? = null,
    val type: String? = null,
    val displayNameEn: String? = null,
    val displayNameFa: String? = null,
    val logoBase: String? = null,
    val logoQuote: String? = null,
    val tickSize: Double? = null,
    val minQty: Double? = null,
    val status: String? = null,
    val turnover24h: Double? = null,
)

internal data class UniverseResponseDto(
    /** The brief's shape. A bare array is also accepted — see [NetworkSymbolUniverseGateway.load]. */
    val symbols: List<UniverseSymbolDto> = emptyList(),
)

internal interface SymbolUniverseApi {
    @GET
    suspend fun symbols(@Url path: String): UniverseResponseDto
}

/**
 * The universe path on each backend.
 *
 * The same shape as [snapshotPath] and for the same reason: TradeYar's mobile surface sits under a
 * prefix its nginx owns, and one hard-coded path means the universe on one platform never fills.
 */
internal fun MarketPlatform.universePath(): String = when (this) {
    MarketPlatform.COINEPRO_FX -> "v1/symbols"
    MarketPlatform.TRADEYAR -> "api/mobile/v1/symbols"
}

class NetworkSymbolUniverseGateway private constructor(
    private val api: SymbolUniverseApi,
    private val catalogue: MarketCatalogGateway,
    private val platform: MarketPlatform,
    private val path: String,
) : SymbolUniverseGateway {

    /**
     * The venue's list, or the snapshot's, or the bundled one — in that order, never empty.
     *
     * Each step is tried and its failure is a reason to go on rather than a reason to throw. A
     * markets screen that shows the bundled three hundred with no prices is usable; one that shows
     * an error because a path 404s is not, and the reader cannot act on either fact.
     */
    override suspend fun load(): List<UniverseSymbol> {
        val listed = runCatching { api.symbols(path).symbols }.getOrNull().orEmpty()
        val live = listed.mapNotNull(::toDomain)
        if (live.isNotEmpty()) return SymbolUniverse.merge(live)

        val venue = platform.venueName()
        val fromSnapshot = runCatching { catalogue.load() }.getOrNull()
        // No turnover on this path, and none is invented. A snapshot quote carries a price, a
        // change and a timestamp; the volume that would rank these rows is exactly one of the
        // things `v1/symbols` exists to send. Rows with no figure rank behind rows with one —
        // `SymbolRanking.byLiquidity` — rather than being read as zero.
        val snapshot = fromSnapshot?.markets.orEmpty().map { meta ->
            SymbolUniverse.of(meta = meta, venue = venue)
        }
        return SymbolUniverse.merge(snapshot)
    }

    private fun toDomain(dto: UniverseSymbolDto): UniverseSymbol? {
        val id = dto.id?.trim()?.uppercase().orEmpty()
        if (id.isEmpty()) return null
        // Classified from the ticker whatever the venue said, so a row with no `type`, no names and
        // no legs is still a market this app can draw and search rather than a row of blanks.
        val meta = SymbolClassifier.classify(id)
        return UniverseSymbol(
            id = id,
            base = dto.base?.uppercase() ?: meta.base,
            quote = dto.quote?.uppercase() ?: meta.quote,
            venue = dto.venue?.takeIf { it.isNotBlank() } ?: platform.venueName(),
            type = dto.type.toCategory() ?: meta.category,
            displayNameEn = dto.displayNameEn?.takeIf { it.isNotBlank() } ?: meta.descriptionEn,
            displayNameFa = dto.displayNameFa?.takeIf { it.isNotBlank() } ?: meta.description,
            logoBase = dto.logoBase?.uppercase() ?: meta.base,
            logoQuote = dto.logoQuote?.uppercase() ?: meta.quote,
            tickSize = dto.tickSize?.takeIf { it.isFinite() && it > 0 },
            minQty = dto.minQty?.takeIf { it.isFinite() && it > 0 },
            status = dto.status.toStatus(),
            turnover24h = dto.turnover24h?.takeIf { it.isFinite() && it >= 0 },
        )
    }

    companion object {
        fun create(
            retrofit: Retrofit,
            catalogue: MarketCatalogGateway,
            platform: MarketPlatform,
        ): NetworkSymbolUniverseGateway = NetworkSymbolUniverseGateway(
            api = retrofit.create(SymbolUniverseApi::class.java),
            catalogue = catalogue,
            platform = platform,
            path = platform.universePath(),
        )
    }
}

/** What a row says it is, in the vocabulary the brief uses. Anything else is classified from the ticker. */
internal fun String?.toCategory(): SymbolCategory? = when (this?.trim()?.lowercase()) {
    "crypto" -> SymbolCategory.CRYPTO
    "forex", "fx" -> SymbolCategory.FOREX
    "metal", "metals" -> SymbolCategory.METAL
    "index", "indices" -> SymbolCategory.INDEX
    "energy" -> SymbolCategory.ENERGY
    else -> null
}

/** A listing's state. Unknown words become [SymbolStatus.UNKNOWN], which is tradable. */
internal fun String?.toStatus(): SymbolStatus = when (this?.trim()?.lowercase()) {
    "trading", "open", "live", "active" -> SymbolStatus.TRADING
    "paused", "halted", "suspended", "break" -> SymbolStatus.PAUSED
    "delisted", "closed", "removed" -> SymbolStatus.DELISTED
    else -> SymbolStatus.UNKNOWN
}

/** The name a reader would recognise the venue by, for the filter sheet's venue column. */
internal fun MarketPlatform.venueName(): String = when (this) {
    MarketPlatform.COINEPRO_FX -> "CoinePro FX"
    MarketPlatform.TRADEYAR -> "LBank"
}
