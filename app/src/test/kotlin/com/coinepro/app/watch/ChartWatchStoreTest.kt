package com.coinepro.app.watch

import com.coinepro.core.chart.ChartWatch
import com.coinepro.core.chart.WatchSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the picture-in-picture window is handed.
 *
 * The assertion this file exists for is that the rate limit is **at the write**: a throttle
 * downstream of the state would still be recomposed by everything upstream of it, so it would be
 * decoration. The second is that leaving the mode forgets the chart — a window opened tomorrow
 * flashing yesterday's price is a stale number in the one place on the screen with no room to
 * qualify it.
 */
class ChartWatchStoreTest {

    private fun snapshot(price: Double) = WatchSnapshot(
        symbol = "BTCUSDT",
        intervalWire = "1h",
        price = price,
        changePercent = 1.0,
    )

    @Test
    fun `a fresh store is watching nothing`() {
        assertNull(ChartWatchStore().snapshot.value)
    }

    @Test
    fun `the first snapshot goes straight through`() {
        // A window that waited a second before drawing anything would open empty.
        val store = ChartWatchStore()
        store.offer(snapshot(100.0), nowMillis = 1_000L)
        assertEquals(100.0, store.snapshot.value?.price)
    }

    @Test
    fun `a second snapshot inside the window is dropped`() {
        val store = ChartWatchStore()
        store.offer(snapshot(100.0), nowMillis = 1_000L)
        store.offer(snapshot(101.0), nowMillis = 1_500L)
        assertEquals(100.0, store.snapshot.value?.price)
    }

    @Test
    fun `a snapshot after the window is kept`() {
        val store = ChartWatchStore()
        store.offer(snapshot(100.0), nowMillis = 1_000L)
        store.offer(snapshot(101.0), nowMillis = 1_000L + ChartWatch.MIN_PUBLISH_MILLIS)
        assertEquals(101.0, store.snapshot.value?.price)
    }

    @Test
    fun `a dropped snapshot does not move the clock`() {
        // The bug this guards: if a refused offer stamped the time, a chart ticking forty times a
        // second would keep pushing the window out and the price would never update at all.
        val store = ChartWatchStore()
        store.offer(snapshot(100.0), nowMillis = 1_000L)
        store.offer(snapshot(101.0), nowMillis = 1_500L)
        store.offer(snapshot(102.0), nowMillis = 2_000L)
        assertEquals(102.0, store.snapshot.value?.price)
    }

    @Test
    fun `an unchanged snapshot is never republished`() {
        val store = ChartWatchStore()
        store.offer(snapshot(100.0), nowMillis = 1_000L)
        val first = store.snapshot.value
        store.offer(snapshot(100.0), nowMillis = 9_000L)
        // Identity, not equality: an equal snapshot republished is a recomposition carrying no new
        // information, and on this surface that is the whole budget.
        assertEquals(true, first === store.snapshot.value)
    }

    @Test
    fun `clearing forgets the chart and the clock with it`() {
        val store = ChartWatchStore()
        store.offer(snapshot(100.0), nowMillis = 1_000L)
        store.clear()
        assertNull(store.snapshot.value)
        // And the next window opens immediately rather than waiting out a stale interval.
        store.offer(snapshot(200.0), nowMillis = 1_100L)
        assertEquals(200.0, store.snapshot.value?.price)
    }
}
