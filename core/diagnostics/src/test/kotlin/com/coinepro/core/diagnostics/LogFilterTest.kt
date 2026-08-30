package com.coinepro.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterTest {

    private val now = 1_756_000_000_000

    private val entries = listOf(
        entry(1, LogLevel.DEBUG, LogTag.NETWORK, "GET /user/me", now - 10_000, mapOf("status" to "200")),
        entry(2, LogLevel.WARN, LogTag.SOCKET, "reconnecting", now - 600_000),
        entry(3, LogLevel.ERROR, LogTag.NETWORK, "GET /user/signals", now - 5_000, mapOf("status" to "404")),
        entry(4, LogLevel.INFO, LogTag.AUTH, "signed in", now - 90L * 60_000),
    )

    @Test
    fun `the default shows everything, because an empty panel reads as a broken one`() {
        assertEquals(entries, entries.matching(LogFilter(), now))
        assertFalse(LogFilter().active)
    }

    @Test
    fun `the level is a floor rather than a selection`() {
        val warnings = entries.matching(LogFilter(minimumLevel = LogLevel.WARN), now)

        // "Warnings and worse", not "warnings", which is what an operator means by the word.
        assertEquals(listOf(2L, 3L), warnings.map(LogEntry::sequence))
    }

    @Test
    fun `no tag selected means every tag, and one selected means only that one`() {
        assertEquals(4, entries.matching(LogFilter(tags = emptySet()), now).size)
        assertEquals(
            listOf(1L, 3L),
            entries.matching(LogFilter(tags = setOf(LogTag.NETWORK)), now).map(LogEntry::sequence),
        )
        assertEquals(
            listOf(1L, 2L, 3L),
            entries.matching(LogFilter(tags = setOf(LogTag.NETWORK, LogTag.SOCKET)), now)
                .map(LogEntry::sequence),
        )
    }

    @Test
    fun `the window is relative to a passed clock, so one frame cannot disagree with itself`() {
        assertEquals(
            listOf(1L, 3L),
            entries.matching(LogFilter(window = LogWindow.FIVE_MINUTES), now).map(LogEntry::sequence),
        )
        assertEquals(3, entries.matching(LogFilter(window = LogWindow.ONE_HOUR), now).size)
        assertEquals(4, entries.matching(LogFilter(window = LogWindow.ONE_DAY), now).size)
    }

    @Test
    fun `free text searches the whole entry, not only the message`() {
        // Typing 404 means "wherever that appears". A search that only read the message would miss
        // every entry where the status is in the field it belongs in.
        assertEquals(
            listOf(3L),
            entries.matching(LogFilter(query = "404"), now).map(LogEntry::sequence),
        )
        assertEquals(
            listOf(1L, 3L),
            entries.matching(LogFilter(query = "network"), now).map(LogEntry::sequence),
        )
        assertEquals(
            listOf(1L, 3L),
            entries.matching(LogFilter(query = "/USER/"), now).map(LogEntry::sequence),
        )
    }

    @Test
    fun `filters compose rather than replacing one another`() {
        val filter = LogFilter(
            minimumLevel = LogLevel.WARN,
            tags = setOf(LogTag.NETWORK),
            window = LogWindow.FIVE_MINUTES,
            query = "signals",
        )

        assertEquals(listOf(3L), entries.matching(filter, now).map(LogEntry::sequence))
        assertTrue(filter.active)
    }

    @Test
    fun `toggling a tag adds it and toggling it again takes it away`() {
        val once = LogFilter().toggling(LogTag.CHART)
        assertEquals(setOf(LogTag.CHART), once.tags)
        assertEquals(emptySet<LogTag>(), once.toggling(LogTag.CHART).tags)
    }

    private fun entry(
        sequence: Long,
        level: LogLevel,
        tag: LogTag,
        message: String,
        epochMillis: Long,
        fields: Map<String, String> = emptyMap(),
    ) = LogEntry(
        sequence = sequence,
        epochMillis = epochMillis,
        uptimeMillis = sequence,
        level = level,
        tag = tag,
        message = message,
        fields = fields,
    )
}
