package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lookback a reader can change, and the two ways it can silently lie.
 *
 * Every period in this app was a literal until the stepper landed — `Indicators.ema(close, 20)`
 * with `label = "EMA 20"` written out beside it. Once one of those becomes a variable and the
 * other does not, the chart draws a fifty-bar average under a label that says twenty, and nobody
 * looking at the screen can tell. That is what the first test here is for.
 */
class IndicatorPeriodTest {

    /**
     * Six hundred bars that actually move.
     *
     * Long enough that a two-hundred-bar average has values at the end — a fixture shorter than
     * the longest period under test would make every "these differ" assertion pass for the wrong
     * reason, because both lines would be null. A sine plus a drift, so no two lookbacks agree.
     */
    private val series = CandleSeries(
        (0 until 600).map { index ->
            val base = 100.0 + index * 0.1 + 8 * kotlin.math.sin(index * 2 * Math.PI / 37)
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = base,
                h = base + 1.2,
                l = base - 1.1,
                c = base + 0.3,
                v = 500.0 + index,
            )
        },
    )

    private fun option(id: String): IndicatorOption =
        ChartCatalog.INDICATORS.first { it.id == id }

    @Test
    fun `the label carries the period the maths used`() {
        ChartCatalog.PERIODS.forEach { (id, bounds) ->
            val option = option(id)
            val chosen = (bounds.default + 17).coerceIn(bounds.min, bounds.max)
            val labels = when (option.pane) {
                IndicatorPane.PRICE ->
                    ChartCatalog.overlayFor(option, series, chosen).mapNotNull { it.label }
                IndicatorPane.SEPARATE ->
                    listOfNotNull(ChartCatalog.paneFor(option, series, chosen)?.title)
                IndicatorPane.STRUCTURE -> emptyList()
            }
            assertTrue("$id produced no label to check", labels.isNotEmpty())
            assertTrue(
                "$id was drawn at $chosen but its label says ${labels.first()}",
                labels.any { it.contains(chosen.toString()) },
            )
        }
    }

    @Test
    fun `a different period is a different line`() {
        // The other half of the same lie: a label that changes while the values do not. Twenty and
        // two hundred cannot produce the same average on a series that moves.
        val ema = option("ema")
        val short = ChartCatalog.overlayFor(ema, series, 20).first().values
        val long = ChartCatalog.overlayFor(ema, series, 200).first().values
        val last = series.size - 1
        assertNotEquals(short.raw(last), long.raw(last))
    }

    @Test
    fun `null means the indicator's own default`() {
        ChartCatalog.PERIODS.forEach { (id, bounds) ->
            val option = option(id)
            if (option.pane != IndicatorPane.PRICE) return@forEach
            val fromNull = ChartCatalog.overlayFor(option, series, null).first().values
            val fromDefault = ChartCatalog.overlayFor(option, series, bounds.default).first().values
            val last = series.size - 1
            assertEquals(
                "$id disagrees with its own default",
                fromDefault.raw(last),
                fromNull.raw(last),
                0.0,
            )
        }
    }

    @Test
    fun `an out-of-range period is clamped rather than refused`() {
        // A value stored by an older build with wider bounds must not come back as a chart of
        // nulls. Zero and a million are both drawn as the nearest legal length.
        val sma = option("sma")
        val bounds = ChartCatalog.periodOf("sma")!!
        val tooSmall = ChartCatalog.overlayFor(sma, series, 0).first()
        val tooLarge = ChartCatalog.overlayFor(sma, series, 1_000_000).first()
        assertTrue(tooSmall.label!!.contains(bounds.min.toString()))
        assertTrue(tooLarge.label!!.contains(bounds.max.toString()))
    }

    @Test
    fun `only indicators with one lookback are offered one`() {
        // MACD has three parameters, Ichimoku three spans, VWAP none. A stepper on any of them
        // would move a number that changes nothing on screen, which is worse than no control.
        listOf("macd", "ichimoku", "vwap", "supertrend", "pivots").forEach { id ->
            assertNull("$id should have no single lookback", ChartCatalog.periodOf(id))
        }
        // And every id in the table has to be a real indicator, or the picker offers a stepper for
        // a row that does not exist.
        ChartCatalog.PERIODS.keys.forEach { id ->
            assertTrue("$id is not in the catalogue", ChartCatalog.INDICATORS.any { it.id == id })
        }
    }
}
