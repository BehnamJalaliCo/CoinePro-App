package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import com.coinepro.core.designsystem.LtrDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
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
import com.coinepro.core.chartevents.ChartEventNotice
import com.coinepro.core.chartevents.reasonRes
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProMotionSpecs
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPress
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.continuousMotionAllowed
import com.coinepro.core.designsystem.onPageAccent
import com.coinepro.core.designsystem.pageAccent
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.pressScale
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.spectrumRim
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
 *
 * ### Why the symbol switcher joined it
 *
 * Because *which instrument* is the same kind of decision as *which bar length*, made about as
 * often, and until now it had nowhere on this page to be made: `SymbolWheelBar` documents how the
 * old strip came to be unreachable code and why the watchlist pane is not a substitute.
 *
 * It shares the bar-length row rather than taking a tier of its own — item 7, and the arrangement
 * in the owner's own screenshot: a narrow vertical scroll of tickers at the leading edge, then the
 * lengths, then the tools. See `SymbolScrollWheel` for why it is a drag rather than a list, and why
 * the horizontal three-cell version is still the right shape in the fullscreen strip and not here.
 * It is absent entirely for a reader with nothing to switch to, so it costs a first-time reader no
 * height at all.
 */
@Composable
internal fun ChartCommandBand(
    interval: ChartInterval,
    starred: List<String>,
    onSelectInterval: (ChartInterval) -> Unit,
    onMoreIntervals: () -> Unit,
    armedTool: Boolean,
    /** Whether the draw button belongs here. False where a tool column already offers it. */
    showDraw: Boolean = true,
    indicators: Int,
    drawings: Int,
    onOpen: (ChartSheet) -> Unit,
    onFullscreen: () -> Unit,
    onMore: () -> Unit,
    /** Whether the hub has something non-default in it — a span, a comparison, a scale. */
    moreActive: Boolean,
    modifier: Modifier = Modifier,
    /** The wheel's ring — the watchlist, or the catalogue's most traded where the list is short. */
    symbols: List<String> = emptyList(),
    symbol: String = "",
    onSelectSymbol: ((String) -> Unit)? = null,
    /** Quotes for the wheel's rows, keyed by symbol. */
    quotes: Map<String, WatchlistQuote> = emptyMap(),
    /** The one-step undo, or null with nothing to walk back. TradingView keeps it on the bar. */
    onUndo: (() -> Unit)? = null,
    /** A finger landing on or leaving the symbol wheel; the page draws the big picker meanwhile. */
    onSymbolDrag: (Boolean) -> Unit = {},
) {
    // Unused here since the intervals moved into the date-range sheet and the wheel stopped
    // printing a move beside the ticker, kept on the signature so the two-pane and fullscreen
    // callers that still build a strip from them do not change.
    @Suppress("UNUSED_VARIABLE") val strip = Triple(starred, onSelectInterval, quotes)
    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Stage)) {
        // TradingView's chart toolbar closes the pane with a hairline; measured `#EBEBEB` on white.
        HorizontalDivider(color = CoineProColors.BorderSubtle, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOOLBAR_HEIGHT)
                .padding(horizontal = CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The symbol, bold, on the reading edge — a drag on it turns the wheel. Then the
            // interval beside it, which opens the date-range sheet. Both 16 sp bold, measured off
            // the phone app's `BTCUSD 4H`.
            onSelectSymbol?.let { select ->
                SymbolScrollWheel(
                    symbols = symbols,
                    current = symbol,
                    onSelect = select,
                    onDragging = onSymbolDrag,
                )
            }
            ToolbarText(text = interval.wire, onClick = onMoreIntervals)
            Spacer(modifier = Modifier.weight(1f))
            if (showDraw) {
                ToolbarButton(
                    icon = DesignR.drawable.tv_pencil,
                    label = stringResource(R.string.chart_band_draw),
                    active = armedTool,
                    onClick = { onOpen(ChartSheet.TOOLS) },
                )
            }
            ToolbarButton(
                icon = DesignR.drawable.icon_sliders_horizontal,
                label = stringResource(R.string.chart_band_studies),
                active = indicators > 0,
                onClick = { onOpen(ChartSheet.INDICATORS) },
            )
            ToolbarButton(
                icon = DesignR.drawable.tv_more_horizontal,
                label = stringResource(R.string.chart_band_more),
                active = moreActive || drawings > 0,
                onClick = onMore,
            )
            VerticalDivider(
                color = CoineProColors.BorderSubtle,
                thickness = 1.dp,
                modifier = Modifier.height(TOOLBAR_GLYPH).padding(horizontal = 2.dp),
            )
            ToolbarButton(
                icon = DesignR.drawable.icon_arrow_counter_clockwise,
                label = stringResource(R.string.chart_more_undo),
                active = false,
                onClick = onUndo,
            )
            ToolbarButton(
                icon = DesignR.drawable.tv_maximize2,
                label = stringResource(R.string.chart_band_fullscreen),
                active = false,
                onClick = onFullscreen,
            )
        }
    }
}

/**
 * One glyph on the toolbar: 22 dp of ink in a 44 dp target, the primary ink at rest and the page
 * accent when the thing behind it is armed or carrying something. No label — TradingView's bar
 * has none, and every glyph here is one the reader has met on its own sheet.
 */
@Composable
private fun ToolbarButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    onClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    val enabled = onClick != null
    val ink = when {
        !enabled -> CoineProColors.TextDisabled
        active -> CoineProColors.pageAccentInk
        else -> CoineProColors.TextPrimary
    }
    Box(
        modifier = Modifier
            .size(TOOLBAR_TARGET)
            .pressScale(interaction, CoineProPress.CONTROL)
            .clip(CoineProShapes.small)
            .clickable(interaction, null, enabled = enabled) {
                haptics.select()
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = ink,
            modifier = Modifier.size(TOOLBAR_GLYPH),
        )
    }
}

/** The interval on the toolbar: bold, Latin, and a tap away from the date-range sheet. */
@Composable
private fun ToolbarText(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    LtrDirection {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = CoineProColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier
                .pressScale(interaction, CoineProPress.CONTROL)
                .clip(CoineProShapes.small)
                .clickable(interaction, null) {
                    haptics.select()
                    onClick()
                }
                .heightIn(min = TOOLBAR_TARGET)
                .padding(horizontal = CoineProSpacing.One)
                .wrapContentHeight(),
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
internal fun ChartReadingsPanel(
    reading: ChartReading,
    /** The bar length this reading was taken on. It is half the answer — see the title below. */
    interval: ChartInterval,
    modifier: Modifier = Modifier,
) {
    CoineProCard(
        modifier = modifier.fillMaxWidth(),
        shape = CoineProShapes.medium,
        contentPadding = PaddingValues(
            horizontal = CoineProSpacing.Two,
            vertical = CoineProSpacing.OneHalf,
        ),
    ) {
        // The question **and the bar it was asked on**, in one line.
        //
        // Every figure here is computed from `visibleSeries`, so switching timeframe already
        // changed all three — and nothing on the panel said so. A reader who taps M15 and watches
        // «قدرت روند» go from «متوسط» to «قوی» has no way to know whether that is the market or the
        // app, and the honest reading of a trend strength is meaningless without the length of bar
        // it was measured over. So the title carries it: «این بازار چه می‌کند · ۱ ساعت».
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chart_reading_title),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            // No separator dot here, and this is not a style preference. The timeframe that follows
            // begins with a numeral, the Persian zero **is** a small dot, and «· ۱ ساعت» drew as
            // «۱۰ ساعت» — the chart's own timeframe, misread by a factor of ten, in the caption
            // over the readings taken at it. The label is muted and the timeframe is gold; the
            // colour and the gap already say these are two things.
            // Crossfaded rather than replaced. The three readings below animate to their new
            // values on a timeframe change; a label that snapped while they travelled would be the
            // one part of the panel not taking part in the same movement.
            AnimatedContent(
                targetState = interval.label,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "reading-interval",
            ) { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.pageAccentInk,
                )
            }
        }
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
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "reading-value",
            modifier = Modifier.padding(top = 2.dp),
        ) { word ->
            Text(
                text = word,
                style = MaterialTheme.typography.titleSmall,
                color = tone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ReadingMeter(
            fraction = fraction,
            tone = tone,
            modifier = Modifier.padding(top = CoineProSpacing.One),
        )
        // Two lines, not one.
        //
        // «+0.09% فاصلهٔ میانگین‌ها» does not fit a third of a phone at any font this panel would
        // use, so at one line it rendered as «+0.09% فاصلهٔ میانگ…» — the number survived and the
        // thing it measures was cut off, which is the half that makes it mean anything. Two lines
        // fit all three readings' reasons at every width, and the panel grows by one line of
        // eleven-point text.
        Text(
            text = why,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextDisabled,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/**
 * Where one reading sits on its own scale, and it travels there.
 *
 * ### Why this animates now, having deliberately not before
 *
 * The note that used to stand here said a fact about the bars on screen should not slide, because
 * a pan changes the window continuously and a meter chasing it would be motion reporting nothing.
 * That reasoning holds for a pan and does not hold for the case it was blocking: switching
 * timeframe replaces the whole series at once, and all three readings jump together. Jumping is
 * exactly when a reader cannot tell *which* of the three moved, or by how much — the one moment
 * the movement is the information.
 *
 * So it travels, on the design system's own standard curve, and only there is any distance to
 * cover. A pan produces a hundred tiny targets and the spring simply tracks them, which is
 * indistinguishable from the old behaviour; a timeframe change produces one large one and the eye
 * follows it.
 *
 * Under `continuousMotionAllowed()` the fill is placed rather than animated. The reduced-motion
 * reader gets the same number with no travel, which is the whole point of the setting.
 */
@Composable
private fun ReadingMeter(fraction: Float, tone: Color, modifier: Modifier = Modifier) {
    val target = fraction.coerceIn(0f, 1f).takeIf { it.isFinite() } ?: 0f
    val filled = if (continuousMotionAllowed()) {
        animateFloatAsState(
            targetValue = target,
            animationSpec = CoineProMotionSpecs.standard(),
            label = "reading-meter",
        ).value
    } else {
        target
    }
    // The ink travels with the fill. A reading that crosses from «کم» into «زیاد» changes both its
    // word and its colour, and a colour that cut while the bar was still moving would put the new
    // verdict on the old length for a fifth of a second.
    val ink by animateColorAsState(
        targetValue = tone,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "reading-meter-ink",
    )
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
                .fillMaxWidth(filled)
                .clip(CoineProPillShape)
                .background(ink),
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
    bars: List<Candle>,
    onGoToDate: (Int) -> Unit,
    /** Null with nothing to walk back, which draws the tile dimmed rather than dropping it. */
    onUndo: (() -> Unit)?,
    onRedo: (() -> Unit)?,
    comparisons: Int,
    scaleLabel: String,
    scaleAdjusted: Boolean,
    onOpen: (ChartSheet) -> Unit,
    onCreateAlert: (() -> Unit)?,
    onBacktest: (() -> Unit)?,
    onShare: () -> Unit,
    onOpenStudio: (() -> Unit)?,
    /** Null where nothing fetches events; the tile is not drawn. */
    onEvents: (() -> Unit)? = null,
    eventKinds: Int = 0,
    /** Why the event strip is empty, where it is; said on the tile rather than only behind it. */
    eventNotice: ChartEventNotice? = null,
    /** The web terminal, where a deployment reports one. */
    onOpenTerminal: (() -> Unit)? = null,
    /** The trade card and the ring under the live bar share this. Null draws the card dimmed. */
    onTrade: (() -> Unit)? = null,
    /** Enter bar replay. Null off a series too short to rewind, or while already replaying. */
    onReplay: (() -> Unit)? = null,
    /** The «Help Center» row at the foot of the sheet. */
    onHelpCenter: (() -> Unit)? = null,
) {
    // TradingView's «Analysis hub»: a sheet of tiles rather than a list of rows. Measured off the
    // phone app — three bordered tiles across for the chart's own settings, then the broker card
    // in its spectrum rim, a section headed TOOLS with grey plates two across, a section headed
    // MORE, and «Help Center» at the foot. Every row this sheet used to list is a tile now, with
    // the same handler behind it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CoineProSpacing.Gutter)
            .padding(bottom = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        HubGrid(columns = 3, outlined = true) {
            HubTile(
                icon = DesignR.drawable.icon_bookmark_simple,
                label = stringResource(R.string.chart_more_layouts),
                onClick = { onOpen(ChartSheet.LAYOUTS) },
            )
            HubTile(
                icon = DesignR.drawable.tv_chart_percent,
                label = stringResource(R.string.chart_more_scale),
                note = scaleLabel,
                marked = scaleAdjusted,
                onClick = { onOpen(ChartSheet.SCALE) },
            )
            HubTile(
                icon = DesignR.drawable.icon_camera,
                label = stringResource(R.string.chart_more_share),
                onClick = onShare,
            )
            HubTile(
                icon = DesignR.drawable.icon_arrow_counter_clockwise,
                label = stringResource(R.string.chart_more_undo),
                onClick = onUndo,
            )
            HubTile(
                icon = DesignR.drawable.icon_arrow_clockwise,
                label = stringResource(R.string.chart_more_redo),
                onClick = onRedo,
            )
            onOpenTerminal?.let {
                HubTile(
                    icon = DesignR.drawable.tv_maximize2,
                    label = stringResource(R.string.chart_hub_terminal),
                    onClick = it,
                )
            }
        }

        HorizontalDivider(color = CoineProColors.BorderSubtle)

        TradeCard(onClick = onTrade)

        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            SheetLabel(stringResource(R.string.chart_more_span))
            RangeChipRow(selected = range, onSelect = onSelectRange, contentPadding = PaddingValues(0.dp))
        }
        if (bars.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                SheetLabel(stringResource(R.string.chart_more_goto))
                GoToDateField(bars = bars, onGoTo = onGoToDate)
            }
        }

        HorizontalDivider(color = CoineProColors.BorderSubtle)

        SheetLabel(stringResource(R.string.chart_hub_tools))
        HubGrid(columns = 2, outlined = false) {
            HubTile(
                icon = DesignR.drawable.icon_sliders_horizontal,
                label = stringResource(R.string.chart_band_studies),
                onClick = { onOpen(ChartSheet.INDICATORS) },
            )
            HubTile(
                icon = DesignR.drawable.tv_chart_line,
                label = stringResource(R.string.chart_more_compare),
                count = comparisons,
                onClick = { onOpen(ChartSheet.COMPARE) },
            )
            HubTile(
                icon = DesignR.drawable.tv_bell,
                label = stringResource(R.string.chart_more_alert),
                onClick = onCreateAlert,
            )
            HubTile(
                icon = DesignR.drawable.icon_skip_back,
                label = stringResource(R.string.chart_hub_replay),
                onClick = onReplay,
            )
            HubTile(
                icon = DesignR.drawable.tv_play,
                label = stringResource(R.string.chart_more_backtest),
                onClick = onBacktest,
            )
            HubTile(
                icon = DesignR.drawable.tv_chart_candles,
                label = stringResource(R.string.chart_band_type),
                onClick = { onOpen(ChartSheet.TYPE) },
            )
            HubTile(
                icon = DesignR.drawable.tv_tool_cursor,
                label = stringResource(R.string.chart_band_objects),
                onClick = { onOpen(ChartSheet.DRAWINGS) },
            )
        }

        // MORE — the phone app's second section: what belongs to the page rather than to the
        // chart. Drawn only when there is something in it; a heading over nothing is a promise.
        if (onEvents != null || onOpenStudio != null || onOpenTerminal != null) {
            SheetLabel(stringResource(R.string.chart_hub_more))
            HubGrid(columns = 2, outlined = false) {
                onEvents?.let {
                    HubTile(
                        icon = DesignR.drawable.tv_calendar_days,
                        label = stringResource(R.string.chart_more_events),
                        note = eventNotice?.let { reason -> stringResource(reason.reasonRes()) },
                        count = eventKinds,
                        onClick = it,
                    )
                }
                onOpenStudio?.let {
                    HubTile(
                        icon = DesignR.drawable.tv_layout_grid,
                        label = stringResource(R.string.chart_more_studio),
                        onClick = it,
                    )
                }
                onOpenTerminal?.let {
                    HubTile(
                        icon = DesignR.drawable.tv_maximize2,
                        label = stringResource(R.string.chart_hub_terminal),
                        onClick = it,
                    )
                }
            }
        }

        onHelpCenter?.let { open ->
            HorizontalDivider(color = CoineProColors.BorderSubtle)
            HelpCenterRow(onClick = open)
        }
    }
}

/**
 * «Trade with your broker»: the one card on the hub with a rim in colour.
 *
 * Measured off the phone app: a 72 pt plate across the sheet, 12 pt corners, a grey fill and a
 * 1.5 pt rose-to-blue rim — see `spectrumRim` for why the rim lives in the design system. The
 * glyph over the label is the same arrangement the tiles use. Here it leads to the trade sheet on
 * a chart with a setup, and to the terminal otherwise; with neither it is drawn dimmed, like any
 * tile with nothing behind it.
 */
@Composable
private fun TradeCard(onClick: (() -> Unit)?) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    val enabled = onClick != null
    val ink = if (enabled) CoineProColors.TextPrimary else CoineProColors.TextDisabled
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(TRADE_CARD_HEIGHT)
            .pressScale(interaction, CoineProPress.CHIP)
            .clip(CoineProShapes.medium)
            .background(CoineProColors.SurfaceElevated)
            .spectrumRim(CoineProShapes.medium)
            .clickable(interaction, null, enabled = enabled) {
                haptics.select()
                onClick?.invoke()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_tool_longshort),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(HUB_GLYPH),
        )
        Text(
            text = stringResource(R.string.chart_hub_trade),
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = CoineProSpacing.Half),
        )
    }
}

/** «Help Center», centred at the foot of the hub: a ringed question mark beside a bold label. */
@Composable
private fun HelpCenterRow(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .clickable(interaction, null) {
                haptics.select()
                onClick()
            }
            .padding(vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_help_circle),
            contentDescription = null,
            tint = CoineProColors.TextPrimary,
            modifier = Modifier.size(HELP_GLYPH),
        )
        Text(
            text = stringResource(R.string.chart_hub_help),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = CoineProColors.TextPrimary,
        )
    }
}

/**
 * The hub's grid: [columns] tiles across, laid out by hand so a trailing row of one or two tiles
 * keeps the same tile width as a full row rather than stretching to fill it — which is what
 * TradingView's does and what a `LazyVerticalGrid` inside a scrolling column cannot.
 */
@Composable
private fun HubGrid(columns: Int, outlined: Boolean, content: @Composable HubScope.() -> Unit) {
    val scope = remember(outlined) { HubScope(outlined) }
    // Collected on every composition rather than remembered: the tiles close over handlers that
    // change with the chart's state, and a cached list would keep calling last frame's.
    scope.tiles.clear()
    scope.content()
    val tiles = scope.tiles.toList()
    Column(verticalArrangement = Arrangement.spacedBy(HUB_GAP)) {
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(HUB_GAP)) {
                row.forEach { tile -> Box(modifier = Modifier.weight(1f)) { tile() } }
                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** Collects the tiles a [HubGrid] lays out. */
internal class HubScope(val outlined: Boolean) {
    val tiles = mutableListOf<@Composable () -> Unit>()
}

/**
 * One tile: a glyph over a label, 100 dp tall — TradingView's measure. Outlined tiles carry a
 * hairline on the sheet's own ground; plate tiles take the elevated rung with no edge. A tile
 * with nothing behind it is drawn dimmed rather than dropped, so the sheet keeps its shape.
 */
@Composable
private fun HubScope.HubTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: (() -> Unit)?,
    note: String? = null,
    count: Int = 0,
    marked: Boolean = false,
) {
    val outlined = this.outlined
    tiles += {
        val interaction = remember { MutableInteractionSource() }
        val haptics = rememberCoineProHaptics()
        val enabled = onClick != null
        val active = marked || count > 0
        val ink = when {
            !enabled -> CoineProColors.TextDisabled
            active -> CoineProColors.pageAccentInk
            else -> CoineProColors.TextPrimary
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(HUB_TILE)
                .pressScale(interaction, CoineProPress.CHIP)
                .clip(CoineProShapes.medium)
                .background(if (outlined) CoineProColors.Surface else CoineProColors.SurfaceElevated)
                .then(
                    if (outlined) {
                        Modifier.border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.medium)
                    } else {
                        Modifier
                    },
                )
                .clickable(interaction, null, enabled = enabled) {
                    haptics.select()
                    onClick?.invoke()
                }
                .padding(horizontal = CoineProSpacing.Half),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(HUB_GLYPH),
                )
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
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) CoineProColors.TextPrimary else CoineProColors.TextDisabled,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = CoineProSpacing.Half),
            )
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
// TradingView's chart toolbar, measured off the phone app: a 44 pt bar, 22 pt glyphs in 44 pt targets.
private val TOOLBAR_HEIGHT = 44.dp
private val TOOLBAR_TARGET = 44.dp
private val TOOLBAR_GLYPH = 22.dp

// The analysis hub's tiles: 100 pt tall with an 8 pt gutter, a 26 pt glyph over a one-line label.
private val HUB_TILE = 100.dp
private val HUB_GAP = 8.dp
private val HUB_GLYPH = 26.dp

/** The broker card, 72 pt across the sheet; and the ringed «?» before «Help Center», 24 pt. */
private val TRADE_CARD_HEIGHT = 72.dp
private val HELP_GLYPH = 24.dp

/** The glyph on a sheet row that is still a row — the go-to-date field's calendar. */
private val MORE_GLYPH = 20.dp



/** The reading bar. Four points: read as a measure, not as a rule. */
private val METER_HEIGHT = 4.dp

/** A range chip, sized for a thumb rather than for its two Persian words. */
private val RANGE_CHIP_HEIGHT = 36.dp
private val RANGE_CHIP_WIDTH = 56.dp
