package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The morning brief.
 *
 * It arrives while the reader is asleep and is read before they open anything, so the assertions
 * here are mostly about what it refuses to say: no brief about an empty list, no brief built out of
 * changes nobody measured, and no story invented out of a tenth of a percent.
 */
class RasadBriefTest {

    private fun market(symbol: String, change: Double?) = BriefMarket(symbol, change)

    @Test
    fun `an empty list produces nothing`() {
        assertNull(RasadBrief.of(emptyList()))
    }

    @Test
    fun `a list nobody could measure produces nothing`() {
        // Not «zero markets moved»: the feed did not say, and a brief that reported silence as
        // flatness would be inventing the one fact it exists to carry.
        assertNull(RasadBrief.of(listOf(market("BTCUSDT", null), market("XAUUSD", null))))
    }

    @Test
    fun `unmeasured markets are left out of the count rather than counted as flat`() {
        val brief = RasadBrief.of(
            listOf(market("BTCUSDT", 2.0), market("XAUUSD", null), market("ETHUSDT", -1.0)),
        )
        assertEquals("۱ بالا، ۱ پایین", brief?.headline)
    }

    @Test
    fun `the mover is the largest move either way`() {
        val markets = listOf(market("BTCUSDT", 2.0), market("XAUUSD", -6.0), market("ETHUSDT", 3.0))
        assertEquals("XAUUSD", RasadBrief.moverOf(markets)?.symbol)
        assertEquals("XAUUSD", RasadBrief.of(markets)?.mover)
    }

    @Test
    fun `a tie goes to the reader's own order`() {
        val markets = listOf(market("ETHUSDT", -4.0), market("BTCUSDT", 4.0))
        assertEquals("ETHUSDT", RasadBrief.moverOf(markets)?.symbol)
    }

    @Test
    fun `a quiet night is still sent, and says so`() {
        // The decision in this file's own KDoc: a scheduled brief that sometimes does not arrive is
        // indistinguishable from a broken one.
        val brief = RasadBrief.of(listOf(market("BTCUSDT", 0.04), market("XAUUSD", -0.02)))
        assertNull(brief?.mover)
        assertEquals("شب آرامی برای ۲ بازار شما", brief?.headline)
        assertTrue(brief!!.lines.single().isNotBlank())
    }

    @Test
    fun `a move at the floor is not a story and one above it is`() {
        assertNull(RasadBrief.of(listOf(market("BTCUSDT", RasadBrief.QUIET_PERCENT - 0.001)))?.mover)
        assertEquals(
            "BTCUSDT",
            RasadBrief.of(listOf(market("BTCUSDT", RasadBrief.QUIET_PERCENT)))?.mover,
        )
    }

    @Test
    fun `the figure is Latin and the count is Persian, in the same brief`() {
        val brief = RasadBrief.of(listOf(market("BTCUSDT", 2.41), market("XAUUSD", -0.5)))!!
        // The headline is prose about how many markets, so Persian digits.
        assertEquals("۱ بالا، ۱ پایین", brief.headline)
        // The body is a market figure, so Latin ones — a reader compares it against another
        // terminal and must not have to transliterate first.
        assertTrue(brief.lines.first(), brief.lines.first().contains("2.41"))
        assertTrue(brief.lines.first(), brief.lines.first().startsWith("BTCUSDT"))
    }

    @Test
    fun `a fall is named as a fall`() {
        val brief = RasadBrief.of(listOf(market("XAUUSD", -3.0)))!!
        assertTrue(brief.lines.first(), brief.lines.first().contains("پایین آمد"))
        // The figure is the size of the move, not a signed number with a stray minus in the prose.
        assertTrue(brief.lines.first(), brief.lines.first().contains("3.00"))
    }

    @Test
    fun `English says the same thing`() {
        val brief = RasadBrief.of(listOf(market("BTCUSDT", 2.0), market("XAUUSD", -1.0)), english = true)!!
        assertEquals("1 up, 1 down", brief.headline)
        assertTrue(brief.lines.first(), brief.lines.first().contains("rose"))
    }

    @Test
    fun `without candles the brief is shorter rather than absent`() {
        val brief = RasadBrief.of(listOf(market("BTCUSDT", 5.0)))!!
        assertEquals(1, brief.lines.size)
    }

    @Test
    fun `with candles Rasad's own first sentence rides along`() {
        val series = rising(120)
        val brief = RasadBrief.of(listOf(market("BTCUSDT", 5.0)), moverSeries = series)!!
        assertEquals(2, brief.lines.size)
        // The same sentence the chart would print, not a second opinion about the same bars.
        assertEquals(RasadCoach.readChart(series).first(), brief.lines[1])
    }

    @Test
    fun `a series too short to read leaves the brief at one line`() {
        val brief = RasadBrief.of(listOf(market("BTCUSDT", 5.0)), moverSeries = rising(5))!!
        assertEquals(1, brief.lines.size)
    }

    private fun rising(count: Int): CandleSeries = CandleSeries(
        (0 until count).map { index ->
            val base = 100.0 + index
            Candle(
                t = 1_700_000_000L + index * 3_600L,
                o = base,
                h = base + 1.0,
                l = base - 1.0,
                c = base + 0.5,
                v = 10.0,
            )
        },
    )
}
