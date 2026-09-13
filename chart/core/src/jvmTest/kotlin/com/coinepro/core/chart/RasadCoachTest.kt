package com.coinepro.core.chart

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the coach is allowed to say.
 *
 * Two properties carry most of this file, and they are the two that make a deterministic coach worth
 * having at all: **the same chart always produces the same words**, and **every word is checkable
 * against the picture**. A coach that paraphrases differently on each recomposition is one a reader
 * learns to distrust; a coach that names a level that is not on the chart is one that costs them
 * money once and then is uninstalled.
 */
class RasadCoachTest {

    /** A rising market with a real shape: a drift plus a repeatable shock, so levels actually form. */
    private fun trending(count: Int = 200, drift: Double = 0.35): CandleSeries {
        var state = 0x51E2D3L
        fun shock(): Double {
            state = (state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L)
            return ((state ushr 11).toDouble() / (1L shl 53).toDouble()) - 0.5
        }
        var close = 100.0
        return CandleSeries(
            (0 until count).map { index ->
                close += drift + shock() * 2.0
                Candle(
                    t = 1_700_000_000L + index * 3_600L,
                    o = close - 0.3,
                    h = close + 0.8,
                    l = close - 0.9,
                    c = close,
                    v = 1_000.0,
                )
            },
        )
    }

    /** A market going nowhere: the same handful of prices, over and over, which is what a range is. */
    private fun ranging(count: Int = 200): CandleSeries {
        val shape = listOf(100.0, 102.0, 104.0, 102.0, 100.0, 98.0, 96.0, 98.0)
        return CandleSeries(
            (0 until count).map { index ->
                val close = shape[index % shape.size]
                Candle(
                    t = 1_700_000_000L + index * 3_600L,
                    o = close,
                    h = close + 1.0,
                    l = close - 1.0,
                    c = close,
                    v = 1_000.0,
                )
            },
        )
    }

    @Test
    fun `it says three sentences, always three, in the same order`() {
        val said = RasadCoach.readChart(trending())
        assertEquals("the coach must say exactly three sentences", 3, said.size)
        for (sentence in said) {
            assertTrue("a sentence is empty", sentence.isNotBlank())
            assertTrue("a sentence does not end: $sentence", sentence.trimEnd().endsWith("."))
        }
    }

    @Test
    fun `the same chart always produces the same words`() {
        // The property the whole design rests on. Two calls, no shared state, identical output —
        // which also means a screenshot of the coach is a golden rather than a sample.
        val series = trending()
        assertEquals(RasadCoach.readChart(series), RasadCoach.readChart(series))
    }

    @Test
    fun `a chart too short to read says nothing rather than guessing`() {
        assertEquals(emptyList<String>(), RasadCoach.readChart(trending(count = 12)))
        assertNull(RasadCoach.suggestAlert(trending(count = 12)))
    }

    @Test
    fun `it names no level that is not on the chart`() {
        // Every price in the level sentence must be one `Structure` actually found, to the precision
        // the sentence prints it at. This is the check that makes the coach falsifiable: a reader can
        // hold the sentence against the chart and the two cannot disagree.
        val series = trending()
        val last = series.close.last()
        val levels = Structure.supportResistance(series).map { it.price }
        val sentence = RasadCoach.readChart(series)[1]
        val printed = Regex("[0-9]+\\.?[0-9]*").findAll(sentence).map { it.value.toDouble() }.toList()
        for (price in printed) {
            val known = levels.any { abs(it - price) < 0.05 } || abs(last - price) < 0.05
            assertTrue("the coach named $price, which is not a level on this chart: $sentence", known)
        }
    }

    @Test
    fun `a range is called a range rather than a weak trend`() {
        // The most useful sentence the coach has: inside a range, every trend study on the chart is
        // measuring something that is not happening, and a reader told «the trend is weak» will take
        // the next crossover anyway.
        val said = RasadCoach.readChart(ranging())
        assertTrue("a range was not named as one: ${said.first()}", said.first().contains("محدوده"))
    }

    @Test
    fun `it counts the studies it is counting out of`() {
        val reads = listOf(
            SignalRead("ema", MarketState.BULL),
            SignalRead("rsi", MarketState.BULL),
            SignalRead("macd", MarketState.BEAR),
            SignalRead.quiet("volumeprofile"),
        )
        val sentence = RasadCoach.readChart(
            series = trending(),
            reads = reads,
            setup = SetupScore(60, MarketState.BULL, 3, 4),
        )[2]
        // Four studies, three with an opinion, and they disagree — all three facts in one sentence,
        // because «the studies say up» over a chart where one of them says down is the sentence that
        // loses a reader's trust the first time they check it.
        // Persian digits, because a count of studies is prose. The prices in the sentence above
        // are market figures and stay Latin — the app draws that line everywhere.
        assertTrue("the total is missing: $sentence", sentence.contains("۴"))
        assertTrue("the disagreement is not named: $sentence", sentence.contains("موافق نیستند"))
    }

    @Test
    fun `a chart whose studies are all silent says so`() {
        val sentence = RasadCoach.readChart(
            series = trending(),
            reads = listOf(SignalRead.quiet("volumeprofile")),
        )[2]
        assertTrue("silence was not reported: $sentence", sentence.contains("نظری"))
    }

    @Test
    fun `the suggested alert is never at the price the market is already at`() {
        // An alert at the current price fires on the next tick, and a reader whose first alert did
        // that learns that alerts are noise.
        val series = trending()
        val last = series.close.last()
        val suggestion = RasadCoach.suggestAlert(series)
        assertNotNull("nothing was suggested on a chart with levels on it", suggestion)
        val distance = abs(suggestion!!.price - last) / last
        assertTrue("the suggestion is on top of the price", distance >= 0.002)
        assertTrue("the suggestion is a year away", distance <= 0.06)
        assertEquals("the side disagrees with the level", suggestion.price > last, suggestion.above)
        assertTrue("the suggestion does not say why", suggestion.why.isNotBlank())
    }

    @Test
    fun `a trade with no stop is reviewed as a trade with no stop`() {
        val review = RasadCoach.reviewTrade(TradeFacts(entry = 100.0, exit = 104.0, hadStop = false))
        assertEquals(2, review.size)
        assertTrue("the missing stop was not named: ${review[0]}", review[0].contains("حد ضرر"))
        // And the profit is not the headline. A winning trade taken without a stop is a worse trade
        // than a losing one taken with a stop, and leading with the result teaches the opposite.
        assertTrue("the review led with the money", review.none { it.contains("برابر") })
    }

    @Test
    fun `a stop that closed a trade is reported as the plan working`() {
        val review = RasadCoach.reviewTrade(
            TradeFacts(entry = 100.0, exit = 98.0, stop = 98.0, closedByStop = true, rMultiple = -1.0),
        )
        assertEquals(3, review.size)
        assertTrue("a stop-out was reported as a failure: ${review[1]}", review[1].contains("کار کرد"))
        assertTrue("the result is last", review[2].contains("برابر"))
    }

    @Test
    fun `a record that does not say whether there was a stop does not claim there was none`() {
        // The three-way case. A take-profit arrives from the app's own book with no stop price
        // attached, because the book keeps it only where the stop is what closed the trade — and
        // scolding a reader for that gap is the coach inventing a fault out of a database.
        val review = RasadCoach.reviewTrade(TradeFacts(entry = 100.0, exit = 104.0, target = 104.0))
        assertTrue("a missing record was read as a missing stop: ${review[0]}", !review[0].contains("بدون حد ضرر"))
        assertTrue("the entry and the exit are both named", review[0].contains("100") && review[0].contains("104"))
        assertTrue("it did not report reaching the target: ${review[1]}", review[1].contains("هدف"))
    }

    @Test
    fun `the English coach speaks English`() {
        val said = RasadCoach.readChart(trending(), english = true) +
            RasadCoach.reviewTrade(TradeFacts(100.0, 104.0, stop = 98.0, rMultiple = 2.0), english = true) +
            listOfNotNull(RasadCoach.suggestAlert(trending(), english = true)?.why)
        for (sentence in said) {
            assertTrue("a Persian word reached the English coach: $sentence", sentence.none { it in '؀'..'ۿ' })
        }
    }
}
