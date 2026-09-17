package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fling on Compose's `exponentialDecay`: the distances run Τ is about, and the contract the
 * chart's frame loop relies on. Pure JVM — the spec is arithmetic.
 *
 * The numbers here were retuned in run Τ against the owner's frame-by-frame measurement of
 * TradingView on the same phone with the same finger. Friction went from 3.8 to 1.4 per second,
 * which is the distance a flick covers, and the cut-off from 20 px/s to 150 — see
 * `KineticScroll.MIN_VELOCITY` for what twenty looked like on a 120 Hz screen.
 */
class ChartFlingTest {

    @Test
    fun `a flick covers velocity over friction, which is what the eye actually measures`() {
        // Distance is the complaint and distance is the assertion. `(v − cut-off) / f`: at
        // 4 300 px/s — the speed the owner's finger produced in TradingView — that is about
        // 3 250 px, against the 2 900 px TradingView itself covered.
        val fling = ChartFling()
        fling.start(4_300f)
        var now = 0L
        var travelled = fling.tick(now)
        var frames = 0
        while (fling.isRunning && frames < 2_000) {
            now += FRAME_NANOS
            travelled += fling.tick(now)
            frames++
        }
        assertEquals((4_300f - KineticScroll.MIN_VELOCITY) / FRICTION, travelled, 30f)
    }

    @Test
    fun `the steps never speed up, sum to the curve's distance, and the fling stops on its own`() {
        val fling = ChartFling()
        fling.start(3_000f)
        var now = 0L
        val steps = mutableListOf(fling.tick(now))
        var travelled = steps.first()
        var frames = 0
        while (fling.isRunning && frames < 2_000) {
            now += FRAME_NANOS
            val step = fling.tick(now)
            steps += step
            travelled += step
            frames++
        }
        // **Measured in groups of eight frames, not frame to frame.**
        //
        // Compose reads the decay curve off a clock in whole milliseconds, and a 120 Hz frame is
        // 8⅓ of them — so frames cover 8, 8, 9, 8, 8, 9 … milliseconds of the curve and every third
        // or fourth step is about five per cent longer than the one before it. At the start of a
        // flick that is six tenths of a pixel and at the end of one it is nothing, but it is real,
        // and a frame-to-frame assertion would be asserting the clock rather than the physics.
        // Sixty-six milliseconds of travel has no such artefact in it: each group must be shorter
        // than the one before, which is what «never speeds up» means to a reader.
        val groups = steps.chunked(GROUP).map { it.sum() }
        groups.indices.drop(1).forEach { i ->
            assertTrue(
                "a fling must never speed up: group $i covered ${groups[i]} after ${groups[i - 1]}",
                groups[i] <= groups[i - 1] + 1e-3f,
            )
        }
        assertTrue(!fling.isRunning)
        assertEquals((3_000f - KineticScroll.MIN_VELOCITY) / FRICTION, travelled, 20f)
        assertEquals(0f, fling.tick(now + FRAME_NANOS), 0f)
    }

    @Test
    fun `it stops rather than creeping a pixel a frame`() {
        // The other half of what the owner filmed: «۲،۲،۲،۲،۱،۱،۱،۲،۱،۱» — two hundred milliseconds
        // of one pixel a frame after the motion was over, which reads as the chart catching on
        // something. The cut-off is what ends it, and it is set to exactly two pixels a frame at
        // 120 Hz so that the tail cannot exist: the last step the reader sees is a real one.
        val fling = ChartFling()
        fling.start(3_000f)
        var now = 0L
        val steps = mutableListOf(fling.tick(now))
        var frames = 0
        while (fling.isRunning && frames < 2_000) {
            now += FRAME_NANOS
            steps += fling.tick(now)
            frames++
        }
        val tail = steps.takeLastWhile { kotlin.math.abs(it) < 2f }
        assertTrue("the fling crept for ${tail.size} frames: $tail", tail.size <= 3)
    }

    @Test
    fun `the first frame after the release moves`() {
        // **Run Τ, item 3.** The finger lifts between two frames, so by the time the fling loop
        // gets a frame the release is already about one old. Reading the curve at zero on that
        // frame spends it standing still — the picture tracks the finger at full speed, stops for
        // eight milliseconds, then starts again — and a hand-off with a hole in it is the one
        // stutter a thumb can feel, because it happens where the chart is moving fastest.
        val fling = ChartFling()
        fling.start(3_000f)
        val handoff = fling.tick(0L)
        assertTrue("the hand-off frame moved nothing at all", handoff > 0f)
        // And it is a frame's worth, not a jump: a 3 000 px/s release covers about 25 px in the
        // 8⅓ ms of the first frame, so anything near the whole travel means the clock is wrong.
        assertTrue("the hand-off frame moved $handoff px, which is not one frame", handoff < 60f)
        val second = fling.tick(FRAME_NANOS)
        assertTrue("the second frame moved nothing", second > 0f)
        assertTrue("the fling sped up across the hand-off", second <= handoff + 1e-3f)
    }

    @Test
    fun `a leftward flick moves left, and a release slower than a drag starts nothing`() {
        val fling = ChartFling()
        fling.start(-1_200f)
        assertTrue(fling.tick(0L) < 0f)
        assertTrue(fling.tick(FRAME_NANOS) < 0f)
        // A hundred and forty is under the cut-off: a finger that slow was placing the chart, not
        // throwing it, and momentum on top of a placement is the chart moving after the reader
        // stopped.
        fling.start(140f)
        assertTrue(!fling.isRunning)
    }

    private companion object {
        /** One frame at 120 Hz, which is the rate this chart asks the panel for. */
        const val FRAME_NANOS = 8_333_333L
        const val FRICTION = 1.25f

        /** Frames per group — 66 ms, long enough to swallow the millisecond clock's sawtooth. */
        const val GROUP = 8
    }
}
