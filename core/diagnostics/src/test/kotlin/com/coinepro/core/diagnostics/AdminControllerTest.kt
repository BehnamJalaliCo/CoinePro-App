package com.coinepro.core.diagnostics

import com.coinepro.core.model.MarketPlatform
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminControllerTest {

    private val now = 1_756_000_000_000L

    @Test
    fun `a build with no credential opens nothing, which is the point of the default`() = runTest {
        val controller = controller(this)

        assertFalse(controller.state.value.gate.unlocked)
        assertFalse(controller.unlock("BehnamJalali", "anything"))
        assertFalse(controller.state.value.gate.provisioned)
    }

    @Test
    fun `the credential the build was given opens it, and locking closes it again`() = runTest {
        val log = AppLog()
        val controller = controller(this, appLog = log, credential = credential("throwaway"))

        assertTrue(controller.unlock("BehnamJalali", "throwaway"))
        assertTrue(controller.state.value.gate.unlocked)

        controller.lock()

        assertFalse(controller.state.value.gate.unlocked)
        // Both transitions are in the log, which is where an operator investigating "who opened
        // this" would look.
        assertEquals(2, log.entries.value.count { it.tag == LogTag.SECURITY })
    }

    @Test
    fun `the filter narrows the panel without destroying the history behind it`() = runTest {
        val log = AppLog()
        val controller = controller(this, appLog = log)
        log.log(LogLevel.DEBUG, LogTag.CHART, "series loaded", epochMillis = now)
        log.log(LogLevel.ERROR, LogTag.NETWORK, "GET /user/signals", epochMillis = now)

        controller.setMinimumLevel(LogLevel.WARN)

        val state = controller.state.value
        assertEquals(1, state.log.matching(state.filter, now).size)
        // The whole ring is still there. An operator who narrows to ERROR and then wants the DEBUG
        // line before it must not have to reproduce the fault again.
        assertEquals(2, state.log.size)
    }

    @Test
    fun `the export carries the filter the operator narrowed to, and says so`() = runTest {
        val log = AppLog()
        val controller = controller(this, appLog = log)
        log.log(LogLevel.DEBUG, LogTag.CHART, "series loaded", epochMillis = now)
        log.log(LogLevel.ERROR, LogTag.NETWORK, "GET /user/signals", epochMillis = now)
        controller.setMinimumLevel(LogLevel.ERROR)

        val context = controller.exportContext()

        assertEquals(LogLevel.ERROR, context.filter.minimumLevel)
        assertEquals(listOf("GET /user/signals"), context.entries.map(LogEntry::message))
        // Stated in the file, so a report holding one line cannot be mistaken for a truncated one.
        assertTrue(DiagnosticExport.render(context, now).contains("filter.minimumLevel"))
    }

    @Test
    fun `wiping the log leaves nothing for the next export to carry`() = runTest {
        val log = AppLog()
        val controller = controller(this, appLog = log)
        log.info(LogTag.STATE, "something")

        controller.clearLog()

        assertTrue(controller.state.value.log.isEmpty())
        assertTrue(controller.exportContext().entries.isEmpty())
    }

    @Test
    fun `verbosity is in the state, so the control shows what is actually in force`() = runTest {
        val log = AppLog()
        val controller = controller(this, appLog = log)

        controller.setVerbosity(LogLevel.TRACE)

        assertEquals(LogLevel.TRACE, controller.state.value.verbosity)
        assertEquals(LogLevel.TRACE, log.minimumLevel)
    }

    private fun controller(
        scope: TestScope,
        appLog: AppLog = AppLog(),
        credential: BuildCredential? = null,
    ) = AdminController(
        build = AdminBuildInfo("1.0.0", "1", "staging", "com.coinepro.app", false, true),
        platforms = listOf(PlatformBuildInfo(MarketPlatform.COINEPRO_FX, "https://api.example.invalid/")),
        probers = emptyMap(),
        requestLog = RequestLog(),
        appLog = appLog,
        // Unconfined so the collectors in `init` have run by the time an assertion reads the state;
        // the controller's own behaviour is what is under test, not the dispatcher's.
        scope = TestScope(UnconfinedTestDispatcher(scope.testScheduler)),
        initialPlatform = MarketPlatform.COINEPRO_FX,
        gate = AdminGate(credential, appLog = appLog, clock = { now }),
        clock = { now },
    )

    private fun credential(password: String): BuildCredential {
        val salt = "0123456789abcdef".toByteArray(Charsets.UTF_8)
        return BuildCredential(
            username = "BehnamJalali",
            salt = Base64.getEncoder().encodeToString(salt),
            hash = Base64.getEncoder()
                .encodeToString(AdminPasswordHash.derive(password, salt, 1_000)),
            iterations = 1_000,
        )
    }
}
