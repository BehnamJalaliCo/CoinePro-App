package com.coinepro.core.execution

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live LBank trading from the app (5.18.0): the wire shapes TradeYar's `/trade` routes answer
 * with, the checks the ticket runs before a round trip, and what the controller does with each
 * kind of answer.
 */
class LiveTradeTest {

    // The app's own converter settings (`NetworkFactory`): snake_case on the wire.
    private val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()

    @Test
    fun `an order as the server writes it reads back whole`() {
        val json = """{"order": {"id": 41, "venue": "lbank", "symbol": "BTCUSDT", "side": "buy",
            "type": "limit", "reduce_only": false, "quantity": 0.012, "price": 59000.0,
            "stop_loss": 58000.0, "take_profit": null, "status": "open", "filled_quantity": null,
            "avg_price": null, "provider_order_id": "Lcpo41", "protection": "pending",
            "error_message": null, "can_cancel": true, "can_amend": true,
            "created_at": "2026-09-25T10:00:00+00:00"}}"""
        val order = gson.fromJson(json, LiveOrderEnvelopeDto::class.java).order?.toDomain()!!
        assertEquals(41L, order.id)
        assertEquals(LiveSide.BUY, order.side)
        assertEquals(LiveOrderType.LIMIT, order.type)
        assertEquals(LiveOrderStatus.OPEN, order.status)
        assertTrue(order.status.working && order.canAmend)
        assertEquals(ProtectionState.PENDING, order.protection)
        assertEquals(58000.0, order.stopLoss!!, 0.0)
    }

    @Test
    fun `an unfamiliar status is unknown and never a fill`() {
        assertEquals(LiveOrderStatus.UNKNOWN, LiveOrderStatus.of("settling"))
        assertFalse(LiveOrderStatus.UNKNOWN.working)
    }

    @Test
    fun `positions and the account read the server's shapes`() {
        val positions = gson.fromJson(
            """{"items": [{"symbol": "ETHUSDT", "side": "short", "quantity": 3.0, "entry_price": 2500.0,
               "unrealized_pnl": -4.5, "liquidation_price": 2900.0}, {"symbol": "", "side": "long", "quantity": 1}]}""",
            LivePositionsDto::class.java,
        ).items.mapNotNull { it.toDomain() }
        assertEquals(1, positions.size)
        assertEquals(PositionSide.SHORT, positions.single().side)
        val account = gson.fromJson(
            """{"available": 900.5, "equity": 1000.0, "margin_used": 99.5, "trading_enabled": false}""",
            LiveAccountDto::class.java,
        ).toDomain()
        assertEquals(1000.0, account.equity!!, 0.0)
        assertFalse(account.tradingEnabled)
    }

    private fun request(
        side: LiveSide = LiveSide.BUY,
        type: LiveOrderType = LiveOrderType.MARKET,
        price: Double? = null,
        stop: Double? = null,
        target: Double? = null,
        reduceOnly: Boolean = false,
    ) = LiveOrderRequest("BTCUSDT", side, type, 0.01, price, reduceOnly, stop, target, "req-000001")

    @Test
    fun `the ticket catches what the server would refuse`() {
        assertNull(LiveOrderCheck.check(request(stop = 58_000.0, target = 63_000.0), 60_000.0))
        assertEquals(LiveOrderCheck.Problem.PRICE_MISSING, LiveOrderCheck.check(request(type = LiveOrderType.LIMIT), 60_000.0))
        assertEquals(
            LiveOrderCheck.Problem.PRICE_BAND,
            LiveOrderCheck.check(request(type = LiveOrderType.LIMIT, price = 6_000.0), 60_000.0),
        )
        assertEquals(LiveOrderCheck.Problem.STOP_SIDE, LiveOrderCheck.check(request(stop = 61_000.0), 60_000.0))
        assertEquals(
            LiveOrderCheck.Problem.TARGET_SIDE,
            LiveOrderCheck.check(request(side = LiveSide.SELL, target = 61_000.0), 60_000.0),
        )
        assertEquals(
            LiveOrderCheck.Problem.CLOSING_WITH_PROTECTION,
            LiveOrderCheck.check(request(reduceOnly = true, stop = 58_000.0), 60_000.0),
        )
        assertEquals(
            LiveOrderCheck.Problem.QUANTITY,
            LiveOrderCheck.check(request().copy(quantity = 0.0), 60_000.0),
        )
    }

    @Test
    fun `a refusal carries the server's sentence and a read failure keeps the book on screen`() {
        val gateway = FakeLiveTrade()
        val controller = LiveTradeController(gateway, FakeVenue(LbankPermission.FUTURES), CoroutineScope(Dispatchers.Unconfined)) { "minted-id-1" }
        controller.refreshAvailability()
        assertTrue(controller.state.value.available)
        assertEquals(1, controller.state.value.positions.size)

        gateway.refusal = LiveTradeException("حداقل مقدار سفارش 0.001 است.", "quantity", 422)
        gateway.readsFail = true
        controller.place(controller.draft("BTCUSDT", LiveSide.BUY, LiveOrderType.MARKET, 0.0001))
        val outcome = controller.state.value.outcome as LiveOutcome.Refused
        assertEquals("حداقل مقدار سفارش 0.001 است.", outcome.message)
        assertEquals("the book still reads what it held", 1, controller.state.value.positions.size)
        assertEquals("minted-id-1", gateway.lastRequest?.clientRequestId)
    }

    @Test
    fun `a spot key is not a live book`() {
        val controller = LiveTradeController(FakeLiveTrade(), FakeVenue(LbankPermission.SPOT), CoroutineScope(Dispatchers.Unconfined))
        controller.refreshAvailability()
        assertFalse(controller.state.value.available)
    }

    @Test
    fun `a placed order is the outcome and the book reloads`() {
        val gateway = FakeLiveTrade()
        val controller = LiveTradeController(gateway, FakeVenue(LbankPermission.FUTURES), CoroutineScope(Dispatchers.Unconfined))
        controller.refreshAvailability()
        controller.place(controller.draft("BTCUSDT", LiveSide.BUY, LiveOrderType.LIMIT, 0.01, price = 59_000.0))
        val placed = (controller.state.value.outcome as LiveOutcome.Order).order
        assertEquals(LiveOrderStatus.OPEN, placed.status)
        assertEquals(listOf(placed), controller.state.value.workingFor("btcusdt"))
        assertFalse(controller.state.value.busy)
    }
}

private class FakeVenue(private val permission: LbankPermission) : ExecutionGateway {
    override suspend fun connections(): Pair<VenueConnection?, VenueConnection?> =
        null to VenueConnection(ExecutionVenue.LBANK, configured = true, connected = true, status = "connected", lbankPermission = permission)
    override suspend fun connectMt5(broker: String, server: String, login: String, password: String) = Unit
    override suspend fun disconnectMt5() = Unit
    override suspend fun connectLbank(apiKey: String, apiSecret: String, permission: LbankPermission) = Unit
    override suspend fun disconnectLbank() = Unit
    override suspend fun executeSignal(signalId: Long, venue: ExecutionVenue, quantity: Double, clientRequestId: String): SignalExecution =
        throw ExecutionUnsupportedException()
    override suspend fun executions(limit: Int): List<SignalExecution> = emptyList()
    override suspend fun execution(executionId: String): SignalExecution = throw ExecutionUnsupportedException()
    override suspend fun requestClose(executionId: String): SignalExecution = throw ExecutionUnsupportedException()
}

private class FakeLiveTrade : LiveTradeGateway {
    var refusal: LiveTradeException? = null
    var readsFail = false
    var lastRequest: LiveOrderRequest? = null
    private val placed = mutableListOf<LiveOrder>()

    private fun reads() {
        if (readsFail) throw IllegalStateException("offline")
    }

    override suspend fun account(): LiveAccount = reads().let { LiveAccount(900.0, 1000.0, 100.0, 0.0, true) }
    override suspend fun positions(symbol: String?): List<LivePosition> = reads().let {
        listOf(LivePosition("BTCUSDT", PositionSide.LONG, 0.05, 60_000.0, null, 1.0, 10.0, null))
    }
    override suspend fun orders(limit: Int): List<LiveOrder> = reads().let { placed.toList() }
    override suspend fun place(request: LiveOrderRequest): LiveOrder {
        lastRequest = request
        refusal?.let { throw it }
        return LiveOrder(
            id = placed.size + 1L, symbol = request.symbol, side = request.side, type = request.type,
            reduceOnly = request.reduceOnly, quantity = request.quantity, price = request.price,
            stopLoss = request.stopLoss, takeProfit = request.takeProfit, status = LiveOrderStatus.OPEN,
            filledQuantity = null, avgPrice = null, protection = ProtectionState.NONE, errorMessage = null,
            canCancel = true, canAmend = true, createdAt = null,
        ).also { placed += it }
    }
    override suspend fun amend(orderId: Long, price: Double): LiveOrder = placed.first { it.id == orderId }
    override suspend fun cancel(orderId: Long): LiveOrder = placed.first { it.id == orderId }
    override suspend fun protect(symbol: String, side: PositionSide, stopLoss: Double?, takeProfit: Double?) = Unit
    override suspend fun close(symbol: String, side: PositionSide, quantity: Double?, clientRequestId: String): LiveOrder =
        throw LiveTradeException("no position", status = 404)
    override suspend fun setLeverage(symbol: String, leverage: Int) = Unit
}
