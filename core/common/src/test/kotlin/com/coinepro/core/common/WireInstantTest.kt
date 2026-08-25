package com.coinepro.core.common

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WireInstantTest {
    private val noon: Instant = Instant.parse("2026-09-24T12:00:00Z")

    @Test
    fun `the Z form both servers were asked for`() {
        assertEquals(noon, parseWireInstant("2026-09-24T12:00:00Z"))
    }

    @Test
    fun `the offset form python's isoformat actually writes`() {
        // Both backends are FastAPI over Postgres and reach for datetime.isoformat(), which never
        // writes Z. Instant.parse rejects this, and every caller drops the row without a word — so
        // a subscription showed no expiry and a news feed would have come back permanently empty.
        assertEquals(noon, parseWireInstant("2026-09-24T12:00:00+00:00"))
        assertEquals(noon, parseWireInstant("2026-09-24T15:30:00+03:30"))
        assertEquals(noon, parseWireInstant("2026-09-24T12:00:00.000000+00:00"))
    }

    @Test
    fun `a naive value is read as UTC, not as the phone's own zone`() {
        assertEquals(
            "Reading it in the device zone would move the moment by hours on most phones",
            noon,
            parseWireInstant("2026-09-24T12:00:00"),
        )
    }

    @Test
    fun `surrounding whitespace does not lose a timestamp`() {
        assertEquals(noon, parseWireInstant("  2026-09-24T12:00:00Z\n"))
    }

    @Test
    fun `anything nobody sends stays null rather than being guessed at`() {
        assertNull(parseWireInstant(null))
        assertNull(parseWireInstant(""))
        assertNull(parseWireInstant("   "))
        assertNull(parseWireInstant("next tuesday"))
        assertNull(parseWireInstant("2026-09-24"))
        assertNull(parseWireInstant("24/09/2026 12:00"))
        assertNull("An epoch number is a different contract, not a date string", parseWireInstant("1790251200"))
    }
}
