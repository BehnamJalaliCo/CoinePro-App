package com.coinepro.feature.chart

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.CoineProChart

/**
 * The reader's own chart, drawn read-only beside something else.
 *
 * ### What it is for
 *
 * The NamaScript studio's split view (run I item 0.2). Its right half used to be a sandbox — two
 * hundred bars of the symbol with nothing else on them — and the owner's note names that as the
 * defect: «پیش‌نمایش باید اختیاری باشد و مقصد پیش‌فرض، چارت اصلی». So the right half is now *this*,
 * the same controller the chart screen reads, which means the reader watches their script appear
 * next to their EMA, on their timeframe, with their drawings, and the sandbox is a chip away.
 *
 * ### Why it is read-only
 *
 * No gestures, no crosshair, no toolbar. A second interactive chart on the same controller would
 * give one state two sets of hands: a pan here would move the chart behind the studio, and an
 * indicator armed there would appear here mid-edit. What a writer needs from this half is to *see*
 * the effect of the line they just typed, and seeing is the whole contract.
 */
@Composable
fun ChartMirror(controller: ChartController, modifier: Modifier = Modifier) {
    val state by controller.state.collectAsStateWithLifecycle()
    CoineProChart(
        series = state.visibleSeries,
        modifier = modifier,
        interactive = false,
        decoration = ChartDecoration(
            colours = state.chartColours,
            overlays = state.overlays,
            levels = state.levels,
            markers = state.markers,
            panes = state.panes,
            drawings = state.canvasDrawing.drawings,
            comparisons = state.comparisons,
            comparisonBasis = state.comparisonBasis,
            // The countdown belongs to the chart the reader is trading from, not to a copy of it
            // in an editor: two of them ticking on one screen is one too many clocks.
            showCountdown = false,
        ),
    )
}
