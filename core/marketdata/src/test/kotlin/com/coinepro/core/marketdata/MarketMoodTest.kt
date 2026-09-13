package com.coinepro.core.marketdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the board's mood is allowed to claim.
 *
 * The strip this feeds is the first thing on the first screen, which makes it the figure a reader is
 * least likely to check and most likely to remember — so the interesting tests here are the ones
 * about what it must *not* say: a direction it does not have, a denominator that includes markets
 * nobody quoted, and a percentage computed off an empty table.
 */
class MarketMoodTest {

    private fun table(vararg tickers: MarketTicker) = MarketTickerTable(
        tickers = tickers.associateBy { it.symbol.uppercase() },
        serverTimeEpochMillis = null,
        cacheTtlMillis = null,
        fetchedAtEpochMillis = null,
        source = null,
    )

    private fun ticker(
        symbol: String,
        change: Double? = null,
        turnover: Double? = null,
    ) = MarketTicker(
        symbol = symbol,
        last = 100.0,
        changePercent24h = change,
        turnover24h = turnover,
    )

    @Test
    fun `an empty table has no mood rather than a neutral one`() {
        val mood = MarketMood.of(MarketTickerTable.Empty)
        assertNull("a board nobody has quoted must not read as fifty per cent", mood.breadth)
        assertEquals(MarketLean.UNKNOWN, mood.lean)
        assertTrue(mood.isEmpty)
    }

    @Test
    fun `markets with no figure are not counted as neither up nor down`() {
        // The failure this catches is the one that would be invisible: a half-loaded table where
        // three hundred of four hundred markets have no change yet, counted into the denominator,
        // drags every reading towards the middle and reports «مختلط» on a day the board is flying.
        val mood = MarketMood.of(
            table(
                ticker("AAAUSDT", change = 3.0),
                ticker("BBBUSDT", change = 2.0),
                ticker("CCCUSDT", change = 1.0),
                ticker("DDDUSDT", change = -1.0),
                ticker("EEEUSDT"),
                ticker("FFFUSDT"),
                ticker("GGGUSDT"),
            ),
        )
        assertEquals(3, mood.advancing)
        assertEquals(1, mood.declining)
        assertEquals("three of four, not three of seven", 75, mood.breadth)
        assertEquals(MarketLean.UP, mood.lean)
    }

    @Test
    fun `a board inside the noise band is called mixed rather than given a direction`() {
        val mood = MarketMood.of(
            table(
                ticker("AAAUSDT", change = 1.0),
                ticker("BBBUSDT", change = 1.0),
                ticker("CCCUSDT", change = -1.0),
                ticker("DDDUSDT", change = -1.0),
            ),
        )
        assertEquals(50, mood.breadth)
        assertEquals("a 50-50 board is not a direction", MarketLean.MIXED, mood.lean)
    }

    @Test
    fun `the movers are the biggest moves whichever way they went`() {
        val mood = MarketMood.of(
            table(
                ticker("UPUSDT", change = 4.0),
                ticker("DOWNUSDT", change = -9.0),
                ticker("FLATUSDT", change = 0.2),
                ticker("MIDUSDT", change = 5.0),
            ),
        )
        // A «top movers» list that showed only risers would be a list of the day's good news, which
        // is not what the reader is looking at the board to find out.
        assertEquals(listOf("DOWNUSDT", "MIDUSDT", "UPUSDT"), mood.movers.map { it.symbol })
    }

    @Test
    fun `the unusual list is busy and moving, not merely moving`() {
        // A market that moved eleven per cent on no turnover at all is a market nobody traded, and
        // putting it under «شلوغ» would be the strip reporting an illiquid tick as the day's story.
        val busy = (1..20).map { ticker("BUSY${it}USDT", change = 1.0, turnover = 1_000_000.0 + it) }
        val quietButWild = ticker("GHOSTUSDT", change = 40.0, turnover = 12.0)
        val busyAndWild = ticker("REALUSDT", change = 9.0, turnover = 5_000_000.0)
        val mood = MarketMood.of(table(*busy.toTypedArray(), quietButWild, busyAndWild))
        assertTrue("the busy mover is missing", mood.unusual.any { it.symbol == "REALUSDT" })
        assertTrue("an untraded market reached the strip", mood.unusual.none { it.symbol == "GHOSTUSDT" })
    }

    @Test
    fun `no symbol without artwork reaches the strip`() {
        // The same rule the catalogue and the live feed are held to. A strip is a list.
        val mood = MarketMood.of(
            table(
                ticker("BTCUSDT", change = 2.0, turnover = 9.0),
                ticker("NOARTUSDT", change = 50.0, turnover = 9.0),
            ),
            covered = setOf("BTCUSDT"),
        )
        assertEquals(listOf("BTCUSDT"), mood.movers.map { it.symbol })
        assertEquals(1, mood.advancing)
    }

    @Test
    fun `each list stops at three`() {
        val many = (1..40).map { ticker("S${it}USDT", change = it.toDouble(), turnover = it.toDouble()) }
        val mood = MarketMood.of(table(*many.toTypedArray()))
        assertEquals(MarketMood.LISTED, mood.movers.size)
        assertTrue(mood.unusual.size <= MarketMood.LISTED)
    }
}
