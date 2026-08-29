package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.ChartReading
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPress
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.onPageAccent
import com.coinepro.core.designsystem.pageAccent
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.pressScale
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.ChartInterval
import kotlin.math.abs

/**
 * The chart page's chrome: one control band, one panel that reads the market, one way to the studio.
 *
 * ### What was wrong
 *
 * The page under the plot was six stacked bands — a timeframe strip, a range strip, a full-width
 * date field, a scrolling row of nine unlabelled icons, three reading cards and a studio card —
 * each with the same weight and none grouped. The plot itself sat in a bordered card and got under
 * half the glass. The owner's verdict on it was five out of a hundred, and the parts were never the
 * problem: every control on that page works and most of them took two waves of work to become
 * reachable at all. The composition was the problem.
 *
 * So this file is a triage of the same controls by *how often a hand reaches for one*, and nothing
 * else:
 *
 * * **Every session** — the bar length, the drawing tools, the studies, the chart type, the object
 *   list, the way to a full screen. These live in [ChartCommandBand], one object directly under the
 *   plot, permanently visible, every control labelled.
 * * **Once a month** — the span of history, «رفتن به تاریخ», a second instrument, the price axis,
 *   layouts, an alert, a backtest, a screenshot. These live one tap behind the band's «بیشتر», in
 *   [ChartMoreSheetBody], with a word each saying what they do.
 *
 * Nothing was removed. `ChartScreen`'s own report says where each control went.
 *
 * ### Why the accent is the page's and not gold
 *
 * `CoineProPageAccent` calls a gold selection on an analysis screen the bug it is meant to prevent,
 * and the chart route already declares `PageAccent.ANALYSIS`. The strip was gold because nothing
 * had asked the question. Reading the accent off the page also means the one genuinely gold object
 * left on the screen — the drawn setup's card — is again the only one, which is the surface rule
 * this design system is built on.
 */

/**
 * The band: bar length above, the six controls a session actually uses below.
 *
 * ### Why it is one object and not two rows
 *
 * It has one ground, one radius and one pair of hairlines, so a reader sees a toolbar attached to
 * the plot rather than two more of the six bands this page used to be. Inside it the two tiers
 * carry different kinds of decision and are drawn differently on purpose — the lengths are filled
 * keys, the controls are labelled glyphs — which is what stops them reading as one undifferentiated
 * wall of pills the way the timeframe and range strips used to.
 *
 * ### Why the labels are back
 *
 * Nine unlabelled glyphs in a scrolling row is a row nobody learns. Six labelled ones fit a phone
 * without scrolling, and a label is what makes an *active* state legible: «اندیکاتور ۴» says both
 * what the control is and what is on behind it, which no badge on a bare icon has ever managed.
 */
@Composable
internal fun ChartCommandBand(
    interval: ChartInterval,
    /** The bar lengths pinned to the strip. See [TimeframeFavourites]. */
    starred: List<String>,
    onSelectInterval: (ChartInterval) -> Unit,
    onMoreIntervals: () -> Unit,
    /** Whether a drawing tool is armed — the state that changes what the next tap on the plot does. */
    armedTool: Boolean,
    indicators: Int,
    drawings: Int,
    onOpen: (ChartSheet) -> Unit,
    onFullscreen: () -> Unit,
    onMore: () -> Unit,
    /**
     * Whether anything behind «بیشتر» is off its default — a span chosen, an instrument compared,
     * an axis adjusted.
     *
     * Without it the sheet would swallow every state it holds, and a reader who set the chart to a
     * year would have nothing on screen telling them so.
     */
    moreActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Surface)) {
        IntervalRow(
            selected = interval,
            onSelect = onSelectInterval,
            onMore = onMoreIntervals,
            starred = starred,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Six points, not eight: each button carries two of its own inside the ripple, so
                // this is what lines the glyph row up with the length keys in the tier above.
                .padding(start = 6.dp, end = 6.dp, bottom = CoineProSpacing.Half),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BandButton(
                icon = DesignR.drawable.tv_pencil,
                label = stringResource(R.string.chart_band_draw),
                armed = armedTool,
                onClick = { onOpen(ChartSheet.TOOLS) },
                modifier = Modifier.weight(1f),
            )
            BandButton(
                icon = DesignR.drawable.icon_sliders_horizontal,
                label = stringResource(R.string.chart_band_studies),
                count = indicators,
                onClick = { onOpen(ChartSheet.INDICATORS) },
                modifier = Modifier.weight(1f),
            )
            BandButton(
                icon = DesignR.drawable.tv_chart_candles,
                label = stringResource(R.string.chart_band_type),
                onClick = { onOpen(ChartSheet.TYPE) },
                modifier = Modifier.weight(1f),
            )
            BandButton(
                icon = DesignR.drawable.tv_tool_cursor,
                label = stringResource(R.string.chart_band_objects),
                count = drawings,
                onClick = { onOpen(ChartSheet.DRAWINGS) },
                modifier = Modifier.weight(1f),
            )
            BandButton(
                icon = DesignR.drawable.tv_maximize2,
                label = stringResource(R.string.chart_band_fullscreen),
                onClick = onFullscreen,
                modifier = Modifier.weight(1f),
            )
            BandButton(
                icon = DesignR.drawable.tv_more_horizontal,
                label = stringResource(R.string.chart_band_more),
                marked = moreActive,
                onClick = onMore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One labelled control on the band, in one of four states a reader can tell apart without reading.
 *
 * * **Armed** ([armed]) — a tinted block with a tinted hairline. Only the drawing control takes it,
 *   because it is the only one whose state changes what the *next tap on the plot* does, and that
 *   is worth the strongest treatment on the row.
 * * **Active** — a count above zero, or [marked]. The glyph, the label and the figure all take the
 *   page's accent, so «۴ اندیکاتور روشن است» is answered from across the room.
 * * **Idle** — neutral ink on nothing.
 * * **Unavailable** — [onClick] null: disabled ink and no ripple. A control that cannot be pressed
 *   must not look like one that can, and must not merely be absent either, or the reader spends the
 *   session looking for it.
 */
@Composable
private fun BandButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    armed: Boolean = false,
    marked: Boolean = false,
    count: Int = 0,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    val enabled = onClick != null
    val active = armed || marked || count > 0
    val ink = when {
        !enabled -> CoineProColors.TextDisabled
        active -> CoineProColors.pageAccentInk
        else -> CoineProColors.TextSecondary
    }
    Column(
        modifier = modifier
            .pressScale(interaction, CoineProPress.CHIP)
            .clip(CoineProShapes.small)
            .background(
                if (armed) {
                    CoineProTint.fill(CoineProColors.pageAccentInk, CoineProColors.Surface)
                } else {
                    Color.Transparent
                },
            )
            .then(
                if (armed) {
                    Modifier.border(
                        width = 1.dp,
                        color = CoineProTint.edge(CoineProColors.pageAccentInk),
                        shape = CoineProShapes.small,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(interaction, null, enabled = enabled) {
                haptics.select()
                onClick?.invoke()
            }
            .heightIn(min = BAND_BUTTON_HEIGHT)
            .padding(vertical = CoineProSpacing.Half, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(BAND_GLYPH),
            )
            // Zero is drawn as nothing rather than as «۰»: a nought says what an absent badge says
            // and costs a glyph to read. A prose count, so Persian digits.
            if (count > 0) {
                Text(
                    text = count.toPersianDigits(),
                    style = MaterialTheme.typography.labelMedium,
                    color = ink,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active && enabled) ink else CoineProColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * The span of history, as chips that cannot be mistaken for the bar-length keys above.
 *
 * ### Why they had to stop looking alike
 *
 * «M15» and «۱ ماه» were two rows of the same outlined pill, one directly under the other, and they
 * answer completely different questions: how long one candle is, and how much history is in front
 * of you. A reader could not tell them apart without reading both rows, which is the failure a
 * control row is supposed to prevent.
 *
 * So they differ on every axis available. Shape: a rounded rectangle against the keys' capsule.
 * Weight: an outline against a fill. Script: Persian prose durations against Latin wire spellings.
 * Position: behind «بیشتر», because a span is chosen once a session at most while a length is
 * changed constantly.
 *
 * See [ChartRange] for why tapping one also changes the bar length.
 */
@Composable
internal fun RangeChipRow(
    selected: ChartRange?,
    onSelect: (ChartRange) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = CoineProSpacing.Two),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        contentPadding = contentPadding,
    ) {
        items(ChartRange.OFFERED, key = { it.name }) { range ->
            val active = range == selected
            val ink = if (active) CoineProColors.pageAccentInk else CoineProColors.TextMuted
            Box(
                modifier = Modifier
                    .clip(CoineProShapes.extraSmall)
                    .background(
                        if (active) {
                            CoineProTint.fill(CoineProColors.pageAccentInk, CoineProColors.Surface)
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (active) {
                            CoineProTint.edge(CoineProColors.pageAccentInk)
                        } else {
                            CoineProColors.Border
                        },
                        shape = CoineProShapes.extraSmall,
                    )
                    .clickable { onSelect(range) }
                    .heightIn(min = RANGE_CHIP_HEIGHT)
                    .widthIn(min = RANGE_CHIP_WIDTH)
                    .padding(horizontal = CoineProSpacing.One),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // Persian prose durations, so they are never forced Latin the way a wire
                    // spelling is.
                    text = range.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ink,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * What this market is doing, and why it reads that way.
 *
 * ### Why it stopped being three cards like everything else
 *
 * Trend strength, volatility and bias are the answer to the only question a reader opens a chart
 * with, and they were styled exactly like the studio card, the setup card and every pill on the
 * page — same radius, same surface, same weight. Given equal weight with a row of sheet-openers,
 * the most valuable thing on the page read as the least.
 *
 * Now it is one panel with a heading that says what it is for, and each reading carries three
 * things instead of one word: the word, a bar showing where the value sits in its own scale, and
 * the figure the word came from. «قوی» alone is an assertion; «قوی · ADX 34 · a bar two-thirds
 * along» is a reading somebody can disagree with, which is the difference between an indicator and
 * a decoration.
 *
 * The figures are the app's own arithmetic — see `ChartReading`, which computes all three from the
 * bars on screen — so nothing here is a second opinion about the same series.
 */
@Composable
internal fun ChartReadingsPanel(reading: ChartReading, modifier: Modifier = Modifier) {
    CoineProCard(
        modifier = modifier.fillMaxWidth(),
        shape = CoineProShapes.medium,
        contentPadding = PaddingValues(
            horizontal = CoineProSpacing.Two,
            vertical = CoineProSpacing.OneHalf,
        ),
    ) {
        Text(
            text = stringResource(R.string.chart_reading_title),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CoineProSpacing.OneHalf),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            ReadingColumn(
                label = stringResource(R.string.chart_reading_strength),
                value = reading.strengthLabel,
                // ADX runs 0..100 in theory and 0..50 in practice; fifty is the top of the scale a
                // reader ever sees, so it is what the bar is measured against.
                fraction = (reading.strength / ADX_FULL_SCALE).toFloat(),
                tone = reading.strengthColour(),
                why = BidiText.isolateLtr("ADX " + reading.strength.asFigure(0)),
                modifier = Modifier.weight(1f),
            )
            ReadingColumn(
                label = stringResource(R.string.chart_reading_volatility),
                value = reading.volatilityLabel,
                fraction = reading.volatility.toFloat(),
                tone = CoineProColors.TextPrimary,
                why = stringResource(
                    R.string.chart_reading_volatility_why,
                    BidiText.isolateLtr((reading.volatility * PERCENT).asFigure(0) + "%"),
                ),
                modifier = Modifier.weight(1f),
            )
            ReadingColumn(
                label = stringResource(R.string.chart_reading_bias),
                value = reading.biasLabel,
                // Half a percent of price is a wide separation between a twenty and a fifty
                // average; past it the bar is simply full and the word has already said so.
                fraction = (abs(reading.bias) / BIAS_FULL_SCALE).toFloat(),
                tone = reading.biasColour(),
                why = stringResource(
                    R.string.chart_reading_bias_why,
                    BidiText.isolateLtr(
                        (if (reading.bias > 0) "+" else "") +
                            (reading.bias * PERCENT).asFigure(2) + "%",
                    ),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One reading: the word, where it sits, and the figure it came from. */
@Composable
private fun ReadingColumn(
    label: String,
    value: String,
    fraction: Float,
    tone: Color,
    why: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        ReadingMeter(
            fraction = fraction,
            tone = tone,
            modifier = Modifier.padding(top = CoineProSpacing.One),
        )
        Text(
            text = why,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/**
 * Where one reading sits on its own scale.
 *
 * Four points tall and drawn, not animated: it is a fact about the bars on screen, and a fact that
 * slides into place every time a pan changes the window would be motion reporting nothing. It fills
 * from the start edge, which in this app's default locale is the right one — the same direction the
 * label above it is read in.
 */
@Composable
private fun ReadingMeter(fraction: Float, tone: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(METER_HEIGHT)
            .clip(CoineProPillShape)
            .background(CoineProColors.SurfaceElevated),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CoineProPillShape)
                .background(tone),
        )
    }
}

/**
 * Everything a reader touches about once a month, behind the band's one «بیشتر».
 *
 * ### Why these and not others
 *
 * The split is by reach, not by importance. A span of history, a date to jump to, a second
 * instrument, the axis, a saved layout, an alert, a backtest and a screenshot are all things
 * somebody does deliberately, having decided to; the bar length and the drawing tools are things
 * they do while reading. The first set cost this page four permanent bands — one of them a
 * full-width text field for a control used a few times a year — and they cost one tap now.
 *
 * Each row says what it does in a sentence, which the icon row they came from never did, and a row
 * with nothing behind it is drawn disabled rather than hidden: a reader who cannot find «بک‌تست»
 * because this chart has no bars yet will hunt for it, while one who can see it greyed knows to
 * come back.
 */
@Composable
internal fun ChartMoreSheetBody(
    range: ChartRange?,
    onSelectRange: (ChartRange) -> Unit,
    /** The bars «رفتن به تاریخ» resolves against. Empty leaves the field out — there is nothing to find. */
    bars: List<Candle>,
    onGoToDate: (Int) -> Unit,
    comparisons: Int,
    /** What the price axis is measuring now, so the row can say it without being opened. */
    scaleLabel: String,
    /** Whether the axis is off its defaults at all — inverted, locked, or a pinned precision. */
    scaleAdjusted: Boolean,
    onOpen: (ChartSheet) -> Unit,
    onCreateAlert: (() -> Unit)?,
    onBacktest: (() -> Unit)?,
    onShare: () -> Unit,
    onOpenStudio: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            SheetLabel(stringResource(R.string.chart_more_span))
            Text(
                text = stringResource(R.string.chart_more_span_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        RangeChipRow(
            selected = range,
            onSelect = onSelectRange,
            contentPadding = PaddingValues(horizontal = CoineProSpacing.Gutter),
        )

        if (bars.isNotEmpty()) {
            HorizontalDivider(color = CoineProColors.Border)
            Column(
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                SheetLabel(stringResource(R.string.chart_more_goto))
                GoToDateField(bars = bars, onGoTo = onGoToDate)
            }
        }

        HorizontalDivider(color = CoineProColors.Border)

        MoreRow(
            icon = DesignR.drawable.tv_chart_line,
            title = stringResource(R.string.chart_more_compare),
            note = stringResource(R.string.chart_more_compare_note),
            count = comparisons,
            onClick = { onOpen(ChartSheet.COMPARE) },
        )
        MoreRow(
            icon = DesignR.drawable.tv_chart_percent,
            title = stringResource(R.string.chart_more_scale),
            note = scaleLabel,
            marked = scaleAdjusted,
            onClick = { onOpen(ChartSheet.SCALE) },
        )
        MoreRow(
            icon = DesignR.drawable.icon_bookmark_simple,
            title = stringResource(R.string.chart_more_layouts),
            note = stringResource(R.string.chart_more_layouts_note),
            onClick = { onOpen(ChartSheet.LAYOUTS) },
        )
        MoreRow(
            icon = DesignR.drawable.tv_bell,
            title = stringResource(R.string.chart_more_alert),
            note = stringResource(R.string.chart_more_alert_note),
            onClick = onCreateAlert,
        )
        MoreRow(
            icon = DesignR.drawable.tv_play,
            title = stringResource(R.string.chart_more_backtest),
            note = stringResource(R.string.chart_more_backtest_note),
            onClick = onBacktest,
        )
        MoreRow(
            icon = DesignR.drawable.icon_camera,
            title = stringResource(R.string.chart_more_share),
            note = stringResource(R.string.chart_more_share_note),
            onClick = onShare,
        )
        MoreRow(
            icon = DesignR.drawable.tv_layout_grid,
            title = stringResource(R.string.chart_more_studio),
            note = stringResource(R.string.chart_more_studio_note),
            onClick = onOpenStudio,
        )
    }
}

/** One row in the «بیشتر» sheet: a glyph, what it does, and whether it can be pressed at all. */
@Composable
private fun MoreRow(
    @DrawableRes icon: Int,
    title: String,
    note: String,
    onClick: (() -> Unit)?,
    count: Int = 0,
    marked: Boolean = false,
) {
    val enabled = onClick != null
    val active = enabled && (marked || count > 0)
    val ink = when {
        !enabled -> CoineProColors.TextDisabled
        active -> CoineProColors.pageAccentInk
        else -> CoineProColors.TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick?.invoke() }
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(MORE_GLYPH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) CoineProColors.TextPrimary else CoineProColors.TextDisabled,
            )
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) CoineProColors.TextMuted else CoineProColors.TextDisabled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (count > 0) {
            Text(
                text = count.toPersianDigits(),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.onPageAccent,
                modifier = Modifier
                    .clip(CoineProPillShape)
                    .background(CoineProColors.pageAccent)
                    .padding(horizontal = CoineProSpacing.One, vertical = 1.dp),
            )
        }
    }
}

/**
 * The one way from the reading page into the working one.
 *
 * A single row rather than the three-control card it was. The camera and the backtest that used to
 * sit beside it are in the «بیشتر» sheet with the rest of the once-a-month work, which leaves this
 * as what it always should have been: a labelled door with the state of the chart written on it.
 */
@Composable
internal fun StudioRow(
    /** «۴ اندیکاتور · ۲ ترسیم», or what the studio holds when nothing is on. See `studioSummary`. */
    summary: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(CoineProColors.Surface)
            .clickable(onClick = onOpen)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_layout_grid),
            contentDescription = null,
            tint = CoineProColors.TextSecondary,
            modifier = Modifier.size(MORE_GLYPH),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.chart_more_studio),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(CoineProIcons.ChevronForward),
            contentDescription = null,
            tint = CoineProColors.TextMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * A double rendered for a reader rather than for a machine.
 *
 * `String.format` follows the default locale, which on this app's Persian default prints «۳۴» — and
 * every figure in this panel is a market figure, which this product writes in Latin digits so it
 * can be held against a broker statement. `Locale.US` is the whole of the fix and it has to be said
 * at every call site that formats a number for display.
 */
private fun Double.asFigure(decimals: Int): String =
    String.format(java.util.Locale.US, "%." + decimals + "f", this)

/** ADX's practical ceiling. Above fifty the reading is «قوی» and the bar has nothing left to say. */
private const val ADX_FULL_SCALE = 50.0

/** Half a percent of price between the two averages: a wide separation on any instrument. */
private const val BIAS_FULL_SCALE = 0.005

private const val PERCENT = 100.0

/** Comfortably past the 44dp target, with room for a glyph over a label. */
private val BAND_BUTTON_HEIGHT = 50.dp

private val BAND_GLYPH = 19.dp

private val MORE_GLYPH = 20.dp

/** The reading bar. Four points: read as a measure, not as a rule. */
private val METER_HEIGHT = 4.dp

/** A range chip, sized for a thumb rather than for its two Persian words. */
private val RANGE_CHIP_HEIGHT = 36.dp
private val RANGE_CHIP_WIDTH = 56.dp
