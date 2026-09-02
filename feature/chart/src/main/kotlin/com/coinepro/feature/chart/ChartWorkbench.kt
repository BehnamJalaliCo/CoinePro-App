package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * Which permanent columns the chart page has room for, worked out from the room it was given.
 *
 * Three states rather than "phone or tablet", because the two columns cost different amounts and
 * become affordable at different widths — and because the interesting device, a tablet held
 * upright, sits between them. Everything here is measured against the *content* area, which is the
 * window less whatever the navigation rail took; a decision made against the window would put the
 * tool column on at exactly the width where it no longer fits.
 */
@Immutable
enum class ChartWorkbenchColumns {
    /** Nothing permanent. The phone page, unchanged: every control is a sheet. */
    NONE,

    /** The drawing tools, permanently open beside the plot. What a tablet held upright gets. */
    TOOLS,

    /**
     * The readings only.
     *
     * Reachable from a caller that offers no palette — the studio, which has its own. It exists as
     * its own case rather than being folded into [TOOLS_AND_READINGS] because the page reads
     * [hasTools] to decide whether to drop the band's drawing button, and a state that claims tools
     * nobody composed would take that button away and leave nothing in its place.
     */
    READINGS,

    /** The tools and the readings panel both. A tablet held sideways, or a desktop window. */
    TOOLS_AND_READINGS,
    ;

    val hasTools: Boolean get() = this == TOOLS || this == TOOLS_AND_READINGS
    val hasReadings: Boolean get() = this == READINGS || this == TOOLS_AND_READINGS
}

/**
 * The chart page, with the columns a large screen can afford.
 *
 * ### What this is for
 *
 * The chart page was designed for one screenful and every decision in it followed from that: the
 * tools are a sheet, the readings are a block under the plot that scrolls away, and the plot itself
 * is a fraction of a phone's height. All three are right on a phone and all three are wrong on a
 * tablet, where the page is a single column of content down the middle of a metre of glass with
 * nothing at either side.
 *
 * So on a large window this puts the tools back where a terminal keeps them — permanently open, at
 * the reading-start edge, no sheet to open and close between two lines — and gives the readings
 * their own column instead of a band the reader scrolls past. The plot keeps the middle and takes
 * every point neither column needed.
 *
 * ### Why the columns arrive one at a time
 *
 * Because they cost different amounts, and a rule that switched both on at 840dp would leave a
 * portrait tablet with a 260dp plot — which is worse than the phone layout it replaced, on a bigger
 * device. [columnsFor] spends the width in order of what a reader touches most: the tools first,
 * because arming a tool is a per-minute action and opening a sheet for it is the single most
 * repeated cost on this page; the readings second, because they are read once a visit.
 *
 * Both are refused before [CHART_MIN_PLOT_WIDTH] is broken. A chart screen whose chart is narrower
 * than the same chart on a phone is not a tablet layout, it is a regression with more furniture.
 *
 * ### Right-to-left
 *
 * The tools are drawn first in the `Row` and the readings last, so the palette takes the start edge
 * — the right in Persian — and the readings the end. Nothing here names left or right, and the two
 * dividers are siblings between the columns, so they land correctly in both directions.
 */
@Composable
internal fun ChartWorkbench(
    modifier: Modifier = Modifier,
    /** The drawing palette. Null on a caller that has no tools to offer. */
    tools: (@Composable (Modifier) -> Unit)? = null,
    /** The readings, the drawn setup, the studio entry — everything that belongs beside the plot. */
    readings: (@Composable (Modifier) -> Unit)? = null,
    /**
     * The page itself, told which columns were taken out of it.
     *
     * It has to know, because the blocks that moved into the side column must not also be drawn in
     * the page — two readings panels on one screen is the failure this parameter exists to prevent.
     */
    page: @Composable (Modifier, ChartWorkbenchColumns) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = columnsFor(
            width = maxWidth,
            hasTools = tools != null,
            hasReadings = readings != null,
        )
        if (columns == ChartWorkbenchColumns.NONE) {
            page(Modifier.fillMaxSize(), columns)
            return@BoxWithConstraints
        }
        Row(modifier = Modifier.fillMaxSize()) {
            if (columns.hasTools && tools != null) {
                tools(Modifier.width(CHART_TOOL_COLUMN).fillMaxHeight())
                VerticalDivider(color = CoineProColors.Border)
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                page(Modifier.fillMaxSize(), columns)
            }
            if (columns.hasReadings && readings != null) {
                VerticalDivider(color = CoineProColors.Border)
                readings(Modifier.width(CHART_READINGS_COLUMN).fillMaxHeight())
            }
        }
    }
}

/**
 * Which columns [width] can pay for, in the order a reader would miss them.
 *
 * Not a size class. The size classes answer "is this a tablet"; this answers "is there 280 points
 * spare after the plot has had the 440 it needs", which is the only question the layout actually
 * has. The two agree on real hardware and would diverge on a multi-window split, where this one is
 * right.
 */
internal fun columnsFor(
    width: Dp,
    hasTools: Boolean,
    hasReadings: Boolean,
): ChartWorkbenchColumns {
    val both = CHART_TOOL_COLUMN + CHART_READINGS_COLUMN + CHART_MIN_PLOT_WIDTH
    return when {
        hasTools && hasReadings && width >= both -> ChartWorkbenchColumns.TOOLS_AND_READINGS
        hasTools && width >= CHART_TOOL_COLUMN + CHART_MIN_PLOT_WIDTH -> ChartWorkbenchColumns.TOOLS
        // Readings without tools is reachable only from a caller that offers no palette — the
        // studio, say. It still earns its column at the same price the tools would have paid.
        !hasTools && hasReadings && width >= CHART_READINGS_COLUMN + CHART_MIN_PLOT_WIDTH ->
            ChartWorkbenchColumns.READINGS
        else -> ChartWorkbenchColumns.NONE
    }
}

/**
 * The drawing palette, for both places it is drawn.
 *
 * Extracted because there are now two: the sheet a phone opens, and the column a tablet keeps open.
 * Every one of `ToolRail`'s parameters was a feature that existed and could not be reached until
 * this call site passed it, so a second copy of the call is a second chance to leave one out — and
 * the failure would be silent, because the rail simply omits the row a missing callback belongs to.
 *
 * [onArmed] is what differs between the two: the sheet closes itself once a tool is armed, and the
 * column stays open, because staying open is the whole reason it is a column.
 */
@Composable
internal fun ChartToolPalette(
    state: ChartUiState,
    controller: ChartController,
    /** The armed tool's saved styles. See `ToolTemplateRow`. */
    templates: List<DrawingTemplate>,
    defaultTemplateId: String?,
    onHelp: ((String) -> Unit)?,
    onArmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(CoineProColors.Surface)) {
        // The armed tool's saved styles, above the rail rather than on the toolbar. Choosing one
        // arms the tool *and* sets the style in a single tap, which is the whole point: "draw a
        // trend line the way I always draw trend lines" is one decision, not two.
        ToolTemplateRow(
            tool = state.drawing.tool,
            templates = templates,
            defaultTemplateId = defaultTemplateId,
            onApply = { template ->
                controller.armWithStyle(state.drawing.tool, template.colour, template.widthDp)
                onArmed()
            },
        )
        ToolRail(
            selected = state.drawing.tool?.id,
            onSelect = { tool ->
                // Plain arm. This tool's own default template, where the reader has set one, is
                // applied by the effect that watches the armed tool — one place rather than two, so
                // the studio's rail gets the same behaviour without repeating it.
                controller.arm(tool)
                onArmed()
            },
            onHelp = onHelp,
            // Every parameter the rail takes, and each one was previously a feature that existed
            // and could not be reached. The rail's own action row — magnet, keep drawing, lock all,
            // hide layers — renders only when a caller offers at least one of these callbacks, and
            // neither call site offered any: the whole row had never been on a screen. The magnet
            // in particular was `OFF` for the life of the app, which also made the OHLC channel
            // bindings dead code, since one is written only by a tap the magnet moved.
            //
            // `hasVolume` is the one that was actively wrong rather than merely absent. On the MT5
            // forex feed a reader could arm a volume-profile tool and watch it draw nothing,
            // because the rail was offering three tools the renderer has no column for.
            hasVolume = state.series.hasVolume,
            favourites = state.drawing.favourites,
            onToggleFavourite = { controller.toggleToolFavourite(it.id) },
            magnet = state.drawing.magnetMode,
            onCycleMagnet = controller::cycleMagnet,
            keepDrawing = state.drawing.keepDrawing,
            onKeepDrawing = controller::setKeepDrawing,
            lockedAll = state.drawing.lockedAll,
            onLockAll = controller::setLockAllDrawings,
            hidden = state.drawing.hidden,
            onHide = controller::setLayerHidden,
            onHideAll = controller::setAllLayersHidden,
            // «Remove all objects», as the phone app's Drawings sheet offers it. One tap and
            // reversible from the toolbar's undo, which is what makes a confirmation unnecessary.
            onRemoveAll = controller::clearDrawings,
        )
    }
}

/**
 * The palette with a heading over it, for the permanent column.
 *
 * The sheet gets its title and its tool count from `CoineProSheet`; the column has no sheet to get
 * them from, and a rail of eighty-five glyphs with nothing saying what it is reads as decoration
 * rather than as the tool box.
 */
@Composable
internal fun ChartToolColumn(
    state: ChartUiState,
    controller: ChartController,
    templates: List<DrawingTemplate>,
    defaultTemplateId: String?,
    onHelp: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.background(CoineProColors.Surface)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CoineProSpacing.Gutter,
                    end = CoineProSpacing.Gutter,
                    top = CoineProSpacing.OneHalf,
                ),
        ) {
            Text(
                text = stringResource(R.string.chart_tools_column_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                // Persian digits: a tool count is prose, not a market figure.
                text = stringResource(
                    R.string.chart_tools_column_count,
                    DrawingTools.ALL.size.toPersianDigits(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        ChartToolPalette(
            state = state,
            controller = controller,
            templates = templates,
            defaultTemplateId = defaultTemplateId,
            onHelp = onHelp,
            // Nothing. The column stays open after a tool is armed, which is the difference between
            // a palette and a sheet: a reader drawing three lines on one chart arms once and draws
            // three times, instead of opening the sheet three times.
            onArmed = {},
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/**
 * The side column: everything about the chart that is *read* rather than *touched*.
 *
 * It scrolls on its own, so a reader can leave the plot exactly where it is and still reach the
 * setup card. That is the whole reason this is a column rather than a taller page — on the phone
 * these blocks live under the plot, and reaching them means scrolling the plot off the screen.
 */
@Composable
internal fun ChartReadingsColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(vertical = CoineProSpacing.One),
    ) {
        content()
    }
}

/**
 * How wide the permanent tool column is.
 *
 * `ToolRail` lays its cells out four across at a fixed height, so this is the width at which four
 * of them are square-ish rather than four slivers. Narrower and the glyph and its label stop
 * fitting in one cell, which is the point at which a palette becomes a puzzle.
 */
internal val CHART_TOOL_COLUMN = 280.dp

/** The readings panel is three columns with a meter under each; 320 is the width it was drawn at. */
internal val CHART_READINGS_COLUMN = 320.dp

/**
 * The narrowest the plot may be left after the columns have taken theirs.
 *
 * A large phone is 411dp wide and the plot is bled to both edges of it, so 440dp is the promise
 * that the chart on a tablet is never narrower than the chart on a phone. It is a low bar and it is
 * deliberately the bar: a column is worth having only if the reader does not pay for it in the one
 * thing they came to the screen for.
 */
internal val CHART_MIN_PLOT_WIDTH = 440.dp
