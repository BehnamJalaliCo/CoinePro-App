package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The file the operator hands to a developer.
 *
 * The assertions are about *completeness*, which is the property the owner actually asked for: a
 * report somebody can diagnose from without asking a follow-up question. A log on its own does not
 * say which build, which phone, or what the app's state was, and every one of those is the first
 * question a developer would send back.
 */
class DiagnosticExportTest {

    private val at = 1_756_000_000_000L

    @Test
    fun `the name sorts, says what it is, and saves on a machine that dislikes colons`() {
        val name = DiagnosticExport.fileName(at)

        assertEquals("coinepro-diagnostics-20250824-014640.txt", name)
        assertFalse("A colon is legal on the phone and illegal on Windows", name.contains(":"))
    }

    @Test
    fun `every section a developer needs is present, even when it has nothing to say`() {
        val report = DiagnosticExport.render(context(), at)

        for (section in listOf(
            "== COINEPRO DIAGNOSTIC REPORT",
            "== VERDICT",
            "== BUILD",
            "== DEVICE",
            "== PLATFORMS",
            "== STATE AT EXPORT",
            "== FAILING REQUESTS",
            "== ROUTE AUDIT",
            "== LAST CRASH",
            "== LOG",
            "== END",
        )) {
            // A section that vanishes when it is empty makes the reader wonder whether it was
            // collected at all. "None recorded" is an answer; a missing heading is not.
            assertTrue(section, report.contains(section))
        }
    }

    @Test
    fun `the build and the handset are in it, because that is the first question back`() {
        val report = DiagnosticExport.render(
            context(
                device = DeviceReport(
                    manufacturer = "Xiaomi",
                    model = "2201117TG",
                    androidRelease = "13",
                    sdkInt = 33,
                    abi = "arm64-v8a",
                    locale = "fa-IR",
                    maxHeapMegabytes = 256,
                    usedHeapMegabytes = 91,
                    freeStorageMegabytes = 1_204,
                ),
            ),
            at,
        )

        assertTrue(report.contains("1.4.0 (140)"))
        assertTrue(report.contains("com.coinepro.app.staging"))
        assertTrue(report.contains("Xiaomi"))
        assertTrue(report.contains("13 (API 33)"))
        assertTrue(report.contains("arm64-v8a"))
        assertTrue(report.contains("fa-IR"))
        assertTrue(report.contains("91 MB used of 256 MB"))
    }

    @Test
    fun `the failing request is in it with its route, its status and when it last happened`() {
        val report = DiagnosticExport.render(
            context(
                failures = listOf(
                    FailureGroup(
                        platform = MarketPlatform.COINEPRO_FX,
                        method = "GET",
                        path = "user/signals",
                        status = 404,
                        failure = null,
                        count = 12,
                        lastAtEpochMillis = at - 30_000,
                        slowestMillis = 143,
                    ),
                ),
                findings = listOf(HealthFinding(FindingKind.ROUTE_MISSING, 1)),
            ),
            at,
        )

        assertTrue(report.contains("user/signals"))
        assertTrue(report.contains("404"))
        assertTrue(report.contains("2025-08-24T01:46:10Z"))
        assertTrue(report.contains("ROUTE_MISSING  x1"))
    }

    @Test
    fun `the host is masked and the scheme and path survive, which is where a misconfiguration shows`() {
        val report = DiagnosticExport.render(
            context(
                platforms = listOf(
                    PlatformBuildInfo(MarketPlatform.COINEPRO_FX, "http://api.internal.example/api/v1/"),
                ),
            ),
            at,
        )

        assertFalse(report.contains("api.internal.example"))
        // An `http://` where `https://` was expected, and a missing prefix, are exactly the class of
        // bug this file exists to make visible.
        assertTrue(report.contains("http://"))
        assertTrue(report.contains("/api/v1/"))
    }

    @Test
    fun `the filter the operator narrowed to is stated, so the file cannot look truncated`() {
        val report = DiagnosticExport.render(
            context(
                filter = LogFilter(
                    minimumLevel = LogLevel.WARN,
                    tags = setOf(LogTag.NETWORK),
                    window = LogWindow.FIVE_MINUTES,
                    query = "404",
                ),
            ),
            at,
        )

        assertTrue(report.contains("filter.minimumLevel"))
        assertTrue(report.contains("WARN"))
        assertTrue(report.contains("NETWORK"))
        assertTrue(report.contains("FIVE_MINUTES"))
        assertTrue(report.contains("404"))
    }

    @Test
    fun `an unasked capability reads as unknown rather than as off`() {
        val report = DiagnosticExport.render(
            context(
                capabilities = mapOf(
                    MarketPlatform.COINEPRO_FX to ServerCapabilities(emailPassword = true, google = false),
                ),
            ),
            at,
        )

        assertEquals("on", report.valueOf("COINEPRO_FX.emailPassword"))
        assertEquals("off", report.valueOf("COINEPRO_FX.google"))
        // The distinction the whole capability model rests on: a server nobody asked and a server
        // that answered no are different facts.
        assertEquals("unknown", report.valueOf("COINEPRO_FX.telegram"))
    }

    @Test
    fun `the crash travels with its trace and the first frame in this app`() {
        val trace = """
            java.lang.IllegalStateException: no session
                at com.coinepro.feature.home.HomeController.load(HomeController.kt:88)
                at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:104)
        """.trimIndent()
        val report = DiagnosticExport.render(context(crash = Crash(at - 5_000, trace)), at)

        assertTrue(report.contains("java.lang.IllegalStateException: no session"))
        assertTrue(report.contains("HomeController.load"))
        assertTrue(report.contains("2025-08-24T01:46:35Z"))
    }

    /** One `name : value` line out of the report, so a test asserts on the value not the padding. */
    private fun String.valueOf(name: String): String? = lineSequence()
        .firstOrNull { it.startsWith(name) && it.contains(':') }
        ?.substringAfter(':')
        ?.trim()

    private fun context(
        device: DeviceReport = DeviceReport(),
        platforms: List<PlatformBuildInfo> = listOf(
            PlatformBuildInfo(MarketPlatform.COINEPRO_FX, "https://api.example.invalid/"),
        ),
        capabilities: Map<MarketPlatform, ServerCapabilities> = emptyMap(),
        failures: List<FailureGroup> = emptyList(),
        findings: List<HealthFinding> = emptyList(),
        filter: LogFilter = LogFilter(),
        crash: Crash? = null,
    ) = DiagnosticContext(
        build = AdminBuildInfo(
            versionName = "1.4.0",
            versionCode = "140",
            environment = "staging",
            applicationId = "com.coinepro.app.staging",
            debuggable = false,
            firebaseConfigured = true,
        ),
        device = device,
        platforms = platforms,
        selected = MarketPlatform.COINEPRO_FX,
        capabilities = capabilities,
        failures = failures,
        findings = findings,
        filter = filter,
        crash = crash,
    )
}
