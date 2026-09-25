package com.coinepro.feature.chart

import com.coinepro.core.chart.OrderLineKind
import com.coinepro.core.execution.LiveOrder
import com.coinepro.core.execution.LiveOrderStatus
import com.coinepro.core.execution.LiveOrderType
import com.coinepro.core.execution.LivePosition
import com.coinepro.core.execution.LiveSide
import com.coinepro.core.execution.LiveTradeState
import com.coinepro.core.execution.PositionSide
import com.coinepro.core.execution.ProtectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live LBank book on the chart (5.18.0): which lines it draws, and what dragging one names. */
class LiveTradeLinesTest {

    private val words = TradeLineWords(entry = "Entry", stop = "SL", target = "TP", buy = "BUY", sell = "SELL")

    private fun order(id: Long, symbol: String, status: LiveOrderStatus, price: Double?) = LiveOrder(
        id = id, symbol = symbol, side = LiveSide.BUY, type = LiveOrderType.LIMIT, reduceOnly = false,
        quantity = 0.01, price = price, stopLoss = null, takeProfit = null, status = status,
        filledQuantity = null, avgPrice = null, protection = ProtectionState.NONE, errorMessage = null,
        canCancel = true, canAmend = true, createdAt = null,
    )

    private val state = LiveTradeState(
        available = true,
        positions = listOf(LivePosition("BTCUSDT", PositionSide.LONG, 0.05, 60_000.0, null, 1.0, 10.0, null)),
        orders = listOf(
            order(7, "BTCUSDT", LiveOrderStatus.OPEN, 59_000.0),
            order(8, "BTCUSDT", LiveOrderStatus.FILLED, 59_500.0),
            order(9, "ETHUSDT", LiveOrderStatus.OPEN, 2_400.0),
        ),
    )

    @Test
    fun `only this symbol's entry and resting orders are drawn, and only the order moves`() {
        val lines = LiveTradeLines.of(state, "BTCUSDT", words, "LBank")
        assertEquals(2, lines.size)
        val entry = lines.first { it.kind == OrderLineKind.ENTRY }
        assertFalse("an entry is history", entry.movable)
        val working = lines.single { it.kind == OrderLineKind.WORKING }
        assertTrue(working.movable)
        assertEquals(59_000.0, working.price, 0.0)
        assertEquals("LBank BUY", working.label)
    }

    @Test
    fun `a drag names the live order and never a paper one`() {
        val working = LiveTradeLines.of(state, "BTCUSDT", words, "LBank").single { it.kind == OrderLineKind.WORKING }
        assertEquals(7L, LiveTradeLines.orderIdOf(working.id))
        assertTrue(LiveTradeLines.isLive(working.id))
        assertNull(LiveTradeLines.orderIdOf("ord:7"))
        assertFalse(LiveTradeLines.isLive("pos:1:sl"))
    }
}
