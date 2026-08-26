package com.coinepro.core.chart

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every chart type and indicator the app offers must have help behind its «؟».
 *
 * The two lists are written by hand in two different modules — the options here, the content in
 * `core:help` — and nothing connects them but a string. A typo produces a «؟» that opens nothing,
 * or worse, opens the wrong tool's explanation. Both were real: `sma` and `williamsr` were wrong on
 * the first pass, because the web terminal calls them `ma` and `willr`.
 *
 * The check reads the shipped JSON directly rather than parsing it through `core:help`, so this
 * module does not have to depend on that one just to be tested.
 */
class ChartCatalogTest {

    private val helpIds: Set<String> = run {
        val file = File("../help/src/main/assets/help/content.json")
        assertTrue(
            "The help catalogue is not where this test expects it: ${file.absolutePath}",
            file.exists(),
        )
        // Top-level keys only. A real parser would be better and would also mean a dependency on
        // Gson here for one line; the ids are the object's own keys and this reads them exactly.
        Regex("^  \"([^\"]+)\":", RegexOption.MULTILINE)
            .findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
            .ifEmpty {
                Regex("\"([A-Za-z0-9_+-]+)\"\\s*:\\s*\\{\\s*\"title\"")
                    .findAll(file.readText())
                    .map { it.groupValues[1] }
                    .toSet()
            }
    }

    @Test
    fun `the help catalogue was actually read`() {
        assertTrue("no ids parsed out of the catalogue", helpIds.size > 150)
    }

    @Test
    fun `every chart type points at a real help entry`() {
        for (option in ChartCatalog.CHART_TYPES) {
            assertTrue(
                "chart type ${option.type} points at '${option.helpId}', which has no help entry",
                option.helpId in helpIds,
            )
        }
    }

    @Test
    fun `every indicator points at a real help entry`() {
        for (option in ChartCatalog.INDICATORS) {
            assertTrue(
                "indicator ${option.id} points at '${option.helpId}', which has no help entry",
                option.helpId in helpIds,
            )
        }
    }

    @Test
    fun `every chart type the engine can draw is offered`() {
        // Otherwise a type exists in the engine, is tested, and no reader can ever reach it.
        assertEquals(ChartType.entries.toSet(), ChartCatalog.CHART_TYPES.map { it.type }.toSet())
    }

    @Test
    fun `no option is listed twice`() {
        assertEquals(
            ChartCatalog.INDICATORS.size,
            ChartCatalog.INDICATORS.map { it.id }.toSet().size,
        )
        assertEquals(
            ChartCatalog.CHART_TYPES.size,
            ChartCatalog.CHART_TYPES.map { it.type }.toSet().size,
        )
    }

    @Test
    fun `every price-pane indicator actually produces lines`() {
        // A price-pane indicator with no overlay is a switch that does nothing when tapped.
        val series = CandleSeries(
            (0 until 120).map { index ->
                val base = 100.0 + index * 0.2
                Candle(1_700_000_000L + index * 3600, base, base + 1, base - 1, base + 0.4, 500.0)
            },
        )
        for (option in ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.PRICE }) {
            val lines = ChartCatalog.overlayFor(option, series)
            assertTrue("${option.id} draws nothing on the price pane", lines.isNotEmpty())
            assertTrue(
                "${option.id} produced a line with no values at all",
                lines.all { line -> (0 until series.size).any { line.values.isPresent(it) } },
            )
        }
    }

    @Test
    fun `a separate-pane indicator draws nothing over the price`() {
        // Plotting RSI's 0-100 against a gold price of 2,600 collapses the price axis to a line.
        val series = CandleSeries(
            (0 until 60).map { Candle(it.toLong(), 100.0, 101.0, 99.0, 100.5, 10.0) },
        )
        for (option in ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.SEPARATE }) {
            assertTrue(
                "${option.id} must not draw on the price pane",
                ChartCatalog.overlayFor(option, series).isEmpty(),
            )
        }
    }

    @Test
    fun `an empty series produces no overlay rather than an exception`() {
        for (option in ChartCatalog.INDICATORS) {
            assertTrue(ChartCatalog.overlayFor(option, CandleSeries.EMPTY).isEmpty())
        }
    }
}
