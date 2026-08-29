package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val silent = chartExclusions(state(bars = series(200, volume = null)))
        assertTrue(silent.any { it.contains("حجم") })
    }

    @Test
    fun `a feed that does report volume says nothing about it`() {
        assertTrue(chartExclusions(state(bars = series(200, volume = 5.0))).none { it.contains("حجم") })
    }

    @Test
    fun `a folded bar length names the bars it was actually built from`() {
        val folded = chartExclusions(state(interval = ChartInterval.Preset(Timeframe.H2)))
        assertTrue(folded.any { it.contains(Timeframe.H1.wire) })
    }

    @Test
    fun `a bar length the feed serves outright claims no folding`() {
        val native = chartExclusions(state(interval = ChartInterval.Preset(Timeframe.H1)))
        assertTrue(native.none { it.contains("ساخته می‌شوند") })
    }

    @Test
    fun `a replay says the future is being withheld on purpose`() {
        val replaying = state(replay = ReplayState(bars = series(200).bars, cursor = 100))
        assertTrue(chartExclusions(replaying).any { it.contains("بازپخش") })
    }

    @Test
    fun `a repainting study is named rather than merely losing the trust mark`() {
        assertTrue(chartExclusions(state(setOf("zigzag"))).any { it.contains(RepaintClaim.REPAINTS.note) })
    }

    @Test
    fun `an ordinary chart has nothing to exclude and prints no line at all`() {
        assertTrue(chartExclusions(state()).isEmpty())
        assertEquals("", exclusionsLine(emptyList()))
    }

    @Test
    fun `the exclusions read as one sentence with a heading when there are any`() {
        val line = exclusionsLine(listOf("یک", "دو"))
        assertTrue(line.startsWith("آنچه در این تصویر نیست"))
        assertTrue(line.contains("یک"))
        assertTrue(line.contains("دو"))
    }

    @Test
    fun `an empty chart claims nothing about a volume column it has never seen`() {
        // A failed first load is not evidence about the feed. Saying "this feed reports no volume"
        // over an empty chart would be inventing a fact out of an absence of data.
        assertTrue(chartExclusions(state(bars = CandleSeries.EMPTY)).none { it.contains("حجم") })
    }

    @Test
    fun `the bar count is prose and therefore in Persian digits`() {
        val line = barCountLine(120)
        assertTrue("bar count leaked Latin digits into prose", line.none { it in '0'..'9' })
        assertTrue(line.contains("کندل"))
    }
}
