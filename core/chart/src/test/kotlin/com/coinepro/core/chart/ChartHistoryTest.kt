package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bound between an archive that goes deep and a chart that has to stay quick.
 *
 * Drawing is not what breaks: the viewport already works between its first and last visible bar, so
 * a frame costs what is on screen. What breaks is everything that is linear in what is *loaded* —
 * the memory a series and its six columns occupy, the indicators that walk a lookback at every
 * index, and the chart types that rebuild every bar whenever the series is replaced. Eight live
 * controllers holding fifty thousand bars each is around fifty megabytes of candles for charts
 * nobody is looking at, so the resident window is the thing that makes a deep archive affordable.
 */
class ChartHistoryTest {

    private fun series(count: Int): CandleSeries = CandleSeries(
        (0 until count).map { index ->
            Candle(
                t = 1_600_000_000L + index * 3_600L,
                o = 1.0 + index,
                h = 2.0 + index,
                l = index.toDouble(),
                c = 1.5 + index,
                v = 1.0,
            )
        },
    )

    @Test
    fun `a series inside the budget is handed back untouched`() {
        val small = series(300)
        // The same object, not an equal one. `ChartDerived` is keyed on series identity, so a slice
        // that merely equalled this one would throw away every carried indicator on every frame.
        assertSame(small, small.resident())
    }

    @Test
    fun `a series past the budget is cut down to it`() {
        val deep = series(50_000)

        val held = deep.resident(BarWindow.WHOLE_SERIES)

        assertEquals(ChartHistory.MAX_RESIDENT_BARS, held.size)
        // With no viewport to go on, the newest bars are the ones kept: that is where a chart opens.
        assertEquals(deep.bars.last(), held.bars.last())
    }

    @Test
    fun `the window kept is the one the reader is looking at, centred and full`() {
        val deep = series(50_000)

        val kept = requireNotNull(ChartHistory.residentRange(deep.size, BarWindow.visible(20_000, 20_120)))

        assertTrue("visible bars must be held", 20_000 >= kept.first && 20_120 <= kept.last)
        assertEquals(ChartHistory.MAX_RESIDENT_BARS, kept.last - kept.first + 1)
        // Roughly half the spare on each side, so a pan either way has the same distance to run
        // before anything has to be rebuilt.
        assertTrue("headroom behind is ${20_000 - kept.first}", 20_000 - kept.first > ChartHistory.HEADROOM_BARS)
        assertTrue("headroom ahead is ${kept.last - 20_120}", kept.last - 20_120 > ChartHistory.HEADROOM_BARS)
    }

    @Test
    fun `re-slicing is asked for before the reader reaches the edge, not after`() {
        // Deeper than the ceiling, whatever it is — below it `residentRange` holds everything and
        // there is nothing to slide.
        val total = ChartHistory.MAX_RESIDENT_BARS * 2
        val middle = total / 2
        val kept = requireNotNull(ChartHistory.residentRange(total, BarWindow.visible(middle, middle + 120)))

        // Sitting in the middle of the window: nothing to do, and doing something would be an
        // allocation and a full indicator recompute on every frame of the drag that got here.
        assertFalse(ChartHistory.needsReslice(kept, BarWindow.visible(middle, middle + 120), total))
        // Within the headroom of the old edge: rebuild now, while there is still slack to draw from.
        assertTrue(ChartHistory.needsReslice(kept, BarWindow.visible(kept.first + 10, kept.first + 130), total))
        // Against the oldest bar the archive holds, there is nothing behind it to slide towards.
        val fromStart = requireNotNull(ChartHistory.residentRange(total, BarWindow.visible(0, 120)))
        assertFalse(ChartHistory.needsReslice(fromStart, BarWindow.visible(0, 120), total))
    }

    @Test
    fun `a window against the live edge slides inward rather than coming back short`() {
        val deep = series(50_000)

        val kept = requireNotNull(ChartHistory.residentRange(deep.size, BarWindow.visible(49_880, 49_999)))

        assertEquals(49_999, kept.last)
        // The full budget, not a truncated one: clipping at the edge would hand back a narrow
        // window at exactly the moment the reader is about to pan towards more of it.
        assertEquals(ChartHistory.MAX_RESIDENT_BARS, kept.last - kept.first + 1)
    }

    @Test
    fun `an empty series has no resident range at all`() {
        assertNull(ChartHistory.residentRange(0))
        assertEquals(0, CandleSeries.EMPTY.resident().size)
    }

    @Test
    fun `what is held is a copy, so the rest of the history can be collected`() {
        // Deeper than the ceiling, whatever the ceiling is. It used to be a flat 50,000 against a
        // 12,000 budget; the budget is 50,000 now, so a fixed number would have been asserting that
        // the whole series is held — which is true and is not what this test is about.
        val deep = series(ChartHistory.MAX_RESIDENT_BARS * 2)

        val held = deep.resident(BarWindow.visible(10_000, 10_100))

        // A `subList` view would keep all fifty thousand alive and free nothing, which is the one
        // failure mode of this whole idea that would look like it worked.
        assertTrue(held.bars !== deep.bars)
        assertEquals(ChartHistory.MAX_RESIDENT_BARS, held.size)
        val kept = requireNotNull(ChartHistory.residentRange(deep.size, BarWindow.visible(10_000, 10_100)))
        assertEquals(deep.bars[10_000], held.bars[10_000 - kept.first])
        assertTrue(held.size < deep.size)
    }

    @Test
    fun `the budget is one a phone can carry four times over`() {
        // Four is `ChartControllers.MAX_CONTROLLERS`: a reader flipping between symbols keeps that
        // many charts alive so their zoom and drawings survive the trip. The number that has to be
        // affordable is therefore four of these, not one.
        //
        // It was eight against a twelve-thousand-bar ceiling. The ceiling is fifty thousand now,
        // because a back-test over a year of hourly candles is not a back-test if the chart drops
        // the far end of the year, and the controller count came down in the same change to pay for
        // it. The two constants are one decision and this is where that is written down: raising
        // either without lowering the other has to fail here rather than on somebody's phone.
        val perChart = ChartHistory.estimatedBytes(ChartHistory.MAX_RESIDENT_BARS)
        assertTrue("one chart is $perChart bytes", perChart < 8L * 1_024 * 1_024)
        assertTrue("four charts are ${perChart * 4} bytes", perChart * 4 < 32L * 1_024 * 1_024)
    }
}
