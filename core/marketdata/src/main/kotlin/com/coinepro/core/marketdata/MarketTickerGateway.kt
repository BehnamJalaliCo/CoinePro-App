package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The twenty-four-hour rollup for one market.
 *
 * ### Why this is not folded into `MarketQuote`
 *
 * A quote is a tick: sub-second, and stale the moment the socket drops. This is a rolling window
 * the server recomputes and caches for five seconds. Putting them in one object would make
 * `changePercent` look exactly as fresh as `price`, and a reader deciding on a move needs to know
 * which of the two numbers in front of them is live. They are merged for display and kept apart in
 * the model.
 *
 * ### Every field but the first two is nullable, and that is the contract
 *
 * The server was asked to **omit** a value it does not have rather than send zero, and it does:
 * "only `symbol` and `last` are guaranteed". The distinction is the whole reason this data is
 * trustworthy — `volume24h = 0.0` is a claim that nothing traded, and null is the truth that
 * nobody knows. Anything that later defaults one of these to zero to avoid a null check is
 * reintroducing the lie the contract was written to prevent.
 */
data class MarketTicker(
    val symbol: String,
    val last: Double,
    val open24h: Double? = null,
    val high24h: Double? = null,
    val low24h: Double? = null,
    /**
     * Computed by the server, deliberately, and not derived here from `last / open`.
     *
     * If the app did that arithmetic, every market that arrived without an `open` would quietly
     * become zero percent — a flat market, which is a specific and wrong claim — instead of
     * becoming unknown.
     */
    val changePercent24h: Double? = null,
    /** In the base asset. Not interchangeable with [turnover24h]; see it. */
    val volume24h: Double? = null,
    /**
     * In the quote currency.
     *
     * Both are carried because they answer different questions and neither substitutes for the
     * other: "most traded" across a list of markets priced in different assets is [turnover24h],
     * and sorting by [volume24h] there compares a count of bitcoin against a count of dogecoin.
     *
     * The server's own relay has this exact bug elsewhere and said so — its `volume24h` field is
     * filled from turnover, is named for base and has been read as base by the website since day
     * one. This route is the one that gets it right, and the two names here are kept apart so the
     * app cannot inherit the confusion.
     */
    val turnover24h: Double? = null,
    /** Futures only. Null means this market has no funding, not that the rate is zero. */
    val fundingRate: Double? = null,
    /**
     * How often that funding is charged, in seconds.
     *
     * Carried because without it the rate is not comparable between two markets, and the server
     * measured the difference rather than assuming it: 28 800 on BTCUSDT and 14 400 on METISUSDT.
     * A rate of 0.01% every four hours is twice the cost of 0.01% every eight, and a screen that
     * printed the two side by side without this would be inviting exactly the wrong comparison.
     */
    val fundingIntervalSeconds: Long? = null,
    val nextFundingAtEpochMillis: Long? = null,
    val markPrice: Double? = null,
    /**
     * The index price — the other half of the story [markPrice] starts.
     *
     * Not asked for; the server sent it because it was free in the same envelope, and it is the
     * more useful of the two: what liquidates a position is the gap between these, and no screen
     * in this app has ever been able to show that.
     */
    val indexPrice: Double? = null,
    /**
     * Open interest, in contracts.
     *
     * Present only while the server's relay is reading the websocket layer. Its REST fallback does
     * not carry the field at all — structurally, not occasionally — so this goes absent rather
     * than zero whenever that fallback is in use. The server was explicit that this had been
     * happening silently for forty-five hours when they wrote the route.
     */
    val openInterest: Double? = null,
    /** When the venue stamped this rollup. Absent where the venue sends no time. */
    val timestampEpochMillis: Long? = null,
)

/**
 * Every market's twenty-four-hour figures, in one request.
 *
 * ### What this unblocks
 *
 * Six things, all of which were built and inert. Neither feed has ever sent a day's change: the
 * snapshot carries a symbol, a price, a bid, an ask and a timestamp, and `MarketQuote.changePercent`
 * has been null on every quote either backend has ever returned. So the screener fetched a whole
 * candle series per market to derive one number, the heat map had no second variable to size or
 * colour by, the market list could sort by neither volume nor change, and "top gainers" was not
 * expressible at all.
 *
 * The cost on the server was near zero because the venue publishes the whole table in one call.
 * That is the shape worth noticing: the expensive thing was never the data, it was the absence of
 * a route.
 */
data class MarketTickerTable(
    val tickers: Map<String, MarketTicker>,
    val serverTimeEpochMillis: Long?,
    /**
     * How long the server intends to serve this same answer.
     *
     * Carried so a screen can say how old its figures are instead of implying they are live. The
     * server sends `fetched_at_ms` alongside for the same reason, which is a better answer than
     * inferring staleness from a TTL.
     */
    val cacheTtlMillis: Long?,
    val fetchedAtEpochMillis: Long?,
    val source: String?,
) {
    companion object {
        val Empty = MarketTickerTable(emptyMap(), null, null, null, null)
    }
}

/**
 * Reads the day's figures for a whole catalogue.
 *
 * **TradeYar only.** CoinePro-FX has no equivalent route: it would have to derive gold and silver's
 * day from its own daily candle or from Finnhub, and it has not. Rather than pretend, [supported]
 * says so and the gateway answers with an empty table — so a caller can tell "this platform does
 * not serve these figures" apart from "the request failed", and show the right thing for each.
 */
interface MarketTickerGateway {
    val supported: Boolean

    suspend fun load(symbols: List<String>? = null): MarketTickerTable
}

/** The honest gateway for a platform that has no such route. Never fails, never claims. */
class UnsupportedMarketTickerGateway : MarketTickerGateway {
    override val supported: Boolean = false

    override suspend fun load(symbols: List<String>?): MarketTickerTable = MarketTickerTable.Empty
}

class NetworkMarketTickerGateway private constructor(
    private val api: MarketTickerApi,
    private val path: String,
) : MarketTickerGateway {

    override val supported: Boolean = true

    override suspend fun load(symbols: List<String>?): MarketTickerTable {
        // Omitted asks for the whole catalogue, which is the call this route exists for — one
        // request, 801 rows, and a five-second cache in front of it, so naming symbols would cost
        // the same round trip for less answer.
        val requested = symbols?.takeIf { it.isNotEmpty() }?.joinToString(",")
        val response = api.tickers(path, requested)
        return MarketTickerTable(
            tickers = response.tickers.mapNotNull { it.toDomain() }.associateBy { it.symbol },
            serverTimeEpochMillis = response.serverTimeMs,
            cacheTtlMillis = response.cacheTtlMs,
            fetchedAtEpochMillis = response.fetchedAtMs,
            source = response.source,
        )
    }

    companion object {
        fun create(retrofit: Retrofit, platform: MarketPlatform): MarketTickerGateway =
            when (platform) {
                MarketPlatform.TRADEYAR -> NetworkMarketTickerGateway(
                    api = retrofit.create(MarketTickerApi::class.java),
                    path = TRADEYAR_PATH,
                )
                // Not a stub standing in for work not yet done — there is no route to call. See
                // the interface's own note.
                MarketPlatform.COINEPRO_FX -> UnsupportedMarketTickerGateway()
            }

        /**
         * Under the mobile prefix, like every other TradeYar route — its nginx has a `/ws` location
         * that the prefix deliberately sits inside, and a path written without it reaches a
         * different server.
         */
        internal const val TRADEYAR_PATH = "api/mobile/v1/market/tickers"
    }
}

internal interface MarketTickerApi {
    @GET
    suspend fun tickers(@Url path: String, @Query("symbols") symbols: String?): MarketTickersDto
}

internal data class MarketTickersDto(
    val tickers: List<WireTickerDto> = emptyList(),
    @SerializedName("server_time_ms") val serverTimeMs: Long? = null,
    @SerializedName("cache_ttl_ms") val cacheTtlMs: Long? = null,
    @SerializedName("fetched_at_ms") val fetchedAtMs: Long? = null,
    val source: String? = null,
)

/**
 * The wire row.
 *
 * ### Every wire name is pinned, and that is not belt-and-braces
 *
 * The network layer sets Gson's `LOWER_CASE_WITH_UNDERSCORES`, which inserts a separator before
 * each **uppercase letter**. A digit is not an uppercase letter, so `open24h` maps to `open24h`
 * and never to the `open_24h` this route actually sends — and the same for the day's high, low,
 * change, volume and turnover. Relying on the policy here would have left six fields null on every
 * row of a response that was completely correct, and it would have failed as an empty screen
 * rather than as an error. `MarketTickerWireTest` parses the server's own sample body to hold this.
 *
 * Every field nullable including the two the server guarantees, because a guarantee is about what
 * the server sends and this class is about what arrives — a truncated body, a proxy that rewrote
 * the payload, or a future version that drops a field all produce a row this app must survive
 * rather than crash on. [toDomain] is where a row without the two mandatory values is dropped.
 */
internal data class WireTickerDto(
    val symbol: String? = null,
    val last: Double? = null,
    @SerializedName("open_24h") val open24h: Double? = null,
    @SerializedName("high_24h") val high24h: Double? = null,
    @SerializedName("low_24h") val low24h: Double? = null,
    @SerializedName("change_percent_24h") val changePercent24h: Double? = null,
    @SerializedName("volume_24h") val volume24h: Double? = null,
    @SerializedName("turnover_24h") val turnover24h: Double? = null,
    @SerializedName("funding_rate") val fundingRate: Double? = null,
    @SerializedName("funding_interval_s") val fundingIntervalSeconds: Long? = null,
    @SerializedName("next_funding_at_ms") val nextFundingAtMs: Long? = null,
    @SerializedName("mark_price") val markPrice: Double? = null,
    @SerializedName("index_price") val indexPrice: Double? = null,
    @SerializedName("open_interest") val openInterest: Double? = null,
    val ts: Long? = null,
)

/**
 * A row, or null where it is not one.
 *
 * Two rules, and both drop rather than repair:
 *
 * A row with no symbol or no usable price is not a market — it is a hole in the response, and
 * carrying it forward would put a blank line in every list built from this table.
 *
 * And a non-finite number is discarded per field rather than failing the row. JSON has no NaN, but
 * a proxy or a future encoder can produce one, and `Double.NaN` propagates silently through every
 * comparison it touches: a sort keyed on it produces an order that depends on the algorithm, and a
 * percentage rendered from it prints "NaN%" on a market page.
 */
internal fun WireTickerDto.toDomain(): MarketTicker? {
    val ticker = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val price = last?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return MarketTicker(
        symbol = ticker,
        last = price,
        open24h = open24h.finiteOrNull(),
        high24h = high24h.finiteOrNull(),
        low24h = low24h.finiteOrNull(),
        changePercent24h = changePercent24h.finiteOrNull(),
        volume24h = volume24h.finiteOrNull(),
        turnover24h = turnover24h.finiteOrNull(),
        fundingRate = fundingRate.finiteOrNull(),
        fundingIntervalSeconds = fundingIntervalSeconds?.takeIf { it > 0 },
        nextFundingAtEpochMillis = nextFundingAtMs?.takeIf { it > 0 },
        markPrice = markPrice.finiteOrNull(),
        indexPrice = indexPrice.finiteOrNull(),
        openInterest = openInterest.finiteOrNull(),
        timestampEpochMillis = ts?.takeIf { it > 0 },
    )
}

/**
 * Null unless the number is one.
 *
 * Zero is kept deliberately: a funding rate of exactly zero is a real reading, and so is a market
 * that genuinely did not trade. The contract says an unknown value is an absent key, so a zero
 * that arrives is a zero the server meant.
 */
private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
