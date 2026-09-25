package com.coinepro.core.execution

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.network.ApiErrors
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * The reader's own orders on LBank (5.18.0), through TradeYar's `/api/mobile/v1/trade`.
 *
 * Crypto only, and only on TradeYar: LBank is the one venue this product can place a real order
 * on, and CoinePro-FX has no trading surface at all. Everything that decides whether an order may
 * go — the contract's steps, the 25 % fat-finger band, stop and target on the right side — is the
 * server's (`app/api/mobile/live_orders.py`), so a client cannot talk its way past it; the app
 * checks the same things first only so the reader hears about a typo before a round trip.
 */
enum class LiveSide(val wire: String) {
    BUY("buy"),
    SELL("sell"),
}

enum class LiveOrderType(val wire: String) {
    MARKET("market"),
    LIMIT("limit"),
}

enum class PositionSide(val wire: String) {
    LONG("long"),
    SHORT("short"),
}

/**
 * The server's own vocabulary. `FILLED` and `OPEN` are the only states that claim the exchange
 * confirmed something; `UNKNOWN` is a real answer — «we could not confirm» — never a failure.
 */
enum class LiveOrderStatus(val wire: String) {
    QUEUED("queued"),
    SUBMITTED("submitted"),
    OPEN("open"),
    PARTIAL("partial"),
    FILLED("filled"),
    CANCEL_REQUESTED("cancel_requested"),
    CANCELLED("cancelled"),
    REJECTED("rejected"),
    FAILED("failed"),
    UNKNOWN("unknown"),
    ;

    /** Resting on the book, so it has a line on the chart and can be moved or cancelled. */
    val working: Boolean get() = this == OPEN || this == PARTIAL

    companion object {
        fun of(wire: String?): LiveOrderStatus = entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

/** Whether a stop / target the reader asked for is on the exchange yet. */
enum class ProtectionState(val wire: String) {
    NONE("none"),
    PENDING("pending"),
    ARMED("armed"),
    FAILED("failed"),
    ;

    companion object {
        fun of(wire: String?): ProtectionState = entries.firstOrNull { it.wire == wire } ?: NONE
    }
}

data class LiveAccount(
    val available: Double?,
    val equity: Double?,
    val marginUsed: Double?,
    val unrealizedPnl: Double?,
    /** False while the server's kill switch is on: reads work, every write is refused. */
    val tradingEnabled: Boolean,
)

data class LivePosition(
    val symbol: String,
    val side: PositionSide,
    val quantity: Double,
    val entryPrice: Double?,
    val markPrice: Double?,
    val unrealizedPnl: Double?,
    val leverage: Double?,
    val liquidationPrice: Double?,
)

data class LiveOrder(
    val id: Long,
    val symbol: String,
    val side: LiveSide,
    val type: LiveOrderType,
    val reduceOnly: Boolean,
    val quantity: Double?,
    val price: Double?,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val status: LiveOrderStatus,
    /** As LBank reported it, as text — never re-rounded on the way to the screen. */
    val filledQuantity: String?,
    val avgPrice: String?,
    val protection: ProtectionState,
    val errorMessage: String?,
    val canCancel: Boolean,
    val canAmend: Boolean,
    val createdAt: String?,
)

data class LiveOrderRequest(
    val symbol: String,
    val side: LiveSide,
    val type: LiveOrderType,
    val quantity: Double,
    val price: Double? = null,
    val reduceOnly: Boolean = false,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    /** Minted once per press of «confirm», so a retried request finds the first order. */
    val clientRequestId: String,
)

/**
 * A write the server refused, in its own sentence, with the field it is about.
 *
 * [status] is the HTTP status: 400 no key or a spot key, 409 not in a state that allows it,
 * 422 a guard, 503 the kill switch.
 */
class LiveTradeException(
    override val message: String?,
    val field: String? = null,
    val status: Int = 0,
) : Exception(message)

interface LiveTradeGateway {
    suspend fun account(): LiveAccount
    suspend fun positions(symbol: String? = null): List<LivePosition>
    suspend fun orders(limit: Int = 50): List<LiveOrder>
    suspend fun place(request: LiveOrderRequest): LiveOrder
    suspend fun amend(orderId: Long, price: Double): LiveOrder
    suspend fun cancel(orderId: Long): LiveOrder
    suspend fun protect(symbol: String, side: PositionSide, stopLoss: Double?, takeProfit: Double?)
    suspend fun close(symbol: String, side: PositionSide, quantity: Double?, clientRequestId: String): LiveOrder
    suspend fun setLeverage(symbol: String, leverage: Int)
}

internal interface LiveTradeApi {
    @GET
    suspend fun account(@Url path: String): LiveAccountDto

    @GET
    suspend fun positions(@Url path: String, @Query("symbol") symbol: String?): LivePositionsDto

    @GET
    suspend fun orders(@Url path: String, @Query("limit") limit: Int): LiveOrdersDto

    @POST
    suspend fun place(@Url path: String, @Body body: LivePlaceDto): LiveOrderEnvelopeDto

    @PATCH
    suspend fun amend(@Url path: String, @Body body: LiveAmendDto): LiveOrderEnvelopeDto

    @HTTP(method = "DELETE")
    suspend fun cancel(@Url path: String): LiveOrderEnvelopeDto

    @POST
    suspend fun protect(@Url path: String, @Body body: LiveProtectionDto): LivePositionEnvelopeDto

    @POST
    suspend fun close(@Url path: String, @Body body: LiveCloseDto): LiveOrderEnvelopeDto

    @POST
    suspend fun leverage(@Url path: String, @Body body: LiveLeverageDto): LiveLeverageDto
}

internal class LiveTradePaths(private val prefix: String) {
    val account = "$prefix/trade/account"
    val positions = "$prefix/trade/positions"
    val orders = "$prefix/trade/orders"
    fun order(id: Long) = "$prefix/trade/orders/$id"
    val protection = "$prefix/trade/positions/protection"
    val close = "$prefix/trade/positions/close"
    val leverage = "$prefix/trade/leverage"

    companion object {
        fun of(platform: MarketPlatform): LiveTradePaths? = when (platform) {
            MarketPlatform.TRADEYAR -> LiveTradePaths("api/mobile/v1")
            MarketPlatform.COINEPRO_FX -> null
        }
    }
}

internal data class LiveAccountDto(
    val available: Double? = null,
    val equity: Double? = null,
    val marginUsed: Double? = null,
    val unrealizedPnl: Double? = null,
    val tradingEnabled: Boolean? = null,
)

internal data class LivePositionDto(
    val symbol: String? = null,
    val side: String? = null,
    val quantity: Double? = null,
    val entryPrice: Double? = null,
    val markPrice: Double? = null,
    val unrealizedPnl: Double? = null,
    val leverage: Double? = null,
    val liquidationPrice: Double? = null,
)

internal data class LivePositionsDto(val items: List<LivePositionDto> = emptyList())
internal data class LivePositionEnvelopeDto(val position: LivePositionDto? = null)

internal data class LiveOrderDto(
    val id: Long? = null,
    val symbol: String? = null,
    val side: String? = null,
    val type: String? = null,
    val reduceOnly: Boolean? = null,
    val quantity: Double? = null,
    val price: Double? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
    val status: String? = null,
    val filledQuantity: String? = null,
    val avgPrice: String? = null,
    val protection: String? = null,
    val errorMessage: String? = null,
    val canCancel: Boolean? = null,
    val canAmend: Boolean? = null,
    val createdAt: String? = null,
)

internal data class LiveOrdersDto(val items: List<LiveOrderDto> = emptyList())
internal data class LiveOrderEnvelopeDto(val order: LiveOrderDto? = null)

internal data class LivePlaceDto(
    val clientRequestId: String,
    val symbol: String,
    val side: String,
    val type: String,
    val quantity: Double,
    val price: Double? = null,
    val reduceOnly: Boolean = false,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
)

internal data class LiveAmendDto(val price: Double)

internal data class LiveProtectionDto(
    val symbol: String,
    val side: String,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null,
)

internal data class LiveCloseDto(
    val clientRequestId: String,
    val symbol: String,
    val side: String,
    val quantity: Double? = null,
)

internal data class LiveLeverageDto(val symbol: String? = null, val leverage: Int? = null)

internal fun LiveAccountDto.toDomain() = LiveAccount(
    available = available,
    equity = equity,
    marginUsed = marginUsed,
    unrealizedPnl = unrealizedPnl,
    tradingEnabled = tradingEnabled ?: true,
)

internal fun LivePositionDto.toDomain(): LivePosition? {
    val safeSymbol = symbol?.takeIf { it.isNotBlank() } ?: return null
    val safeSide = PositionSide.entries.firstOrNull { it.wire == side } ?: return null
    val safeQuantity = quantity?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return LivePosition(
        symbol = safeSymbol,
        side = safeSide,
        quantity = safeQuantity,
        entryPrice = entryPrice,
        markPrice = markPrice,
        unrealizedPnl = unrealizedPnl,
        leverage = leverage,
        liquidationPrice = liquidationPrice,
    )
}

internal fun LiveOrderDto.toDomain(): LiveOrder? {
    val safeId = id ?: return null
    val safeSymbol = symbol?.takeIf { it.isNotBlank() } ?: return null
    val safeSide = LiveSide.entries.firstOrNull { it.wire == side } ?: return null
    val safeType = LiveOrderType.entries.firstOrNull { it.wire == type } ?: return null
    return LiveOrder(
        id = safeId,
        symbol = safeSymbol,
        side = safeSide,
        type = safeType,
        reduceOnly = reduceOnly ?: false,
        quantity = quantity,
        price = price,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
        status = LiveOrderStatus.of(status),
        filledQuantity = filledQuantity,
        avgPrice = avgPrice,
        protection = ProtectionState.of(protection),
        errorMessage = errorMessage,
        canCancel = canCancel ?: false,
        canAmend = canAmend ?: false,
        createdAt = createdAt,
    )
}

class NetworkLiveTradeGateway private constructor(
    private val api: LiveTradeApi,
    private val paths: LiveTradePaths?,
) : LiveTradeGateway {

    private fun paths(): LiveTradePaths = paths ?: throw ExecutionUnsupportedException()

    override suspend fun account(): LiveAccount = translate { api.account(paths().account).toDomain() }

    override suspend fun positions(symbol: String?): List<LivePosition> = translate {
        api.positions(paths().positions, symbol).items.mapNotNull { it.toDomain() }
    }

    override suspend fun orders(limit: Int): List<LiveOrder> = translate {
        api.orders(paths().orders, limit.coerceIn(1, 200)).items.mapNotNull { it.toDomain() }
    }

    override suspend fun place(request: LiveOrderRequest): LiveOrder = translate {
        require(request.quantity.isFinite() && request.quantity > 0.0) { "Order quantity must be positive" }
        require(request.clientRequestId.length >= 8) { "Missing idempotency request ID" }
        order(
            api.place(
                paths().orders,
                LivePlaceDto(
                    clientRequestId = request.clientRequestId,
                    symbol = request.symbol,
                    side = request.side.wire,
                    type = request.type.wire,
                    quantity = request.quantity,
                    price = request.price.takeIf { request.type == LiveOrderType.LIMIT },
                    reduceOnly = request.reduceOnly,
                    stopLoss = request.stopLoss,
                    takeProfit = request.takeProfit,
                ),
            ),
        )
    }

    override suspend fun amend(orderId: Long, price: Double): LiveOrder = translate {
        order(api.amend(paths().order(orderId), LiveAmendDto(price)))
    }

    override suspend fun cancel(orderId: Long): LiveOrder = translate { order(api.cancel(paths().order(orderId))) }

    override suspend fun protect(symbol: String, side: PositionSide, stopLoss: Double?, takeProfit: Double?) {
        translate { api.protect(paths().protection, LiveProtectionDto(symbol, side.wire, stopLoss, takeProfit)) }
    }

    override suspend fun close(symbol: String, side: PositionSide, quantity: Double?, clientRequestId: String): LiveOrder =
        translate { order(api.close(paths().close, LiveCloseDto(clientRequestId, symbol, side.wire, quantity))) }

    override suspend fun setLeverage(symbol: String, leverage: Int) {
        translate { api.leverage(paths().leverage, LiveLeverageDto(symbol, leverage)) }
    }

    private fun order(envelope: LiveOrderEnvelopeDto): LiveOrder =
        requireNotNull(envelope.order?.toDomain()) { "Invalid order response" }

    /** A refusal carries the server's sentence; everything else is rethrown as it was. */
    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        if (error.code() in REFUSALS) {
            val parsed = ApiErrors.from(error)
            throw LiveTradeException(parsed.message, parsed.field, error.code())
        }
        throw error
    }

    companion object {
        private val REFUSALS = setOf(400, 404, 409, 422, 429, 502, 503)

        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkLiveTradeGateway =
            NetworkLiveTradeGateway(retrofit.create(LiveTradeApi::class.java), LiveTradePaths.of(platform))
    }
}
