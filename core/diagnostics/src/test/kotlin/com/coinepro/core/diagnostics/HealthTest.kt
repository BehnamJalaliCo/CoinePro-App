package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The verdict, and the bug it replaces.
 *
 * The panel used to open onto a red headline on every install that had not signed in yet, because
 * it counted every non-2xx status as a failure and a signed-out session answers 401 to every
 * authenticated route. These tests pin the distinction: an unexplained fault is a finding, an
 * expected refusal is not, and a fresh install with nothing recorded is healthy.
 */
class HealthTest {

    private val now = 1_756_000_000_000L

    @Test
    fun `a panel opened on a fresh install reports nothing wrong`() {
        // The single most important assertion in this file. A panel that greets an operator with an
        // error before they have done anything is a panel they stop reading.
        assertTrue(state().findings(now).isEmpty())
        assertEquals(HubTone.GOOD, state().findings(now).verdictTone())
    }

    @Test
    fun `a signed-out session answering 401 everywhere is not a fault`() {
        val signedOut = state(
            requests = listOf(
                request(1, "user/me", 401),
                request(2, "user/mobile/portfolio", 401),
                request(3, "user/mobile/alerts", 403),
            ),
        )

        assertTrue(signedOut.findings(now).isEmpty())
    }

    @Test
    fun `a route the server does not serve is a fault, and the worst kind`() {
        val missing = state(
            probes = listOf(
                probe("user/auth/methods", ProbeOutcome.REACHED),
                probe("user/signals", ProbeOutcome.NOT_FOUND),
                probe("user/signals/execution/connections", ProbeOutcome.NOT_FOUND),
            ),
        )

        val findings = missing.findings(now)
        assertEquals(listOf(HealthFinding(FindingKind.ROUTE_MISSING, 2)), findings)
        assertEquals(HubTone.BAD, findings.verdictTone())
    }

    @Test
    fun `a server error and a call that never answered are separate findings`() {
        val broken = state(
            requests = listOf(
                request(1, "user/mobile/briefing", 503),
                request(2, "user/mobile/portfolio", null, failure = "SocketTimeoutException"),
            ),
        )

        assertEquals(
            listOf(
                HealthFinding(FindingKind.SERVER_ERROR, 1),
                HealthFinding(FindingKind.TRANSPORT_FAILURE, 1),
            ),
            broken.findings(now),
        )
    }

    @Test
    fun `a platform this build was never given a base url for is named as such`() {
        val unconfigured = state(baseUrl = null)

        assertTrue(unconfigured.findings(now).contains(HealthFinding(FindingKind.GATEWAY_UNCONFIGURED, 1)))
    }

    @Test
    fun `findings are scoped to the platform on screen`() {
        val mixed = state(
            requests = listOf(
                request(1, "user/mobile/briefing", 503),
                request(2, "api/mobile/v1/briefing", 503, platform = MarketPlatform.TRADEYAR),
            ),
        )

        // "Three failures" that turns out to be one server having a bad afternoon while the other
        // is fine is a sentence that has to say which.
        assertEquals(listOf(HealthFinding(FindingKind.SERVER_ERROR, 1)), mixed.findings(now))
    }

    @Test
    fun `errors older than an hour do not colour today's verdict`() {
        val log = listOf(
            logEntry(1, LogLevel.ERROR, now - 90L * 60_000),
            logEntry(2, LogLevel.ERROR, now - 60_000),
        )

        assertEquals(
            listOf(HealthFinding(FindingKind.ERRORS_LOGGED, 1)),
            state(log = log).findings(now),
        )
    }

    @Test
    fun `a crash outranks everything else in the list`() {
        val crashed = state(
            crash = Crash(atEpochMillis = now - 1_000, trace = "java.lang.IllegalStateException"),
            requests = listOf(request(1, "user/mobile/briefing", 503)),
        )

        assertEquals(FindingKind.CRASH, crashed.findings(now).first().kind)
    }

    @Test
    fun `forty retries of one broken route are one row, not forty`() {
        val requests = (1..40).map { request(it.toLong(), "user/signals", 404) } +
            request(41, "user/mobile/briefing", 503)

        val groups = requests.failureGroups()

        assertEquals(2, groups.size)
        assertEquals(40, groups.first().count)
        assertEquals("user/signals", groups.first().path)
    }

    @Test
    fun `an expected refusal sorts below an unexplained failure`() {
        val groups = listOf(
            request(1, "user/me", 401),
            request(2, "user/me", 401),
            request(3, "user/signals", 404),
        ).failureGroups()

        // The operator's own bad news should not be below twenty rows explaining that they are
        // signed out.
        assertEquals("user/signals", groups.first().path)
        assertTrue(groups.last().expected)
    }

    private fun state(
        requests: List<RecordedRequest> = emptyList(),
        probes: List<EndpointProbe> = emptyList(),
        log: List<LogEntry> = emptyList(),
        crash: Crash? = null,
        baseUrl: String? = "https://api.example.invalid/",
    ) = AdminUiState(
        build = AdminBuildInfo("1.0.0", "1", "staging", "com.coinepro.app", false, true),
        selected = MarketPlatform.COINEPRO_FX,
        panels = mapOf(
            MarketPlatform.COINEPRO_FX to PlatformPanel(
                platform = MarketPlatform.COINEPRO_FX,
                build = PlatformBuildInfo(MarketPlatform.COINEPRO_FX, baseUrl),
                probes = probes,
            ),
        ),
        requests = requests,
        log = log,
        crash = crash,
    )

    private fun request(
        sequence: Long,
        path: String,
        status: Int?,
        failure: String? = null,
        platform: MarketPlatform = MarketPlatform.COINEPRO_FX,
    ) = RecordedRequest(
        sequence = sequence,
        platform = platform,
        method = "GET",
        path = path,
        status = status,
        durationMillis = 88,
        elapsedRealtimeMillis = now,
        failure = failure,
    )

    private fun probe(path: String, outcome: ProbeOutcome) = EndpointProbe(
        endpoint = CatalogedEndpoint("GET", path, "signals"),
        outcome = outcome,
        status = if (outcome == ProbeOutcome.NOT_FOUND) 404 else 200,
    )

    private fun logEntry(sequence: Long, level: LogLevel, epochMillis: Long) = LogEntry(
        sequence = sequence,
        epochMillis = epochMillis,
        uptimeMillis = sequence,
        level = level,
        tag = LogTag.NETWORK,
        message = "something",
    )
}
