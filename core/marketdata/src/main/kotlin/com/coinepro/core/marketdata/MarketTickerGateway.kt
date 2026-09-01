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
 * Which layer the venue's prices are reaching us through.
 *
 * A string on the wire rather than a number, and that is deliberate on the server's side: a count
 * cannot say "I don't know" and a name can. [UNKNOWN] is therefore a real answer — Redis down, or a
 * tier this build has never heard of — and it is never optimistically read as healthy.
 */
enum class PriceFeedTier {
    /** The websocket layer. The only healthy value. */
    WS,

    /** Every shard is down and the relay is polling REST instead. Prices move in steps, not ticks. */
    REST_FALLBACK,

    /** The relay could not be read, or named a tier this build does not know. Not "fine". */
    UNKNOWN,
    ;

    internal companion object {
        fun parse(wire: String?): PriceFeedTier = when (wire?.trim()?.lowercase()) {
            "ws" -> WS
            "rest_fallback" -> REST_FALLBACK
            else -> UNKNOWN
        }
    }
}

/**
 * How the venue's live prices are actually arriving — the field that ends a silent outage.
 *
 * ### Why this exists at all
 *
 * TradeYar's price relay sat on its REST fallback for **forty-five hours** and nothing anywhere
 * said so. Every health probe was green, because a fallback tier answers `200`: "broken but up" is
 * a successful HTTP status. The app showed prices that were minutes old and had no way to know.
 *
 * ### The reading rule, and why one field is not enough
 *
 * The relay labels itself `lbank-rest` only when **every** shard is down, and calls itself
 * connected whenever `sockets_up > 0`. So four dead shards out of five is a relay reporting `ws`
 * and `connected` with four fifths of the catalogue frozen. Hence [degraded] is two conditions:
 *
 * > broken = `tier != ws` **or** `socketsUp < socketsTotal`
 *
 * ### The trap in [tickAgeMillis]
 *
 * It is an upper bound on staleness, not a tick rate. The relay rewrites its health record every
 * five seconds, so a perfectly healthy feed reports anything from 0 to 5 000 here — the server's
 * own sample read 3 618 ms while the true tick age was 2 ms. A "stale" badge at five seconds would
 * blink on a healthy feed, which is why [STALE_AFTER_MILLIS] is fifteen: the threshold the
 * server's own website uses, chosen by them for this exact reason.
 */
data class PriceFeedStatus(
    val tier: PriceFeedTier,
    /** Live shards, or null where the relay did not say. */
    val socketsUp: Int? = null,
    /** Shards there should be. Null and [socketsUp] travel together. */
    val socketsTotal: Int? = null,
    /** An upper bound on how old the newest tick is. See the note above before drawing it. */
    val tickAgeMillis: Long? = null,
) {

    /** Some shards are down but not all — the outage no single flag on the wire can see. */
    val partialOutage: Boolean
        get() = socketsUp != null && socketsTotal != null && socketsTotal > 0 && socketsUp in 1 until socketsTotal

    /** Every shard is down and prices are coming from REST polling. */
    val fullOutage: Boolean
        get() = tier == PriceFeedTier.REST_FALLBACK || (socketsUp != null && socketsUp <= 0)

    /** The one question a screen asks. See the reading rule above — both halves are needed. */
    val degraded: Boolean
        get() = tier != PriceFeedTier.WS ||
            (socketsUp != null && socketsTotal != null && socketsUp < socketsTotal)

    /** Whether the newest tick is old enough to be worth saying so. */
    val stale: Boolean
        get() = tickAgeMillis != null && tickAgeMillis > STALE_AFTER_MILLIS

    companion object {
        /**
         * Fifteen seconds, and not five.
         *
         * The relay's health record is rewritten every five seconds, so the age reported here is a
         * bound rather than a measurement and a threshold at the write interval fires on a healthy
         * feed. Fifteen is what the server's own site uses and what they asked us to copy.
         */
        const val STALE_AFTER_MILLIS = 15_000L
    }
}

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
    /**
     * How the venue's live prices are reaching the relay, or null where the server does not say.
     *
     * **Null is not health.** It means this deployment predates the field, and a screen must draw
     * nothing at all rather than a reassuring badge — the whole point of the field is that silence
     * used to look exactly like success.
     *
     * It describes the socket layer and not the rows beside it: the day's figures in this response
     * are read from the venue's REST snapshot either way. The one row value that follows the tier
     * is `open_interest`, which is structurally absent on the fallback. So a degraded feed here
     * means "the live price is stale", never "these numbers are wrong".
     */
    val priceFeed: PriceFeedStatus? = null,
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

/**
 * The honest gateway for a platform that has no such route. Never fails, never claims.
 *
 * No longer reached from [NetworkMarketTickerGateway.create] — both backends serve the rollup now —
 * and kept because it is what a caller with no configured platform, and every test that does not
 * care about tickers, is handed. A screen that reads [MarketTickerGateway.supported] gets `false`
 * and draws the columns it can rather than columns of dashes.
 */
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
            priceFeed = response.priceFeed?.toDomain(),
        )
    }

    companion object {
        /**
         * One reader, two paths, and — since 2026-09-01 — two platforms.
         *
         * The forex branch used to be [UnsupportedMarketTickerGateway], with a comment saying it
         * was not a stub standing in for unfinished work because there was no route to call. That
         * was accurate and it was three screens' worth of missing product: a market row on
         * CoinePro-FX had no twenty-four-hour change while the same row on TradeYar did, the
         * heatmap had nothing to colour, and the screener carried columns that were always empty.
         * A reader switching platform lost half the columns and was told nothing.
         *
         * The data was there the whole time — `candles` holds an hourly bar per symbol and the
         * chart routes have read it for years — so the route is an aggregate over a day of it,
         * with the live price from the same place `/ws/snapshot` takes it. Same field names as the
         * crypto route, because this app has one model for both.
         */
        fun create(retrofit: Retrofit, platform: MarketPlatform): MarketTickerGateway =
            NetworkMarketTickerGateway(
                api = retrofit.create(MarketTickerApi::class.java),
                path = when (platform) {
                    MarketPlatform.TRADEYAR -> TRADEYAR_PATH
                    MarketPlatform.COINEPRO_FX -> FOREX_PATH
                },
            )

        /**
         * Under the mobile prefix, like every other TradeYar route — its nginx has a `/ws` location
         * that the prefix deliberately sits inside, and a path written without it reaches a
         * different server.
         */
        internal const val TRADEYAR_PATH = "api/mobile/v1/market/tickers"

        /**
         * And under `user/mobile` on the forex side, where the rest of that platform's app surface
         * lives. The two prefixes are the servers' own and nothing here tries to reconcile them.
         */
        internal const val FOREX_PATH = "user/mobile/market/tickers"
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
    @SerializedName("price_feed") val priceFeed: PriceFeedDto? = null,
)

/**
 * The relay's own health, in the envelope rather than on the rows.
 *
 * `source` was deliberately left alone by the server — it names the exchange and this app already
 * parses it — so the transport got its own key instead. Every field is nullable here for the usual
 * reason: this class is about what arrives, not about what was promised.
 */
internal data class PriceFeedDto(
    val tier: String? = null,
    @SerializedName("sockets_up") val socketsUp: Int? = null,
    @SerializedName("sockets_total") val socketsTotal: Int? = null,
    @SerializedName("tick_age_ms") val tickAgeMs: Long? = null,
)

/**
 * The wire status as a reading.
 *
 * A negative shard count or a negative age is a broken reading rather than a small one, so it is
 * dropped to null — [PriceFeedStatus.degraded] then falls back to the tier alone, which is the
 * conservative half of the rule.
 */
internal fun PriceFeedDto.toDomain(): PriceFeedStatus = PriceFeedStatus(
    tier = PriceFeedTier.parse(tier),
    socketsUp = socketsUp?.takeIf { it >= 0 },
    socketsTotal = socketsTotal?.takeIf { it > 0 },
    tickAgeMillis = tickAgeMs?.takeIf { it >= 0 },
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
