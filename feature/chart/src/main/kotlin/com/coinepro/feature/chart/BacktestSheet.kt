package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.coinepro.core.backtest.Backtest
import com.coinepro.core.chart.Candle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * Running one of three rules over the bars already on screen.
 *
 * Three named strategies rather than a script editor. The web terminal has `namescript` and a
 * server to run it on; this is the version that answers the question a reader actually asks — does
 * this idea survive the last thousand bars — without a language, a request, or a wait.
 *
 * The costs field is on the sheet rather than buried, because it is where the finding usually
 * lives: a rule that flips every few bars is a fortune at zero cost and ruinous at five basis
 * points, and a reader who cannot see the number cannot see that.
 */
@Composable
internal fun BacktestSheetBody(bars: List<Candle>, symbol: String) {
    var strategy by remember { mutableStateOf(Backtest.Strategy.MA_CROSS) }
    var costBasisPoints by remember { mutableStateOf(5) }

    val result = remember(bars, strategy, costBasisPoints) {
        Backtest.run(
            bars,
            Backtest.Settings(strategy = strategy, costFraction = costBasisPoints / 10_000.0),
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Backtest.Strategy.entries.forEach { option ->
                Chip(option.label(), option == strategy) { strategy = option }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            listOf(0, 2, 5, 10).forEach { points ->
                Chip(
                    // Latin, like every market figure: a reader compares this against an exchange's
                    // published fee schedule.
                    label = BidiText.isolateLtr("$points bp"),
                    selected = points == costBasisPoints,
                ) { costBasisPoints = points }
            }
        }

        if (result == null) {
            Text(
                text = "برای بک‌تست دست‌کم ${Backtest.MINIMUM_BARS.toPersianDigits()} کندل لازم است. کمی به عقب اسکرول کنید تا بارگذاری شود.",
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextMuted,
            )
            return@Column
        }

        EquityCurve(result.equity)

        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                Line("تعداد معامله", result.closed.toPersianDigits())
                result.winRate?.let {
                    Line(
                        "نرخ برد",
                        BidiText.isolateLtr(MarketNumberFormatter.price(it, 1) + "%"),
                        if (it >= 50) CoineProColors.Buy else CoineProColors.Sell,
                    )
                }
                result.totalReturnPercent?.let {
                    Line(
                        "بازده کل",
                        MarketNumberFormatter.signedPercent(it),
                        if (it >= 0) CoineProColors.Buy else CoineProColors.Sell,
                    )
                }
                Line(
                    // The number that decides position size, and the one a flattering backtest
                    // leaves out: peak to trough, not start to end.
                    "بیشترین افت",
                    BidiText.isolateLtr(MarketNumberFormatter.price(result.maxDrawdownPercent, 1) + "%"),
                    CoineProColors.Sell,
                )
                result.profitFactor?.let {
                    Line("ضریب سود", BidiText.isolateLtr(MarketNumberFormatter.price(it, 2)))
                }
            }
        }

        Text(
            text = "ورود در باز شدنِ کندلِ بعد از سیگنال حساب می‌شود، نه در بستهٔ همان کندل. فقط خرید — فروش استقراضی نیاز به قرض و نرخ فاندینگ دارد که یک سری کندل نمی‌داند. نتیجهٔ گذشته تضمین آینده نیست.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * The equity curve.
 *
 * One line, no axis, no grid. It is here to answer one question at a glance — did this climb or did
 * it bleed — and everything else on the sheet is the precise version of the same answer.
 */
@Composable
private fun EquityCurve(points: List<Double>) {
    if (points.size < 2) return
    val low = points.min()
    val high = points.max()
    val span = (high - low).takeIf { it > 0 } ?: 1.0
    val rising = points.last() >= points.first()
    val ink = if (rising) CoineProColors.Buy else CoineProColors.Sell
    // Resolved outside the draw scope: a DrawScope is not a composable scope, and the palette is
    // read through a CompositionLocal.
    val baselineInk = CoineProColors.Border

    Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * index / (points.size - 1)
            val y = size.height - (size.height * ((value - low) / span)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, ink, style = Stroke(width = 2.dp.toPx()))
        // The starting stake, so a curve that ends above the line is visibly a profit rather than
        // just a shape that goes up.
        val baseline = size.height - (size.height * ((1.0 - low) / span)).toFloat()
        drawLine(
            baselineInk,
            Offset(0f, baseline),
            Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

@Composable
private fun Line(label: String, value: String, colour: Color = CoineProColors.TextPrimary) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colour)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(if (selected) CoineProColors.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

private fun Backtest.Strategy.label(): String = when (this) {
    Backtest.Strategy.MA_CROSS -> "تقاطع میانگین"
    Backtest.Strategy.RSI_REVERSION -> "بازگشت RSI"
    Backtest.Strategy.BREAKOUT -> "شکست کانال"
}
