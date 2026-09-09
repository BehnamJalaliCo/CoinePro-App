package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The replay ladder, the two jumps, and the raw slice.
 *
 * `ReplayAndTradeTest` already covers the original transitions. This file covers what was added
 * around them: the nine-step speed ladder, [Replay.goTo] and [Replay.jumpToLive], and the assertion
 * the whole design rests on — that replay hands back *untransformed* bars, so a Renko replay is the
 * Renko history would have printed rather than a truncated one.
 */
class ReplayTest {

    /** A gently trending series, one bar an hour, wide enough that a Renko brick prints often. */
    private fun bars(count: Int): List<Candle> = (0 until count).map { index ->
        val base = 100.0 + index * 0.5
        Candle(1_700_000_000L + index * 3_600, base, base + 1, base - 1, base + 0.4)
    }

    // ── the speed ladder ──────────────────────────────────────────────────────────────

    @Test
    fun `the ladder has nine steps and runs from slow study to fast scan`() {
        assertEquals(9, ReplaySpeed.entries.size)
        assertEquals(0.1, ReplaySpeed.STUDY.multiplier, 1e-9)
        assertEquals(30.0, ReplaySpeed.THIRTY.multiplier, 1e-9)
        assertEquals(16_000L, ReplaySpeed.STUDY.millisPerBar)
        assertEquals(53L, ReplaySpeed.THIRTY.millisPerBar)
    }

    @Test
    fun `every step of the ladder is strictly faster than the one before it`() {
        // Two chips that hold a bar for the same time is the bug this replaces: the old delay
        // clamped at four seconds, so 0.1x and 0.25x were the same control wearing two labels.
        val steps = ReplaySpeed.entries
        for (index in 1 until steps.size) {
            assertTrue(
                "${steps[index]} must be faster than ${steps[index - 1]}",
                steps[index].millisPerBar < steps[index - 1].millisPerBar,
            )
            assertTrue(steps[index].multiplier > steps[index - 1].multiplier)
        }
    }

    @Test
    fun `the multiplier list the picker draws is the ladder itself`() {
        assertEquals(ReplaySpeed.entries.map { it.multiplier }, Replay.SPEEDS)
    }

    @Test
    fun `the delay arithmetic reproduces the ladder at every step`() {
        // The enum and the formula are both public and both used; if they ever disagree the picker
        // shows one speed and the scheduler runs another, which is invisible until it is timed.
        ReplaySpeed.entries.forEach { step ->
            assertEquals(step.millisPerBar, Replay.delayMillis(step.multiplier))
            assertEquals(step.millisPerBar, Replay.delayMillis(step))
        }
    }

    @Test
    fun `a speed of zero or below is read as normal rather than dividing to infinity`() {
        assertEquals(ReplaySpeed.NORMAL.millisPerBar, Replay.delayMillis(0.0))
        assertEquals(ReplaySpeed.NORMAL.millisPerBar, Replay.delayMillis(-4.0))
    }

    @Test
    fun `an off-ladder multiplier from a saved layout resolves to the nearest step`() {
        assertEquals(ReplaySpeed.TRIPLE, ReplaySpeed.nearest(2.9))
        assertEquals(ReplaySpeed.STUDY, ReplaySpeed.nearest(0.0))
        assertEquals(ReplaySpeed.THIRTY, ReplaySpeed.nearest(999.0))
    }

    @Test
    fun `setting a speed by ladder step always applies, unlike the raw double`() {
        val state = Replay.enter(bars(60))!!
        assertEquals(5.0, Replay.setSpeed(state, ReplaySpeed.QUINTUPLE).speed, 1e-9)
        assertEquals(ReplaySpeed.QUINTUPLE, Replay.setSpeed(state, ReplaySpeed.QUINTUPLE).speedStep)
        // The double overload is a filter, and 7 is not on the ladder.
        assertEquals(state.speed, Replay.setSpeed(state, 7.0).speed, 1e-9)
    }

    // ── goTo and jumpToLive ───────────────────────────────────────────────────────────

    @Test
    fun `goTo repositions without leaving replay`() {
        val state = Replay.enter(bars(200), startIndex = 120)!!
        val moved = Replay.goTo(state, 40)
        assertEquals(40, moved.cursor)
        assertTrue("the snapshot is kept, so replay is still on", moved.isOn)
        assertEquals(200, moved.bars.size)
    }

    @Test
    fun `goTo stops playback so the bar jumped to is not immediately left behind`() {
        val playing = Replay.play(Replay.enter(bars(200), startIndex = 50)!!)
        assertTrue(playing.playing)
        assertFalse(Replay.goTo(playing, 90).playing)
    }

    @Test
    fun `goTo clamps at both ends instead of throwing`() {
        val state = Replay.enter(bars(80), startIndex = 40)!!
        assertEquals(0, Replay.goTo(state, -500).cursor)
        assertEquals(79, Replay.goTo(state, 5_000).cursor)
        assertEquals(79, Replay.goTo(state, 80).cursor)
    }

    @Test
    fun `goTo on a series that never loaded is a no-op`() {
        assertEquals(ReplayState(), Replay.goTo(ReplayState(), 12))
    }

    @Test
    fun `jumpToLive lands on the newest bar and keeps the snapshot`() {
        val state = Replay.enter(bars(150), startIndex = 20)!!
        val live = Replay.jumpToLive(state)
        assertEquals(149, live.cursor)
        assertTrue(live.atEnd)
        assertTrue("jumping to live is not leaving replay", live.isOn)
        assertEquals(150, live.visibleRaw().size)
    }

    @Test
    fun `jumpToLive stops playback, and exit is what actually forgets the snapshot`() {
        val playing = Replay.play(Replay.enter(bars(150), startIndex = 20)!!)
        assertFalse(Replay.jumpToLive(playing).playing)
        assertFalse("only exit ends replay", Replay.exit().isOn)
    }

    // ── guards ────────────────────────────────────────────────────────────────────────

    @Test
    fun `entering before the series has loaded returns null rather than throwing`() {
        assertNull(Replay.enter(emptyList()))
        assertNull(Replay.enter(bars(1)))
        assertNull(Replay.enter(bars(Replay.MINIMUM_BARS - 1), startIndex = 3))
        assertNotNull(Replay.enter(bars(Replay.MINIMUM_BARS)))
    }

    @Test
    fun `a start index outside the series is clamped, not rejected`() {
        assertEquals(0, Replay.enter(bars(40), startIndex = -12)!!.cursor)
        assertEquals(39, Replay.enter(bars(40), startIndex = 4_000)!!.cursor)
    }

    @Test
    fun `stepping past either end clamps and never runs off the series`() {
        var state = Replay.enter(bars(40), startIndex = 38)!!
        repeat(20) { state = Replay.step(state) }
        assertEquals(39, state.cursor)
        assertEquals(40, state.visibleRaw().size)

        repeat(200) { state = Replay.stepBack(state) }
        assertEquals(0, state.cursor)
        assertEquals(1, state.visibleRaw().size)
    }

    // ── the raw slice, which is why replay works on every chart type ──────────────────

    @Test
    fun `visibleRaw returns the untransformed bars, identical to the head of the input`() {
        val all = bars(120)
        val state = Replay.enter(all, startIndex = 63)!!
        val raw = state.visibleRaw()
        assertEquals(64, raw.size)
        assertEquals(all.take(64), raw)
        // The last revealed bar is the reader's "now" and its close is a real traded price, not a
        // Heikin average or a Renko brick.
        assertEquals(all[63].c, raw.last().c, 1e-9)
    }

    @Test
    fun `visibleRaw is stable across calls, so a viewport keyed on it survives`() {
        val state = Replay.enter(bars(120), startIndex = 63)!!
        assertSame(state.visibleRaw(), state.visibleRaw())
        assertSame(state.visible.bars, state.visibleRaw())
    }

    @Test
    fun `a Renko replay is built from the slice, not sliced from the Renko`() {
        // The reason we replay every chart type and the web terminal replays none of the
        // path-dependent ones. Renko is time-free: bricks print only when price has travelled a
        // box, so truncating a finished brick series gives a chart history never produced. Running
        // the transform over the raw slice gives exactly the chart that existed at that bar.
        val all = bars(120)
        val config = ChartTypeConfig(brick = 2.0)
        val state = Replay.enter(all, startIndex = 63)!!

        val fromSlice = ChartTransforms.apply(CandleSeries(state.visibleRaw()), ChartType.RENKO, config)
        val whatLiveWouldHaveDrawn =
            ChartTransforms.apply(CandleSeries(all.take(64)), ChartType.RENKO, config)
        assertEquals(whatLiveWouldHaveDrawn.bars, fromSlice.bars)

        // And it is genuinely a different count from the full history's bricks, so the slice is
        // doing work rather than the assertion above being trivially true.
        val full = ChartTransforms.apply(CandleSeries(all), ChartType.RENKO, config)
        assertTrue("a truncated history must have fewer bricks", fromSlice.size < full.size)
    }

    @Test
    fun `a Heikin Ashi replay recomputes from the slice and never reveals a later bar`() {
        val all = bars(120)
        val state = Replay.enter(all, startIndex = 40)!!
        val replayed = ChartTransforms.apply(CandleSeries(state.visibleRaw()), ChartType.HEIKIN_ASHI)
        assertEquals(41, replayed.size)
        assertEquals(all[40].t, replayed.time.last())
    }
}
