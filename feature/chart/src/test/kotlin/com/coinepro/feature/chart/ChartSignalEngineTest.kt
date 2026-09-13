package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ConfidenceEngine
import com.coinepro.core.chart.MarketState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reader's own script explains itself like a built-in does (run Ω1).
 *
 * The three cases are the three ways a script can arrive: it says what it means with `signal(...)`,
 * it says nothing and is read off its own line, or it draws nothing at all. The first is the one the
 * thesis needs — an author's verdict, in their words, with the app measuring how often it has been
 * right — and the second is the one that makes the promise hold for the other ninety per cent of
 * scripts, which will never call `signal`.
 */
class ChartSignalEngineTest {

    private fun rising(bars: Int = 80): CandleSeries = CandleSeries(
        (0 until bars).map { index ->
            val base = 100.0 + index
            Candle(t = 1_700_000_000L + index * 3_600L, o = base, h = base + 1, l = base - 1, c = base + 0.5, v = 10.0)
        },
    )

    private fun script(id: String, source: String, name: String = "Study $id") =
        ChartScript(instanceId = id, name = name, source = source)

    private fun draw(series: CandleSeries, scripts: List<ChartScript>): ChartScriptDraw =
        ChartScriptEngine().evaluate(series, scripts) { 0L }

    @Test
    fun `a script that says buy is read as bullish, in its author's own words`() {
        val series = rising()
        val instance = script("1", "signal(close > ta.sma(close, 20), text = \"Above the average\")", name = "Mine")
        val layer = ChartSignalEngine.evaluate(
            series = series,
            indicatorIds = emptyList(),
            scripts = listOf(instance),
            draw = draw(series, listOf(instance)),
        )
        val read = layer.readOf(instance.ownerId)
        assertNotNull("the script produced no reading at all", read)
        assertEquals(MarketState.BULL, read!!.state)
        assertTrue("the verdict's bars became events", read.events.isNotEmpty())
        // The author's sentence wins over the app's phrasing, in both languages, because they know
        // what their script is for and the app is reading a line.
        assertEquals("Above the average", layer.sentence(instance.ownerId, english = true))
        assertEquals("Above the average", layer.sentence(instance.ownerId, english = false))
    }

    @Test
    fun `a script that says nothing is read off its own line`() {
        val series = rising()
        val instance = script("2", "plot(ta.ema(close, 10))", name = "Quiet")
        val layer = ChartSignalEngine.evaluate(
            series = series,
            indicatorIds = emptyList(),
            scripts = listOf(instance),
            draw = draw(series, listOf(instance)),
        )
        val read = layer.readOf(instance.ownerId)
        assertNotNull("a script with a line on the price must still explain itself", read)
        // Rising market, line under the close: bullish, with the app's own sentence rather than the
        // author's, because there is no author's sentence.
        assertEquals(MarketState.BULL, read!!.state)
        assertNotNull(layer.sentence(instance.ownerId, english = true))
    }

    @Test
    fun `a script that draws nothing is left out rather than shown as neutral`() {
        val series = rising()
        val instance = script("3", "log(\"nothing to draw\")", name = "Silent")
        val layer = ChartSignalEngine.evaluate(
            series = series,
            indicatorIds = emptyList(),
            scripts = listOf(instance),
            draw = draw(series, listOf(instance)),
        )
        // A row that says «neutral» about a study with no opinion is noise in the one strip the
        // reader is supposed to be able to scan.
        assertTrue(layer.reads.none { it.id == instance.ownerId })
    }

    @Test
    fun `the strip carries the built-ins and the scripts together, in one order`() {
        val series = rising()
        val instance = script("4", "signal(close > open)", name = "Mine")
        val layer = ChartSignalEngine.evaluate(
            series = series,
            indicatorIds = listOf("ema", "rsi"),
            scripts = listOf(instance),
            draw = draw(series, listOf(instance)),
        )
        assertEquals(listOf("ema", "rsi", instance.ownerId), layer.reads.map { it.id })
        // And every one of them has a base rate computed, even where there is not enough of it.
        assertEquals(layer.reads.size, layer.confidence.size)
    }

    @Test
    fun `a study that fired forty times draws six marks, not forty`() {
        val series = rising(200)
        val layer = ChartSignalEngine.evaluate(series = series, indicatorIds = listOf("ema"))
        val marks = ChartSignalEngine.markersFor(layer, series)
        assertTrue("a mark per firing would be a rash, not a signal layer", marks.size <= ChartSignalEngine.MARKERS_PER_STUDY)
    }

    @Test
    fun `an eye switched off on a study takes its marks with it`() {
        val series = rising(200)
        val layer = ChartSignalEngine.evaluate(series = series, indicatorIds = listOf("ema"))
        assertTrue(ChartSignalEngine.markersFor(layer, series, hidden = setOf("ema")).isEmpty())
    }

    @Test
    fun `the setup score is over the studies that have an opinion`() {
        val series = rising()
        val layer = ChartSignalEngine.evaluate(series = series, indicatorIds = listOf("ema", "sma"))
        // Both averages are under a rising close, so the chart agrees with itself about the
        // direction. The *score* is a different question now — see `ConfidenceEngine.setupScore`,
        // run Ω-FIX item 3 — and on a straight-line rise neither average has ever crossed the close,
        // so neither has a measured record and the confidence in that direction is honestly nought.
        assertEquals(MarketState.BULL, layer.setup.side)
        assertEquals(2, layer.setup.studies)
        assertTrue(
            "a chart of unmeasured studies scored ${layer.setup.score}",
            layer.setup.score <= ConfidenceEngine.CONFIDENCE_CAP,
        )
    }
}
