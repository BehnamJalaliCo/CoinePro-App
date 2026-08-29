package com.coinepro.feature.chart

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the divider between the chart and the watchlist may sit, and that it stays put.
 *
 * The arithmetic is three lines and every one of them has a way of being wrong that only shows up
 * on a device: a NaN from a zero-height layout pass, a drag that inverts, a fling that leaves the
 * chart with no height and nothing to drag it back by. This is the cheapest place to be sure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartSplitTest {

    @Test
    fun `a ratio inside the bounds is left exactly where it is`() {
        assertEquals(0.5f, ChartSplit.clamp(0.5f), 1e-6f)
        assertEquals(ChartSplit.DEFAULT, ChartSplit.clamp(ChartSplit.DEFAULT), 1e-6f)
    }

    @Test
    fun `a ratio past either bound is pulled back to it rather than refused`() {
        assertEquals(ChartSplit.MIN, ChartSplit.clamp(0.05f), 1e-6f)
        assertEquals(ChartSplit.MAX, ChartSplit.clamp(0.99f), 1e-6f)
        assertEquals(ChartSplit.MIN, ChartSplit.clamp(-3f), 1e-6f)
    }

    @Test
    fun `a not-a-number ratio goes back to the default instead of propagating`() {
        assertEquals(ChartSplit.DEFAULT, ChartSplit.clamp(Float.NaN), 1e-6f)
        assertEquals(ChartSplit.DEFAULT, ChartSplit.clamp(Float.POSITIVE_INFINITY), 1e-6f)
        assertEquals(ChartSplit.DEFAULT, ChartSplit.clamp(Float.NEGATIVE_INFINITY), 1e-6f)
    }

    @Test
    fun `dragging down grows the chart by the fraction of the pane the finger moved`() {
        // A hundred pixels down a thousand-pixel pane is a tenth more chart.
        assertEquals(0.60f, ChartSplit.after(0.50f, dragPx = 100f, totalPx = 1000f), 1e-6f)
    }

    @Test
    fun `dragging up shrinks the chart by the same fraction`() {
        assertEquals(0.40f, ChartSplit.after(0.50f, dragPx = -100f, totalPx = 1000f), 1e-6f)
    }

    @Test
    fun `a drag that overshoots stops at the bound and stays draggable back`() {
        val pinned = ChartSplit.after(0.80f, dragPx = 900f, totalPx = 1000f)
        assertEquals(ChartSplit.MAX, pinned, 1e-6f)
        // And the way back out of the pin still works, which is the property that matters.
        assertTrue(ChartSplit.after(pinned, dragPx = -100f, totalPx = 1000f) < pinned)
    }

    @Test
    fun `an unmeasured pane leaves the ratio alone rather than dividing by its height`() {
        assertEquals(0.5f, ChartSplit.after(0.5f, dragPx = 240f, totalPx = 0f), 1e-6f)
        assertEquals(0.5f, ChartSplit.after(0.5f, dragPx = 240f, totalPx = -10f), 1e-6f)
    }

    @Test
    fun `a reader who has never dragged the handle gets the default`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        assertEquals(ChartSplit.DEFAULT, store.splitRatio.first(), 1e-6f)
    }

    @Test
    fun `where the reader left the handle is what comes back`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        store.setSplitRatio(0.44f)
        assertEquals(0.44f, store.splitRatio.first(), 1e-6f)
    }

    @Test
    fun `a stored ratio outside the bounds is clamped on the way back out`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        // Written past the ceiling: the write clamps, and so does the read, so a record from a
        // build with different bounds cannot produce a layout with no watchlist in it.
        store.setSplitRatio(2f)
        assertEquals(ChartSplit.MAX, store.splitRatio.first(), 1e-6f)
    }

    @Test
    fun `the second pane's symbol is stored uppercased so two spellings are one instrument`() =
        runTest {
            val store = ChartWorkspaceStore(FakeWorkspacePreferences())
            store.setSecondPaneSymbol(" btcusdt ")
            assertEquals("BTCUSDT", store.secondPaneSymbol.first())
        }

    @Test
    fun `a blank second-pane symbol is refused rather than stored as an empty row`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        store.setSecondPaneSymbol("XAUUSD")
        store.setSecondPaneSymbol("   ")
        assertEquals("XAUUSD", store.secondPaneSymbol.first())
    }
}

/** The preferences file as an in-memory value, which is all these stores need to be exercised. */
internal class FakeWorkspacePreferences(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
