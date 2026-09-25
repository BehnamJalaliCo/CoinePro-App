package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartOrderLine
import com.coinepro.core.chart.OrderLineKind
import com.coinepro.core.papertrade.PaperOrder
import com.coinepro.core.papertrade.PaperPosition

/** What dragging a trading line on the chart asks the paper book to do (5.17.0). */
sealed interface ChartTradeEdit {
    /** A position's stop or target moved; the other one is carried as it was. */
    data class Protection(val positionId: Long, val stopLoss: Double?, val takeProfit: Double?) : ChartTradeEdit

    /** A working order's price moved: the limit for a limit order, the trigger for a stop. */
    data class Amend(val orderId: Long, val limitPrice: Double?, val stopPrice: Double?) : ChartTradeEdit
}

/** The words on the tags, resolved by the screen in the reader's language. */
data class TradeLineWords(val entry: String, val stop: String, val target: String, val buy: String, val sell: String)

/**
 * The lines a position and its working orders put on the chart, and the edit a drag of one means.
 * Pure, so what a drag does is a unit test rather than a gesture.
 */
object ChartTradeLines {

    fun of(position: PaperPosition?, working: List<PaperOrder>, words: TradeLineWords): List<ChartOrderLine> = buildList {
        position?.let { held ->
            add(ChartOrderLine("$POSITION${held.id}$ENTRY", held.entry, OrderLineKind.ENTRY, words.entry))
            held.stopLoss?.let { add(ChartOrderLine("$POSITION${held.id}$STOP", it, OrderLineKind.STOP, words.stop)) }
            held.takeProfit?.let { add(ChartOrderLine("$POSITION${held.id}$TARGET", it, OrderLineKind.TARGET, words.target)) }
        }
        working.forEach { order ->
            val price = order.limitPrice ?: order.stopPrice ?: return@forEach
            val side = if (order.side.name == "BUY") words.buy else words.sell
            add(ChartOrderLine("$ORDER${order.id}", price, OrderLineKind.WORKING, side + " " + order.type.name.take(3)))
        }
    }

    fun edit(id: String, price: Double, position: PaperPosition?, working: List<PaperOrder>): ChartTradeEdit? {
        if (!price.isFinite() || price <= 0.0) return null
        if (id.startsWith(ORDER)) {
            val orderId = id.removePrefix(ORDER).toLongOrNull() ?: return null
            val order = working.firstOrNull { it.id == orderId } ?: return null
            return if (order.limitPrice != null) {
                ChartTradeEdit.Amend(orderId, limitPrice = price, stopPrice = null)
            } else {
                ChartTradeEdit.Amend(orderId, limitPrice = null, stopPrice = price)
            }
        }
        val held = position ?: return null
        if (!id.startsWith("$POSITION${held.id}")) return null
        return when {
            id.endsWith(STOP) -> ChartTradeEdit.Protection(held.id, stopLoss = price, takeProfit = held.takeProfit)
            id.endsWith(TARGET) -> ChartTradeEdit.Protection(held.id, stopLoss = held.stopLoss, takeProfit = price)
            else -> null
        }
    }

    private const val POSITION = "pos:"
    private const val ORDER = "ord:"
    private const val ENTRY = ":entry"
    private const val STOP = ":sl"
    private const val TARGET = ":tp"
}
