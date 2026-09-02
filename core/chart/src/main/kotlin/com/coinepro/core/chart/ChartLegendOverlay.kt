package com.coinepro.core.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * What one legend row is about — and therefore what the buttons on it act on.
 *
 * Indices rather than object identity, because the objects are value classes rebuilt on every
 * emission of the screen's state: an [ChartLine] carries a whole `DoubleArray`, so two frames'
 * worth of "the same" EMA are equal but not identical, and a callback carrying one back would give
 * the caller a copy to search its own list for. The index is what the caller already keyed its list
 * by, which is the whole of what it needs to hide, configure or remove the thing.
 */
sealed interface ChartLegendTarget {

    /** The price itself — the candles, the line, whatever the type is drawing. */
    data object Series : ChartLegendTarget

    /** An overlay drawn on the price, by its position in [ChartDecoration.overlays]. */
    data class Overlay(val index: Int) : ChartLegendTarget

    /** A pane below the price, by its position in [ChartDecoration.panes]. */
    data class Pane(val index: Int) : ChartLegendTarget

    /** A compared instrument, by its position in [ChartDecoration.comparisons]. */
    data class Comparison(val index: Int) : ChartLegendTarget
}

/**
 * Whether the instrument's market is trading, for the name on the legend's head row.
 *
 * A type of this module's own rather than `core:symbols`' `MarketStatus`, and that is not
 * duplication for its own sake: `core:chart` does not depend on `core:symbols`, and acquiring the
 * dependency so that a legend can print one of three words would put the symbol classifier, its
 * catalogue and its clock on the classpath of every screen in the app that draws a sparkline. The
 * caller already holds the real answer — `MarketHours.statusOf` — and maps it onto this at the
 * boundary, which is the same trade `ChartScreen` already makes to hand the canvas a palette
 * without `core:chart` learning about `core:datastore`.
 *
 * Three states and not a boolean, because the two ways of being shut are not the same news. A
 * weekend is the calendar and will end on Sunday evening; a market shut inside the trading week is
 * a halt, a holiday or a broker outage, and a reader who is told only "closed" cannot tell which
 * of those they are looking at.
 */
enum class ChartMarketStatus {

    /** Trading. */
    OPEN,

    /** Shut inside the trading week: a halt, a holiday, an outage — something only the server knows. */
    CLOSED,

    /** The weekend, which is the one closure a client is entitled to be certain of offline. */
    WEEKEND,
}

/**
 * The session's move, as the *caller* measures it.
 *
 * Both halves together or neither, deliberately. Two independent nullable numbers would let a
 * caller hand over an absolute from one measurement and a percentage from another — the page
 * header's session change beside the legend's own bar change — and the row would then print two
 * figures that do not describe the same move while looking exactly like a pair that does. That is
 * the class of wrong answer this legend cannot afford: it is plausible.
 */
data class ChartLegendChange(
    /** The move in the instrument's own units, signed. */
    val absolute: Double,
    /** The same move as a percentage, signed. Not a fraction — 0.48 means 0.48%. */
    val percent: Double,
)

/**
 * One row of the legend: what it names, what it currently reads, and whether it carries the buttons.
 *
 * [alternatives] is how the price row keeps fitting. Four prices at four decimals do not fit on a
 * narrow phone at any spacing, and «O 2571.2  H 2575.7  L 2570.1  C 2…» cuts off the one number the
 * reader came for. So the row offers the same reading at four densities — spaced, tight, bare
 * numbers, close only — and the overlay picks the widest that fits. Every other row has exactly one.
 *
 * [colour] is null on the price row alone, which is drawn in the direction colour the candles are
 * using and so cannot be decided by a function with no palette.
 */
data class ChartLegendRow(
    val target: ChartLegendTarget,
    val label: String,
    val alternatives: List<String>,
    val colour: Long?,
    /**
     * Whether this row carries the hide, settings and remove buttons.
     *
     * False on the second and later rows of a pane, which are the pane's own lines: three buttons
     * repeated per line would offer to remove an RSI four times, and the reader would reasonably
     * expect the four to do different things.
     *
     * False on the change row too, for a different reason: it is not a thing. A row reading
     * «Δ +12.34 +0.48%» describes the series above it, so an eye on it would be a second switch for
     * one line and a remove would be an offer to delete arithmetic.
     */
    val primary: Boolean,
)

/**
 * The rows the legend prints, as data.
 *
 * Pure, and separated from the drawing for the reason the last wave made unavoidable: the legend
 * was painted straight onto the canvas, so nothing about it could be tested and nothing about it
 * could be *pressed*. Building the rows here means the composable below is layout only.
 *
 * The reading follows the crosshair's bar when there is one and the last bar otherwise — which is
 * what turns the legend into a scrubber over history rather than a second copy of the header.
 *
 * In tracking mode every pane line joins the list. That is the whole point of the mode: a reader
 * holding a crosshair on a divergence wants the oscillator's number, and it is otherwise nowhere on
 * the screen.
 */
internal fun legendRows(
    decoration: ChartDecoration,
    series: CandleSeries,
    index: Int,
    tracking: Boolean,
    rebased: List<DoubleArray>,
    seriesLabel: String?,
): List<ChartLegendRow> {
    val bar = series.bars.getOrNull(index) ?: return emptyList()
    val decimals = decimalsFor(bar.c)
    val rows = mutableListOf<ChartLegendRow>()
    // O H L C, in TradingView's order and with TradingView's grouping — `O 77,004.19 H 77,182.00
    // L 76,748.01 C 77,058.57`. The close used to lead so an ellipsis would take the open first;
    // the narrow forms below still keep the close, and the wide form now reads the way every
    // terminal a trader has used reads.
    val o = groupThousands(formatPrice(bar.o, decimals))
    val h = groupThousands(formatPrice(bar.h, decimals))
    val l = groupThousands(formatPrice(bar.l, decimals))
    val c = groupThousands(formatPrice(bar.c, decimals))
    rows += ChartLegendRow(
        target = ChartLegendTarget.Series,
        label = seriesLabel.orEmpty(),
        alternatives = listOf(
            "O $o   H $h   L $l   C $c",
            "O $o H $h L $l C $c",
            "$o $h $l $c",
            "C $c",
        ),
        colour = null,
        primary = true,
    )
    decoration.overlays.forEachIndexed { position, overlay ->
        val label = overlay.label
        if (label.isNullOrBlank()) return@forEachIndexed
        rows += ChartLegendRow(
            target = ChartLegendTarget.Overlay(position),
            label = label,
            // An overlay is a price and shares the price's precision. A pane line does not — an RSI
            // at two decimals and a MACD at two decimals are two different mistakes.
            alternatives = listOf(groupThousands(reading(overlay.values[index], decimals))),
            colour = overlay.colour,
            primary = true,
        )
    }
    decoration.panes.forEachIndexed { position, pane ->
        val target = ChartLegendTarget.Pane(position)
        rows += ChartLegendRow(
            target = target,
            label = pane.title,
            alternatives = listOf(""),
            colour = pane.lines.firstOrNull()?.colour ?: pane.histogram?.colour,
            primary = true,
        )
        if (!tracking) return@forEachIndexed
        (pane.lines + listOfNotNull(pane.histogram))
            .filter { !it.label.isNullOrBlank() }
            .forEach { line ->
                rows += ChartLegendRow(
                    target = target,
                    label = line.label.orEmpty(),
                    alternatives = listOf(reading(line.values[index], null)),
                    colour = line.colour,
                    primary = false,
                )
            }
    }
    // Never capped, unlike the overlays: there are at most `MAX_COMPARISONS` of them by
    // construction, and a comparison line with no legend row is an unexplained coloured line on
    // somebody's chart — which is the one thing that makes a comparison worse than opening the
    // second chart.
    decoration.comparisons.forEachIndexed { position, comparison ->
        val value = rebased.getOrNull(position)?.getOrNull(index) ?: Double.NaN
        rows += ChartLegendRow(
            target = ChartLegendTarget.Comparison(position),
            label = comparison.label,
            alternatives = listOf(comparisonReading(value, decoration.comparisonBasis)),
            colour = comparison.colour,
            primary = true,
        )
    }
    return rows
}

/**
 * The row under the head: what the price has done.
 *
 * ### Why a legend needs it
 *
 * The head row printed four prices and stopped, which is the reading of a *bar* and not of an
 * instrument. Every terminal's legend carries the move beside the prices, because the four numbers
 * on their own do not answer the question the reader arrived with. Until this existed the figure
 * lived only in `ChartScreen`'s page header — which is off screen the moment the chart goes
 * fullscreen, and was never on screen at all in the panes layout.
 *
 * ### Which move
 *
 * [change] is the caller's — the session move the page header prints — and it wins wherever it is
 * offered. Where it is not, the row is computed from the bar itself as `close − open`, and that is
 * not a second source of truth for the same figure: it is a different, honest figure that this
 * legend is in a better position to state than its caller. The legend follows the crosshair, so a
 * reading taken on a bar from March sits under March's prices, and a "day change" measured against
 * today printed in that row would be a number about a different day. The composable therefore
 * passes [change] through only while the reading is on the last bar, and lets the bar answer for
 * itself everywhere else.
 *
 * ### Why the percentage is isolated and the minus is not a hyphen
 *
 * Both are the app's standing rules for a signed market figure, and both fail in the same quiet
 * way when skipped: the sign lands on the wrong number of the pair and the reader has no way at
 * all to tell. See [isolateLtr] and [signOf].
 */
internal fun legendChangeRow(
    bar: Candle,
    decimals: Int,
    change: ChartLegendChange?,
): ChartLegendRow {
    val figure = groupThousands(signedFigure(change?.absolute ?: (bar.c - bar.o), decimals))
    val share = signedPercent(change?.percent ?: percentOf(bar))
    return ChartLegendRow(
        target = ChartLegendTarget.Series,
        label = CHANGE_LABEL,
        // The same three densities the head row offers, and the last resort is the percentage
        // rather than the absolute: «+0.48%» is comparable across instruments and «+12.34» is not
        // worth much to anybody who does not already know that gold costs 2,600.
        alternatives = listOf("$figure   $share", "$figure $share", share),
        colour = null,
        primary = false,
    )
}

/**
 * The instrument's name with its market's state on it, which is where the state belongs.
 *
 * ### Why not a row of its own
 *
 * Because the plate cannot afford one. Its height budget is a quarter of the plot — 75dp on the
 * 300dp chart this app puts on the symbol page — which is three rows, and spending one of them on
 * a word would have cost the reader their last visible study to say something that fits in the
 * space after the symbol.
 *
 * ### Why silence means open
 *
 * This is the rule the search list already follows, and for the reason stated there: a closed
 * market explains an unmoving price and is worth a word, while an open one is the ordinary state
 * of affairs and saying so out loud says the same thing twice, forever, on every chart in the app.
 * So [ChartMarketStatus.OPEN] and a caller that does not know both leave the name alone, and the
 * two ways of being shut each name themselves.
 */
/**
 * The legend title's ink: TradingView's primary text, `#DBDBDB` on the dark pane and near-black on
 * the light one, told apart by the pane's own luminance rather than by the app theme, because a
 * light colour template on a dark phone still wants a dark title.
 */
internal val ChartPalette.title: Color
    get() = if (stage.luminance() < 0.5f) {
        Color(TradingViewPalette.DARK_TEXT_PRIMARY)
    } else {
        Color(TradingViewPalette.LIGHT_TEXT_PRIMARY)
    }

internal fun legendSeriesName(label: String, status: ChartMarketStatus?): String {
    val note = status?.let(::statusNote) ?: return label
    return if (label.isBlank()) note else "$label · $note"
}

/**
 * The bar's own move as a percentage of where it opened.
 *
 * NaN rather than zero on an open of zero. Zero would be a claim that the price did not move, and
 * a synthetic or malformed bar is not the same news as a flat one.
 */
private fun percentOf(bar: Candle): Double =
    if (bar.o == 0.0 || !bar.o.isFinite()) Double.NaN else (bar.c - bar.o) / bar.o * 100.0

/**
 * How many of [heights] the plate can print, in order, without cutting one in half.
 *
 * The plate has always had a height budget — a quarter of the plot at rest, half of it while the
 * crosshair is down — and until this existed nothing consulted it except a `clipToBounds`. So the
 * rows past the budget were sliced through the middle, and the «+N» counter, which counts only
 * what the *line cap* dropped, said nothing whatever about them: on the phone chart the legend
 * simply stopped at whatever row happened to reach 75dp, mid-glyph.
 *
 * Every row's own height, rather than one figure for all of them, because they are not the same
 * height: a row carrying buttons is as tall as a button and a row of plain text is not, and
 * measuring the short ones as tall ones throws away a study row that would have fitted.
 */
internal fun legendRowsThatFit(budget: Float, padding: Float, gap: Float, heights: List<Float>): Int {
    if (!budget.isFinite()) return 0
    var used = padding * 2
    var fit = 0
    heights.forEachIndexed { position, height ->
        used += height + if (position == 0) 0f else gap
        if (used > budget) return fit
        fit++
    }
    return fit
}

/**
 * One value, or the empty set.
 *
 * Not «N/A», which is English on a Persian screen, and not a dash — a dash in a column of signed
 * market figures is a minus sign, and it has been read as one here before. [places] null means the
 * value sets its own precision, which is what a pane line needs.
 */
private fun reading(value: Double?, places: Int?): String = when {
    value == null || !value.isFinite() -> NO_VALUE
    places == null -> formatPrice(value, decimalsFor(value))
    else -> formatPrice(value, places)
}

/**
 * A signed price, in the instrument's own units.
 *
 * The magnitude is formatted from its absolute value and the sign is put back by hand, because
 * `String.format` writes a hyphen and this app's minus is U+2212. On a row that already contains
 * «O», «H» and a percent sign, a hyphen is the character that reads as a dash between two numbers.
 */
private fun signedFigure(value: Double, decimals: Int): String =
    if (!value.isFinite()) NO_VALUE else isolateLtr(signOf(value) + formatPrice(abs(value), decimals))

/** The same move as a percentage. Always signed, because a percentage here is a *change*. */
private fun signedPercent(value: Double): String =
    if (!value.isFinite()) {
        NO_VALUE
    } else {
        isolateLtr(signOf(value) + formatPrice(abs(value), CHANGE_PERCENT_DECIMALS) + "%")
    }

/**
 * The sign, with U+2212 for a fall.
 *
 * Not the hyphen-minus a keyboard produces: it is narrower than a plus, so a column of signed
 * figures does not line up, and beside a Persian run it is read as punctuation rather than as part
 * of the number. Nothing at all for a flat price — «+0.00» claims a rise that did not happen.
 */
private fun signOf(value: Double): String = when {
    value > 0.0 -> "+"
    value < 0.0 -> "−"
    else -> ""
}

/**
 * A Latin run that keeps its own direction inside Persian copy.
 *
 * `core.common.BidiText.isolateLtr` is these same two characters and is the canonical one; this is
 * a copy, and deliberately a copy, because `core:chart` does not depend on `core:common` — the same
 * trade `MARKET_COLOURS` makes in this module and `AI_VISION_INTERVALS` makes in `feature:chart`.
 * Without it, «+12.34» beside «−0.48%» renders with the signs against the wrong numbers on a
 * right-to-left screen: a wrong answer that looks exactly like a right one.
 *
 * Written as escapes rather than as the characters themselves. They have no width, so pasted in
 * literally they are two invisible edits in a diff nobody can see and nobody can delete on purpose.
 */
private fun isolateLtr(value: String): String =
    if (value.isEmpty()) value else "\u2066$value\u2069"

/**
 * The word a shut market is named by, or null for one that is trading.
 *
 * One word and not a sentence: it sits after the symbol on a row that also has to hold four
 * prices. A weekend and a mid-week halt are told apart because they are not the same news — the
 * first ends on Sunday evening and the second is a holiday, a halt or an outage, and a reader told
 * only «بسته» cannot tell which they are looking at.
 */
private fun statusNote(status: ChartMarketStatus): String? = when (status) {
    ChartMarketStatus.OPEN -> null
    ChartMarketStatus.CLOSED -> "بسته"
    ChartMarketStatus.WEEKEND -> "تعطیل"
}

/**
 * The legend, as a composable laid over the canvas rather than painted into it.
 *
 * ### Why it had to stop being pixels
 *
 * Because a legend row is where a reader reaches for an indicator. Every terminal puts hide,
 * settings and remove on it, and this one painted the same text with no hit target anywhere near
 * it — so the only way to switch an EMA off was to find it again in the indicator sheet. Text drawn
 * into a `Canvas` cannot be pressed, cannot be read by TalkBack and cannot show a pressed state.
 *
 * ### What is kept
 *
 * Everything it showed: the bar's four prices in the direction colour, one row per overlay in that
 * overlay's colour with its value at this bar, the pane rows while tracking, and a row per
 * comparison in the basis' own units. It stays at the top-left of the price pane — laid out
 * left-to-right explicitly, because the chart is drawn left to right in every locale and a legend
 * that mirrors itself would sit over the newest bars.
 *
 * The plate behind it is not decoration: without it the writing is drawn over the candles and the
 * moving averages, and every stroke that crosses a glyph takes a bite out of it.
 *
 * ### What is new
 *
 * The move, on a row under the head — see [legendChangeRow] — and the market's state, on the head
 * row's own name where it costs no height — see [legendSeriesName]. And a plate that decides how
 * many rows it can hold rather than clipping whatever did not fit, so that what is dropped is
 * counted instead of sliced through the middle. The plate now paints its rounded background rather
 * than clipping to it, which is what lets a button's touch target be larger than the space the
 * button occupies: a clip clips hit testing too. See [touchTarget].
 */
@Composable
internal fun ChartLegendOverlay(
    decoration: ChartDecoration,
    series: CandleSeries,
    viewport: ChartViewport,
    /** The comparisons already rebased, aligned with [ChartDecoration.comparisons]. */
    rebased: List<DoubleArray>,
    /**
     * The crosshair, read *inside* this composable and not passed as a value.
     *
     * That is the whole of what the two-layer repaint buys. Read in the parent, a crosshair moving
     * under a finger would recompose the chart — the gesture handlers, the whole draw lambda — sixty
     * times a second to move a readout. Deferred behind a lambda, the read happens in this scope and
     * only this scope is invalidated.
     */
    crosshair: () -> Crosshair?,
    seriesLabel: String?,
    palette: ChartPalette,
    hidden: Set<ChartLegendTarget>,
    measurer: TextMeasurer,
    tracking: Boolean,
    onToggleVisibility: (ChartLegendTarget) -> Unit,
    onOpenSettings: ((ChartLegendTarget) -> Unit)?,
    onRemove: ((ChartLegendTarget) -> Unit)?,
    /**
     * The session's move, from whoever already measures it. Null lets the bar answer for itself.
     *
     * Defaulted so that the change row exists on every chart in the app the day this ships, wired
     * or not: a caller that says nothing still gets the bar's own `close − open`, which is true, and
     * a caller that hands over its header's figure gets that instead on the last bar. See
     * [legendChangeRow] for why the two are not two answers to one question.
     */
    change: ChartLegendChange? = null,
    /**
     * Whether this instrument's market is trading. Named on the head row when it is not.
     *
     * Null and not [ChartMarketStatus.OPEN] as the default, and the difference matters even though
     * both print nothing: `core:chart` has no calendar, no symbol classifier and no clock it
     * trusts, so a default of OPEN would be this module holding an opinion it has no way to form.
     * A caller that knows says so; a caller that does not, says nothing. See [legendSeriesName].
     */
    marketStatus: ChartMarketStatus? = null,
    modifier: Modifier = Modifier,
) {
    if (viewport.visibleCount == 0) return
    val index = (crosshair()?.index ?: viewport.lastVisible)
        .coerceIn(viewport.firstVisible, viewport.lastVisible)
    val bar = series.bars.getOrNull(index) ?: return
    val rows = legendRows(
        decoration = decoration,
        series = series,
        index = index,
        tracking = tracking,
        rebased = rebased,
        seriesLabel = seriesLabel,
    )
    if (rows.isEmpty()) return
    // The caller's figure is a statement about now, so it is only true of the last bar. Anywhere
    // else the crosshair has taken the legend into history and the bar answers for itself.
    val session = change?.takeIf { index == series.bars.lastIndex }
    // **Only while the crosshair is down.**
    //
    // At rest this row said «Δ −27.1 −1.04%» over the candles — the same two numbers the page
    // header prints in large type a centimetre above the plot. Two statements of one fact, and the
    // duplicate was the one costing a row of chart. The header cannot answer for a *historical*
    // bar, though, so the moment the reader puts the crosshair down the row is the only thing that
    // can say what that bar did, and it comes back.
    // Always, not only while the crosshair is down: TradingView prints the change beside the close
    // on every frame, and a reader glancing at the chart wants the day's move without touching it.
    val move = legendChangeRow(bar = bar, decimals = decimalsFor(bar.c), change = session)
    val movedUp = (session?.absolute ?: (bar.c - bar.o)) >= 0.0
    val rising = bar.up
    val lines = if (tracking) TRACKING_LEGEND_LINES else LEGEND_LINES
    val body = rows.drop(1)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier) {
            val density = LocalDensity.current
            /**
             * Whether the per-row controls are on screen.
             *
             * Local state and deliberately not hoisted: it is a disclosure, not a setting. Nothing
             * outside this overlay is different for it being open, it should not survive leaving the
             * chart, and a caller that had to store it would be storing a fact about a plate.
             *
             * It resets on the study list, so removing the last indicator closes the panel it was
             * removed from rather than leaving three buttons hanging beside the price.
             */
            var expanded by remember(body.size) { mutableStateOf(false) }
            val budget = maxHeight * if (tracking) TRACKING_LEGEND_BUDGET else LEGEND_BUDGET
            // How many buttons the widest row will carry. Every primary row reserves all of them,
            // including the price row, which has no remove — so the eyes line up in a column
            // instead of stepping sideways by one button per row.
            //
            // **Zero unless the reader has asked for them.** Three buttons on every primary row is
            // between forty and fifty per cent of the plate's width and it lifts each of those rows
            // from the height of its own text to [LEGEND_BUTTON_DP], which on a phone meant a legend
            // that covered the top-left quarter of the plot in order to show *two* studies out of
            // ten and a «+8» underneath. The reading — swatch, name, value — is what a legend is
            // for; the controls are what a reader reaches for occasionally, and they now cost
            // nothing until they are reached for. See [expanded].
            val slots = if (expanded) {
                1 + (if (onOpenSettings != null) 1 else 0) + (if (onRemove != null) 1 else 0)
            } else {
                0
            }
            val size = legendFontSizeSp().sp
            val headStyle = axisStyle(if (rising) palette.up else palette.down)
            // Measured rather than assumed, because it follows the system font setting: a reader
            // at the largest text size has rows half again as tall and a plate that holds one
            // fewer, and a legend that decided this from a constant would go back to painting the
            // last one through the middle.
            val textDp = with(density) { measurer.measure("0", headStyle).size.height.toDp().value }
            // A row is as tall as its text until it has buttons in it. Collapsed, that is the whole
            // saving: ten studies at text height fit in the budget two studies at button height did.
            val rowDp = if (expanded) maxOf(textDp, LEGEND_BUTTON_DP.value) else textDp
            // The head always carries the one control that opens the rest, so it is always the
            // taller kind of row. One row of chrome instead of one per study.
            val headDp = maxOf(textDp, LEGEND_BUTTON_DP.value)
            // The head first, then the move, then the studies — the order they are printed in, and
            // the order they are worth. A row that does not fit is folded into «+N» rather than
            // sliced off at the plate's edge.
            val heights = buildList {
                add(headDp)
                if (move != null) add(textDp)
                body.forEach { add(if (it.primary) rowDp else textDp) }
            }
            val room = legendRowsThatFit(
                budget = budget.value,
                padding = LEGEND_PLATE_PADDING_DP.value,
                gap = LEGEND_GAP_DP.value,
                heights = heights,
            )
            // The counter is a row too, and it is worth one: «+3» is what tells a reader that
            // there is more to see, and without it a full legend and a truncated one look alike.
            // Both reasons a row can be dropped are asked about — the plate ran out of height, or
            // the line cap did — because either one puts a «+N» under the last row that fitted.
            val fit = if (room < heights.size || body.size > lines) {
                legendRowsThatFit(
                    budget = budget.value - textDp - LEGEND_GAP_DP.value,
                    padding = LEGEND_PLATE_PADDING_DP.value,
                    gap = LEGEND_GAP_DP.value,
                    heights = heights,
                )
            } else {
                room
            }
            if (fit == 0) return@BoxWithConstraints
            val cap = minOf(lines, (fit - 2).coerceAtLeast(0))
            val shown = body.take(cap)
            val overflow = body.size - shown.size
            val plate = with(density) {
                (maxWidth - LEGEND_INSET_DP * 2 - LEGEND_PLATE_PADDING_DP * 2).toPx()
            }
            val actions = with(density) {
                (LEGEND_ACTIONS_GAP_DP + (LEGEND_BUTTON_DP + LEGEND_GAP_DP) * slots).toPx()
            }
            Column(
                modifier = Modifier
                    .padding(LEGEND_INSET_DP)
                    .widthIn(max = maxWidth - LEGEND_INSET_DP * 2)
                    .heightIn(max = budget)
                    // Painted, not clipped. `Modifier.clip` would take the rounded plate *and* the
                    // hit testing of anything reaching past it, which is precisely what the
                    // enlarged touch targets do. Nothing needs clipping any more: the row count is
                    // decided against the budget above and every text ellipsises rather than runs.
                    .background(
                        color = palette.stage.copy(alpha = LEGEND_PLATE_ALPHA),
                        shape = RoundedCornerShape(LEGEND_PLATE_RADIUS_DP),
                    )
                    .padding(LEGEND_PLATE_PADDING_DP),
                verticalArrangement = Arrangement.spacedBy(LEGEND_GAP_DP),
            ) {
                // The name carries the market's state, so a chart sitting on a Saturday price says
                // so where the reader is already looking. See [legendSeriesName].
                val head = rows.first().let { it.copy(label = legendSeriesName(it.label, marketStatus)) }
                // The head row hands its alternatives over whole. `LegendRow` picks against the
                // width it is actually given, which is the only measurement that cannot be wrong —
                // see the note there.
                LegendRow(
                    row = head,
                    colour = if (rising) palette.up else palette.down,
                    palette = palette,
                    measurer = measurer,
                    fontSize = size,
                    dimmed = ChartLegendTarget.Series in hidden,
                    slots = slots,
                    onToggleVisibility = onToggleVisibility,
                    onOpenSettings = onOpenSettings,
                    // The price is not removable. A chart with no series on it is not a chart, and
                    // an affordance that has to refuse is worse than one that is not offered.
                    onRemove = null,
                    // The one control that is always on the plate, and the only one when it is
                    // closed. It goes on the head row rather than on the plate as a whole because a
                    // tappable plate is a tappable rectangle over the top-left of the plot, and the
                    // plot underneath it has to keep taking pans and pinches — a legend that
                    // swallowed a drag would be a worse defect than the one being fixed.
                    disclosure = { expanded = !expanded },
                    disclosed = expanded,
                )
                if (move != null && fit > 1) {
                    LegendRow(
                        row = move,
                        // The direction of the *move*, which is not always the direction of the bar
                        // under the crosshair: a session up on the day can contain a red bar, and
                        // taking the colour from that bar would paint a rise red.
                        colour = if (movedUp) palette.up else palette.down,
                        palette = palette,
                        measurer = measurer,
                        fontSize = size,
                        // It fades with the price it describes, because that is what it describes.
                        dimmed = ChartLegendTarget.Series in hidden,
                        slots = slots,
                        onToggleVisibility = onToggleVisibility,
                        onOpenSettings = null,
                        onRemove = null,
                    )
                }
                shown.forEach { row ->
                    LegendRow(
                        row = row,
                        colour = row.colour?.let { Color(opaqueArgb(it)) } ?: palette.text,
                        palette = palette,
                        measurer = measurer,
                        fontSize = size,
                        dimmed = row.target in hidden,
                        slots = slots,
                        onToggleVisibility = onToggleVisibility,
                        onOpenSettings = onOpenSettings,
                        onRemove = onRemove,
                    )
                }
                if (overflow > 0) {
                    Text(
                        text = "+" + overflow.toString(),
                        color = palette.text,
                        fontSize = size,
                        textAlign = TextAlign.Right,
                    )
                }
            }
        }
    }
}

/**
 * One row: a swatch, the name, the reading, and the three things a reader can do to it.
 *
 * ### The order of the three, and why it does not mirror
 *
 * Eye, then settings, then remove, always, and always at the end of the row. The plate is laid out
 * left to right whatever the locale — see [ChartLegendOverlay] — so "the end" is the right-hand
 * side, and the ordering rule is not reading direction but consequence: the eye is the one a reader
 * presses repeatedly and it sits nearest the text it acts on; remove is the one that cannot be
 * undone and it sits furthest from it, at the outside edge, where a mis-aimed thumb lands on the
 * plate's padding instead.
 *
 * ### What it looks like with three instead of one
 *
 * [slots] is how many buttons the *widest* row in this legend carries, and every primary row
 * reserves that many. Without it the price row — which has two, having no remove — would put its
 * settings button under the next row's eye, and a column of buttons that steps sideways by one
 * position per row is a column a thumb cannot travel down. The reading takes a weight so that the
 * buttons are laid out first and the text ellipsises into what is left: before, a long indicator
 * name pushed the whole cluster past the plate's edge, so the row with the most to remove was the
 * one whose remove button was not there.
 */
@Composable
private fun LegendRow(
    row: ChartLegendRow,
    colour: Color,
    palette: ChartPalette,
    measurer: TextMeasurer,
    fontSize: TextUnit,
    dimmed: Boolean,
    slots: Int,
    onToggleVisibility: (ChartLegendTarget) -> Unit,
    onOpenSettings: ((ChartLegendTarget) -> Unit)?,
    onRemove: ((ChartLegendTarget) -> Unit)?,
    /**
     * Opens and closes the per-row controls. Non-null on the head row only.
     *
     * It is drawn *after* the reserved button slots, so it keeps its column whether the panel is
     * open or shut and the reader's thumb does not have to travel to close what it just opened.
     */
    disclosure: (() -> Unit)? = null,
    /** Whether [disclosure] is currently showing the controls, which is what the glyph says. */
    disclosed: Boolean = false,
) {
    val faded = if (dimmed) colour.copy(alpha = HIDDEN_ROW_ALPHA) else colour
    // The line box, pinned to the letters rather than to the font's own metrics.
    //
    // IRANYekanX carries the tall ascent and deep descent a Persian face needs for its diacritics
    // and its descenders, and Compose honours them: a 10sp legend row was laying out at close to
    // 25dp, so three rows of eight-point-tall words occupied the height of five. That is invisible
    // in the code and it is most of what made the legend feel like a panel rather than a caption.
    //
    // `Trim.Both` with a proportional alignment takes the leading off the top of the first line and
    // the bottom of the last, which for a single-line row is the whole of it. Nothing about the
    // glyphs changes; the empty space above and below them goes.
    val lineHeight = fontSize * LEGEND_LINE_HEIGHT
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LEGEND_GAP_DP),
    ) {
        if (row.colour != null) {
            Box(
                modifier = Modifier
                    .size(SWATCH_DP)
                    .clip(CircleShape)
                    .background(faded),
            )
        }
        if (row.label.isNotBlank()) {
            // The series title is TradingView's largest legend text — 16 px against 13 px values —
            // in the primary ink, not the direction colour: the name of the instrument does not go
            // red when a bar closes down. Every other row's label keeps its own colour.
            val title = row.target == ChartLegendTarget.Series && row.primary
            val titleInk = if (dimmed) palette.title.copy(alpha = HIDDEN_ROW_ALPHA) else palette.title
            Text(
                text = row.label,
                color = if (title) titleInk else faded,
                fontSize = if (title) fontSize * TITLE_SCALE else fontSize,
                fontWeight = if (title) FontWeight.Bold else null,
                lineHeight = if (title) lineHeight * TITLE_SCALE else lineHeight,
                style = LocalTextStyle.current.copy(lineHeightStyle = lineHeightStyle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // The alternative is chosen **here**, against the width this text is actually handed.
        //
        // It used to be picked one level up, by measuring the label and the buttons and subtracting
        // them from the plate. That arithmetic and the layout disagreed — the row was measured in
        // one style and rendered in another, and the gaps and the swatch were not all in the sum —
        // so the widest alternative was chosen and then ellipsised, which is the failure the
        // alternatives exist to prevent. A `BoxWithConstraints` cannot disagree with itself: its
        // `maxWidth` *is* the space left after every sibling has taken its share.
        if (row.alternatives.any { it.isNotBlank() }) {
            BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
                val style = LocalTextStyle.current.copy(
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    lineHeightStyle = lineHeightStyle,
                )
                val room = constraints.maxWidth
                val value = remember(row.alternatives, room, fontSize) {
                    row.alternatives.firstOrNull { text ->
                        measurer.measure(text, style, maxLines = 1).size.width <= room
                    } ?: row.alternatives.last()
                }
                Text(
                    // The letters in the axis ink and the numbers in the direction colour, which is
                    // how TradingView sets `O 77,004.19 H …`: the label is a caption, the figure is
                    // the reading. One colour for both made the row a coloured sentence.
                    text = if (row.target == ChartLegendTarget.Series) {
                        ohlcAnnotated(value, labels = palette.text, values = faded)
                    } else {
                        AnnotatedString(value)
                    },
                    color = faded,
                    fontSize = fontSize,
                    style = style,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Right,
                )
            }
        }
        // `slots == 0` is the closed legend, and it means *no controls at all* rather than controls
        // with no width reserved for them. Reserving zero columns and then drawing three buttons
        // into them is the shape of the bug this gate closes: the plate came back to its old width
        // with the row heights of the new one.
        if (row.primary && slots > 0) {
            Spacer(modifier = Modifier.width(LEGEND_ACTIONS_GAP_DP))
            LegendButton(
                glyph = if (dimmed) GLYPH_HIDDEN else GLYPH_VISIBLE,
                description = if (dimmed) SHOW_LABEL else HIDE_LABEL,
                colour = palette.text,
                fontSize = fontSize,
            ) { onToggleVisibility(row.target) }
            onOpenSettings?.let { settings ->
                LegendButton(
                    glyph = GLYPH_SETTINGS,
                    description = SETTINGS_LABEL,
                    colour = palette.text,
                    fontSize = fontSize,
                ) { settings(row.target) }
            }
            onRemove?.let { remove ->
                LegendButton(
                    glyph = GLYPH_REMOVE,
                    description = REMOVE_LABEL,
                    colour = palette.text,
                    fontSize = fontSize,
                ) { remove(row.target) }
            }
            val carried = 1 + (if (onOpenSettings != null) 1 else 0) + (if (onRemove != null) 1 else 0)
            repeat((slots - carried).coerceAtLeast(0)) {
                Spacer(modifier = Modifier.size(LEGEND_BUTTON_DP))
            }
        }
        disclosure?.let { toggle ->
            // The gap is only added here when the buttons above did not already add one, so a
            // closed legend puts exactly one control at the end of one row and nothing else.
            if (!row.primary || slots == 0) Spacer(modifier = Modifier.width(LEGEND_ACTIONS_GAP_DP))
            LegendButton(
                glyph = if (disclosed) GLYPH_COLLAPSE else GLYPH_EXPAND,
                description = if (disclosed) COLLAPSE_LABEL else EXPAND_LABEL,
                colour = palette.text,
                fontSize = fontSize,
                onClick = toggle,
            )
        }
    }
}

/**
 * One of the three buttons on a legend row.
 *
 * A glyph rather than a vector asset, because `core:chart` ships no drawables and a legend button is
 * a small mark on a plate — at that size an icon and a glyph are the same picture.
 *
 * The mark and its footprint are [LEGEND_BUTTON_DP]; what a thumb has to hit is [LEGEND_TOUCH_DP],
 * which is larger than the row it sits in and costs the row nothing. See [touchTarget].
 */
@Composable
private fun LegendButton(
    glyph: String,
    description: String,
    colour: Color,
    fontSize: TextUnit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .touchTarget(footprint = LEGEND_BUTTON_DP, target = LEGEND_TOUCH_DP)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, color = colour, fontSize = fontSize)
    }
}

/**
 * A touch target larger than the space the control occupies in its row.
 *
 * ### Why not simply make the button 44dp
 *
 * Because the plate has a height budget of a quarter of the plot, which is 75dp on the 300dp chart
 * this app puts on the symbol page. Rows of 44dp would fit one of them. The legend would have
 * gained a thumb-sized eye and lost the four readings it exists to print, which is not a trade
 * worth making — a reader who cannot see their EMA does not need a button for it.
 *
 * ### Why not `requiredSize`
 *
 * A required size is still the size the child *reports* to its parent, so the row grows to it and
 * the paragraph above applies unchanged. This measures the child at [target] and then lays out
 * [footprint], placing the child centred on it so that it overflows equally on all four sides.
 * Compose hit tests where a child was placed rather than where its parent's box ends, so the
 * overflow is pressable — the one thing that could take it away is a clipping ancestor, which is
 * why the plate above paints its rounded background rather than clipping to it.
 *
 * The overflow is [LEGEND_TOUCH_DP] minus [LEGEND_BUTTON_DP], halved: ten density-independent
 * pixels on each side, which is less than the plate's own padding and inset together, so a target
 * never reaches past the legend into the candles. Where two stacked rows' targets meet, the lower
 * row wins the shared band — so a button in the middle of a legend owns the full target across and
 * its row's pitch down, and the outermost rows own the whole of it in both directions.
 */
private fun Modifier.touchTarget(footprint: Dp, target: Dp): Modifier = layout { measurable, _ ->
    val reach = target.roundToPx()
    val box = footprint.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(reach, reach))
    layout(box, box) {
        placeable.place((box - reach) / 2, (box - reach) / 2)
    }
}

/**
 * The space a button takes in a legend row.
 *
 * Smaller than any tap target ought to be, on purpose, and it is not the tap target — see
 * [touchTarget]. This is the room the row pays for, and the row is one of four the plate can afford.
 */
private val LEGEND_BUTTON_DP = 24.dp

/** The series title against the values: 16 px over 13 px on TradingView, which is this ratio. */
private const val TITLE_SCALE = 1.25f

/**
 * `O 77,004.19 H 77,182.00` with the letters in one colour and the figures in another.
 *
 * A token is a label when it is a single letter or the change mark — `O`, `H`, `L`, `C`, `Δ` —
 * and a figure otherwise. Whitespace is kept as it was, so the widest and the narrowest forms in
 * [legendRows] both survive with their own spacing.
 */
internal fun ohlcAnnotated(text: String, labels: Color, values: Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val blank = text[index].isWhitespace()
        var end = index
        while (end < text.length && text[end].isWhitespace() == blank) end++
        val token = text.substring(index, end)
        val label = token.length == 1 && (token[0].isLetter() || token[0] == 'Δ')
        withStyle(SpanStyle(color = if (label) labels else values)) { append(token) }
        index = end
    }
}

/**
 * What a thumb has to hit.
 *
 * Forty-four, the smaller of the two figures the accessibility guidance gives, chosen over 48
 * because the overflow has to stay inside the plate's inset — see [touchTarget] — and 48 would put
 * it two density-independent pixels into the candles.
 */
private val LEGEND_TOUCH_DP = 44.dp

/**
 * The gap between the reading and the buttons.
 *
 * Wider than the gap between the buttons themselves, so the cluster reads as a set of controls
 * rather than as the last two words of the reading. It is also the strip a thumb aiming at the eye
 * lands on when it falls short, which is the harmless place for it to land.
 */
private val LEGEND_ACTIONS_GAP_DP = 8.dp

/** The colour dot that ties a row to its line. */
/**
 * The legend's line box, as a multiple of its type size.
 *
 * 1.15, which is a hair over the cap-to-descender reach of the Latin figures this row is mostly
 * made of and tight enough that three rows read as three rows of one thing. See the note in
 * [LegendRow] for why it has to be stated at all rather than left to the font.
 */
private const val LEGEND_LINE_HEIGHT = 1.15f

private val SWATCH_DP = 6.dp

/** How far a hidden row fades, so it reads as switched off rather than as missing. */
private const val HIDDEN_ROW_ALPHA = 0.35f

/**
 * How the change row names itself.
 *
 * A delta and not «تغییر». The row is otherwise a run of Latin market figures, and one Persian word
 * at the head of it flips the run's direction for the reader as well as costing four times the
 * width of the mark — on the narrowest phone that word is the difference between the row fitting
 * and the row being the percentage alone. Δ is the mark every terminal in this category uses for
 * exactly this figure.
 */
private const val CHANGE_LABEL = "Δ"

/**
 * Two decimals on the percentage, whatever the instrument's own precision.
 *
 * A percentage is not a price: gold at one decimal and a memecoin at six both move by fractions of
 * a percent, and six decimals of one would be a number nobody reads to the end of.
 */
private const val CHANGE_PERCENT_DECIMALS = 2

private const val GLYPH_VISIBLE = "◉"
private const val GLYPH_HIDDEN = "◌"
private const val GLYPH_SETTINGS = "⋮"
private const val GLYPH_REMOVE = "✕"

/**
 * The disclosure, closed and open.
 *
 * A horizontal ellipsis for "there is more here" and a chevron for "put it away", which is the pair
 * every dense interface uses and the pair that needs no label at four millimetres across. Neither
 * collides with the three above: the eye is a disc, settings is a *vertical* ellipsis, and remove is
 * a cross.
 */
private const val GLYPH_EXPAND = "⋯"
private const val GLYPH_COLLAPSE = "⌃"

private const val HIDE_LABEL = "پنهان کردن"
private const val SHOW_LABEL = "نمایش دادن"
private const val SETTINGS_LABEL = "تنظیمات"
private const val REMOVE_LABEL = "حذف"
private const val EXPAND_LABEL = "کنترل‌های اندیکاتورها"
private const val COLLAPSE_LABEL = "بستن کنترل‌ها"
