package com.coinepro.core.chart

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Signal Layer, on series whose answers are known before the code runs (run Ω1).
 *
 * ### Why these fixtures rather than a recorded market
 *
 * Because the thing being tested is a *claim about the market*, and a claim is only testable against
 * data whose answer somebody can work out by hand. A staircase that rises for forty bars and falls
 * for forty has one EMA cross in it and everybody can agree where; a real chart has eleven, and a
 * test that asserts eleven is asserting that today's code does what today's code does.
 *
 * The confidence tests are the important ones. A win rate is the number this product asks a reader
 * to trust, and there are exactly two ways to get it wrong that a reader could never detect: count
 * a signal whose horizon has not printed yet, and ignore a stop that was hit on the way to a win.
 * Both are pinned below.
 */
class SignalLayerTest {

    /** A series that rises for [up] bars and then falls for [down], one unit a bar. */
    private fun staircase(up: Int, down: Int, start: Double = 100.0, step: Double = 1.0): CandleSeries {
        val bars = mutableListOf<Candle>()
        var price = start
        repeat(up) { index ->
            bars += bar(index, price, price + step)
            price += step
        }
        repeat(down) { index ->
            bars += bar(up + index, price, price - step)
            price -= step
        }
        return CandleSeries(bars)
    }

    private fun bar(index: Int, open: Double, close: Double): Candle = Candle(
        t = 1_700_000_000L + index * 3_600L,
        o = open,
        h = maxOf(open, close) + 0.5,
        l = minOf(open, close) - 0.5,
        c = close,
        v = 10.0,
    )

    private fun flat(bars: Int, price: Double = 100.0): CandleSeries =
        CandleSeries((0 until bars).map { bar(it, price, price) })

    // ── the four rules ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a rising market reads bullish against its own average`() {
        val read = SignalSpec.read("ema", staircase(up = 60, down = 0))
        assertEquals(MarketState.BULL, read.state)
        // The close is above the average on every bar of a rise, so there is nothing to cross: the
        // sentence is the standing state rather than an event, which is the distinction the whole
        // note vocabulary exists to keep.
        assertEquals(NoteShape.SITS_ABOVE, read.note.shape)
    }

    @Test
    fun `a market that rolls over crosses its average and says so`() {
        val read = SignalSpec.read("ema", staircase(up = 40, down = 30))
        assertEquals(MarketState.BEAR, read.state)
        assertTrue("the turn should have produced a cross", read.events.isNotEmpty())
        assertEquals(TradeSide.SELL, read.events.last().side)
        // And the stop is on the other side of the swing the signal came out of.
        assertNotNull(read.stop)
    }

    @Test
    fun `an oscillator leaving its floor is a buy, and the sentence names the level`() {
        // Down hard, then up: RSI goes under thirty and comes back over it.
        val series = staircase(up = 20, down = 40, start = 200.0).let { fall ->
            CandleSeries(fall.bars + staircase(up = 15, down = 0, start = fall.bars.last().c).bars.mapIndexed { index, candle ->
                candle.copy(t = fall.bars.last().t + (index + 1) * 3_600L)
            })
        }
        val read = SignalSpec.read("rsi", series)
        val buy = read.events.lastOrNull { it.side == TradeSide.BUY }
        assertNotNull("RSI came back over 30 and should have fired", buy)
        assertTrue(read.events.any { it.side == TradeSide.BUY })
    }

    @Test
    fun `a measurement reports and never fires`() {
        for (id in listOf("adx", "atr", "choppiness")) {
            val read = SignalSpec.read(id, staircase(up = 40, down = 40))
            assertTrue("$id must not produce a buy", read.events.isEmpty())
            assertEquals("$id has no direction in it", MarketState.NEUTRAL, read.state)
        }
    }

    @Test
    fun `every catalogue indicator answers, and none of them throws`() {
        // The claim this test defends is the product's, not the code's: **every** study on the chart
        // explains itself. A study that returned nothing would be a legend row with a blank pill.
        val series = staircase(up = 80, down = 60)
        for (option in ChartCatalog.INDICATORS) {
            val read = SignalSpec.read(option.id, series)
            assertEquals(option.id, read.id)
            assertTrue(
                "${option.id} produced events with nothing to say about them",
                read.note.shape != NoteShape.QUIET || read.events.isEmpty(),
            )
        }
    }

    @Test
    fun `a series too short to mean anything says nothing rather than guessing`() {
        assertEquals(MarketState.NEUTRAL, SignalSpec.read("ema", flat(5)).state)
        assertTrue(SignalSpec.read("rsi", flat(5)).events.isEmpty())
    }

    @Test
    fun `both languages render, and neither leaks into the other`() {
        val series = staircase(up = 40, down = 30)
        val persian = SignalSpec.read("ema", series, english = false).note.text(english = false)
        val english = SignalSpec.read("ema", series, english = true).note.text(english = true)
        assertTrue("the Persian sentence has no Persian in it: $persian", persian.any { it in '؀'..'ۿ' })
        assertFalse("the English sentence has Persian in it: $english", english.any { it in '؀'..'ۿ' })
        // And the study's own name is the same in both, because «EMA» is what it is called.
        assertTrue("EMA" in persian && "EMA" in english)
    }

    // ── confidence ───────────────────────────────────────────────────────────────────────────

    /** Two buys, both of which are ten bars later exactly one step higher, with no stop hit. */
    @Test
    fun `a study that was right both times reads a hundred per cent, and says out of how many`() {
        val series = staircase(up = 60, down = 0)
        val events = listOf(SignalEvent(10, TradeSide.BUY), SignalEvent(20, TradeSide.BUY))
        val read = SignalRead("fixture", MarketState.BULL, events)
        val report = ConfidenceEngine.measure(read, series, horizon = 10) { null }
        assertEquals(2, report.samples)
        assertEquals(1.0, report.winRate, 1e-9)
        // Below the thin line, so no percentage is printed however good it looks.
        assertFalse(report.trustworthy)
        assertEquals(0, report.percent)
    }

    @Test
    fun `a signal whose horizon has not printed yet is not counted`() {
        val series = staircase(up = 30, down = 0)
        // The newest bar is 29; a signal at 25 measured over ten bars has not settled.
        val read = SignalRead("fixture", MarketState.BULL, listOf(SignalEvent(25, TradeSide.BUY)))
        assertEquals(0, ConfidenceEngine.measure(read, series, horizon = 10) { null }.samples)
    }

    @Test
    fun `a trade stopped out on the way to a win is a loss`() {
        // Rises to bar 20, dips hard, then recovers above the entry by the horizon. Read at the
        // horizon alone this is a win; walked bar by bar with the stop in place it is minus one R.
        val bars = mutableListOf<Candle>()
        var price = 100.0
        repeat(21) { bars += bar(it, price, price + 1); price += 1 }
        // The dip: one bar whose low is far under the stop, close back near the entry.
        bars += Candle(t = bars.last().t + 3_600L, o = price, h = price + 0.5, l = price - 20, c = price, v = 1.0)
        repeat(10) { bars += bar(22 + it, price, price + 1); price += 1 }
        val series = CandleSeries(bars)
        val entry = series.close[20]
        val read = SignalRead("fixture", MarketState.BULL, listOf(SignalEvent(20, TradeSide.BUY)))
        val report = ConfidenceEngine.measure(read, series, horizon = 10) { entry - 5.0 }
        assertEquals(1, report.samples)
        assertEquals("the stop was hit before the horizon", 0.0, report.winRate, 1e-9)
        assertEquals(-1.0, report.averageR, 1e-9)
    }

    @Test
    fun `the outcome is in units of risk, so two instruments are comparable`() {
        val series = staircase(up = 40, down = 0)
        val entry = series.close[10]
        // A stop two units under the entry and a move of ten units up is five R.
        val read = SignalRead("fixture", MarketState.BULL, listOf(SignalEvent(10, TradeSide.BUY)))
        val report = ConfidenceEngine.measure(read, series, horizon = 10) { entry - 2.0 }
        assertEquals(1, report.samples)
        assertTrue("expected five R, got ${report.averageR}", abs(report.averageR - 5.0) < 1e-9)
    }

    @Test
    fun `a study with no events has an empty report rather than a zero per cent`() {
        val report = ConfidenceEngine.measure(SignalRead.quiet("adx"), staircase(40, 40))
        assertEquals(0, report.samples)
        assertFalse(report.trustworthy)
    }

    // ── the setup score ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a chart whose studies all agree scores a hundred`() {
        val reads = listOf(
            SignalRead("ema", MarketState.BULL),
            SignalRead("rsi", MarketState.BULL),
        )
        val reports = mapOf(
            "ema" to ConfidenceReport("ema", 20, 0.6, 0.4, emptyList(), 10),
            "rsi" to ConfidenceReport("rsi", 20, 0.7, 0.5, emptyList(), 10),
        )
        val score = ConfidenceEngine.setupScore(reads, reports)
        assertEquals(100, score.score)
        assertEquals(MarketState.BULL, score.side)
        assertEquals(2, score.studies)
    }

    @Test
    fun `a chart that disagrees with itself scores nought rather than fifty`() {
        val reads = listOf(SignalRead("ema", MarketState.BULL), SignalRead("rsi", MarketState.BEAR))
        val reports = mapOf(
            "ema" to ConfidenceReport("ema", 20, 0.6, 0.4, emptyList(), 10),
            "rsi" to ConfidenceReport("rsi", 20, 0.6, 0.4, emptyList(), 10),
        )
        // Fifty would read as «half likely». Nought reads as «this chart is not saying anything»,
        // which is what a dead heat means.
        assertEquals(0, ConfidenceEngine.setupScore(reads, reports).score)
    }

    @Test
    fun `a study with no history still counts, at half weight`() {
        val reads = listOf(SignalRead("ema", MarketState.BULL))
        val score = ConfidenceEngine.setupScore(reads, emptyMap())
        assertEquals("one unopposed study is still unanimous", 100, score.score)
        assertEquals(1, score.studies)
    }

    @Test
    fun `an empty chart scores nothing and says so`() {
        val score = ConfidenceEngine.setupScore(emptyList(), emptyMap())
        assertTrue(score.isEmpty)
        assertEquals(0, score.score)
    }
}
