package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fling on Compose's `exponentialDecay`: the brief's durations, and the contract the chart's
 * frame loop relies on. Pure JVM — the spec is arithmetic.
 */
class ChartFlingTest {

    @Test
    fun `an ordinary flick coasts about one point two seconds and a hard one about one point four`() {
        val fling = ChartFling()
        fling.start(2_000f)
        assertTrue(fling.isRunning)
        assertTrue("${fling.durationMillis} ms", kotlin.math.abs(fling.durationMillis - 1_212L) <= 40L)
        fling.start(4_000f)
        assertTrue("${fling.durationMillis} ms", kotlin.math.abs(fling.durationMillis - 1_394L) <= 40L)
    }

    @Test
    fun `the steps never speed up, sum to the curve's distance, and the fling stops on its own`() {
        val fling = ChartFling()
        fling.start(3_000f)
        var now = 0L
        assertEquals(0f, fling.tick(now), 0f)
        var previous = Float.MAX_VALUE
        var travelled = 0f
        var frames = 0
        while (fling.isRunning && frames < 1_000) {
            now += 16_000_000L
            val step = fling.tick(now)
            assertTrue("a fling must never speed up: $step after $previous", step <= previous + 1e-3f)
            previous = step
            travelled += step
            frames++
        }
        assertTrue(!fling.isRunning)
        // v / f, less the tail under the cut-off: 3 000 / 3.8 ≈ 789 px, minus 20 / 3.8.
        assertEquals(3_000f / 3.8f - 20f / 3.8f, travelled, 8f)
        assertEquals(0f, fling.tick(now + 16_000_000L), 0f)
    }

    @Test
    fun `a leftward flick moves left, and a velocity under the cut-off starts nothing`() {
        val fling = ChartFling()
        fling.start(-1_200f)
        fling.tick(0L)
        assertTrue(fling.tick(16_000_000L) < 0f)
        fling.start(10f)
        assertTrue(!fling.isRunning)
    }
}
