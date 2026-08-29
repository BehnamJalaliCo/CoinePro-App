package com.coinepro.core.chart

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The volume profile over the bars a reader can actually see.
 *
 * `volumeprofile_ind` measured the whole loaded series and drew three flat lines from it. On a
 * chart showing four hours of a coin, the point of control came from a week the reader could not
 * see — an answer to a question nobody asked, drawn across the part they were looking at. The
 * fixture below is built so the two answers *must* differ: the old bars traded heavily around one
 * price and the visible ones lightly around a much higher one.
 */
class VolumeProfileWindowTest {

    /** 150 heavy bars around 100, then 50 light ones around 200. */
    private val series = CandleSeries(
        (0 until 200).map { index ->
            val old = index < 150
            val base = if (old) 100.0 else 200.0
            val drift = (index % 5) * 0.2
            Candle(
                t = 1_700_000_000L + index * 3600L,
                o = base + drift,
                h = base + drift + 1.0,
                l = base + drift - 1.0,
                c = base + drift + 0.5,
                v = if (old) 1_000.0 else 40.0,
            )
        },
    )

    private fun controlPrice(profile: VolumeProfile): Double =
        (profile.rowLow[profile.pocIndex] + profile.rowHigh[profile.pocIndex]) / 2

    @Test
    fun `the visible range profile answers about the visible bars`() {
        val whole = ChartCatalog.volumeProfileFor(series)
        val visible = ChartCatalog.volumeProfileFor(series, BarWindow.visible(150, 199))
        assertNotNull(whole)
        assertNotNull(visible)
        assertTrue(
            "the whole-series control price belongs to the heavy old bars",
            abs(controlPrice(whole!!) - 100.0) < 5.0,
        )
        assertTrue(
            "the visible-range control price belongs to the bars on screen",
            abs(controlPrice(visible!!) - 200.0) < 5.0,
        )
        assertTrue(
            "the two answers must not be the same, or the window is being ignored",
            abs(controlPrice(whole) - controlPrice(visible)) > 50.0,
        )
    }

    @Test
    fun `the value area follows the window too`() {
        val visible = ChartCatalog.volumeProfileFor(series, BarWindow.visible(150, 199))!!
        assertTrue(
            "the value area cannot reach prices the window never traded at",
            visible.rowLow[visible.valueAreaLow] > 150.0,
        )
        assertTrue(visible.valueAreaLow <= visible.pocIndex && visible.valueAreaHigh >= visible.pocIndex)
    }

    @Test
    fun `the overlay draws the window's own three prices and says which window it used`() {
        val option = ChartCatalog.INDICATORS.first { it.id == "volumeprofile_ind" }
        val whole = ChartCatalog.overlayFor(option, series)
        val visible = ChartCatalog.overlayFor(option, series, window = BarWindow.visible(150, 199))
        assertEquals(3, whole.size)
        assertEquals(3, visible.size)
        assertTrue(
            "the point of control drawn must be the one the window found",
            abs(whole.first().values.raw(0) - visible.first().values.raw(0)) > 50.0,
        )
        assertEquals("POC", whole.first().label)
        assertTrue(
            "a windowed profile has to say so, or a reader cannot tell the two apart: ${visible.first().label}",
            visible.first().label!!.contains("POC") && visible.first().label != "POC",
        )
        assertTrue(
            "a level is true on every bar or it is not a level",
            (0 until series.size).all { visible.first().values.raw(it) == visible.first().values.raw(0) },
        )
    }

    @Test
    fun `a window is clamped rather than trusted`() {
        // A viewport is a live thing and its range can briefly run past a series that has just been
        // replaced. That must not throw in the middle of a frame.
        val past = ChartCatalog.volumeProfileFor(series, BarWindow.visible(190, 5_000))
        assertNotNull(past)
        assertEquals(0..199, BarWindow.WHOLE_SERIES.clampedTo(200))
        assertEquals(190..199, BarWindow.visible(190, 5_000).clampedTo(200))
        assertEquals(0..0, BarWindow.visible(-40, 0).clampedTo(200))
        assertNull("an empty series selects nothing", BarWindow.WHOLE_SERIES.clampedTo(0))
        assertNull("and a backwards window selects nothing", BarWindow.visible(180, 20).clampedTo(200))
    }

    @Test
    fun `a feed with no volume still draws no profile whatever the window`() {
        val silent = CandleSeries(
            (0 until 50).map { Candle(1_700_000_000L + it * 3600L, 100.0, 101.0, 99.0, 100.5) },
        )
        assertNull(ChartCatalog.volumeProfileFor(silent, BarWindow.visible(10, 40)))
        val option = ChartCatalog.INDICATORS.first { it.id == "volumeprofile_ind" }
        assertTrue(ChartCatalog.overlayFor(option, silent, window = BarWindow.visible(10, 40)).isEmpty())
    }
}
