package com.coinepro.core.script

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ChartPane
import com.coinepro.core.chart.MarkerGlyph
import com.coinepro.core.chart.PriceLevel
import com.coinepro.core.chart.SignalOverlay

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
) {
    val isEmpty: Boolean
        get() = overlays.isEmpty() && levels.isEmpty() && markers.isEmpty() &&
            pane == null && signal == null
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

    val overlays = plots.filter { !it.ownPane }.map { plot ->
        ChartLine(
            values = plot.values,
            colour = plot.colour,
            widthDp = plot.widthDp,
            label = plot.title,
            dashed = plot.dashed,
        )
    }
    val paneLines = plots.filter { it.ownPane }.map { plot ->
        ChartLine(
            values = plot.values,
            colour = plot.colour,
            widthDp = plot.widthDp,
            label = plot.title,
            dashed = plot.dashed,
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

    return ScriptOverlay(
        overlays = overlays,
        levels = priceLevels,
        markers = markers,
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

private fun ScriptLevel.toPriceLevel(): PriceLevel =
    PriceLevel(price = price, colour = colour, label = title)
