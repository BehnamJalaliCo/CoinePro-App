package com.coinepro.core.portfolio

import com.coinepro.core.model.MarketPlatform
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * One page of closed trades, plus what the server said about the window it actually served.
 *
 * [windowFrom]/[windowTo] exist because TradeYar narrows a request that asks for too much and says
 * so rather than truncating quietly. Their cap is 31 days, and it is not a preference: LBank's own
 * order history refuses anything past about 48 hours per call, so the server walks the window in
 * slices. Asking for 90 days would get 31, and a screen that did not read this back would label a
 * month of results as a quarter.
 */
data class TradeHistoryPage(
    val trades: List<ClosedTrade>,
    val page: Int,
    val total: Int,
    val hasMore: Boolean,
    val windowFrom: Long? = null,
    val windowTo: Long? = null,
    /**
     * The server stopped paging inside one slice because it held too many orders.
     *
     * TradeYar sets this at 2000 orders in a slice. It means the numbers on screen are computed
     * from part of the window, which is exactly the situation that must not be shown as a total.
     */
    val truncated: Boolean = false,
)

/**
 * Where closed trades come from.
 *
 * Both platforms have one and they are not alike. CoinePro-FX pages a broker's own ledger with no
 * window at all — every trade it has, oldest to newest, 50 at a time. TradeYar reconstructs trades
 * from an exchange's order log inside a bounded window, and the window is the request.
 *
 * The difference is in the parameters rather than hidden: [from] and [to] are honoured where they
 * mean something and ignored where they do not, and [TradeHistoryPage.windowFrom] reports what was
 * really served.
 */
interface PortfolioGateway {
    suspend fun history(
        page: Int = 1,
        perPage: Int = DEFAULT_PAGE_SIZE,
        from: Long? = null,
        to: Long? = null,
    ): TradeHistoryPage

    companion object {
        const val DEFAULT_PAGE_SIZE = 50

        /**
         * The window a screen opens on, in days.
         *
         * Thirty rather than TradeYar's 31-day maximum, so the default request is never the one
         * that gets narrowed. A first paint that immediately reports "we shortened your window"
         * would be reporting on a window the reader never chose.
         */
        const val DEFAULT_WINDOW_DAYS = 30L
    }
}

// ── CoinePro-FX ──────────────────────────────────────────────────────────────────────────────

internal interface ForexHistoryApi {
    @GET
    suspend fun history(
        @Url path: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
    ): ForexHistoryDto
}

internal data class ForexHistoryDto(
    val items: List<ForexTradeDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val perPage: Int = 0,
    val totalPages: Int = 0,
)

internal data class ForexTradeDto(
    val id: Long? = null,
    val dealId: Long? = null,
    val signalId: Long? = null,
    val symbol: String? = null,
    val direction: String? = null,
    val volume: Double? = null,
    val entryPrice: Double? = null,
    val exitPrice: Double? = null,
    val openTime: String? = null,
    val closeTime: String? = null,
    val durationSec: Long? = null,
    val grossProfit: Double? = null,
    val commission: Double? = null,
    val swap: Double? = null,
    val spreadCost: Double? = null,
    val netProfit: Double? = null,
    val pips: Double? = null,
    val closeReason: String? = null,
    val balanceAfter: Double? = null,
)

/**
 * The broker ledger, behind the ordinary mobile token.
 *
 * All four trade-history routes were already open to it — their team checked with a real account
 * and changed nothing. Only the list is read here: `/stats`, `/daily` and `/report` all compute
 * figures this app computes itself, and one of them (`max_drawdown_rel_pct`) their own team asked
 * us not to display because its denominator is wrong.
 */
class CoineProFxPortfolioGateway(retrofit: Retrofit) : PortfolioGateway {

    private val api = retrofit.create(ForexHistoryApi::class.java)

    override suspend fun history(page: Int, perPage: Int, from: Long?, to: Long?): TradeHistoryPage {
        // No window parameters: this route has none. Filtering by date here would mean fetching
        // pages and dropping most of them, which is worse than showing what the broker holds.
        val response = api.history(PATH, page, perPage)
        val trades = response.items.mapNotNull { it.toDomain() }
        return TradeHistoryPage(
            trades = trades,
            page = response.page,
            total = response.total,
            hasMore = response.page < response.totalPages,
        )
    }

    private companion object {
        const val PATH = "user/trade-history"
    }
}

internal fun ForexTradeDto.toDomain(): ClosedTrade? {
    val identifier = id ?: dealId ?: return null
    val ticker = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val side = TradeDirection.of(direction) ?: return null
    // A trade with no close time is not a closed trade. The route only serves closed ones, so this
    // is a malformed row rather than an open position, and a row with no x has nowhere on a curve.
    val closed = closeTime.toEpochSeconds() ?: return null
    return ClosedTrade(
        id = identifier.toString(),
        symbol = ticker,
        direction = side,
        volume = volume?.takeIf { it.isFinite() },
        entry = entryPrice?.takeIf { it.isFinite() },
        exit = exitPrice?.takeIf { it.isFinite() },
        openedAt = openTime.toEpochSeconds(),
        closedAt = closed,
        grossProfit = grossProfit?.takeIf { it.isFinite() },
        commission = commission?.takeIf { it.isFinite() },
        swap = swap?.takeIf { it.isFinite() },
        netProfit = netProfit?.takeIf { it.isFinite() },
        pips = pips?.takeIf { it.isFinite() },
        closeReason = closeReason?.trim()?.takeIf { it.isNotEmpty() },
        balanceAfter = balanceAfter?.takeIf { it.isFinite() },
        currency = "USD",
    )
}

/**
 * ISO-8601 with an offset, to unix seconds.
 *
 * This route is the app's one place where a timestamp is a string. Their team was explicit about
 * it and about why it is worth watching: the candle routes on the same server use unix seconds, so
 * two contracts live one directory apart.
 */
internal fun String?.toEpochSeconds(): Long? {
    val text = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        OffsetDateTime.parse(text).toEpochSecond()
    } catch (_: DateTimeParseException) {
        null
    }
}

// ── TradeYar ─────────────────────────────────────────────────────────────────────────────────

internal interface CryptoHistoryApi {
    @GET
    suspend fun history(
        @Url path: String,
        @Query("from") from: Long?,
        @Query("to") to: Long?,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int,
    ): CryptoHistoryDto
}

internal data class CryptoHistoryDto(
    val trades: List<CryptoTradeDto> = emptyList(),
    val page: Int = 1,
    val perPage: Int = 0,
    val total: Int = 0,
    val window: CryptoWindowDto? = null,
    val windowMaxDays: Int? = null,
    val asOf: Long? = null,
    val freshUntil: Long? = null,
    val truncated: Boolean = false,
)

internal data class CryptoWindowDto(val from: Long? = null, val to: Long? = null)

internal data class CryptoTradeDto(
    val id: String? = null,
    val symbol: String? = null,
    val side: String? = null,
    val openedAt: Long? = null,
    val closedAt: Long? = null,
    val entry: Double? = null,
    val exit: Double? = null,
    val quantity: Double? = null,
    val pnl: Double? = null,
    val fee: Double? = null,
    val currency: String? = null,
    val liquidated: Boolean = false,
)

/**
 * Trades reconstructed from LBank's order log.
 *
 * Worth knowing what this costs on the server, because it decides how the screen should behave: a
 * cold seven-day window takes about seventeen seconds, and the same window afterwards takes none.
 * Their team refused to parallelise it, correctly — it signs with the same API key the copy-trade
 * engine uses, and getting that key throttled to draw a history page would delay a real entry or a
 * real stop. So the screen must be patient rather than chatty: one request, cached, no polling.
 */
class TradeYarPortfolioGateway(retrofit: Retrofit) : PortfolioGateway {

    private val api = retrofit.create(CryptoHistoryApi::class.java)

    override suspend fun history(page: Int, perPage: Int, from: Long?, to: Long?): TradeHistoryPage {
        val response = api.history(PATH, from, to, page, perPage)
        val trades = response.trades.mapNotNull { it.toDomain() }
        val served = response.page * response.perPage
        return TradeHistoryPage(
            trades = trades,
            page = response.page,
            total = response.total,
            // Computed rather than read: the answer carries a total and a page size but no "more"
            // flag, and paging past the end would spend seventeen seconds to return nothing.
            hasMore = response.perPage > 0 && served < response.total,
            windowFrom = response.window?.from,
            windowTo = response.window?.to,
            truncated = response.truncated,
        )
    }

    private companion object {
        const val PATH = "api/mobile/v1/portfolio/history"
    }
}

internal fun CryptoTradeDto.toDomain(): ClosedTrade? {
    val identifier = id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val ticker = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val direction = TradeDirection.of(side) ?: return null
    val closed = closedAt?.takeIf { it > 0 } ?: return null
    return ClosedTrade(
        id = identifier,
        symbol = ticker,
        direction = direction,
        volume = quantity?.takeIf { it.isFinite() },
        entry = entry?.takeIf { it.isFinite() },
        exit = exit?.takeIf { it.isFinite() },
        // Null on about one trade in nine, and it is not a bug: the opening leg fell before the
        // window started, so the server genuinely does not know. Their team chose null over a
        // reconstructed guess, which is the right call and the reason this field is nullable.
        openedAt = openedAt?.takeIf { it > 0 },
        closedAt = closed,
        // No gross and no swap on this side. LBank reports one profit figure — its own
        // `closeProfit` — and the fee beside it. Splitting that into a gross would be arithmetic
        // the exchange did not do.
        commission = fee?.takeIf { it.isFinite() }?.let { -it },
        netProfit = pnl?.takeIf { it.isFinite() },
        liquidated = liquidated,
        currency = currency?.trim()?.takeIf { it.isNotEmpty() },
    )
}

/** Which gateway a platform gets. */
object PortfolioGatewayFactory {
    fun create(platform: MarketPlatform, retrofit: Retrofit): PortfolioGateway = when (platform) {
        MarketPlatform.COINEPRO_FX -> CoineProFxPortfolioGateway(retrofit)
        MarketPlatform.TRADEYAR -> TradeYarPortfolioGateway(retrofit)
    }
}
