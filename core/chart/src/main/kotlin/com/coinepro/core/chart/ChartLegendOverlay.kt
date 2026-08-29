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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    rows += ChartLegendRow(
        target = ChartLegendTarget.Series,
        label = seriesLabel.orEmpty(),
        alternatives = listOf(
            "O ${formatPrice(bar.o, decimals)}   H ${formatPrice(bar.h, decimals)}   " +
                "L ${formatPrice(bar.l, decimals)}   C ${formatPrice(bar.c, decimals)}",
            "O ${formatPrice(bar.o, decimals)} H ${formatPrice(bar.h, decimals)} " +
                "L ${formatPrice(bar.l, decimals)} C ${formatPrice(bar.c, decimals)}",
            "${formatPrice(bar.o, decimals)} ${formatPrice(bar.h, decimals)} " +
                "${formatPrice(bar.l, decimals)} ${formatPrice(bar.c, decimals)}",
            "C ${formatPrice(bar.c, decimals)}",
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
            alternatives = listOf(reading(overlay.values[index], decimals)),
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
    val rising = bar.up
    val cap = if (tracking) TRACKING_LEGEND_LINES else LEGEND_LINES
    // The head row is never counted against the cap — it is the reading the legend exists for — so
    // the whole cap is spent on the studies, and what does not fit is stated rather than dropped in
    // silence.
    val body = rows.drop(1)
    val shown = body.take(cap)
    val overflow = body.size - shown.size

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(modifier = modifier) {
            val density = LocalDensity.current
            val budget = maxHeight * if (tracking) TRACKING_LEGEND_BUDGET else LEGEND_BUDGET
            val available = with(density) {
                (maxWidth - LEGEND_INSET_DP * 2 - LEGEND_PLATE_PADDING_DP * 2).toPx()
            }
            val size = axisFontSizeSp(isPriceAxis = true).sp
            Column(
                modifier = Modifier
                    .padding(LEGEND_INSET_DP)
                    .widthIn(max = maxWidth - LEGEND_INSET_DP * 2)
                    .heightIn(max = budget)
                    .clip(RoundedCornerShape(LEGEND_PLATE_RADIUS_DP))
                    .background(palette.stage.copy(alpha = LEGEND_PLATE_ALPHA))
                    .padding(LEGEND_PLATE_PADDING_DP)
                    .clipToBounds(),
                verticalArrangement = Arrangement.spacedBy(LEGEND_GAP_DP),
            ) {
                val head = rows.first()
                val headStyle = axisStyle(if (rising) palette.up else palette.down)
                // The same fitting the canvas did: the separator tightens, then the labels go, and
                // only then does it fall back to the close alone — which is still true and still
                // useful. Four prices at four decimals do not fit a narrow phone at any spacing.
                val headText = head.alternatives.firstOrNull { text ->
                    measurer.measure(text, headStyle).size.width <= available
                } ?: head.alternatives.last()
                LegendRow(
                    row = head.copy(alternatives = listOf(headText)),
                    colour = if (rising) palette.up else palette.down,
                    palette = palette,
                    fontSize = size,
                    dimmed = ChartLegendTarget.Series in hidden,
                    onToggleVisibility = onToggleVisibility,
                    onOpenSettings = onOpenSettings,
                    // The price is not removable. A chart with no series on it is not a chart, and
                    // an affordance that has to refuse is worse than one that is not offered.
                    onRemove = null,
                )
                shown.forEach { row ->
                    LegendRow(
                        row = row,
                        colour = row.colour?.let { Color(opaqueArgb(it)) } ?: palette.text,
                        palette = palette,
                        fontSize = size,
                        dimmed = row.target in hidden,
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

/** One row: a swatch, the name, the reading, and the three things a reader can do to it. */
@Composable
private fun LegendRow(
    row: ChartLegendRow,
    colour: Color,
    palette: ChartPalette,
    fontSize: TextUnit,
    dimmed: Boolean,
    onToggleVisibility: (ChartLegendTarget) -> Unit,
    onOpenSettings: ((ChartLegendTarget) -> Unit)?,
    onRemove: ((ChartLegendTarget) -> Unit)?,
) {
    val faded = if (dimmed) colour.copy(alpha = HIDDEN_ROW_ALPHA) else colour
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
            Text(text = row.label, color = faded, fontSize = fontSize, maxLines = 1)
        }
        val value = row.alternatives.firstOrNull().orEmpty()
        if (value.isNotBlank()) {
            Text(text = value, color = faded, fontSize = fontSize, maxLines = 1, textAlign = TextAlign.Right)
        }
        if (row.primary) {
            Spacer(modifier = Modifier.width(LEGEND_GAP_DP))
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
        }
    }
}

/**
 * One of the three buttons on a legend row.
 *
 * A glyph rather than a vector asset, because `core:chart` ships no drawables and a legend button is
 * a 24dp target with a 10sp mark in it — at that size an icon and a glyph are the same picture. The
 * target is the full 24dp even though the mark is a third of it, which is the difference between a
 * control a thumb can hit on a moving chart and one it cannot.
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
            .size(BUTTON_DP)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = glyph, color = colour, fontSize = fontSize)
    }
}

/** The tap target on a legend row. Smaller than 48dp on purpose — see [LegendButton]. */
private val BUTTON_DP = 24.dp

/** The colour dot that ties a row to its line. */
private val SWATCH_DP = 6.dp

/** How far a hidden row fades, so it reads as switched off rather than as missing. */
private const val HIDDEN_ROW_ALPHA = 0.35f

private const val GLYPH_VISIBLE = "◉"
private const val GLYPH_HIDDEN = "◌"
private const val GLYPH_SETTINGS = "⋮"
private const val GLYPH_REMOVE = "✕"

private const val HIDE_LABEL = "پنهان کردن"
private const val SHOW_LABEL = "نمایش دادن"
private const val SETTINGS_LABEL = "تنظیمات"
private const val REMOVE_LABEL = "حذف"
