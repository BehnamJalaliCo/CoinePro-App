package com.coinepro.feature.chart

import com.coinepro.core.notifications.LocalPriceAlert
import kotlinx.coroutines.flow.Flow

/**
 * The alerts a drawing carries, as the chart is allowed to know them.
 *
 * ### Why the chart knows about alerts at all, and how little it knows
 *
 * It does not run them and it never will: `core:notifications` deliberately does not depend on
 * `core:chart` and the reverse is just as deliberate — a drawing engine has no business evaluating
 * a notification, and the day the two had separate geometries a reader would be told about a touch
 * that is not on the chart in front of them. That argument is written out in
 * `GuestAlertMarketSource`, which is the one seam where the two meet.
 *
 * This is a narrower thing. Deleting a drawing **destroys** an alert somebody configured, silently:
 * the evaluator can no longer resolve the line's level, so the alert stays in the centre looking
 * armed and can never fire again. The chart is the only place that can ask before that happens,
 * because it is the only place the deletion is initiated from. So it holds a list it can count and
 * two verbs it can call, and nothing that could tempt it into deciding when an alert fires.
 *
 * Null everywhere it is optional: a build without one deletes drawings exactly as it did before,
 * which keeps every existing screenshot test and the `:chart-ui` preview host working.
 */
interface DrawingAlerts {

    /** Every alert this phone holds, as it changes. Small by construction — `MAX_ALERTS` of them. */
    val alerts: Flow<List<LocalPriceAlert>>

    /** Forgets these alerts. Called only after the reader has said to. */
    suspend fun remove(alerts: List<LocalPriceAlert>)

    /**
     * Puts them back, unchanged.
     *
     * The undo beside a deleted drawing restores the drawing from the chart's own history; without
     * this the alerts that went with it would not come back, and «واگرد» would be a promise the app
     * only half keeps.
     */
    suspend fun restore(alerts: List<LocalPriceAlert>)
}

/**
 * A drawing deletion waiting on the reader, because it would take alerts with it.
 *
 * ### Why this exists and the ordinary delete does not ask
 *
 * Run Ω2 settled that deleting a drawing is one tap with no question, and paid for it with an undo:
 * a line takes two seconds to draw, so a confirmation costs more than the mistake does. That trade
 * is still right and it is untouched here — [ChartUiState.pendingDrawingDelete] is null for a
 * drawing nothing watches, and the delete happens on the spot as it always has.
 *
 * An alert is not a two-second artefact. The reader chose a condition, a repeat policy, a
 * timeframe and a channel, and none of that is on the chart to be seen. So the question is asked
 * exactly where the cost is real, and nowhere else.
 */
data class PendingDrawingDelete(
    /** The drawings about to go. More than one when the reader deleted a selection. */
    val drawingIds: List<Long>,
    /** The alerts that watch them. Never empty — with none there is nothing to ask about. */
    val alerts: List<LocalPriceAlert>,
) {
    /** How many alerts are at stake. The number the sentence prints. */
    val alertCount: Int get() = alerts.size

    /** How many drawings the reader is deleting. One for the common case. */
    val drawingCount: Int get() = drawingIds.size
}
