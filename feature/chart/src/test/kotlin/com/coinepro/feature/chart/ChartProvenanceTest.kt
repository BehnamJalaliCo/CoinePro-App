package com.coinepro.feature.chart

import com.coinepro.core.common.proseDigits
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertNotNull

/**
 * What the chart says it is *not* showing.
 *
 * Naming the venue answers "where did this come from" and leaves the question that actually
 * produces the accusation: why does this picture differ from the venue's own when both are
 * correct. Each sentence here is a fact about one chart at one moment, and the property worth
 * guarding is that none of them is said when it is not true — a line that is always present is a
 * line nobody reads, which makes every other line in the strip worth less.
 */
class ChartProvenanceTest {

    private fun series(bars: Int, volume: Double? = 5.0) = CandleSeries(
        (0 until bars).map { index ->
            val price = 100.0 + index * 0.5
            Candle(1_700_000_000L + index * 3600, price, price + 1, price - 1, price + 0.2, volume)
        },
    )

    private fun state(
        indicators: Set<String> = emptySet(),
        interval: ChartInterval = ChartInterval.Preset(Timeframe.H1),
        bars: CandleSeries = series(200),
        replay: ReplayState = ReplayState(),
    ) = ChartUiState(
        symbol = "XAUUSD",
        interval = interval,
        series = bars,
        activeIndicators = indicators,
        replay = replay,
    )

    @Test
    fun `a feed that reports no volume says so`() {
        // The TradingView complaint this exists for: their volume did not match the exchange's,
        // nothing said why, and the conclusion drawn was that the data was invented.
        // Asserted on the reason's *identity* rather than on a substring of its Persian (run Ω2):
        // the sentences live in `values/` and `values-fa/` now, and «some string contains حجم» was
        // always a proxy for the thing this test means.
        val silent = chartExclusions(state(bars = series(200, volume = null)))
        assertTrue(silent.any { it.res == R.string.provenance_no_volume })
    }

    @Test
    fun `a feed that does report volume says nothing about it`() {
        assertTrue(
            chartExclusions(state(bars = series(200, volume = 5.0)))
                .none { it.res == R.string.provenance_no_volume },
        )
    }

    @Test
    fun `a folded bar length names the bars it was actually built from`() {
        val folded = chartExclusions(state(interval = ChartInterval.Preset(Timeframe.H2)))
        val reason = folded.firstOrNull { it.res == R.string.provenance_folded }
        assertNotNull("a folded chart said nothing about being folded", reason)
        // And it names the bar it was folded *from*, which is the whole of what the sentence adds.
        assertEquals(listOf<Any>(Timeframe.H1.wire), reason!!.args)
    }

    @Test
    fun `a bar length the feed serves outright claims no folding`() {
        val native = chartExclusions(state(interval = ChartInterval.Preset(Timeframe.H1)))
        assertTrue(native.none { it.res == R.string.provenance_folded })
    }

    @Test
    fun `a replay says the future is being withheld on purpose`() {
        val replaying = state(replay = ReplayState(bars = series(200).bars, cursor = 100))
        assertTrue(chartExclusions(replaying).any { it.res == R.string.provenance_replay })
    }

    @Test
    fun `a repainting study is named rather than merely losing the trust mark`() {
        val reason = chartExclusions(state(setOf("zigzag")))
            .firstOrNull { it.res == R.string.provenance_repainting }
        assertNotNull("a repainting study slipped through unnamed", reason)
        // The claim's own sentence rides in as the second argument, so the colon between them is
        // the resource's business — see `ChartExclusion`.
        assertEquals(RepaintClaim.REPAINTS.noteRes, reason!!.args[1])
    }

    @Test
    fun `an ordinary chart has nothing to exclude and prints no line at all`() {
        assertTrue(chartExclusions(state()).isEmpty())
        assertEquals("", exclusionsLine(emptyList(), heading = "%1\$s"))
    }

    @Test
    fun `the exclusions read as one sentence with a heading when there are any`() {
        // The heading arrives resolved — this is not a composable — so the test supplies its own
        // and checks the shape rather than the wording.
        val line = exclusionsLine(listOf("one", "two"), heading = "not here — %1\$s")
        assertTrue(line.startsWith("not here — "))
        assertTrue(line.contains("one"))
        assertTrue(line.contains("two"))
    }

    @Test
    fun `an empty chart claims nothing about a volume column it has never seen`() {
        // A failed first load is not evidence about the feed. Saying "this feed reports no volume"
        // over an empty chart would be inventing a fact out of an absence of data.
        assertTrue(
            chartExclusions(state(bars = CandleSeries.EMPTY))
                .none { it.res == R.string.provenance_no_volume },
        )
    }

    @Test
    fun `the bar count is prose, so both its digits and its noun follow the screen`() {
        // It was `barCount.toPersianDigits() + " کندل"` — a concatenation, so «۲۰۰ کندل» was drawn
        // on the English tablet. It is a resource now and its argument is a prose count, which is
        // what this asserts without a composition: the two halves that used to be hardcoded.
        assertTrue("a prose count is Persian digits in Persian", 120.proseDigits(english = false).none { it in '0'..'9' })
        assertTrue("and Latin digits in English", 120.proseDigits(english = true).all { it in '0'..'9' })
    }
}
