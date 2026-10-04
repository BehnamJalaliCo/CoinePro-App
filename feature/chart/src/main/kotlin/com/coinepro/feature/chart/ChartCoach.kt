package com.coinepro.feature.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.coinepro.core.chart.ChartCoachAnchors
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.designsystem.CoachAnchor
import com.coinepro.core.designsystem.CoachTip
import com.coinepro.core.designsystem.LocalCoachHost

/**
 * The chart's anchors for the coach, made only where a coach is listening (5.21.0).
 *
 * Null outside the app's teaching host — a preview, a screenshot, a test — so the renderer reports
 * nothing and no tip can be asked for.
 */
@Composable
internal fun rememberChartCoach(): ChartCoachAnchors? {
    val host = LocalCoachHost.current
    return remember(host) { host?.let { ChartCoachAnchors() } }
}

/**
 * Which of the chart's tips may be asked for right now, and where each points.
 *
 * Every tip here waits for its *moment*: the reading gestures only on a chart with bars and nothing
 * armed or selected, the drawing cursor only once a tool that places points is armed, the handles
 * only once a drawing is selected, the legend only with a study on, the event marks only with
 * marks on the chart. The coach picks one at a time, in [CoachTip]'s order.
 */
@Composable
internal fun ChartCoachRequests(
    anchors: ChartCoachAnchors?,
    hasBars: Boolean,
    drawing: DrawingState,
    drawingMode: Boolean,
    canAlert: Boolean,
    hasAlerts: Boolean,
    hasEvents: Boolean,
    hasIndicators: Boolean,
) {
    anchors ?: return
    val reading = hasBars && !drawingMode && drawing.tool == null && drawing.selectedId == null
    CoachAnchor(CoachTip.CHART_PINCH, anchors.plot, reading)
    CoachAnchor(CoachTip.CHART_AXIS, anchors.gutter, reading)
    CoachAnchor(CoachTip.CHART_HOLD, anchors.plot, reading)
    CoachAnchor(CoachTip.CHART_DOUBLE, anchors.plot, reading)
    CoachAnchor(CoachTip.GUTTER_PLUS, anchors.gutter, reading && canAlert)
    CoachAnchor(CoachTip.ALERT_LINE, anchors.plot, reading && hasAlerts)
    CoachAnchor(CoachTip.AXIS_MINIS, anchors.minis, reading)
    CoachAnchor(CoachTip.REALTIME, anchors.realtime, reading)
    CoachAnchor(CoachTip.LEGEND, anchors.legend, reading && hasIndicators)
    CoachAnchor(CoachTip.CHART_EVENTS, anchors.events, reading && hasEvents)
    val armed = drawing.tool
    CoachAnchor(CoachTip.DRAW_CURSOR, anchors.plot, hasBars && armed != null && armed.points > 0)
    CoachAnchor(CoachTip.DRAW_HANDLE, anchors.plot, hasBars && drawing.selectedId != null && armed == null)
}
