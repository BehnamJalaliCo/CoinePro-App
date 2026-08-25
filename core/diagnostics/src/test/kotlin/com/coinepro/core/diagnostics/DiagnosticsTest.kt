package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskingTest {

    @Test
    fun `a token keeps only enough tail to match against a server log`() {
        val masked = maskSecret("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.signature")

        assertTrue(masked.endsWith("ture"))
        assertFalse("The panel is a screenshot waiting to happen", masked.contains("eyJhbGci"))
        assertEquals(5, masked.length)
    }

    @Test
    fun `a short value is hidden completely, since revealing most of it is not a redaction`() {
        assertEquals("…", maskSecret("1234567"))
        assertEquals("…", maskSecret("abc"))
    }

    @Test
    fun `an absent value reads as absent rather than as an empty string`() {
        assertEquals(ABSENT, maskSecret(null))
        assertEquals(ABSENT, maskSecret(""))
        assertEquals(ABSENT, maskSecret("   "))
    }

    @Test
    fun `a base url keeps the scheme and path, which is where the misconfiguration shows`() {
        val masked = maskHost("https://api.tradeyar.trade-future.ir/api/mobile/v1/")

        assertTrue("The prefix is the whole point of showing this", masked.contains("/api/mobile/v1/"))
        assertTrue(masked.startsWith("https://"))
        assertFalse(masked.contains("api.tradeyar"))
    }

    @Test
    fun `an http base url is still visibly http`() {
        // A scheme downgrade is exactly the kind of thing the panel exists to make obvious.
        assertTrue(maskHost("http://staging.example.invalid/").startsWith("http://"))
    }

    @Test
    fun `an unset base url reads as absent`() {
        assertEquals(ABSENT, maskHost(null))
        assertEquals(ABSENT, maskHost(""))
    }
}

class RequestLogTest {

    @Test
    fun `newest first, and bounded so a long session cannot grow without limit`() {
        val log = RequestLog(capacity = 3)
        repeat(5) { index ->
            log.record(entry(sequence = index.toLong(), path = "user/$index"))
        }

        val entries = log.entries.value
        assertEquals(3, entries.size)
        assertEquals("user/4", entries.first().path)
        assertEquals("user/2", entries.last().path)
    }

    @Test
    fun `a call outside the success range is a failure, including one that never answered`() {
        assertFalse(entry(status = 200).failed)
        assertFalse(entry(status = 304).failed)
        assertTrue(entry(status = 404).failed)
        assertTrue(entry(status = 500).failed)
        assertTrue(entry(status = null, failure = "SocketTimeoutException").failed)
    }

    @Test
    fun `failures are what a reader filters to`() {
        val log = RequestLog()
        log.record(entry(sequence = 1, path = "user/mobile/alerts", status = 200))
        log.record(entry(sequence = 2, path = "user/signals/mobile/alerts", status = 404))

        val failures = log.failures()
        assertEquals(1, failures.size)
        assertEquals("user/signals/mobile/alerts", failures.single().path)
    }

    private fun entry(
        sequence: Long = 1,
        path: String = "user/me",
        status: Int? = 200,
        failure: String? = null,
    ) = RecordedRequest(
        sequence = sequence,
        platform = MarketPlatform.COINEPRO_FX,
        method = "GET",
        path = path,
        status = status,
        durationMillis = 12,
        elapsedRealtimeMillis = 0,
        failure = failure,
    )
}

class EndpointCatalogTest {

    @Test
    fun `every platform has a catalogue, because an empty one silently checks nothing`() {
        for (platform in MarketPlatform.entries) {
            assertTrue(platform.name, EndpointCatalog.forPlatform(platform).isNotEmpty())
        }
    }

    @Test
    fun `nothing that writes or spends quota is marked safe to fire`() {
        for (platform in MarketPlatform.entries) {
            val unsafe = EndpointCatalog.forPlatform(platform)
                .filter { it.method != "GET" && it.safeToProbe }

            assertTrue(
                "A diagnostic that creates an alert or burns an AI credit is not a diagnostic: $unsafe",
                unsafe.isEmpty(),
            )
        }
    }

    @Test
    fun `the catalogue covers the routes whose absence went unnoticed before`() {
        val paths = EndpointCatalog.forPlatform(MarketPlatform.COINEPRO_FX).map { it.path }

        // Both of these were being called at a wrong address for a long time. They are in the
        // catalogue now so the prober answers in a second what took a published route table.
        assertTrue(paths.any { it == "user/mobile/alerts" })
        assertTrue(paths.any { it == "user/ai-vision/jobs" })
        assertTrue("The unverified ones matter most", paths.any { it == "user/ai/assistant/messages" })
    }

    @Test
    fun `every TradeYar path carries the prefix its contract requires`() {
        // The app's crypto gateways still build CoinePro-FX's user/… paths against TradeYar's base
        // URL, so every crypto call currently reaches nothing. This is the list that proves it.
        val paths = EndpointCatalog.forPlatform(MarketPlatform.TRADEYAR).map { it.path }

        assertTrue(paths.isNotEmpty())
        assertTrue(
            "There is no user/ segment anywhere on TradeYar's mobile surface",
            paths.none { it.startsWith("user/") },
        )
        assertTrue(paths.all { it.startsWith("api/mobile/v1/") })
    }

    @Test
    fun `the two catalogues share no path, because the two servers share no surface`() {
        val forex = EndpointCatalog.forPlatform(MarketPlatform.COINEPRO_FX).map { it.path }.toSet()
        val crypto = EndpointCatalog.forPlatform(MarketPlatform.TRADEYAR).map { it.path }.toSet()

        assertTrue(
            "A shared path would mean one platform's gateway is pointed at the other's server",
            forex.intersect(crypto).isEmpty(),
        )
    }

    @Test
    fun `no path is absolute, since each is resolved against a platform base url`() {
        for (platform in MarketPlatform.entries) {
            for (endpoint in EndpointCatalog.forPlatform(platform)) {
                assertFalse(endpoint.path, endpoint.path.startsWith("/"))
                assertFalse(endpoint.path, endpoint.path.startsWith("http"))
            }
        }
    }
}
