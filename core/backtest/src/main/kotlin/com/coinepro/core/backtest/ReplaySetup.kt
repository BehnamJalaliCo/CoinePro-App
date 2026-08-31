package com.coinepro.core.backtest

import com.coinepro.core.chart.SignalOverlay

/**
 * A rehearsal position, as the chart draws it.
 *
 * ### Why this converts instead of drawing
 *
 * `feature:chart` already turns a live paper position into a [SignalOverlay] for exactly this
 * reason, and the argument is the same one again: an entry, a level where the reader is wrong, a
 * level where they are done, and the bar it all started from is one picture, and there must be only
 * one opinion in the app about which side of the entry the red goes on. `setupBands` holds that
 * opinion — it reads the *prices*, so a short inverts without a branch — and `setupSpan` holds the
 * other half, that nothing is ever shaded left of the bar the position opened on. A second renderer
 * for replay would be a second chance to get a short's stop drawn under its entry, and the day the
 * two disagreed the reader would be looking at a chart that contradicts the ledger beside it.
 *
 * So a replay position is drawn by the renderer that already exists, and this file is thirty lines
 * of conversion.
 *
 * ### What is deliberately not drawn
 *
 * Size and unrealised P&L. They are on the ledger row under the chart, where there is room to label
 * them, and they change on every step — a figure repainting over the bars is what makes a chart
 * feel cheap. The chart's job here is the geometry: *this* candle, *these* two levels.
 *
 * A position with neither a stop nor a target still draws: the entry line and the bar it began on
 * are true and are worth seeing. `setupBands` simply produces no bands for the levels that are
 * missing, which is the honest picture of a position taken without a plan.
 */
fun replaySetup(
    position: ReplayPosition,
    entryLabel: String? = null,
    stopLabel: String? = null,
    targetLabel: String? = null,
): SignalOverlay? {
    if (!position.entryPrice.isFinite() || position.entryPrice <= 0.0) return null
    if (position.entryTime <= 0L) return null
    val target = position.takeProfit?.takeIf { it.isFinite() && it > 0.0 }
    return SignalOverlay(
        entry = position.entryPrice,
        stopLoss = position.stopLoss?.takeIf { it.isFinite() && it > 0.0 },
        takeProfits = listOfNotNull(target),
        isLong = position.isLong,
        // Unix seconds, the same units every bar in this app carries, and taken from the bar the
        // position was opened on rather than from a clock: a replay happens in the past, and a
        // rehearsal stamped with the reader's wall clock would anchor its zone off the right-hand
        // edge of a chart showing last March.
        issuedAt = position.entryTime,
        // Open, by definition — a closed rehearsal is not in `ReplaySession.open` at all. The zone
        // therefore runs to the right-hand edge of the plot, which during a replay is the cursor:
        // the true statement that the position is open into bars the reader cannot see yet.
        closedAt = null,
        entryLabel = entryLabel,
        stopLabel = if (position.stopLoss != null) stopLabel else null,
        targetLabels = if (target != null) listOfNotNull(targetLabel) else emptyList(),
    )
}
