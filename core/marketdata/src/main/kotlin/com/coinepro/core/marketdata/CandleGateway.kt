package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
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

    companion object {
        /**
         * Enough to fill a phone at any zoom the viewport allows, and well inside both caps.
         *
         * TradeYar's ceiling is 1000 (LBank's own) and CoinePro-FX's is 3000. Asking for either
         * maximum on the first paint would be several hundred kilobytes to draw a hundred and
         * twenty bars.
         */
        const val DEFAULT_LIMIT = 300
    }
}

// ── crypto ───────────────────────────────────────────────────────────────────────────────────

internal interface CryptoCandleApi {
    @GET("market/candles")
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
