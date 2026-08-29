package com.coinepro.feature.journal

import com.coinepro.core.database.JournalEntryEntity
import com.coinepro.core.journal.Journal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter, and the promise that hangs off it.
 *
 * The test that matters most here is the one proving the statistics move when the filter moves. A
 * journal whose win rate ignores the filter above it is worse than one with no statistics at all:
 * it answers a question nobody asked, in the place where the answer to the question they did ask
 * belongs, and there is nothing on the screen to reveal the substitution.
 */
class JournalFilterTest {

    private var next = 1L

    private fun entry(
        symbol: String,
        pnl: Double?,
        tags: String = "",
        note: String = "",
        lesson: String = "",
        emotion: String = "",
    ) = JournalEntryEntity(
        id = next++,
        symbol = symbol,
        buy = true,
        entry = null,
        exit = null,
        size = null,
        pnl = pnl,
        emotion = emotion,
        note = note,
        lesson = lesson,
        tags = tags,
        createdAtEpochMillis = 1_767_225_600_000L,
    )

    /**
     * Four graded entries and one without a number.
     *
     * The breakouts win and the range trades lose, which is what makes the filtered statistics
     * differ from the whole journal's rather than merely being computed over fewer rows.
     */
    private val journal = listOf(
        entry("XAUUSD", 300.0, tags = "بریک‌اوت,ترند", note = "شکست را زود گرفتم"),
        entry("EURUSD", 100.0, tags = "بریک‌اوت", lesson = "صبر"),
        entry("BTCUSDT", -200.0, tags = "رنج"),
        entry("BTCUSDT", -100.0, tags = "رنج,اسکالپ"),
        entry("XAGUSD", null, tags = "نیوز", note = "قیمت 64000 را رد کرد"),
    )

    /** No entry has a picture, which is the state of a journal nobody has attached one in. */
    private val noShots = emptySet<Long>()

    /** The ids of the two entries a reader has attached a chart to. */
    private val someShots: Set<Long> get() = setOf(journal[0].id, journal[2].id)

    @Test
    fun `the screenshot filter narrows the list and the statistics with it`() {
        val documented = JournalFilter(shot = JournalShot.WITH)
        val undocumented = JournalFilter(shot = JournalShot.WITHOUT)

        val withPictures = documented.apply(journal, someShots)
        assertEquals(listOf("XAUUSD", "BTCUSDT"), withPictures.map { it.symbol })
        // The figures follow the list rather than the journal: the two documented trades are a
        // three-hundred win and a two-hundred loss, so a hundred net, not the journal's total.
        assertEquals(100.0, documented.statsOf(journal, someShots).netPnl, 1e-9)

        val without = undocumented.apply(journal, someShots)
        assertEquals(3, without.size)
        assertTrue(without.none { it.id in someShots })
    }

    @Test
    fun `the export writes exactly the filtered rows and says which of them carry a chart`() {
        val filter = JournalFilter(shot = JournalShot.WITH)
        val shown = filter.apply(journal, someShots)
        val csv = JournalExport.csv(shown, someShots)
        val lines = csv.trim().lines()

        // A header and one line per shown entry, and nothing else: the file is the screen.
        assertEquals(shown.size + 1, lines.size)
        assertTrue(lines.first().contains("has_screenshot"))
        assertTrue(lines.drop(1).all { it.endsWith("\"1\"") })
        assertTrue(lines.none { it.contains("XAGUSD") })
    }

    @Test
    fun `an entry with no chart is written as such rather than left blank`() {
        val csv = JournalExport.csv(journal, someShots)
        val rows = csv.trim().lines().drop(1)
        assertEquals(journal.size, rows.size)
        assertEquals(2, rows.count { it.endsWith("\"1\"") })
        assertEquals(3, rows.count { it.endsWith("\"0\"") })
    }

    @Test
    fun `the statistics over a filtered subset differ from the statistics over the whole journal`() {
        val whole = Journal.stats(journal)
        val breakouts = JournalFilter(tags = setOf("بریک‌اوت")).statsOf(journal, noShots)

        // The journal as a whole: four graded, two winners, net +100, and a win rate of fifty.
        assertEquals(4, whole.graded)
        assertEquals(100.0, whole.netPnl, 1e-9)
        assertEquals(50.0, whole.winRate!!, 1e-9)

        // The breakouts on their own: two graded, both winners, net +400.
        assertEquals(2, breakouts.graded)
        assertEquals(400.0, breakouts.netPnl, 1e-9)
        assertEquals(100.0, breakouts.winRate!!, 1e-9)

        assertNotEquals(whole.netPnl, breakouts.netPnl, 1e-9)
        assertNotEquals(whole.winRate!!, breakouts.winRate!!, 1e-9)
        // No losing breakout, so no profit factor rather than an infinite one.
        assertNull(breakouts.profitFactor)
    }

    @Test
    fun `two tags narrow the list instead of widening it`() {
        val one = JournalFilter(tags = setOf("رنج")).apply(journal, noShots)
        val both = JournalFilter(tags = setOf("رنج", "اسکالپ")).apply(journal, noShots)
        assertEquals(2, one.size)
        assertEquals(1, both.size)
        assertTrue(both.single().tags.contains("اسکالپ"))
    }

    @Test
    fun `an outcome filter keeps only the entries that had that outcome`() {
        assertEquals(2, JournalFilter(outcome = JournalOutcome.WINS).apply(journal, noShots).size)
        assertEquals(2, JournalFilter(outcome = JournalOutcome.LOSSES).apply(journal, noShots).size)
        // The row with no number is neither a win nor a loss and is findable on its own.
        val ungraded = JournalFilter(outcome = JournalOutcome.UNGRADED).apply(journal, noShots)
        assertEquals(listOf("XAGUSD"), ungraded.map { it.symbol })
    }

    @Test
    fun `the statistics over the ungraded rows report nothing rather than a run of zeros`() {
        val stats = JournalFilter(outcome = JournalOutcome.UNGRADED).statsOf(journal, noShots)
        assertEquals(0, stats.graded)
        assertEquals(0.0, stats.netPnl, 1e-9)
        assertNull(stats.winRate)
        assertNull(stats.expectancy)
        assertNull(stats.profitFactor)
    }

    @Test
    fun `a search typed on a Persian keyboard finds a number stored in Latin digits`() {
        // The note holds `64000`; a Persian keyboard types «۶۴۰۰۰». The two share not one
        // character, so a search without the digit fold returns nothing while looking as though
        // it worked — and the reader concludes their own note is missing.
        val found = JournalFilter(query = "۶۴۰۰۰").apply(journal, noShots)
        assertEquals(listOf("XAGUSD"), found.map { it.symbol })
    }

    @Test
    fun `a search matches the symbol whatever case it is typed in`() {
        assertEquals(2, JournalFilter(query = "btcusdt").apply(journal, noShots).size)
        assertEquals(2, JournalFilter(query = "  BTC  ").apply(journal, noShots).size)
    }

    @Test
    fun `a search reaches the note and the lesson, which is where a journal is actually written`() {
        assertEquals(listOf("XAUUSD"), JournalFilter(query = "زود").apply(journal, noShots).map { it.symbol })
        assertEquals(listOf("EURUSD"), JournalFilter(query = "صبر").apply(journal, noShots).map { it.symbol })
    }

    @Test
    fun `an empty filter returns the list untouched and in the same order`() {
        val filter = JournalFilter()
        assertTrue(filter.isEverything)
        assertEquals(journal, filter.apply(journal, noShots))
        assertEquals(Journal.stats(journal).netPnl, filter.statsOf(journal, noShots).netPnl, 1e-9)
    }

    @Test
    fun `toggling a tag that is already on turns it off again`() {
        val once = JournalFilter().toggling("رنج")
        assertEquals(setOf("رنج"), once.tags)
        assertTrue(once.toggling("رنج").isEverything)
    }

    @Test
    fun `filters combine, so the losing range trades can be asked for on their own`() {
        val filter = JournalFilter(tags = setOf("رنج"), outcome = JournalOutcome.LOSSES)
        val stats = filter.statsOf(journal, noShots)
        assertEquals(2, filter.apply(journal, noShots).size)
        assertEquals(-300.0, stats.netPnl, 1e-9)
        assertEquals(0.0, stats.winRate!!, 1e-9)
        assertEquals(-150.0, stats.expectancy!!, 1e-9)
    }

    @Test
    fun `tags are split on the comma and trimmed once rather than at each call site`() {
        assertEquals(setOf("رنج", "اسکالپ"), tagsOf(entry("X", null, tags = " رنج , اسکالپ , ")))
        assertTrue(tagsOf(entry("X", null, tags = "  ")).isEmpty())
    }
}
