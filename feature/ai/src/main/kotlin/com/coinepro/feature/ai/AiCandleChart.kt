package com.coinepro.feature.ai

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.aisignal.AiCandle
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.SignalOverlay

/**
 * The recent series the model reasoned over, with the setup's levels drawn across it.
 *
 * Deliberately not a trading chart: [CoineProChart] is asked for a static picture — no pan, no zoom,
 * no crosshair, no indicators. It exists so the entry, stop and targets can be seen against the
 * price action the model actually saw, which is the difference between a number on a card and a
 * claim a reader can check.
 *
 * It draws through the shared engine rather than its own canvas, which is what makes the setup band
 * here the *same* band as on the chart screen — same shading, same ordering, same rule that the
 * risk zone goes under the candles rather than tinting them. Two renderers for one setup is two
 * chances for them to disagree about which side of entry the loss is on.
 *
 * The evidence arrives in two shapes and this handles both. TradeYar timestamps its bars, so those
 * get a real time axis; CoinePro-FX sends twelve bars of open/high/low/close with no time at all,
 * and rather than invent a spacing the axis is dropped and the bars are laid out in order. The
 * price axis stays either way — the levels are the whole point of the picture.
 */
@Composable
internal fun AiCandleChart(
    candles: List<AiCandle>,
    entry: Double?,
    stopLoss: Double?,
    targets: List<Double>,
    isLong: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    if (candles.isEmpty()) return
    // A single bar has no series to speak of, and CandleSeries would happily draw it as one fat
    // candle filling the pane, which reads as data rather than as the absence of it.
    if (candles.size < 2) return

    val timed = candles.all { it.time != null }
    val series = remember(candles) {
        CandleSeries(
            candles.mapIndexed { index, candle ->
                Candle(
                    // The index stands in for time when the server sent none. It never reaches a
                    // reader: with no time axis and no crosshair, nothing formats it as a date.
                    t = candle.time ?: index.toLong(),
                    o = candle.open,
                    h = candle.high,
                    l = candle.low,
                    c = candle.close,
                )
            },
        )
    }
    // A setup needs somewhere to enter. Without one there is no band to draw and the bars are shown
    // on their own, which is still worth seeing.
    val overlay = entry?.takeIf { it.isFinite() }?.let {
        SignalOverlay(
            entry = it,
            stopLoss = stopLoss?.takeIf(Double::isFinite),
            takeProfits = targets.filter(Double::isFinite),
            isLong = isLong,
        )
    }

    CoineProChart(
        series = series,
        modifier = modifier.fillMaxWidth().height(height),
        decoration = ChartDecoration(signal = overlay, showTimeAxis = timed),
        interactive = false,
    )
}
