package com.coinepro.core.execution

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * What the app checks before a live order leaves the phone (5.18.0).
 *
 * The same four guards the server applies (`app/api/mobile/live_orders.py`), so a typo is caught
 * while the reader is still looking at the ticket rather than after a round trip. The server stays
 * the authority: this can only refuse earlier, never allow something the server would refuse.
 */
object LiveOrderCheck {

    /** How far a limit, stop or target may sit from the live price — the server's own 25 %. */
    const val MAX_DEVIATION: Double = 0.25

    enum class Problem { QUANTITY, PRICE_MISSING, PRICE_BAND, STOP_SIDE, TARGET_SIDE, CLOSING_WITH_PROTECTION }

    fun check(request: LiveOrderRequest, livePrice: Double?): Problem? {
        if (!request.quantity.isFinite() || request.quantity <= 0.0) return Problem.QUANTITY
        val limit = request.price.takeIf { request.type == LiveOrderType.LIMIT }
        if (request.type == LiveOrderType.LIMIT && (limit == null || !limit.isFinite() || limit <= 0.0)) {
            return Problem.PRICE_MISSING
        }
        if (request.reduceOnly && (request.stopLoss != null || request.takeProfit != null)) {
            return Problem.CLOSING_WITH_PROTECTION
        }
        listOfNotNull(limit, request.stopLoss, request.takeProfit).forEach { price ->
            if (!inBand(price, livePrice)) return Problem.PRICE_BAND
        }
        val entry = limit ?: livePrice ?: return null
        val long = request.side == LiveSide.BUY
        request.stopLoss?.let { stop -> if (if (long) stop >= entry else stop <= entry) return Problem.STOP_SIDE }
        request.takeProfit?.let { target -> if (if (long) target <= entry else target >= entry) return Problem.TARGET_SIDE }
        return null
    }

    private fun inBand(price: Double, reference: Double?): Boolean =
        reference == null || reference <= 0.0 || abs(price - reference) / reference <= MAX_DEVIATION
}

/** The answer to the reader's last action on the live book. */
sealed interface LiveOutcome {
    /** The exchange's verdict on the order, whatever it was — filled, resting, rejected, unknown. */
    data class Order(val order: LiveOrder) : LiveOutcome

    /** A stop / target set, or leverage changed. */
    data object Done : LiveOutcome

    /** The server refused, in its own sentence. */
    data class Refused(val message: String?) : LiveOutcome

    /** It could not be asked — no connection. The order's fate is unknown until the list refreshes. */
    data object Unreachable : LiveOutcome
}

data class LiveTradeState(
    /** An LBank futures key is linked and verified, so the live book exists at all. */
    val available: Boolean = false,
    val account: LiveAccount? = null,
    val positions: List<LivePosition> = emptyList(),
    val orders: List<LiveOrder> = emptyList(),
    val busy: Boolean = false,
    val outcome: LiveOutcome? = null,
) {
    fun positionsFor(symbol: String): List<LivePosition> = positions.filter { it.symbol.equals(symbol, ignoreCase = true) }

    fun workingFor(symbol: String): List<LiveOrder> =
        orders.filter { it.symbol.equals(symbol, ignoreCase = true) && it.status.working }
}

/**
 * The reader's live LBank book, for the chart, the ticket and the depth ladder.
 *
 * Every write mints its own `client_request_id` once, at the press, and keeps it for that press —
 * so a retry after a dropped response finds the first order on the server instead of placing a
 * second one. Nothing here retries a write on its own.
 */
class LiveTradeController(
    private val trade: LiveTradeGateway,
    private val execution: ExecutionGateway,
    private val scope: CoroutineScope,
    private val newRequestId: () -> String = { UUID.randomUUID().toString() },
) {
    private val _state = MutableStateFlow(LiveTradeState())
    val state: StateFlow<LiveTradeState> = _state.asStateFlow()

    /** Asks whether an LBank futures key is linked, then reads the book if it is. */
    fun refreshAvailability() {
        scope.launch {
            val lbank = runCatching { execution.connections().second }.getOrNull()
            val available = lbank?.connected == true && lbank.lbankPermission != LbankPermission.SPOT
            _state.update { it.copy(available = available) }
            if (available) load()
        }
    }

    fun refresh() {
        if (!_state.value.available) return
        scope.launch { load() }
    }

    private suspend fun load() {
        val account = runCatching { trade.account() }.getOrNull()
        val positions = runCatching { trade.positions() }.getOrNull()
        val orders = runCatching { trade.orders() }.getOrNull()
        _state.update {
            it.copy(
                // A failed read keeps what was on screen: an empty list would read as «you hold
                // nothing», which is a claim about somebody's money this call did not establish.
                account = account ?: it.account,
                positions = positions ?: it.positions,
                orders = orders ?: it.orders,
            )
        }
    }

    fun place(request: LiveOrderRequest) = write {
        LiveOutcome.Order(trade.place(request.copy(clientRequestId = request.clientRequestId.ifBlank { newRequestId() })))
    }

    /** A request for the ticket to fill in, with its idempotency id already minted. */
    fun draft(
        symbol: String,
        side: LiveSide,
        type: LiveOrderType,
        quantity: Double,
        price: Double? = null,
        reduceOnly: Boolean = false,
        stopLoss: Double? = null,
        takeProfit: Double? = null,
    ): LiveOrderRequest = LiveOrderRequest(symbol, side, type, quantity, price, reduceOnly, stopLoss, takeProfit, newRequestId())

    fun amend(orderId: Long, price: Double) = write { LiveOutcome.Order(trade.amend(orderId, price)) }

    fun cancel(orderId: Long) = write { LiveOutcome.Order(trade.cancel(orderId)) }

    fun protect(symbol: String, side: PositionSide, stopLoss: Double?, takeProfit: Double?) = write {
        trade.protect(symbol, side, stopLoss, takeProfit)
        LiveOutcome.Done
    }

    fun close(symbol: String, side: PositionSide, quantity: Double? = null) = write {
        LiveOutcome.Order(trade.close(symbol, side, quantity, newRequestId()))
    }

    fun setLeverage(symbol: String, leverage: Int) = write {
        trade.setLeverage(symbol, leverage)
        LiveOutcome.Done
    }

    fun dismissOutcome() = _state.update { it.copy(outcome = null) }

    private fun write(block: suspend () -> LiveOutcome) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, outcome = null) }
        scope.launch {
            val outcome = runCatching { block() }.getOrElse { error ->
                when (error) {
                    is CancellationException -> throw error
                    is LiveTradeException -> LiveOutcome.Refused(error.message)
                    is ExecutionUnsupportedException -> LiveOutcome.Refused(null)
                    else -> LiveOutcome.Unreachable
                }
            }
            _state.update { it.copy(busy = false, outcome = outcome) }
            load()
        }
    }
}
