package com.coinepro.feature.chart

import com.coinepro.core.chart.BarWindow
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.IndicatorPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a bar of pan is allowed to cost, and the one study that is allowed to cost anything.
 *
 * ### The defect these are the fence around
 *
 * `ChartDerived`'s key held six inputs and was checked as one comparison, and the sixth was the
 * visible window. The window moves on every bar of a drag. So a reader dragging a chart with five
 * ordinary studies on it recomputed every one of them, over every resident bar, per bar of
 * movement, on the main thread inside composition — because one study in the whole catalogue reads
 * the window and the other forty-odd were being invalidated on its behalf.
 *
 * Measured on a desktop JVM before the change: five indicators over twelve thousand resident bars
 * cost fifteen milliseconds a step and the carried value matched on one pan step in sixty. At the
 * fifty thousand bars the owner has asked for it is sixty milliseconds a step, which is a chart
 * that cannot be dragged at all.
 *
 * So the window is checked apart from the other five now, and repaired rather than thrown away.
 * These tests pin both halves: that an ordinary pan reuses the whole answer by identity, and that
 * the one study whose subject *is* the window still follows the reader.
 */
class ChartPanCostTest {

    private fun series(bars: Int, base: Double = 100.0) = CandleSeries(
        (0 until bars).map { index ->
            // A shape rather than a ramp, so a volume profile has something to find and two
            // different windows genuinely bucket differently.
            val wave = base + (index % 40) * 0.9
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = wave,
                h = wave + 1.5,
                l = wave - 1.5,
                c = wave + 0.4,
                v = 10.0 + (index % 7),
            )
        },
    )

    private val bars = series(600)
    private val early = BarWindow.visible(100, 220)
    private val late = BarWindow.visible(430, 550)

    /** Everything about a line that a reader could see change, in a form two of them can be compared by. */
    private fun signature(line: ChartLine): List<Any?> = listOf(
        line.label,
        line.colour,
        line.values.size,
        (0 until line.values.size step 17).map { line.values.raw(it) },
        line.profile?.let { listOf(it.pocIndex, it.valueAreaLow, it.valueAreaHigh, it.volume.toList()) },
    )

    private fun state(active: Set<String>, window: BarWindow) = ChartUiState(
        symbol = "BTCUSDT",
        series = bars,
        activeIndicators = active,
        window = window,
    )

    @Test
    fun `a pan step reuses every line on an ordinary chart`() {
        val at = state(setOf("ema", "sma", "rsi", "macd", "bollinger"), early)
        val computed = at.derived
        // What the controller now publishes on a pan: the same everything, a new window, and the
        // previous answer carried across.
        val panned = at.copy(window = late, carried = computed)

        // By identity, and the list itself rather than its contents: nothing about a pan changes a
        // single line here, so the correct amount of work is none at all.
        assertSame("A pan step recomputed the indicators", computed.overlays, panned.derived.overlays)
        assertSame(computed.panes, panned.derived.panes)
        assertSame(computed.levels, panned.derived.levels)
        assertSame(computed.markers, panned.derived.markers)
    }

    @Test
    fun `the repaired answer records the window it was repaired to`() {
        // Otherwise the next pan compares against a stale window, takes the repair branch again for
        // ever, and the one field that exists to be checked is lying.
        val at = state(setOf("ema"), early)
        val panned = at.copy(window = late, carried = at.derived)
        val twice = panned.copy(carried = panned.derived)

        assertSame(panned.derived.overlays, twice.derived.overlays)
        assertTrue(
            "The carried key did not adopt the new window",
            panned.derived.matches(bars, setOf("ema"), emptyMap(), null, late),
        )
    }

    @Test
    fun `the visible-range profile still follows the pan`() {
        // The whole reason the window is an input at all. A profile that stopped moving would be a
        // "visible range" study measuring a range nobody is looking at, which is the defect the
        // window was added to fix and must not be undone by making the pan cheap.
        val at = state(setOf("volumeprofile_ind"), early)
        val panned = at.copy(window = late, carried = at.derived)

        val before = at.derived.overlays.map(::signature)
        val after = panned.derived.overlays.map(::signature)
        assertEquals(before.size, after.size)
        assertNotEquals("The profile did not follow the reader", before, after)
    }

    @Test
    fun `only the window-scoped study is recomputed, and the rest keep their place`() {
        val active = setOf("ema", "volumeprofile_ind", "bollinger")
        val at = state(active, early)
        val computed = at.derived
        val panned = at.copy(window = late, carried = computed).derived

        // The owners are the alignment that lets the legend take the right study off the chart. A
        // repair that dropped or added a row would leave `overlays` and `overlayOwners` one apart.
        assertEquals(computed.overlayOwners, panned.overlayOwners)
        assertEquals(computed.overlays.size, panned.overlays.size)
        computed.overlays.forEachIndexed { index, line ->
            if (computed.overlayOwners[index] in ChartDerived.WINDOW_SCOPED) return@forEachIndexed
            assertSame(
                "${computed.overlayOwners[index]} was recomputed for a pan it does not read",
                line,
                panned.overlays[index],
            )
        }
    }

    @Test
    fun `the window-scoped set is exactly what the catalogue makes window-dependent`() {
        // The set is a constant, so it can go stale. This derives the same answer from the
        // catalogue itself: ask every price-pane indicator for its lines at two windows and see
        // which ones answer differently. A study added later that reads a window and is not named
        // in the constant fails here, rather than drawing a profile of bars the reader left behind.
        val dependent = ChartCatalog.INDICATORS
            .filter { it.pane == IndicatorPane.PRICE }
            .filter { option ->
                val here = ChartCatalog.overlayFor(option, bars, null, early).map(::signature)
                val there = ChartCatalog.overlayFor(option, bars, null, late).map(::signature)
                here != there
            }
            .map { it.id }
            .toSet()

        assertEquals(dependent, ChartDerived.WINDOW_SCOPED)
    }

    @Test
    fun `a real change still throws the carried answer away`() {
        // The opposite failure, and the dangerous one: carry too eagerly and the chart draws last
        // timeframe's average over this timeframe's candles with nothing on screen to say so.
        val at = state(setOf("ema"), early)
        val reloaded = at.copy(series = series(600, base = 900.0), window = late, carried = at.derived)

        assertNotEquals(
            signature(at.derived.overlays.first()),
            signature(reloaded.derived.overlays.first()),
        )
    }
}
