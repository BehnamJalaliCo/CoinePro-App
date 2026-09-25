package com.coinepro.feature.chart

import com.coinepro.core.chart.OrderLineKind
import com.coinepro.core.papertrade.PaperOrder
import com.coinepro.core.papertrade.PaperOrderType
import com.coinepro.core.papertrade.PaperPosition
import com.coinepro.core.papertrade.PaperSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Trading on the chart (5.17.0): the lines a book draws, and the edit a drag of each asks for. */
class ChartTradeLinesTest {

    private val words = TradeLineWords("Entry", "SL", "TP", "Buy", "Sell")
    private val position = PaperPosition(
        id = 7, symbol = "XAUUSD", side = PaperSide.BUY, size = 1.0, entry = 2_550.0,
        openedAtEpochMillis = 0L, stopLoss = 2_530.0, takeProfit = 2_600.0,
    )
    private val order = PaperOrder(
        id = 9, symbol = "XAUUSD", side = PaperSide.SELL, type = PaperOrderType.LIMIT, size = 1.0,
        limitPrice = 2_620.0, placedAtEpochMillis = 0L,
    )

    @Test
    fun `a position with protection and a working order draw four lines, the entry fixed`() {
        val lines = ChartTradeLines.of(position, listOf(order), words)
        assertEquals(listOf(OrderLineKind.ENTRY, OrderLineKind.STOP, OrderLineKind.TARGET, OrderLineKind.WORKING), lines.map { it.kind })
        assertEquals(listOf(false, true, true, true), lines.map { it.movable })
    }

    @Test
    fun `dragging the stop keeps the target, and dragging a limit amends its limit`() {
        val lines = ChartTradeLines.of(position, listOf(order), words)
        val stop = lines.first { it.kind == OrderLineKind.STOP }
        assertEquals(
            ChartTradeEdit.Protection(7, stopLoss = 2_540.0, takeProfit = 2_600.0),
            ChartTradeLines.edit(stop.id, 2_540.0, position, listOf(order)),
        )
        val working = lines.first { it.kind == OrderLineKind.WORKING }
        assertEquals(
            ChartTradeEdit.Amend(9, limitPrice = 2_610.0, stopPrice = null),
            ChartTradeLines.edit(working.id, 2_610.0, position, listOf(order)),
        )
        assertNull(ChartTradeLines.edit(lines.first().id, 2_560.0, position, listOf(order)))
    }
}
