package com.coinepro.core.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
        val masked = AppLog.redact(token)
        assertEquals("e••••9 (${token.length})", masked)
        // The whole reason it exists: two lines about the same token can be tied together, and the
        // token itself cannot be reconstructed from what is on screen.
        assertFalse(masked.contains("eyJhbGci"))
    }

    @Test
    fun `a rendered line carries the fields and stays greppable`() {
        val log = AppLog()
        log.log(
            level = LogLevel.WARN,
            tag = LogTag.SOCKET,
            message = "reconnecting",
            fields = mapOf("platform" to "TRADEYAR", "attempt" to "3"),
            elapsedRealtimeMillis = 1234,
        )

        assertEquals(
            "1234 W SOCKET: reconnecting {platform=TRADEYAR attempt=3}",
            log.entries.value.single().render(),
        )
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
        log.log(LogLevel.INFO, LogTag.LIFECYCLE, "start", elapsedRealtimeMillis = 1)
        log.log(LogLevel.INFO, LogTag.NAVIGATION, "home", elapsedRealtimeMillis = 2)

        assertEquals(
            "1 I LIFECYCLE: start\n2 I NAVIGATION: home",
            log.dump(),
        )
    }
}
