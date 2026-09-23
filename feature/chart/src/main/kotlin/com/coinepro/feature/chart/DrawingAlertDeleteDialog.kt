package com.coinepro.feature.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProChoice
import com.coinepro.core.designsystem.CoineProChoiceDialog

/**
 * The one question the chart asks before deleting something.
 *
 * Drawn only where [ChartUiState.pendingDrawingDelete] is non-null, which is only where the
 * drawings about to go carry alerts. Everything else on this screen still deletes on the tap with
 * an undo behind it — the trade run Ω2 settled, and it is untouched.
 *
 * ### What each answer costs, which is why there are three
 *
 * * **Both** — the line and the alerts. The honest default for somebody who is finished with an
 *   idea, and the only one that leaves nothing behind.
 * * **The line, keeping the alerts** — for somebody about to redraw it. The alerts are then
 *   pointing at a drawing that is gone and **cannot fire**, so the note says so, and the alert
 *   centre marks each of them rather than letting them sit there looking armed.
 * * **Neither** — the bottom row, the back press and a tap outside. Nothing is written.
 *
 * The «both» row is the destructive one and it is still not the reflex answer: the dismiss is the
 * bottom row, nearest the thumb.
 */
@Composable
internal fun DrawingAlertDeleteDialog(
    pending: PendingDrawingDelete,
    onConfirm: (alsoAlerts: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val many = pending.drawingCount > 1
    val message = if (many) {
        stringResource(
            R.string.chart_drawing_alerts_many,
            pending.alertCount.toPersianDigits(),
            pending.drawingCount.toPersianDigits(),
        )
    } else {
        stringResource(R.string.chart_drawing_alerts_one, pending.alertCount.toPersianDigits())
    }
    CoineProChoiceDialog(
        title = stringResource(R.string.chart_drawing_alerts_title),
        message = message,
        choices = listOf(
            CoineProChoice(
                label = stringResource(
                    if (many) {
                        R.string.chart_drawing_alerts_delete_both_many
                    } else {
                        R.string.chart_drawing_alerts_delete_both
                    },
                ),
                destructive = true,
                onClick = { onConfirm(true) },
            ),
            CoineProChoice(
                label = stringResource(
                    if (many) R.string.chart_drawing_alerts_keep_many else R.string.chart_drawing_alerts_keep,
                ),
                note = stringResource(R.string.chart_drawing_alerts_keep_cost),
                onClick = { onConfirm(false) },
            ),
        ),
        dismissLabel = stringResource(R.string.chart_drawing_alerts_cancel),
        onDismiss = onDismiss,
    )
}
