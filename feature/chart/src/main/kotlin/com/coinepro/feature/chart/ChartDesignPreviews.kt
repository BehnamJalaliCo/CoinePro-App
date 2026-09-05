package com.coinepro.feature.chart

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.Drawing
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe

/**
 * The chart's sheets, drawable on their own for a design review.
 *
 * The sheets themselves are `internal`: they take the chart's live state and a controller, and
 * nothing outside this module should be composing them. But the design work of Sprint A needs a
 * picture of each one in both themes and both locales, before and after — and the screenshot rig
 * lives in `app`, which cannot see an internal composable. These wrappers are the one public
 * door: each takes what a fixture can supply and fills the callbacks with no-ops. They draw
 * exactly what the reader sees, because they call the same composable the screen does.
 */
object ChartDesignPreviews {

    /** The timeframe sheet — date-range chips over the interval groups. */
    @Composable
    fun TimeframeSheet(modifier: Modifier = Modifier) {
        IntervalSheetBody(
            selected = ChartInterval.Preset(Timeframe.M15),
            onSelect = {},
            range = null,
            onSelectRange = {},
            starred = TimeframeFavourites.DEFAULT,
            onStar = {},
            onHide = {},
        )
    }

    /** The drawing-tools sheet, with the trend line armed so its template row is in the picture. */
    @Composable
    fun DrawingsSheet(controller: ChartController, modifier: Modifier = Modifier) {
        val state by controller.state.collectAsState()
        ChartToolPalette(
            state = state,
            controller = controller,
            templates = emptyList(),
            defaultTemplateId = null,
            onHelp = {},
            onArmed = {},
            modifier = modifier.fillMaxSize(),
            onZoomIn = {},
            onZoomOut = {},
        )
    }

    /** A trend line's settings sheet: colour, width, order, delete. */
    @Composable
    fun DrawingSettingsSheet() {
        DrawingStyleSheetBody(
            drawing = Drawing(
                id = 1,
                toolId = "trend",
                points = listOf(ChartPoint(1_700_000_000L, 2_310.0), ChartPoint(1_700_360_000L, 2_348.0)),
            ),
            templates = emptyList(),
            defaultTemplateId = null,
            onSetColour = {},
            onSetWidth = {},
            onSetDeviations = {},
            onApplyTemplate = {},
            onSaveTemplate = {},
            onDeleteTemplate = {},
            onSetDefaultTemplate = {},
            onBringToFront = {},
            onSendToBack = {},
            onDelete = {},
        )
    }

    /** An EMA's settings sheet, opened on its Style tab so the swatches are in the picture. */
    @Composable
    fun IndicatorSettings() {
        IndicatorSettingsBody(
            option = com.coinepro.core.chart.ChartCatalog.INDICATORS.first { it.id == "ema" },
            period = 50,
            colour = null,
            widthDp = null,
            hidden = false,
            onSetPeriod = {},
            onSetColour = {},
            onSetWidth = {},
            onToggleHidden = {},
            onRemove = {},
            initialTab = IndicatorSettingsTab.STYLE,
        )
    }

    /** The analysis hub — the «•••» sheet of tiles. */
    @Composable
    fun AnalysisHub(controller: ChartController) {
        val state by controller.state.collectAsState()
        ChartMoreSheetBody(
            range = null,
            onSelectRange = {},
            bars = state.series.bars,
            onGoToDate = {},
            onUndo = {},
            onRedo = null,
            comparisons = 0,
            scaleLabel = "خودکار",
            scaleAdjusted = false,
            onOpen = {},
            onCreateAlert = {},
            onBacktest = {},
            onShare = {},
            onOpenStudio = {},
            onEvents = {},
            onOpenTerminal = {},
            onAskAi = {},
            onTrade = {},
            onReplay = {},
            onHelpCenter = {},
        )
    }
}
