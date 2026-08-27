package com.coinepro.core.journal

import com.coinepro.core.database.JournalEntryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rule the whole thing turns on: a row with no P&L is not a break-even trade.
 *
 * A journal is kept in a hurry and half its rows are a symbol and a sentence. Counting those as
 * zeros halves every percentage on the screen, silently, in the direction that flatters nothing.
 */
class JournalStatsTest {

    @Test
    fun `entries with no recorded P and L are not counted as scratches`() {
        val stats = Journal.stats(
            listOf(entry(pnl = 120.0), entry(pnl = -40.0), entry(pnl = null), entry(pnl = null)),
        )

        assertEquals(2, stats.graded)
        assertEquals(1, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(50.0, stats.winRate!!, 0.0)
    }

    @Test
    fun `an exact zero is a scratch and counts as neither`() {
        val stats = Journal.stats(listOf(entry(pnl = 90.0), entry(pnl = 0.0)))

        assertEquals(2, stats.graded)
        assertEquals(1, stats.wins)
        assertEquals(0, stats.losses)
    }

    @Test
    fun `a journal with no losses has no profit factor, rather than an infinite one`() {
        val stats = Journal.stats(listOf(entry(pnl = 10.0), entry(pnl = 20.0)))

        // Three winners is not an infinite profit factor. It is a profit factor not yet produced.
        assertNull(stats.profitFactor)
        assertEquals(30.0, stats.netPnl, 0.0)
    }

    @Test
    fun `expectancy is the average over graded trades`() {
        val stats = Journal.stats(listOf(entry(pnl = 100.0), entry(pnl = -50.0), entry(pnl = null)))

        assertEquals(25.0, stats.expectancy!!, 1e-9)
    }

    @Test
    fun `an empty journal has no win rate, not a zero one`() {
        assertNull(Journal.stats(emptyList()).winRate)
    }

    @Test
    fun `tags come back most-used first`() {
        val entries = listOf(
            entry(tags = "بریک‌اوت,نیوز"),
            entry(tags = "بریک‌اوت"),
            entry(tags = "رنج"),
        )

        assertEquals(listOf("بریک‌اوت" to 2, "نیوز" to 1, "رنج" to 1).map { it.first }.first(),
            Journal.tags(entries).first().first)
    }

    /**
     * The byte-order mark is not decoration. Without it Excel opens a Persian CSV as mojibake, and
     * a reader who exports their own diary and gets `Ø¨ÛŒØª` will not export it twice.
     */
    @Test
    fun `the CSV starts with a byte-order mark and quotes its fields`() {
        val csv = Journal.toCsv(listOf(entry(note = "شکست, بعد ریتست \"تمیز\"")))

        assertEquals('﻿', csv.first())
        assert(csv.contains("\"شکست, بعد ریتست \"\"تمیز\"\"\""))
    }
}

private fun entry(
    pnl: Double? = null,
    tags: String = "",
    note: String = "",
) = JournalEntryEntity(
    id = 0,
    symbol = "BTCUSDT",
    buy = true,
    entry = null,
    exit = null,
    size = null,
    pnl = pnl,
    emotion = "",
    note = note,
    lesson = "",
    tags = tags,
    createdAtEpochMillis = 1_700_000_000_000L,
)
