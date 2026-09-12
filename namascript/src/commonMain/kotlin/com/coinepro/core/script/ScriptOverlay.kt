package com.coinepro.core.script

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ChartPane
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.MarkerGlyph
import com.coinepro.core.chart.PriceLevel
import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.chart.formatFixed

/**
 * What a script draws, in the chart's own vocabulary.
 *
 * The whole point of this file is that a reader's script and the app's own indicators arrive at the
 * renderer as the *same* types. A script gets no second-class drawing path: its moving average is a
 * [ChartLine] exactly like the catalogue's, its own-pane plots share the pane renderer with RSI,
 * and its `signal()` becomes the same [SignalOverlay] an AI signal produces. If a script could draw
 * something the app could not, the two would drift apart the first time either changed.
 */
data class ScriptOverlay(
    val overlays: List<ChartLine> = emptyList(),
    val levels: List<PriceLevel> = emptyList(),
    val markers: List<ChartMarker> = emptyList(),
    val pane: ChartPane? = null,
    val signal: SignalOverlay? = null,
    /** The script's labels, lines and boxes as the reader's own drawing types (4.61.0). */
    val drawings: List<Drawing> = emptyList(),
) {
    val isEmpty: Boolean
        get() = overlays.isEmpty() && levels.isEmpty() && markers.isEmpty() &&
            pane == null && signal == null && drawings.isEmpty()
}

/**
 * Converts one run's output into things the chart can draw over [series].
 *
 * Every own-pane plot goes into **one** pane rather than one pane each. A script that plots an RSI
 * and its own smoothing means them read together, and two strips would put them on two scales —
 * which is exactly the comparison the script was written to make impossible to get wrong.
 *
 * [title] names that pane. It is the script's name, because the pane is the script.
 */
fun ScriptResult.toOverlay(series: CandleSeries, title: String): ScriptOverlay {
    if (!ok) return ScriptOverlay()

    // **The first line over the price carries the script's name, not the plot's** (run K item 4).
    //
    // The legend collapses to one row, and that row is the first of these. With the plot's own title
    // on it the plate read «حد ضرر خرید · 76,350.1 ▸ +4»: a phrase from inside somebody's source,
    // with no indication of which study it belongs to, over a chart carrying five. Every terminal
    // names the *study* on its legend row and the plots inside it after. So the lead line is the
    // script — which is what the reader switched on, and what the gear beside it opens — and the
    // rest keep their own titles, which is how two plots of one script stay apart when the plate is
    // open.
    val overlays = plots.filter { !it.ownPane }.mapIndexed { index, plot ->
        ChartLine(
            values = plot.values,
            colour = plot.colour,
            widthDp = plot.widthDp,
            label = if (index == 0) title else plot.title,
            dashed = plot.dashed,
            stepped = plot.stepped,
        )
    }
    val paneLines = plots.filter { it.ownPane }.map { plot ->
        ChartLine(
            values = plot.values,
            colour = plot.colour,
            widthDp = plot.widthDp,
            label = plot.title,
            dashed = plot.dashed,
            stepped = plot.stepped,
        )
    }
    val priceLevels = levels.filter { !it.ownPane }.map { it.toPriceLevel() }
    val paneLevels = levels.filter { it.ownPane }.map { it.toPriceLevel() }

    val markers = markers.flatMap { marker ->
        marker.bars.mapNotNull { index ->
            val bar = series.bars.getOrNull(index) ?: return@mapNotNull null
            val above = marker.style != ScriptMarkerStyle.ARROW_UP
            ChartMarker(
                time = bar.t,
                // An up arrow sits under the bar's low and a down arrow over its high — pointing
                // at the bar from the side the move is expected to go. A mark on the wrong side
                // reads as the opposite call.
                price = if (above) bar.h else bar.l,
                above = above,
                colour = marker.colour,
                glyph = when (marker.style) {
                    ScriptMarkerStyle.ARROW_UP -> MarkerGlyph.ARROW_UP
                    ScriptMarkerStyle.ARROW_DOWN -> MarkerGlyph.ARROW_DOWN
                    ScriptMarkerStyle.CIRCLE -> MarkerGlyph.CIRCLE
                },
                text = marker.title,
            )
        }
    }

    // A trade is two marks: the entry, pointing the way it went, and the exit with its return.
    val tradeMarkers = strategy?.trades.orEmpty().flatMap { trade ->
        val entry = series.bars.getOrNull(trade.entryBar) ?: return@flatMap emptyList()
        val exit = series.bars.getOrNull(trade.exitBar) ?: return@flatMap emptyList()
        listOfNotNull(
            ChartMarker(
                time = entry.t,
                price = if (trade.long) entry.l else entry.h,
                above = !trade.long,
                colour = if (trade.long) 0xFF00B15C else 0xFFF6465D,
                glyph = if (trade.long) MarkerGlyph.ARROW_UP else MarkerGlyph.ARROW_DOWN,
                text = trade.id,
            ),
            if (trade.open) null else ChartMarker(
                time = exit.t,
                price = if (trade.long) exit.h else exit.l,
                above = trade.long,
                colour = if (trade.returnPercent >= 0) 0xFF00B15C else 0xFFF6465D,
                glyph = MarkerGlyph.CIRCLE,
                text = (if (trade.returnPercent >= 0) "+" else "") + formatFixed(trade.returnPercent, 1) + "%",
            ),
        )
    }

    val marks = drawings.mapIndexedNotNull { index, drawing ->
        val times = drawing.bars.map { series.bars.getOrNull(it)?.t ?: return@mapIndexedNotNull null }
        when (drawing.kind) {
            ScriptDrawingKind.LABEL -> Drawing(
                id = SCRIPT_DRAWING_ID_BASE + index,
                toolId = "text",
                points = listOf(ChartPoint(times[0], drawing.prices[0])),
                colour = drawing.colour,
                textColour = drawing.textColour,
                text = drawing.text,
            )
            ScriptDrawingKind.LINE -> Drawing(
                id = SCRIPT_DRAWING_ID_BASE + index,
                toolId = "trend",
                points = listOf(ChartPoint(times[0], drawing.prices[0]), ChartPoint(times[1], drawing.prices[1])),
                colour = drawing.colour,
                widthDp = drawing.widthDp,
            )
            ScriptDrawingKind.BOX -> Drawing(
                id = SCRIPT_DRAWING_ID_BASE + index,
                toolId = "rect",
                points = listOf(ChartPoint(times[0], drawing.prices[0]), ChartPoint(times[1], drawing.prices[1])),
                colour = drawing.colour,
                fillColour = drawing.colour,
                text = drawing.text,
            )
        }
    }

    return ScriptOverlay(
        overlays = overlays,
        levels = priceLevels,
        markers = markers + tradeMarkers,
        drawings = marks,
        pane = if (paneLines.isEmpty() && paneLevels.isEmpty()) {
            null
        } else {
            ChartPane(title = title, lines = paneLines, levels = paneLevels)
        },
        signal = setup?.let { setup ->
            SignalOverlay(
                entry = setup.entry,
                stopLoss = setup.stop,
                takeProfits = listOfNotNull(setup.target),
                isLong = setup.buy,
                issuedAt = series.bars.getOrNull(setup.barIndex)?.t,
                entryLabel = "ورود",
                stopLabel = "حد ضرر",
                targetLabels = if (setup.target == null) emptyList() else listOf("هدف"),
            )
        },
    )
}

/** Ids a script's objects carry, far above anything a reader's own drawings are numbered. */
private const val SCRIPT_DRAWING_ID_BASE = 1_000_000_000L

private fun ScriptLevel.toPriceLevel(): PriceLevel =
    PriceLevel(price = price, colour = colour, label = title)
