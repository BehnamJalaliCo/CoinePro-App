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
    fun `every indicator that claims a help entry has one`() {
        for (option in ChartCatalog.INDICATORS) {
            val helpId = option.helpId ?: continue
            assertTrue(
                "indicator ${option.id} points at '$helpId', which has no help entry",
                helpId in helpIds,
            )
        }
    }

    @Test
    fun `exactly these indicators have no help, and the list may only shrink`() {
        // The web terminal's help was written before these were added to it, so there is nothing to
        // point at. Pinned rather than merely tolerated: a nullable helpId makes it very easy to add
        // one more without noticing, and "no help" is a gap to close, not a default.
        val withoutHelp = ChartCatalog.INDICATORS.filter { it.helpId == null }.map { it.id }
        assertEquals(
            listOf(
                "envelopes", "stddev", "hv", "mom", "roc", "trix", "fisher", "smiErgodic", "smi",
                // The seven structure studies. Their help ids in the web terminal are attached to
                // the drawing tools of the same name — `fib`, `hline` — which explain the tool a
                // reader places by hand, not the study that places it for them. Pointing at those
                // would be worse than pointing at nothing.
                "pivots", "swings", "fractals", "zigzag", "autofib", "sr", "supplydemand",
            ),
            withoutHelp,
        )
    }

    @Test
    fun `every structure study actually draws something`() {
        // The structure equivalent of the price-pane check: a study in the list that produces no
        // lines, no levels and no markers is a switch that does nothing when tapped.
        //
        // The series oscillates rather than trending, and that is the whole reason it works. A clean
        // ramp has no support in it at all — price never revisits a level — so support/resistance
        // correctly finds nothing on one, and a fixture that trends tests the study by accident
        // rather than on purpose. This one tops out near the same price four times.
        val series = CandleSeries(
            (0 until 200).map { index ->
                val base = 110.0 + 10 * kotlin.math.sin(index * 2 * Math.PI / 40)
                val violent = index == 100
                Candle(
                    t = 1_700_000_000L + index * 3600,
                    o = base,
                    h = base + if (violent) 12.0 else 0.8,
                    l = base - 0.8,
                    c = base + if (violent) 10.0 else 0.2,
                    v = 500.0,
                )
            },
        )
        for (option in ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.STRUCTURE }) {
            assertTrue("${option.id} draws nothing", !ChartCatalog.structureFor(option, series).isEmpty)
        }
    }

    @Test
    fun `a structure study draws nothing through the ordinary overlay path`() {
        // And the reverse: overlayFor must not quietly return lines for one, or a study would draw
        // twice — once as an overlay and once as structure.
        val series = CandleSeries((0 until 60).map { Candle(it.toLong(), 100.0, 101.0, 99.0, 100.5) })
        for (option in ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.STRUCTURE }) {
            assertTrue(ChartCatalog.overlayFor(option, series).isEmpty())
        }
        for (option in ChartCatalog.INDICATORS.filterNot { it.pane == IndicatorPane.STRUCTURE }) {
            assertTrue(ChartCatalog.structureFor(option, series).isEmpty)
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
