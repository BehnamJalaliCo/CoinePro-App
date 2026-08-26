package com.coinepro.core.chart

import kotlin.math.abs

/** Which way a setup faces. */
enum class TradeSide { BUY, SELL }

/**
 * A trade laid out on the chart: where to get in, where to be wrong, where to take profit.
 *
 * Ported from `tradingFromChart.js`. Distinct from [SignalOverlay], which is what the *server* sent
 * and is read-only — this is what the reader is building, with one target rather than several,
 * because a setup being dragged around has one of each line to drag.
 */
data class ChartOrder(
    val side: TradeSide,
    val entry: Double,
    val stopLoss: Double,
    val takeProfit: Double,
)

/**
 * The trade arithmetic, ported from the web terminal.
 *
 * Every function here is pure and every one of them is a number the reader will act on, which is
 * why they are separated from the drawing at all: a stop distance that is right on the chart and
 * wrong in the order ticket is the most expensive kind of bug this app can have.
 */
object TradeFromChart {

    /**
     * Reward over risk, or null when there is no risk to divide by.
     *
     * Null rather than infinity or zero. A setup whose stop sits on its entry has no risk *and* no
     * meaning, and any number printed for it would be read as a real ratio.
     */
    fun riskReward(order: ChartOrder): Double? {
        val risk = abs(order.entry - order.stopLoss)
        if (risk == 0.0 || !risk.isFinite()) return null
        return abs(order.takeProfit - order.entry) / risk
    }

    /**
     * Whether the three prices are in an order that makes sense for the side.
     *
     * A buy with its target below its entry is not a pessimistic trade, it is a mistake — usually
     * a stop and a target dragged past each other — and it must not reach an order ticket.
     */
    fun isValid(order: ChartOrder): Boolean {
        if (!order.entry.isFinite() || !order.stopLoss.isFinite() || !order.takeProfit.isFinite()) {
            return false
        }
        return when (order.side) {
            TradeSide.BUY -> order.stopLoss < order.entry && order.takeProfit > order.entry
            TradeSide.SELL -> order.stopLoss > order.entry && order.takeProfit < order.entry
        }
    }

    /**
     * How much price movement one pip is, for this instrument.
     *
     * A heuristic, and it is worth being honest that it is one: the web terminal guesses from the
     * ticker because no feed it reads publishes the figure. Neither of this app's backends does
     * either yet — `docs/REQUEST2_*.md` asks both of them for it — and when they answer, this
     * function should take the server's number and keep the guess only as a fallback.
     */
    fun pipSize(symbol: String?, price: Double? = null): Double {
        val ticker = symbol?.uppercase().orEmpty()
        return when {
            ticker.contains("JPY") -> 0.01
            ticker.contains("XAU") || ticker.contains("GOLD") -> 0.1
            ticker.contains("XAG") || ticker.contains("SILVER") -> 0.01
            // An index or a large-cap coin quoted in the thousands: a pip of 0.0001 there would be
            // a stop distance of forty million pips, which is a number nobody can read.
            price != null && price > 1_000 -> 1.0
            else -> 0.0001
        }
    }

    /** The distance from entry to stop, in pips. */
    fun stopPips(order: ChartOrder, symbol: String?): Double {
        val pip = pipSize(symbol, order.entry)
        return if (pip == 0.0) 0.0 else abs(order.entry - order.stopLoss) / pip
    }

    /** How big a position may be, given how much the reader is prepared to lose reaching the stop. */
    data class PositionSize(val units: Double, val lots: Double, val riskAmount: Double)

    /**
     * Size from risk, which is the only direction this calculation may run.
     *
     * Risk decides size; size does not decide risk. Offering it the other way round — "how much do
     * I lose if I buy one lot" — is the same arithmetic and produces an entirely different habit.
     */
    fun positionSize(
        order: ChartOrder,
        riskAmount: Double,
        contractSize: Double = 100_000.0,
        valuePerUnit: Double = 1.0,
    ): PositionSize {
        if (riskAmount <= 0.0) return PositionSize(0.0, 0.0, 0.0)
        val distance = abs(order.entry - order.stopLoss)
        if (distance == 0.0 || !distance.isFinite()) return PositionSize(0.0, 0.0, riskAmount)
        val units = riskAmount / (distance * valuePerUnit)
        val lots = if (contractSize != 0.0) units / contractSize else 0.0
        return PositionSize(units.coerceAtLeast(0.0), lots.coerceAtLeast(0.0), riskAmount)
    }

    /** Where a position stands right now, in pips and in money. */
    data class Unrealised(val pips: Double, val amount: Double)

    fun unrealised(
        order: ChartOrder,
        livePrice: Double,
        symbol: String? = null,
        units: Double = 0.0,
        valuePerUnit: Double = 1.0,
    ): Unrealised {
        if (!livePrice.isFinite()) return Unrealised(0.0, 0.0)
        val direction = if (order.side == TradeSide.BUY) 1 else -1
        val move = (livePrice - order.entry) * direction
        val pip = pipSize(symbol, order.entry).takeIf { it != 0.0 } ?: 0.0001
        return Unrealised(pips = move / pip, amount = move * units * valuePerUnit)
    }

    /**
     * A starting setup from one tapped price.
     *
     * The defaults are the web terminal's: a stop half a percent away and a target at twice the
     * risk. They are a starting point to drag, not a recommendation — which is why the ratio is
     * two rather than something that looks considered.
     */
    fun defaultOrder(
        side: TradeSide,
        basePrice: Double,
        stopPercent: Double = 0.005,
        riskReward: Double = 2.0,
    ): ChartOrder? {
        if (!basePrice.isFinite() || basePrice == 0.0) return null
        val distance = basePrice * stopPercent
        val sign = if (side == TradeSide.BUY) 1 else -1
        return ChartOrder(
            side = side,
            entry = basePrice,
            stopLoss = basePrice - sign * distance,
            takeProfit = basePrice + sign * riskReward * distance,
        )
    }

    /** Move the stop to entry, keeping everything else. */
    fun moveToBreakeven(order: ChartOrder): ChartOrder = order.copy(stopLoss = order.entry)

    /** What a broker would call this order, given where price is now. */
    enum class OrderType { MARKET, BUY_LIMIT, BUY_STOP, SELL_LIMIT, SELL_STOP }

    /**
     * Market, limit or stop — decided by where the entry sits relative to the live price.
     *
     * [tolerancePercent] is what counts as "at the market". Without it, an entry dragged onto the
     * current price becomes a limit order that fills only if price comes back, which is not what
     * dragging a line onto the current price means.
     */
    fun classify(order: ChartOrder, livePrice: Double, tolerancePercent: Double = 0.0002): OrderType {
        if (!livePrice.isFinite()) return OrderType.MARKET
        if (abs(order.entry - livePrice) <= livePrice * tolerancePercent) return OrderType.MARKET
        return when (order.side) {
            TradeSide.BUY -> if (order.entry < livePrice) OrderType.BUY_LIMIT else OrderType.BUY_STOP
            TradeSide.SELL -> if (order.entry > livePrice) OrderType.SELL_LIMIT else OrderType.SELL_STOP
        }
    }
}
