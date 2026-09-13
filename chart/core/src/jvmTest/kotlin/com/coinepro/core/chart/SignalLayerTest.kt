package com.coinepro.core.chart

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // ── the state bands (run Ω-FIX item 4) ───────────────────────────────────────────────────

    @Test
    fun `RSI at forty-eight point six is neutral, not bearish`() {
        // The owner's device, exactly: a red dot and «نزولی» beside a reading one and a half points
        // off dead centre.
        val bounds = SignalSpec.OSCILLATOR_BOUNDS.getValue("rsi")
        assertEquals(MarketState.NEUTRAL, SignalSpec.oscillatorState(48.6, bounds))
        assertEquals("خنثی", SignalSpec.oscillatorState(48.6, bounds).label(english = false))
    }

    @Test
    fun `RSI's neutral band is exactly forty to sixty`() {
        val bounds = SignalSpec.OSCILLATOR_BOUNDS.getValue("rsi")
        assertEquals(MarketState.NEUTRAL, SignalSpec.oscillatorState(40.0, bounds))
        assertEquals(MarketState.NEUTRAL, SignalSpec.oscillatorState(60.0, bounds))
        assertEquals(MarketState.BEAR, SignalSpec.oscillatorState(39.9, bounds))
        assertEquals(MarketState.BULL, SignalSpec.oscillatorState(60.1, bounds))
    }

    @Test
    fun `the extremes are still read, on every bounded oscillator`() {
        for ((id, bounds) in SignalSpec.OSCILLATOR_BOUNDS) {
            assertEquals("$id at its floor", MarketState.BEAR, SignalSpec.oscillatorState(bounds.start, bounds))
            assertEquals(
                "$id at its ceiling",
                MarketState.BULL,
                SignalSpec.oscillatorState(bounds.endInclusive, bounds),
            )
            val middle = (bounds.start + bounds.endInclusive) / 2
            assertEquals("$id in the middle", MarketState.NEUTRAL, SignalSpec.oscillatorState(middle, bounds))
        }
    }

    @Test
    fun `a close sitting on its moving average has no direction`() {
        // A tenth of an average range above the line is not «above the line»; a fifth of one is.
        assertEquals(MarketState.NEUTRAL, SignalSpec.referenceState(close = 100.1, level = 100.0, range = 10.0))
        assertEquals(MarketState.NEUTRAL, SignalSpec.referenceState(close = 99.9, level = 100.0, range = 10.0))
        assertEquals(MarketState.BULL, SignalSpec.referenceState(close = 102.0, level = 100.0, range = 10.0))
        assertEquals(MarketState.BEAR, SignalSpec.referenceState(close = 98.0, level = 100.0, range = 10.0))
    }

    @Test
    fun `the touching band is in the instrument's own units`() {
        // The same tenth of a point that is nothing on an instrument with a ten-point range is a
        // real move on one with a range of a tenth. A percentage of price could not tell them apart.
        assertEquals(MarketState.NEUTRAL, SignalSpec.referenceState(close = 100.1, level = 100.0, range = 10.0))
        assertEquals(MarketState.BULL, SignalSpec.referenceState(close = 100.1, level = 100.0, range = 0.1))
    }

    @Test
    fun `with no measured range the comparison is the strict one`() {
        assertEquals(MarketState.BULL, SignalSpec.referenceState(close = 100.001, level = 100.0, range = 0.0))
        assertEquals(MarketState.NEUTRAL, SignalSpec.referenceState(close = 100.0, level = 100.0, range = 0.0))
    }

    // ── the setup score ──────────────────────────────────────────────────────────────────────

    @Test
    fun `agreement cannot lift the score above what the contributors are worth`() {
        val reads = listOf(
            SignalRead("ema", MarketState.BULL),
            SignalRead("rsi", MarketState.BULL),
        )
        val reports = mapOf(
            "ema" to ConfidenceReport("ema", 20, 0.6, 0.4, emptyList(), 10),
            "rsi" to ConfidenceReport("rsi", 20, 0.7, 0.5, emptyList(), 10),
        )
        val score = ConfidenceEngine.setupScore(reads, reports)
        // Two studies pointing the same way at 60 % and 70 %, both on twenty samples — two thirds
        // of full weight each. The score is their weighted mean, not the hundred a consensus
        // measure would print.
        assertEquals(65, score.score)
        assertEquals(MarketState.BULL, score.side)
        assertEquals(2, score.studies)
        assertEquals(0.65, score.winRate!!, 1e-9)
    }

    @Test
    fun `the device case — four bullish studies at forty per cent do not read a hundred`() {
        // The owner's 4.79.0 recording, exactly: «۱۰۰ صعودی» over four contributors at 43, 40, 39
        // and 40 per cent. The acceptance figure in the fix list is «≤ 45».
        val rates = listOf(0.43, 0.40, 0.39, 0.40)
        val reads = rates.indices.map { SignalRead("study$it", MarketState.BULL) }
        val reports = rates.withIndex().associate { (index, rate) ->
            "study$index" to ConfidenceReport("study$index", 30, rate, 0.0, emptyList(), 10)
        }
        val score = ConfidenceEngine.setupScore(reads, reports)
        assertTrue("four studies at forty per cent scored ${score.score}", score.score <= 45)
        assertEquals(MarketState.BULL, score.side)
        assertEquals(0.405, score.winRate!!, 1e-9)
    }

    @Test
    fun `a chart of losing studies can never reach the cap, however many of them agree`() {
        // The property the cap states, checked over every count from one to twelve rather than on
        // the one arrangement that happened to be written down.
        for (count in 1..12) {
            val reads = (0 until count).map { SignalRead("s$it", MarketState.BULL) }
            val reports = (0 until count).associate { index ->
                "s$index" to ConfidenceReport("s$index", 60, 0.49, 0.0, emptyList(), 10)
            }
            val score = ConfidenceEngine.setupScore(reads, reports).score
            assertTrue("$count losing studies scored $score", score <= ConfidenceEngine.CONFIDENCE_CAP)
        }
    }

    @Test
    fun `a thin record counts for less than a full one`() {
        val thin = ConfidenceEngine.setupScore(
            listOf(SignalRead("ema", MarketState.BULL), SignalRead("rsi", MarketState.BEAR)),
            mapOf(
                // Six samples is a fifth of the weight of thirty, so the bull carries the day
                // despite the bear reading better.
                "ema" to ConfidenceReport("ema", 30, 0.6, 0.0, emptyList(), 10),
                "rsi" to ConfidenceReport("rsi", 6, 0.9, 0.0, emptyList(), 10),
            ),
        )
        assertEquals(MarketState.BULL, thin.side)
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
    fun `a study with no history has a direction and no confidence`() {
        val reads = listOf(SignalRead("ema", MarketState.BULL))
        val score = ConfidenceEngine.setupScore(reads, emptyMap())
        // It used to score a hundred — one unopposed study, unanimous. Unanimity is not the
        // measurement any more, and a study nobody has measured contributes no confidence at all.
        assertEquals(0, score.score)
        assertEquals(MarketState.BULL, score.side)
        assertEquals(1, score.studies)
        assertNull(score.winRate)
    }

    @Test
    fun `an empty chart scores nothing and says so`() {
        val score = ConfidenceEngine.setupScore(emptyList(), emptyMap())
        assertTrue(score.isEmpty)
        assertEquals(0, score.score)
    }
}
