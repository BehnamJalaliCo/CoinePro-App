package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** TradingView's chart settings dialog and its six indicator templates (5.16.0). */
class TradingViewSettingsTest {

    @Test
    fun `the defaults are TradingView's and encode to nothing`() {
        val defaults = ChartAppearance()
        assertEquals("", defaults.encode())
        assertEquals(0.10, defaults.topMargin, 1e-12)
        assertEquals(0.08, defaults.bottomMargin, 1e-12)
        assertEquals(defaults, ChartAppearance.decode(null))
    }

    @Test
    fun `every switch survives the store and a stranger's key is skipped`() {
        val changed = ChartAppearance(
            colourOnPreviousClose = true,
            wicks = false,
            legendOhlc = false,
            gridVertical = false,
            topMarginPercent = 20,
            bottomMarginPercent = 0,
            tradeRing = false,
        )
        assertEquals(changed, ChartAppearance.decode(changed.encode()))
        assertEquals(changed, ChartAppearance.decode(changed.encode() + "future=1;top=oops;"))
        assertEquals(ChartAppearance.MAX_MARGIN, ChartAppearance.decode("top=90;").topMarginPercent)
    }

    @Test
    fun `the margins are shares of the height and inversion only reflects them`() {
        val series = CandleSeries((0 until 50).map { Candle(it * 60L, 100.0, 110.0, 90.0, 105.0, 1.0) })
        val view = ChartViewport(series = series, plotWidth = 500f, plotHeight = 400f, topMargin = 0.2, bottomMargin = 0.1)
        val range = view.priceRange
        val total = range.endInclusive - range.start
        assertEquals(0.2, (range.endInclusive - 110.0) / total, 1e-9)
        assertEquals(0.1, (90.0 - range.start) / total, 1e-9)
        // Inversion is a reflection of the same range, applied last.
        assertEquals(range, view.copy(inverted = true).priceRange)
    }

    @Test
    fun `the six templates name only studies this build has`() {
        val known = ChartCatalog.INDICATORS.map { it.id }.toSet()
        assertEquals(6, BuiltInIndicatorTemplates.ALL.size)
        BuiltInIndicatorTemplates.ALL.forEach { template ->
            assertTrue(template.id, template.indicators.isNotEmpty() && known.containsAll(template.indicators))
            val applied = IndicatorTemplates.apply(template.indicators, template.periods)
            assertTrue(template.id, applied.dropped.isEmpty())
        }
    }

    @Test
    fun `the displaced EMA is the same line moved right`() {
        val series = CandleSeries((0 until 80).map { Candle(it * 60L, 100.0 + it, 101.0 + it, 99.0 + it, 100.5 + it, 1.0) })
        val ema = ChartCatalog.INDICATORS.first { it.id == "ema" }
        val plain = ChartCatalog.overlayFor(ema, series, 10)
        val moved = ChartCatalog.overlayFor(ema, series, 10, params = mapOf("shift" to 5.0))
        assertEquals(plain.first().values[40]!!, moved.first().values[45]!!, 1e-12)
        assertEquals("EMA 10 +5", moved.first().label)
    }
}

/** TradingView's «Visibility on intervals» families (5.16.1). */
class IntervalFamilyTest {
    @Test
    fun `a bar length belongs to one family`() {
        assertEquals(IntervalFamily.SECONDS, IntervalFamily.ofSeconds(30))
        assertEquals(IntervalFamily.MINUTES, IntervalFamily.ofSeconds(900))
        assertEquals(IntervalFamily.HOURS, IntervalFamily.ofSeconds(14_400))
        assertEquals(IntervalFamily.DAYS, IntervalFamily.ofSeconds(86_400))
        assertEquals(IntervalFamily.WEEKS, IntervalFamily.ofSeconds(604_800))
        assertEquals(IntervalFamily.MONTHS, IntervalFamily.ofSeconds(2_592_000))
    }

    @Test
    fun `the stored set survives and a stranger's id is skipped`() {
        val set = setOf(IntervalFamily.MINUTES, IntervalFamily.HOURS)
        assertEquals("m,h", IntervalFamily.encodeSet(set))
        assertEquals(set, IntervalFamily.parseSet("m,h,zz"))
        assertEquals(emptySet<IntervalFamily>(), IntervalFamily.parseSet(null))
    }

    @Test
    fun `the crosshair magnet is remembered`() {
        val magnet = ChartAppearance(crosshairMagnet = true)
        assertEquals(magnet, ChartAppearance.decode(magnet.encode()))
    }
}
