package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import java.time.ZoneId
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Candles, from whichever backend owns the symbol.
 *
 * The two are not symmetrical and pretending otherwise would hide something a caller needs. Crypto
 * candles are a plain mobile route; forex candles live behind CoinePro-FX's academy scope and need
 * a second token minted from the mobile one. That difference is real, so it is in the types: the
 * forex gateway takes an [AcademyTokenStore] and the crypto one does not.
 */
interface CandleGateway {
    /**
     * Where these bars come from, named the way a reader would name it.
     *
     * ### Why a gateway has to say this out loud
     *
     * The loudest accusation in Persian-language reviews of this whole category of app is
     * «کندل‌سازی» — that the broker manufactures its candles. It is usually wrong and it is never
     * answerable by an app that shows a price with no provenance at all: a chart that simply
     * asserts a number, with nothing saying where the number came from, gives a suspicious reader
     * nothing to check and gives an honest operator no way to be believed.
     *
     * So every gateway names its venue, the chart prints it, and the claim becomes falsifiable —
     * a reader can hold this chart against that venue's own. Default is empty rather than a guess:
     * a test double has no venue, and inventing one for it would put a false name on a fixture.
     */
    val sourceName: String get() = ""

    /**
     * One page, newest at the end.
     *
     * [before] pages backwards: pass the `t` of the oldest bar held and the answer is the page
     * before it, with no overlap and no gap. Null asks for the live edge.
     */
    suspend fun load(
        symbol: String,
        timeframe: Timeframe,
        limit: Int = DEFAULT_LIMIT,
        before: Long? = null,
    ): CandlePage

    /**
     * One page at an arbitrary [ChartInterval], folding on the client where the feed cannot help.
     *
     * ### Why this is not just the other [load] with a wider parameter
     *
     * Neither backend serves [Timeframe.M2], [Timeframe.M3], [Timeframe.M10], [Timeframe.M45],
     * [Timeframe.H2], [Timeframe.H3] or [Timeframe.MN1], and neither has ever heard of a custom
     * minute count. Forwarding one of those verbatim gets an error from CoinePro-FX and, from
     * TradeYar, an echo of whatever spelling it decided the request meant — which is the dangerous
     * case, because a series of the wrong length still draws. So this overload does not forward the
     * interval at all. It resolves the interval to the coarsest [Timeframe] both servers do serve
     * and that divides it exactly (see [resolveCandleRequest]), asks for enough of *those* bars,
     * and folds them with [foldBars].
     *
     * [limit] counts bars the caller wants **after** folding, not bars fetched, so the meaning
     * matches the other [load]. [before] is likewise the folded series' own oldest open time:
     * every source bar older than a bucket's start belongs to an earlier bucket, so the same value
     * pages both grids without overlap.
     *
     * The default body is written once here rather than in each gateway because there is nothing
     * venue-specific in it, and two copies of a fold is two chances to get [Timeframe.MN1] wrong.
     */
    suspend fun load(
        symbol: String,
        interval: ChartInterval,
        limit: Int = DEFAULT_LIMIT,
        before: Long? = null,
        zone: ZoneId = CHART_TIME_ZONE,
    ): CandlePage = loadFolded(symbol, interval, limit, before, zone)

    companion object {
        /**
         * Enough to fill a phone at any zoom the viewport allows, and well inside both caps.
         *
         * TradeYar's ceiling is 1000 (LBank's own) and CoinePro-FX's is 3000. Asking for either
         * maximum on the first paint would be several hundred kilobytes to draw a hundred and
         * twenty bars.
         */
        const val DEFAULT_LIMIT = 300

        /**
         * The largest source page a fold is allowed to ask for, which is the *smaller* of the two
         * backends' caps rather than an average of them.
         *
         * TradeYar stops at 1000 because LBank does; CoinePro-FX allows 3000. A fold multiplies the
         * request — 205 minutes off a five-minute feed is forty-one source bars per drawn bar — so
         * this ceiling is reached by ordinary intervals, not by pathological ones, and reaching it
         * means the chart draws fewer bars than were asked for. That is a real limit of asking a
         * minute feed for a very long bar, and it is reported through
         * [CandleRequestPlan.truncated] rather than hidden: a caller that wants more must page
         * back, because no single request can produce it.
         */
        const val SOURCE_LIMIT_MAX = 1_000
    }
}

// ── crypto ───────────────────────────────────────────────────────────────────────────────────

internal interface CryptoCandleApi {
    // `api/mobile/v1/` is not decoration: TradeYar's mobile surface lives under it and the base URL
    // is the bare host. Without the prefix this resolves to the web portal, which answers 307 to
    // `/login` — verified live, against `401` for the prefixed path. It was wrong here for as long
    // as this file has existed and nothing caught it, because the app had never been run against a
    // live server and this was the one crypto gateway with no path test. There is one now.
    @GET("api/mobile/v1/market/candles")
    suspend fun candles(
        @Query("symbol") symbol: String,
        @Query("tf") timeframe: String,
        @Query("limit") limit: Int,
        @Query("before") before: Long?,
    ): CryptoCandleDto
}

internal data class CryptoCandleDto(
    val symbol: String? = null,
    val tf: String? = null,
    val candles: List<WireBarDto> = emptyList(),
    val oldest: Long? = null,
    val hasMore: Boolean = false,
    val limitMax: Int? = null,
)

internal data class WireBarDto(
    val t: Long? = null,
    val o: Double? = null,
    val h: Double? = null,
    val l: Double? = null,
    val c: Double? = null,
    val v: Double? = null,
    val closed: Boolean? = null,
)

class TradeYarCandleGateway(retrofit: Retrofit) : CandleGateway {

    private val api = retrofit.create(CryptoCandleApi::class.java)

    // The exchange, not the backend. TradeYar relays LBank's candles unchanged, and naming the
    // relay would answer a question nobody asked while dodging the one they did.
    override val sourceName: String = "LBank"

    override suspend fun load(
        symbol: String,
        timeframe: Timeframe,
        limit: Int,
        before: Long?,
    ): CandlePage {
        val response = api.candles(symbol.uppercase(), timeframe.wire, limit, before)
        return CandlePage(
            symbol = response.symbol ?: symbol.uppercase(),
            // The server echoes back the canonical spelling of whatever was sent, so this is what
            // it decided rather than what was asked for. They differ if a saved layout carried an
            // alternate spelling, and the server's answer is the one to believe.
            timeframe = Timeframe.of(response.tf) ?: timeframe,
            candles = response.candles.toBars(timeframe),
            oldest = response.oldest,
            hasMore = response.hasMore,
            limitMax = response.limitMax,
        )
    }
}

// ── forex, behind the academy scope ──────────────────────────────────────────────────────────

internal interface AcademyChartApi {
    @GET("academy/chart/{symbol}")
    suspend fun candles(
        @Header("Authorization") authorization: String,
        @Path("symbol") symbol: String,
        @Query("tf") timeframe: String,
        @Query("limit") limit: Int,
        @Query("before") before: Long?,
    ): AcademyCandleDto

    @GET("academy/chart/symbols")
    suspend fun symbols(@Header("Authorization") authorization: String): AcademySymbolsDto
}

internal data class AcademyCandleDto(
    val symbol: String? = null,
    val timeframe: String? = null,
    val candles: List<WireBarDto> = emptyList(),
    val price: Double? = null,
)

internal data class AcademySymbolsDto(val symbols: List<String> = emptyList())

/**
 * Forex and metal candles.
 *
 * Every call carries an explicit `Authorization` header rather than leaning on the shared
 * interceptor, because the token is a different one — see [AcademyTokenStore]. `NetworkFactory`
 * leaves an explicit header alone for exactly this.
 */
class CoineProFxCandleGateway(
    retrofit: Retrofit,
    private val tokens: AcademyTokenStore,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : CandleGateway {

    private val api = retrofit.create(AcademyChartApi::class.java)

    // MetaTrader 5, by way of the master account's own feed — which is the honest description:
    // these are the prices the copied account trades at, not an index or a composite. That is
    // exactly the distinction a reader asking about «کندل‌سازی» wants settled.
    override val sourceName: String = "MetaTrader 5"

    override suspend fun load(
        symbol: String,
        timeframe: Timeframe,
        limit: Int,
        before: Long?,
    ): CandlePage {
        val token = tokens.token()
        val response = api.candles(
            authorization = "Bearer $token",
            symbol = symbol.uppercase(),
            timeframe = timeframe.wire,
            limit = limit.coerceAtMost(FX_LIMIT_MAX),
            before = before,
        )
        return CandlePage(
            symbol = response.symbol ?: symbol.uppercase(),
            timeframe = Timeframe.of(response.timeframe) ?: timeframe,
            // CoinePro-FX sends no `closed` flag, so it is derived: a bar whose period has not
            // elapsed against the clock is still forming. Deriving it here rather than assuming
            // every bar is closed matters because the last one usually is not.
            candles = response.candles.toBars(timeframe, nowSeconds()),
            // No paging metadata on this route. `hasMore` is inferred from the page being full,
            // which is the weaker signal TradeYar's `has_more` exists to replace — stated rather
            // than hidden, so a caller knows the two backends differ here.
            oldest = response.candles.minOfOrNull { it.t ?: Long.MAX_VALUE }?.takeIf { it != Long.MAX_VALUE },
            hasMore = response.candles.size >= limit.coerceAtMost(FX_LIMIT_MAX),
            limitMax = FX_LIMIT_MAX,
        )
    }

    /**
     * Every symbol the academy chart can draw.
     *
     * It includes LBank's crypto pairs as well, which CoinePro-FX's team flagged: the app filters
     * them out, because a forex platform offering BTCUSDT would be quoting a market it does not
     * execute.
     */
    suspend fun symbols(): List<String> {
        val response = api.symbols("Bearer ${tokens.token()}")
        return response.symbols.map { it.uppercase() }
    }

    private companion object {
        /** The server's stated cap. Larger is silently truncated there, so it is clamped here. */
        const val FX_LIMIT_MAX = 3_000
    }
}

// ── shared mapping ───────────────────────────────────────────────────────────────────────────

/**
 * Wire bars to domain bars, dropping anything incomplete.
 *
 * A bar missing its close is not a bar with a zero close — it is a row the feed could not fill,
 * and carrying it as zero puts a candle on the floor of the chart and rescales the price axis
 * around it.
 */
internal fun List<WireBarDto>.toBars(timeframe: Timeframe, nowSeconds: Long? = null): List<OhlcBar> =
    mapNotNull { bar ->
        val t = bar.t ?: return@mapNotNull null
        val o = bar.o ?: return@mapNotNull null
        val h = bar.h ?: return@mapNotNull null
        val l = bar.l ?: return@mapNotNull null
        val c = bar.c ?: return@mapNotNull null
        OhlcBar(
            t = t,
            o = o,
            h = h,
            l = l,
            c = c,
            v = bar.v ?: 0.0,
            closed = bar.closed ?: (nowSeconds == null || t + timeframe.seconds <= nowSeconds),
        )
    }
        // Ascending and de-duplicated on open time. TradeYar enforces this server-side and said so;
        // CoinePro-FX made no such promise, and a chart drawn from a descending page is a mirror
        // image of the market — which looks like a real chart, which is what makes it dangerous.
        .distinctBy { it.t }
        .sortedBy { it.t }

// ── client-side folding ──────────────────────────────────────────────────────────────────────

/**
 * The eight intervals both backends actually serve, coarsest first.
 *
 * Everything else the picker offers is built from one of these. The order is not cosmetic: the
 * resolver walks this list and takes the first entry that divides the requested interval, so
 * coarsest-first is what makes it pick the *cheapest* correct source — two hundred H1 bars rather
 * than twelve thousand M1 bars for the same two hundred H2 bars.
 */
val SERVER_NATIVE_TIMEFRAMES: List<Timeframe> = listOf(
    Timeframe.W1,
    Timeframe.D1,
    Timeframe.H4,
    Timeframe.H1,
    Timeframe.M30,
    Timeframe.M15,
    Timeframe.M5,
    Timeframe.M1,
)

/** Whether a request for this interval can be forwarded to a server unchanged. */
val Timeframe.isServerNative: Boolean get() = this in SERVER_NATIVE_TIMEFRAMES

/**
 * How a requested interval is going to be obtained: which feed, how many of its bars, how many
 * drawn bars that yields.
 *
 * Kept as a value rather than done inline inside the gateway so the arithmetic can be tested
 * without a network, and so a caller — a controller deciding whether to offer "load more", a
 * diagnostic screen explaining why a chart is short — can ask the same question the gateway will
 * answer without making the request.
 */
data class CandleRequestPlan(
    /** What the reader asked for. */
    val interval: ChartInterval,
    /** The feed the bars are actually fetched at, always one of [SERVER_NATIVE_TIMEFRAMES]. */
    val source: Timeframe,
    /**
     * How many source bars go into one drawn bar.
     *
     * One means the feed serves the interval directly and no fold happens. For [Timeframe.MN1]
     * this is the *longest* month rather than the average one — see [resolveCandleRequest].
     */
    val factor: Int,
    /** The `limit` to send to the backend, already clamped to [CandleGateway.SOURCE_LIMIT_MAX]. */
    val requestLimit: Int,
    /** How many folded bars [requestLimit] source bars can produce, at best. */
    val expectedBars: Int,
    /**
     * Whether the clamp bit, so the answer will be shorter than the caller asked for.
     *
     * This is the honest name for a real limitation and not a defect: a 205-minute bar is
     * forty-one five-minute bars, and a thousand-bar page is therefore twenty-four of them. The
     * only cure is paging back, which costs another request; pretending otherwise would mean
     * inventing bars.
     */
    val truncated: Boolean,
) {
    /** Whether anything is folded at all, which is the cheap test for "can this be forwarded". */
    val foldsOnClient: Boolean get() = factor > 1
}

/**
 * Works out which feed to ask, and how much of it, for one [ChartInterval].
 *
 * ### Choosing the source
 *
 * The rule is the coarsest server-native timeframe whose length divides the requested one exactly.
 * Exactly matters: a source whose bars straddle a bucket boundary cannot be folded at all, because
 * the straddling bar's high belongs partly to one drawn bar and partly to the next, and there is no
 * way to split it that is not a guess.
 *
 * Two cases do not follow from the arithmetic alone and are named here rather than left to it.
 *
 * [Timeframe.MN1] is resolved to [Timeframe.D1] deliberately. Its `seconds` is a nominal thirty
 * days, so dividing by it would be arithmetic on a number that is wrong for eleven months of the
 * year; days are the coarsest bar that nests inside every calendar month, and the fold groups them
 * by [ChartInterval.bucketStart] rather than by counting.
 *
 * A [ChartInterval.Custom] is capped at [Timeframe.M30] however well a coarser one divides it. A
 * custom bucket is anchored to the reader's midnight, and Tehran's midnight is 20:30 UTC — half an
 * hour off the hourly grid every server lays its bars on. An H1 source bar would therefore span two
 * custom buckets, which is the straddle above. Thirty minutes is the coarsest bar that survives a
 * half-hour offset, so that is the ceiling. It costs bandwidth and it is the price of the anchor.
 *
 * ### Sizing the request
 *
 * [limit] is a count of *drawn* bars, so the request is that many times [CandleRequestPlan.factor],
 * clamped at [sourceLimitMax]. The clamp is reached routinely — see
 * [CandleGateway.SOURCE_LIMIT_MAX] — and when it is, [CandleRequestPlan.truncated] says so.
 */
fun resolveCandleRequest(
    interval: ChartInterval,
    limit: Int = CandleGateway.DEFAULT_LIMIT,
    sourceLimitMax: Int = CandleGateway.SOURCE_LIMIT_MAX,
): CandleRequestPlan {
    val source = sourceTimeframeFor(interval)
    val factor = foldFactorFor(interval, source)
    val wanted = limit.coerceAtLeast(1).toLong() * factor
    val requestLimit = wanted.coerceAtMost(sourceLimitMax.coerceAtLeast(1).toLong()).toInt()
    return CandleRequestPlan(
        interval = interval,
        source = source,
        factor = factor,
        requestLimit = requestLimit,
        expectedBars = (requestLimit / factor).coerceAtLeast(1),
        truncated = requestLimit < wanted,
    )
}

/** The feed [interval] is fetched from. Split out of [resolveCandleRequest] so it can be asserted alone. */
fun sourceTimeframeFor(interval: ChartInterval): Timeframe = when (interval) {
    is ChartInterval.Preset -> when {
        interval.timeframe.isServerNative -> interval.timeframe
        interval.timeframe == Timeframe.MN1 -> Timeframe.D1
        else -> largestNativeDividing(interval.seconds, Timeframe.H4.seconds)
    }
    // The half-hour ceiling. See the KDoc on `resolveCandleRequest` for why an hourly source is
    // wrong here even when it divides the interval perfectly.
    is ChartInterval.Custom -> largestNativeDividing(interval.seconds, Timeframe.M30.seconds)
}

/**
 * How many [source] bars make one [interval] bar, for sizing a request.
 *
 * A month is sized as thirty-one days rather than thirty, because sizing is the one place where
 * guessing low is the expensive mistake: too small a request draws a month short of its last days,
 * and the reader sees a monthly candle that closed early. Too large a request costs bytes.
 */
private fun foldFactorFor(interval: ChartInterval, source: Timeframe): Int =
    if (interval is ChartInterval.Preset && interval.timeframe == Timeframe.MN1) {
        LONGEST_MONTH_DAYS
    } else {
        (interval.seconds / source.seconds).toInt().coerceAtLeast(1)
    }

private const val LONGEST_MONTH_DAYS = 31

private fun largestNativeDividing(seconds: Long, ceilingSeconds: Long): Timeframe =
    SERVER_NATIVE_TIMEFRAMES.firstOrNull { it.seconds <= ceilingSeconds && seconds % it.seconds == 0L }
        ?: Timeframe.M1

/**
 * Aggregates finer bars into the bars of [interval], grouping by calendar bucket.
 *
 * ### The rules, and the one that matters
 *
 * Within a bucket the open is the **first** source bar's open, the close is the **last** one's
 * close, the high is the largest high, the low is the smallest low, the volume is the sum, and the
 * timestamp is the bucket's own start rather than any source bar's. Open and close come from
 * position in time, never from the extremes — a bucket that fell all session has its highest price
 * at the open, and taking `max` for the open would draw that same bucket as a rally.
 *
 * **A bucket with no source bars is omitted.** It is never emitted as a flat bar at the previous
 * close. A synthetic bar like that is a price nobody traded at, and once it is on the chart there
 * is nothing to distinguish it from one that was: a reader sees a doji where the market was closed,
 * an indicator averages it in, and a backtest fills an order against it. A gap in a series is
 * information — it says the market was shut, or the feed missed a minute — and flattening it into
 * a candle destroys that information and replaces it with a false one. Omitting is the only honest
 * option, and every consumer of a series here already draws from timestamps rather than assuming
 * an even grid.
 *
 * ### Why grouping, and not division
 *
 * Every bucket is decided by [ChartInterval.bucketStart], not by dividing elapsed seconds by
 * [ChartInterval.seconds]. For intraday intervals the two agree; for [Timeframe.MN1] they do not,
 * and the disagreement is not small. February is 28 days and March is 31, so a monthly series built
 * by counting 2,592,000-second blocks drifts a day out of step within two months and three days out
 * within a year — every monthly bar after the first would open somewhere in the middle of a day.
 * The same goes for [Timeframe.W1] and [Timeframe.D1], where the boundary is the reader's midnight
 * in [zone] and Tehran's is not a whole number of hours from UTC.
 *
 * The result is ordered oldest first, and the input need not be: it is sorted and de-duplicated on
 * open time first, because a page from a feed that made no ordering promise is exactly what this is
 * usually handed.
 */
fun foldBars(
    bars: List<OhlcBar>,
    interval: ChartInterval,
    zone: ZoneId = CHART_TIME_ZONE,
): List<OhlcBar> {
    // Sorted and de-duplicated, and otherwise taken as given. Deciding a bar is invalid is the
    // mapper's job — `toBars` drops the incomplete ones and the cache drops the non-finite ones —
    // and a second opinion here would silently delete bars the caller had already vouched for.
    val ordered = bars.asSequence()
        .distinctBy { it.t }
        .sortedBy { it.t }
        .toList()
    if (ordered.isEmpty()) return emptyList()

    val folded = ArrayList<OhlcBar>(ordered.size)
    var bucket = interval.bucketStart(ordered.first().t, zone)
    var open = ordered.first().o
    var high = ordered.first().h
    var low = ordered.first().l
    var close = ordered.first().c
    var volume = 0.0
    var closed = true

    for (bar in ordered) {
        val start = interval.bucketStart(bar.t, zone)
        if (start != bucket) {
            folded += OhlcBar(t = bucket, o = open, h = high, l = low, c = close, v = volume, closed = closed)
            bucket = start
            open = bar.o
            high = bar.h
            low = bar.l
            volume = 0.0
            closed = true
        }
        high = maxOf(high, bar.h)
        low = minOf(low, bar.l)
        close = bar.c
        // A non-finite volume is dropped rather than propagated: one NaN would make the whole
        // folded bar's volume NaN, and the volume pane would then scale to nothing for the entire
        // series rather than for the one bad minute.
        if (bar.v.isFinite()) volume += bar.v
        // A folded bar is finished only when every source bar in it is. That is right at the live
        // edge, where the newest source bar is still forming and so is the bucket around it. It
        // cannot tell a finished bucket from one whose remaining source bars have not arrived yet,
        // and it does not try: the caller that knows the clock is the one that should decide.
        closed = closed && bar.closed
    }
    folded += OhlcBar(t = bucket, o = open, h = high, l = low, c = close, v = volume, closed = closed)
    return folded
}

/**
 * The body behind [CandleGateway.load] for a [ChartInterval]: resolve, fetch, fold.
 *
 * A free function rather than a private member so that the whole path can be exercised against a
 * fake gateway in a unit test, and so a gateway that ever needs to override the interval overload
 * can still reuse it.
 */
suspend fun CandleGateway.loadFolded(
    symbol: String,
    interval: ChartInterval,
    limit: Int = CandleGateway.DEFAULT_LIMIT,
    before: Long? = null,
    zone: ZoneId = CHART_TIME_ZONE,
    sourceLimitMax: Int = CandleGateway.SOURCE_LIMIT_MAX,
): CandlePage {
    val plan = resolveCandleRequest(interval, limit, sourceLimitMax)
    val page = load(symbol, plan.source, plan.requestLimit, before)
    if (!plan.foldsOnClient) return page

    val folded = foldBars(page.candles, interval, zone)
    // The oldest bucket is dropped when the feed says it has more and the page did not begin on a
    // bucket boundary. In that case the bucket's true open is in bars nobody asked for, and the
    // open drawn from what did arrive would be a mid-bucket price presented as an open — the same
    // fabrication the empty-bucket rule refuses, arrived at from the other side. When `hasMore` is
    // false the feed has nothing older, so a short leading bucket is the market's own and stays.
    // The oldest bar by time rather than by position: a feed that made no ordering promise can and
    // does answer newest-first, and reading position zero would then test the wrong bar entirely.
    val oldestSource = page.candles.minOfOrNull { it.t }
    val startsMidBucket = oldestSource != null && oldestSource != interval.bucketStart(oldestSource, zone)
    val bars = if (page.hasMore && startsMidBucket && folded.isNotEmpty()) folded.drop(1) else folded

    return page.copy(
        // A preset names the bars honestly. A custom interval has no `Timeframe` that describes its
        // bars at all, so this field names the **feed** they were folded from and the caller must
        // read the interval it asked for instead — see the note on `CandlePage.timeframe`.
        timeframe = (interval as? ChartInterval.Preset)?.timeframe ?: plan.source,
        candles = bars,
        oldest = bars.firstOrNull()?.t ?: page.oldest,
        hasMore = page.hasMore,
        limitMax = page.limitMax,
        interval = interval,
    )
}
