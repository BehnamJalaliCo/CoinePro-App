package com.coinepro.feature.search

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartReading
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.pageAccent
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import java.time.Instant
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * **The chart preview** — a market's shape, over any span, without leaving the list (run Ω3).
 *
 * ### The four seconds this exists to remove
 *
 * The sheet a long press opens answers «what is this one doing» with a price, a percentage and one
 * day of closes, and that was the whole of it: any other question — a week, a quarter, what it did
 * in spring — meant opening the chart, which is a route, a candle request and a full terminal
 * layout. On the connection this audience has that is four seconds, and four seconds is long enough
 * that most people do not ask.
 *
 * So the preview grew a chart of its own: six spans, a line you can run a finger along to read any
 * point on it, one line saying what the market is doing, and «چارت» still there for somebody who
 * wants the terminal. It is deliberately *not* the terminal — no candles, no studies, no drawings,
 * no axis. A preview that becomes a second chart is a second chart to maintain, and the reader who
 * wants those is one tap from the real one.
 *
 * ### Why it fetches, when the sheet's own note says it never does
 *
 * [MarketPreviewSheet] is built out of bytes already in memory, and that is still true of everything
 * above this block: the price, the pill and the day's line cost nothing. A *year* of a market's
 * history is not in memory and cannot be — it is not in the list, not in the quote and not in the
 * sparkline store. So the moment a reader taps a span, this asks for it, once, and keeps it for the
 * life of the sheet. The default span is the one the store already holds, which means opening the
 * sheet still costs no network at all; only a deliberate tap does.
 */
@Composable
internal fun MarketPreviewChart(
    symbol: String,
    /** The day of closes the list already has, drawn until — and unless — a span is asked for. */
    fallback: List<Double>,
    candles: MarketPreviewCandles,
    modifier: Modifier = Modifier,
) {
    val english = inEnglish()
    var range by remember(symbol) { mutableStateOf<PreviewRange?>(null) }
    var bars by remember(symbol) { mutableStateOf(emptyList<Candle>()) }
    var waiting by remember(symbol) { mutableStateOf(false) }
    var scrubbed by remember(symbol) { mutableStateOf<Int?>(null) }

    LaunchedEffect(symbol, range) {
        val span = range
        if (span == null) {
            bars = emptyList()
            return@LaunchedEffect
        }
        val held = candles.held(symbol, span)
        if (held != null) {
            bars = held
            return@LaunchedEffect
        }
        // Cleared first, so a span whose answer is slow shows the span's own emptiness rather than
        // the previous span's shape under the new chip — which reads as a year that looks exactly
        // like a week.
        bars = emptyList()
        waiting = true
        bars = candles.load(symbol, span)
        waiting = false
    }

    val closes = if (range == null) fallback else bars.map(Candle::c)
    val reading = remember(bars) { bars.takeIf { it.size >= 2 }?.let { ChartReading.of(CandleSeries(it)) } }
    val colour = when {
        closes.size < 2 -> CoineProColors.TextMuted
        closes.last() >= closes.first() -> CoineProColors.MarketUp
        else -> CoineProColors.MarketDown
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        // The readout sits *above* the line and is always present, at the same height, whether or
        // not a finger is down. A label that appears on touch pushes the line down as it arrives,
        // so the shape moves under the finger reading it.
        ScrubReadout(
            bars = bars,
            index = scrubbed,
            range = range,
            reading = reading,
            english = english,
            waiting = waiting,
        )
        PreviewLine(
            values = closes,
            colour = colour,
            scrubbed = scrubbed,
            onScrub = { scrubbed = it },
            scrubbable = bars.size >= 2,
        )
        PreviewRangeChips(selected = range, onSelect = { span ->
            scrubbed = null
            range = span
        })
    }
}

/**
 * One span of history, and the bar length it is drawn at.
 *
 * The six are the ones every finance app offers, and the lengths behind them are chosen so each span
 * comes out as roughly one to four hundred points: enough that the shape is the market's and not an
 * interpolation, few enough that a phone draws it in one pass. A day at half-hourly bars is four
 * smooth arcs — the complaint that made the watchlist's own line 48 points instead of 24 — and a
 * year at hourly bars is nine thousand points nobody can see.
 */
internal enum class PreviewRange(
    @get:StringRes val labelRes: Int,
    val timeframe: Timeframe,
    val bars: Int,
) {
    DAY(R.string.preview_range_day, Timeframe.M15, 96),
    WEEK(R.string.preview_range_week, Timeframe.H1, 168),
    MONTH(R.string.preview_range_month, Timeframe.H4, 180),
    QUARTER(R.string.preview_range_quarter, Timeframe.D1, 90),
    YEAR(R.string.preview_range_year, Timeframe.D1, 365),

    /**
     * As far back as the venue keeps, at weekly bars.
     *
     * Five hundred and forty weeks is a little past ten years, which is longer than either backend's
     * history for any instrument — so this asks for more than there is and draws whatever comes
     * back, which is exactly what «همه» should mean. A market listed last year draws one year.
     */
    ALL(R.string.preview_range_all, Timeframe.W1, 540),
}

/**
 * The spans, asked for once each and then held.
 *
 * ### Why a class and not a `suspend` parameter
 *
 * The cache is the point. A reader comparing a week against a month against a week again would
 * otherwise ask the server three times for two answers, and the two-way flip is the single most
 * likely thing anybody does with a row of span chips.
 *
 * Held for the life of the object, which the caller scopes: the shell builds one per candle feed, so
 * switching platform builds a new one and nothing stale survives the switch. Not persisted — a span
 * of history is cheap to re-ask once per run and a preview is not a place to keep a database.
 *
 * ### Why a failure is an empty list and not an exception
 *
 * Every caller is a composable drawing a shape. A symbol this venue does not carry, a span it has no
 * history for, a request that timed out — all three are «no line», which is a picture this sheet
 * already draws honestly. Nothing here is retried on its own: see [MarketPreviewChart], where
 * choosing the span again is the retry, and it is the reader's own tap rather than a loop.
 */
class MarketPreviewCandles(private val gateway: CandleGateway) {

    private val held = mutableMapOf<String, List<Candle>>()

    /** What is already in hand for this span, or null if it has never been asked for. */
    internal fun held(symbol: String, range: PreviewRange): List<Candle>? = held[key(symbol, range)]

    /** Asks for the span, once. Returns the bars oldest first, or empty where there are none. */
    internal suspend fun load(symbol: String, range: PreviewRange): List<Candle> {
        val key = key(symbol, range)
        held[key]?.let { return it }
        val bars = runCatching {
            gateway.load(symbol.uppercase(), range.timeframe, limit = range.bars)
                .candles
                .map { bar: OhlcBar -> Candle(t = bar.t, o = bar.o, h = bar.h, l = bar.l, c = bar.c, v = bar.v) }
        }.getOrNull().orEmpty()
        // A failure is not cached: the reader's retry is tapping the chip again, and a stored empty
        // would turn one bad moment on the network into a span that is empty for the whole session.
        if (bars.size >= 2) held[key] = bars
        return bars
    }

    private fun key(symbol: String, range: PreviewRange): String = "${symbol.uppercase()}@${range.name}"
}

/**
 * The six chips.
 *
 * No chip is selected when the sheet opens, and that is not a missing state: it means «the day the
 * list already had», which is the only span that costs nothing. Tapping [PreviewRange.DAY] asks for
 * the same day at a finer resolution, which is a real difference and is why the chip exists.
 */
@Composable
private fun PreviewRangeChips(selected: PreviewRange?, onSelect: (PreviewRange) -> Unit) {
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        for (range in PreviewRange.entries) {
            val lit = range == selected
            Text(
                text = stringResource(range.labelRes),
                style = MaterialTheme.typography.labelSmall.numeric(),
                color = if (lit) CoineProColors.pageAccentInk else CoineProColors.TextSecondary,
                modifier = Modifier
                    .weight(1f)
                    .clip(CoineProPillShape)
                    .background(if (lit) CoineProColors.pageAccent else CoineProColors.SurfaceElevated)
                    .clickable {
                        haptics.select()
                        onSelect(range)
                    }
                    .padding(vertical = CoineProSpacing.Half),
            )
        }
    }
}

/**
 * The line, and the finger on it.
 *
 * A stroke and a crosshair, on the same arithmetic [PreviewRange] chose the bar length for: the
 * series is scaled to its own extremes, because this sheet shows one instrument and a shared scale
 * would be a scale with nothing to share with.
 *
 * The gesture is a press-and-slide with no long press in front of it. The sheet has no horizontal
 * gesture of its own to compete with — it scrolls vertically and dismisses downward — so demanding a
 * hold first would be a toll on the one interaction this block exists for.
 */
@Composable
private fun PreviewLine(
    values: List<Double>,
    colour: Color,
    scrubbed: Int?,
    onScrub: (Int?) -> Unit,
    scrubbable: Boolean,
) {
    val haptics = rememberCoineProHaptics()
    val density = LocalDensity.current.density
    val grid = CoineProColors.BorderSubtle
    val crosshair = CoineProColors.TextSecondary
    var detent by remember { mutableStateOf(-1) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PLOT_HEIGHT)
            .then(
                if (!scrubbable || values.size < 2) {
                    Modifier
                } else {
                    Modifier.pointerInput(values.size) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            fun report(x: Float) {
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                val fraction = (x / width).coerceIn(0f, 1f)
                                onScrub((fraction * (values.size - 1)).roundToInt())
                                // One tick per detent rather than per bar. A year is three hundred
                                // and sixty-five bars across a phone, and a buzz per bar is a
                                // vibration, not feedback; twenty detents is a scale a thumb can
                                // feel regardless of how many points are behind it.
                                val step = floor(fraction * DETENTS).toInt()
                                if (step != detent) {
                                    detent = step
                                    haptics.select()
                                }
                            }
                            report(down.position.x)
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                event.changes.lastOrNull()?.let { report(it.position.x) }
                                pressed = event.changes.any { it.pressed }
                            }
                            detent = -1
                            onScrub(null)
                        }
                    }
                },
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(PLOT_HEIGHT)) {
            // Two rules, at a third and two thirds, and nothing else. They are what stops a line
            // floating in a void from looking like a decoration; a full grid on 96 dp would be more
            // grid than line.
            for (fraction in listOf(1f / 3f, 2f / 3f)) {
                val y = size.height * fraction
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = density)
            }
            if (values.size < 2) return@Canvas
            val high = values.max()
            val low = values.min()
            val span = (high - low).takeIf { it > 0.0 } ?: 1.0
            val step = size.width / (values.size - 1)
            fun pointAt(index: Int) = Offset(
                x = step * index,
                y = (size.height - PLOT_INSET) -
                    ((values[index] - low) / span).toFloat() * (size.height - PLOT_INSET * 2),
            )
            val path = Path().apply {
                moveTo(pointAt(0).x, pointAt(0).y)
                for (index in 1 until values.size) {
                    val point = pointAt(index)
                    lineTo(point.x, point.y)
                }
            }
            drawPath(path, colour, style = Stroke(width = LINE_WIDTH * density, cap = StrokeCap.Round))
            scrubbed?.coerceIn(values.indices)?.let { index ->
                val point = pointAt(index)
                drawLine(crosshair, Offset(point.x, 0f), Offset(point.x, size.height), strokeWidth = density)
                drawCircle(colour, radius = DOT_RADIUS * density, center = point)
            }
        }
    }
}

/**
 * What the finger is on, or what the market is doing when there is no finger.
 *
 * One row, one height, two jobs — and the second is the reason the row is always drawn. Without a
 * finger it is the reading: what the trend is doing, how strong it is, how much the instrument is
 * moving. With a finger it is the price and the moment under it. Neither is secondary text under a
 * control; both are the answer the reader came for.
 */
@Composable
private fun ScrubReadout(
    bars: List<Candle>,
    index: Int?,
    range: PreviewRange?,
    reading: ChartReading?,
    english: Boolean,
    waiting: Boolean,
) {
    val bar = index?.let { bars.getOrNull(it) }
    Row(
        modifier = Modifier.fillMaxWidth().height(READOUT_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        when {
            bar != null -> {
                Text(
                    text = BidiText.isolateLtr(MarketNumberFormatter.priceAuto(bar.c)),
                    style = MaterialTheme.typography.titleSmall.numeric(),
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = momentOf(bar.t, range),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
            waiting -> Text(
                text = stringResource(R.string.preview_range_waiting),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            reading != null -> Text(
                text = stringResource(
                    R.string.preview_reading,
                    reading.biasLabel(english),
                    reading.strengthLabel(english),
                    reading.volatilityLabel(english),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextSecondary,
            )
            else -> Unit
        }
    }
}

/**
 * A bar's own moment, at the resolution the bar has.
 *
 * A daily or weekly bar carries no useful clock — «۵ شهریور · 00:00» invites a reader to believe the
 * price was struck at midnight — so from a day upward the time is dropped and a year of them says
 * which year. The threshold is the *bar's spacing* and not the span, because that is what decides
 * whether a clock means anything at all.
 */
private fun momentOf(epochSeconds: Long, range: PreviewRange?): String {
    val instant = Instant.ofEpochSecond(epochSeconds)
    return when (range?.timeframe) {
        Timeframe.W1 -> PersianDateTime.dayWithYear(instant)
        Timeframe.D1 -> if (range == PreviewRange.YEAR) {
            PersianDateTime.dayWithYear(instant)
        } else {
            PersianDateTime.day(instant)
        }
        else -> PersianDateTime.moment(instant)
    }
}

/** The plot, and the two figures that keep the stroke off its own edge. */
private val PLOT_HEIGHT = 96.dp
private val READOUT_HEIGHT = 24.dp
private const val PLOT_INSET = 4f
private const val LINE_WIDTH = 1.8f
private const val DOT_RADIUS = 3f

/**
 * How many detents a thumb crosses from one end of the line to the other.
 *
 * Twenty, which on a 360 dp phone is a tick every 18 dp — about the spacing a thumb reads as
 * separate events rather than as a buzz. It is a property of the *hand*, not of the data, which is
 * why it is a constant here and not a function of the bar count.
 */
private const val DETENTS = 20
