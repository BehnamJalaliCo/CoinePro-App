package com.coinepro.feature.chart

import com.coinepro.core.chart.BarWindow
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The renderer reports the visible window on every bar of a drag. This is what that is allowed to
 * cost the rest of the app.
 *
 * A published window is a new `ChartUiState`, a push through the flow, a recomposition of the whole
 * chart page and six getters re-answered. Exactly one study reads the value — the visible-range
 * volume profile — so on every chart in the app until somebody switches that on, all of it was
 * being paid to record a number with no consumer. That is the second half of «چارت وحشتناک کنده»:
 * not the arithmetic, the churn behind it.
 *
 * The awkward case is the one these tests exist for. A window that is not published is a window
 * that goes stale, so switching the profile on after a long pan would compute it over the bars the
 * reader *was* looking at. The controller remembers every window and spends the remembered one at
 * the moment the study comes on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartWindowPublishTest {

    private class OneShot(private val bars: List<OhlcBar>) : CandleGateway {
        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage = CandlePage(symbol, timeframe, if (before == null) bars else emptyList())
    }

    private fun bars(count: Int) = (0 until count).map { index ->
        val price = 100.0 + (index % 30)
        OhlcBar(t = 1_700_000_000L + index * 3600L, o = price, h = price + 1, l = price - 1, c = price, v = 5.0)
    }

    private fun controller(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        ChartController("BTCUSDT", OneShot(bars(400)), TestScope(StandardTestDispatcher(scheduler)))

    @Test
    fun `an ordinary chart publishes nothing as the reader drags`() = runTest {
        val chart = controller(testScheduler)
        chart.start()
        advanceUntilIdle()
        val before = chart.state.value

        repeat(60) { step -> chart.setVisibleWindow(BarWindow.visible(200 - step, 320 - step)) }

        // The same state object. Not an equal one — the point is that nothing was pushed at all.
        assertSame("A drag pushed a state per bar for a value nothing reads", before, chart.state.value)
        assertEquals(BarWindow.WHOLE_SERIES, chart.state.value.window)
    }

    @Test
    fun `switching the profile on takes the window the reader is actually looking at`() = runTest {
        val chart = controller(testScheduler)
        chart.start()
        advanceUntilIdle()

        // A long pan with the study off, so nothing is published…
        chart.setVisibleWindow(BarWindow.visible(40, 160))
        assertEquals(BarWindow.WHOLE_SERIES, chart.state.value.window)

        // …and then the reader switches it on, parked where they are.
        chart.toggleIndicator("volumeprofile_ind")

        assertEquals(
            "A visible-range profile opened on a range nobody is looking at",
            BarWindow.visible(40, 160),
            chart.state.value.window,
        )
        assertNotNull(chart.state.value.overlays.firstOrNull { it.profile != null })
    }

    @Test
    fun `with the profile on, the window is published and the answer follows`() = runTest {
        val chart = controller(testScheduler)
        chart.start()
        advanceUntilIdle()
        chart.toggleIndicator("volumeprofile_ind")

        chart.setVisibleWindow(BarWindow.visible(40, 160))
        val here = chart.state.value.overlays.first { it.profile != null }.profile
        chart.setVisibleWindow(BarWindow.visible(240, 360))
        val there = chart.state.value.overlays.first { it.profile != null }.profile

        assertEquals(BarWindow.visible(240, 360), chart.state.value.window)
        assertEquals(
            "Two windows of the same bars produced the same profile",
            false,
            requireNotNull(here).volume.toList() == requireNotNull(there).volume.toList(),
        )
    }
}
