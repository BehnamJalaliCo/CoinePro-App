package com.coinepro.app.watch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.app.brief.BriefSparklineShape
import com.coinepro.core.chart.ChartWatch
import com.coinepro.core.chart.WatchSnapshot
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.NumberStyle
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.numeric
import kotlinx.coroutines.delay

/**
 * The chart, in a window about the size of a stamp — run Τ2, B8.
 *
 * ### What is on it, and what is deliberately not
 *
 * Four facts and a line: which market, what it costs, which way it has gone, and how long this
 * candle has left. Nothing else fits, and more importantly nothing else is *readable* — a picture-
 * in-picture window is about a hundred and fifty points across and the reader is looking at
 * something else. No axes, no legend, no indicators, no controls: a tap anywhere on the window is
 * Android's own «go back to the app», and a button in here would be competing with it.
 *
 * ### Why this reads a store and not a chart
 *
 * The chart's composition may be gone. A reader who popped the window out and then went home has
 * left the whole navigation graph behind, and the window is still there. See [ChartWatchStore]:
 * the chart pushes what it knows, this draws it, and nothing in between has to stay alive.
 */
@Composable
fun WatchWindow(snapshot: WatchSnapshot?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(WINDOW_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        if (snapshot == null) {
            // Nothing has been published yet — the window opened before the first snapshot, or the
            // reader came back to one that was cleared. A blank stage rather than a spinner: a
            // spinner in a window this size is a moving object in the corner of somebody's eye,
            // which is the one thing this mode must not be.
            return@Box
        }
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // The ticker, isolated: a Latin run inside a right-to-left layout.
                    text = BidiText.isolateLtr(snapshot.symbol),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = BidiText.isolateLtr(snapshot.intervalWire.uppercase()),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    maxLines = 1,
                )
            }
            Text(
                // A market figure, so Latin digits and the numeric style — the working agreement's
                // rule, and the reason this window has no prose count on it at all.
                text = BidiText.isolateLtr(NumberStyle.grouped(snapshot.price, PRICE_DECIMALS)),
                style = MaterialTheme.typography.titleMedium.numeric(),
                color = CoineProColors.TextPrimary,
                maxLines = 1,
            )
            snapshot.changePercent?.let { change ->
                Text(
                    text = BidiText.isolateLtr(
                        (if (change > 0) "+" else "") + NumberStyle.percent(change, CHANGE_DECIMALS),
                    ),
                    style = MaterialTheme.typography.labelSmall.numeric(),
                    color = CoineProColors.marketMove(change),
                    maxLines = 1,
                )
            }
            WatchLine(snapshot)
            WatchCountdown(snapshot)
        }
    }
}

/**
 * The shape of the tail, with no axes under it.
 *
 * `BriefSparklineShape` again rather than a second normaliser: the morning brief's picture and this
 * one answer the same question about a list of closes, and two copies of «where does this point
 * land when the span is zero» is two chances to disagree. It is already tested there.
 */
@Composable
private fun WatchLine(snapshot: WatchSnapshot) {
    val shape = BriefSparklineShape.ofCloses(snapshot.closes) ?: return
    // The market's own two colours, first against last — the sparkline's rule, unchanged. A tail
    // that fell all the way and bounced on the final close is a tail that fell.
    val colour = if (shape.rising) CoineProColors.MarketUp else CoineProColors.MarketDown
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(LINE_HEIGHT)
            .padding(top = CoineProSpacing.Half),
    ) {
        val points = shape.points.map { Offset(it.x * size.width, it.y * size.height) }
        for (index in 1 until points.size) {
            drawLine(
                color = colour,
                start = points[index - 1],
                end = points[index],
                strokeWidth = LINE_STROKE.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * «0:45» — how long this candle has left.
 *
 * Latin digits, because it is a figure read at a glance against a clock. The working agreement's
 * rule, and the same one `ArenaBar`'s clock follows.
 *
 * Its own ticker rather than a field on the snapshot, because a countdown changes every second and
 * the snapshot is published at most once a second **only when something else changed**. Reading the
 * clock here is one integer per second in a window that is already awake.
 *
 * Nothing at all once the close has passed. See `ChartWatch.countdownSeconds`: a stuck «0:00» is
 * worse than no line, and it was a real bug on the chart's own live tag.
 */
@Composable
private fun WatchCountdown(snapshot: WatchSnapshot) {
    val left by produceState<Int?>(initialValue = null, key1 = snapshot.barClosesAtEpochSeconds) {
        while (true) {
            value = ChartWatch.countdownSeconds(
                snapshot.barClosesAtEpochSeconds,
                System.currentTimeMillis() / 1_000L,
            )
            delay(1_000L)
        }
    }
    val seconds = left ?: return
    Text(
        text = BidiText.isolateLtr("${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"),
        style = MaterialTheme.typography.labelSmall.numeric(),
        color = CoineProColors.TextMuted,
        maxLines = 1,
    )
}

private val WINDOW_PADDING = 8.dp
private val LINE_STROKE = 1.5.dp

/** Enough to read a shape in, not enough to crowd out the price above it. */
private val LINE_HEIGHT = 22.dp

/** Two places, which is what a window this size can carry without the number wrapping. */
private const val PRICE_DECIMALS = 2
private const val CHANGE_DECIMALS = 2
