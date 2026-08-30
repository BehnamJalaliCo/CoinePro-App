package com.coinepro.core.diagnostics

import java.io.File
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppLogTest {

    @Test
    fun `the ring is bounded and keeps the newest`() {
        val log = AppLog(capacity = 3)
        repeat(10) { log.info(LogTag.STATE, "entry $it") }

        val entries = log.entries.value
        assertEquals(3, entries.size)
        assertEquals(listOf("entry 7", "entry 8", "entry 9"), entries.map(LogEntry::message))
    }

    @Test
    fun `a level below the floor costs nothing and is not recorded`() {
        val log = AppLog()
        log.minimumLevel = LogLevel.WARN
        log.debug(LogTag.NETWORK, "chatty")
        log.trace(LogTag.SOCKET, "very chatty")
        log.warn(LogTag.NETWORK, "worth knowing")

        assertEquals(listOf("worth knowing"), log.entries.value.map(LogEntry::message))
    }

    @Test
    fun `an error keeps the class and the message and never the throwable`() {
        val log = AppLog()
        log.error(LogTag.AUTH, "sign-in failed", IllegalStateException("no route"))

        val entry = log.entries.value.single()
        assertEquals("IllegalStateException: no route", entry.error)
        // The point of the assertion: nothing here can reach a stack frame, and a stack frame on
        // the sign-in path holds the password that was in scope when it was captured.
        assertTrue(entry.error!!.length < 120)
    }

    @Test
    fun `redaction keeps the shape and drops the content`() {
        assertEquals("—", AppLog.redact(null))
        assertEquals("—", AppLog.redact(""))
        assertEquals("•• (2)", AppLog.redact("ab"))

        val identifier = "01931f9c-4a2b-7c31-8e55-0f0a2b3c4d5e"
        val masked = AppLog.redact(identifier)
        assertEquals("0••••e (${identifier.length})", masked)
        // The whole reason it exists: two lines about the same value can be tied together, and the
        // value itself cannot be reconstructed from what is on screen.
        assertFalse(masked.contains("4a2b"))
    }

    @Test
    fun `a rendered line is UTC, sortable and greppable`() {
        val log = AppLog()
        log.log(
            level = LogLevel.WARN,
            tag = LogTag.SOCKET,
            message = "reconnecting",
            fields = mapOf("platform" to "TRADEYAR", "attempt" to "3"),
            epochMillis = 1_756_000_000_000,
            uptimeMillis = 4_200,
        )

        val line = log.entries.value.single().render()
        // ISO-8601 in UTC so it lines up against a server log without anybody converting anything,
        // and so `sort` on the exported file is chronological.
        assertTrue(line, line.startsWith("2025-08-24T01:46:40.000Z W SOCKET ["))
        assertTrue(line, line.endsWith(": reconnecting {platform=TRADEYAR attempt=3}"))
    }

    @Test
    fun `timed returns the block's value and warns when it is slow`() {
        val log = AppLog()
        val answer = log.timed(LogTag.PERFORMANCE, "quick") { 42 }
        assertEquals(42, answer)

        val entry = log.entries.value.single()
        assertEquals(LogLevel.DEBUG, entry.level)
        assertTrue(entry.fields.containsKey("ms"))
    }

    @Test
    fun `timed still records when the block throws`() {
        val log = AppLog()
        runCatching { log.timed<Unit>(LogTag.STORAGE, "write") { error("disk full") } }

        // The measurement a failure produces is the one worth having: it is how a hang is told
        // apart from a crash.
        assertEquals(1, log.entries.value.size)
        assertEquals("write", log.entries.value.single().message)
    }

    @Test
    fun `dump is oldest first and one line per entry`() {
        val log = AppLog()
        log.info(LogTag.LIFECYCLE, "start")
        log.info(LogTag.NAVIGATION, "home")

        val lines = log.dump().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0], lines[0].contains("LIFECYCLE") && lines[0].endsWith(": start"))
        assertTrue(lines[1], lines[1].contains("NAVIGATION") && lines[1].endsWith(": home"))
    }

    @Test
    fun `counters are counted rather than accumulated, so they match the list above them`() {
        val now = 1_756_000_000_000
        val log = AppLog(capacity = 3)
        log.log(LogLevel.ERROR, LogTag.NETWORK, "old", epochMillis = now - 7_200_000)
        log.log(LogLevel.ERROR, LogTag.NETWORK, "recent", epochMillis = now - 60_000)
        log.log(LogLevel.WARN, LogTag.SOCKET, "warned", epochMillis = now)

        val counters = log.counters(now)
        assertEquals(3, counters.total)
        assertEquals(2, counters.errors)
        assertEquals(1, counters.warnings)
        // The number that separates "a problem now" from "a problem once, two hours ago".
        assertEquals(1, counters.errorsLastHour)
    }
}

/**
 * The half of the log that is new: it is now on disk, and the reason is that the failures worth
 * diagnosing are the ones that end the process holding the log.
 */
class PersistedLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** Appends on the caller's thread, so a test can assert on the file straight after the call. */
    private val direct = Executor { it.run() }

    @Test
    fun `entries written by one process are read back by the next`() {
        val directory = folder.newFolder("logs")
        val first = AppLog(sink = FileLogSink(directory, executor = direct))
        first.info(LogTag.LIFECYCLE, "cold start")
        first.error(LogTag.NETWORK, "gateway refused", IllegalStateException("500"))

        // A second AppLog over the same directory is what the next launch has: a new process, a new
        // ring, and the file the previous one left behind.
        val second = AppLog(sink = FileLogSink(directory, executor = direct))
        val restored = second.entries.value

        assertEquals(listOf("cold start", "gateway refused"), restored.map(LogEntry::message))
        assertEquals(LogLevel.ERROR, restored.last().level)
        assertEquals("IllegalStateException: 500", restored.last().error)
    }

    @Test
    fun `a new process keeps numbering above what it restored`() {
        val directory = folder.newFolder("logs")
        val first = AppLog(sink = FileLogSink(directory, executor = direct))
        repeat(4) { first.info(LogTag.STATE, "entry $it") }

        val second = AppLog(sink = FileLogSink(directory, executor = direct))
        second.info(LogTag.STATE, "after restart")

        // Sequence numbers are what an operator correlates two lines by. Restarting the count would
        // put two different entries under the same number in one exported file.
        assertEquals(5L, second.entries.value.last().sequence)
    }

    @Test
    fun `a message with a newline in it cannot forge a second entry`() {
        val directory = folder.newFolder("logs")
        val log = AppLog(sink = FileLogSink(directory, executor = direct))
        log.warn(LogTag.GATEWAY, "unexpected body\nnot a real entry\tnor this")

        val restored = AppLog(sink = FileLogSink(directory, executor = direct)).entries.value
        assertEquals(1, restored.size)
        assertEquals("unexpected body\nnot a real entry\tnor this", restored.single().message)
    }

    @Test
    fun `the file rotates rather than growing without limit`() {
        val directory = folder.newFolder("logs")
        val sink = FileLogSink(directory, maxBytes = 400, executor = direct)
        val log = AppLog(sink = sink)
        repeat(40) { log.info(LogTag.STATE, "a reasonably long line number $it") }

        // Two files, and never a third: the floor is a full file of history, the ceiling is twice
        // the budget on a phone whose owner may be out of room.
        assertEquals(2, directory.listFiles().orEmpty().size)
        assertTrue(sink.sizeBytes() < 400 * 3)
        assertTrue(directory.listFiles().orEmpty().all(File::isFile))
    }

    @Test
    fun `clearing wipes the file as well as the ring`() {
        val directory = folder.newFolder("logs")
        val sink = FileLogSink(directory, executor = direct)
        val log = AppLog(sink = sink)
        log.info(LogTag.STATE, "something")
        assertTrue(sink.sizeBytes() > 0)

        log.clear()

        // The whole point of the control: an operator who deletes the log must not be left with one
        // still on disk that the next export would carry.
        assertEquals(0, sink.sizeBytes())
        assertTrue(log.entries.value.isEmpty())
        assertTrue(AppLog(sink = FileLogSink(directory, executor = direct)).entries.value.isEmpty())
    }

    @Test
    fun `a half-written last line is skipped rather than losing the whole file`() {
        val directory = folder.newFolder("logs")
        val log = AppLog(sink = FileLogSink(directory, executor = direct))
        log.info(LogTag.LIFECYCLE, "complete entry")

        // What a process killed mid-append leaves behind, which is routine rather than exotic.
        File(directory, "app-log.tsv").appendText("991\t17560000\tnot-a-number")

        val restored = AppLog(sink = FileLogSink(directory, executor = direct)).entries.value
        assertEquals(listOf("complete entry"), restored.map(LogEntry::message))
    }

    @Test
    fun `a sink that cannot write is never the reason an entry is missing from the screen`() {
        // A directory the app cannot create a file in — a full disk, or storage the system pulled
        // out from under the process. The screen must still show the entry.
        val blocked = File(folder.newFile("not-a-directory"), "logs")
        val log = AppLog(sink = FileLogSink(blocked, executor = direct))
        log.warn(LogTag.STORAGE, "still recorded")

        assertNotNull(log.entries.value.singleOrNull())
        assertEquals("still recorded", log.entries.value.single().message)
    }
}
