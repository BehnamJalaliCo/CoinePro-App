package com.coinepro.feature.chart

import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.papertrade.PaperPosition
import com.coinepro.core.papertrade.PaperSide

/**
 * An open position, as the chart draws it.
 *
 * ### Why a position and a signal render through the same overlay
 *
 * They are the same picture: an entry, a level where the reader is wrong, a level where they are
 * done, and a moment the whole thing started from. `SignalOverlay` already carries exactly that
 * shape and `setupSpan` already knows the one rule that matters — nothing is shaded left of the bar
 * it began on. Giving a position its own overlay type and its own renderer would be a second
 * opinion about which side of the entry the red goes on, and the day the two disagreed the reader
 * would be looking at a chart that says their stop is above their entry on a long.
 *
 * So this converts, and the rest of the chart is untouched.
 *
 * ### What is deliberately not drawn
 *
 * **Size, margin and unrealised P&L.** They are on the paper-trading screen, where there is room to
 * label them, and they change on every tick — a figure repainting over the bars several times a
 * second is the thing that makes a chart feel cheap. The chart's job here is the geometry: *this*
 * candle, *these* two levels.
 *
 * **A position with no time on it.** Not possible for a paper position — the book stamps
 * [PaperPosition.openedAtEpochMillis] when it fills — but the guard is here rather than assumed,
 * because an unanchored overlay is drawn as bare lines across the whole plot and that is precisely
 * the picture the owner asked to be rid of.
 */
fun positionOverlay(
    position: PaperPosition,
    entryLabel: String? = null,
    stopLabel: String? = null,
    targetLabel: String? = null,
): SignalOverlay? {
    if (!position.entry.isFinite() || position.entry <= 0.0) return null
    if (position.openedAtEpochMillis <= 0L) return null
    val target = position.takeProfit?.takeIf { it.isFinite() && it > 0.0 }
    return SignalOverlay(
        entry = position.entry,
        stopLoss = position.stopLoss?.takeIf { it.isFinite() && it > 0.0 },
        takeProfits = listOfNotNull(target),
        isLong = position.side == PaperSide.BUY,
        // The bars carry unix **seconds**; the book keeps milliseconds because it also stamps
        // fills, which happen several times inside one bar. Truncating rather than rounding puts a
        // position opened at 10:00:59 on the 10:00 bar, which is the bar it was opened on.
        issuedAt = position.openedAtEpochMillis / 1_000L,
        // Open, by definition: a closed position is not in `PaperBook.positions` at all. The zone
        // therefore runs to the right-hand edge of the plot, empty slots included — which is the
        // true statement that it is open into the near future.
        closedAt = null,
        entryLabel = entryLabel,
        stopLabel = stopLabel,
        targetLabels = if (target != null) listOfNotNull(targetLabel) else emptyList(),
    )
}
