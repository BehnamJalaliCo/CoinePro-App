package com.coinepro.core.common

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryAfterTest {
    private val now: Instant = Instant.parse("2026-08-25T10:00:00Z")

    @Test
    fun `reads the delta-seconds form`() {
        assertEquals(120, RetryAfter.parseSeconds("120", now))
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals(30, RetryAfter.parseSeconds("  30 ", now))
    }

    @Test
    fun `reports retry-immediately as one second rather than zero`() {
        assertEquals(1, RetryAfter.parseSeconds("0", now))
    }

    @Test
    fun `reads the http-date form against the supplied instant, not the device clock`() {
        assertEquals(300, RetryAfter.parseSeconds("Tue, 25 Aug 2026 10:05:00 GMT", now))
    }

    @Test
    fun `treats a date that has already passed as ready now`() {
        assertEquals(1, RetryAfter.parseSeconds("Tue, 25 Aug 2026 09:55:00 GMT", now))
    }

    @Test
    fun `clamps an implausible wait rather than showing a countdown of days`() {
        assertEquals(24 * 60 * 60, RetryAfter.parseSeconds("999999", now))
    }

    @Test
    fun `returns null for a missing, empty or unreadable header`() {
        assertNull(RetryAfter.parseSeconds(null, now))
        assertNull(RetryAfter.parseSeconds("", now))
        assertNull(RetryAfter.parseSeconds("soon", now))
        assertNull(RetryAfter.parseSeconds("-5", now))
    }
}
