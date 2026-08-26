package com.coinepro.feature.terminal

import com.coinepro.core.marketdata.AcademyTokenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalControllerTest {

    private class FakeTokens(
        private val value: String = "jwt.token.value",
        private val failure: Throwable? = null,
    ) : AcademyTokenStore {
        var calls = 0
            private set

        override suspend fun token(): String {
            calls++
            failure?.let { throw it }
            return value
        }

        override fun clear() = Unit
    }

    private fun controller(
        url: String,
        tokens: AcademyTokenStore = FakeTokens(),
        scheduler: kotlinx.coroutines.test.TestCoroutineScheduler,
    ) = TerminalController(url, tokens, TestScope(UnconfinedTestDispatcher(scheduler)))

    @Test
    fun `a build with no terminal address says so instead of loading nothing`() = runTest {
        val controller = controller("", scheduler = testScheduler)
        controller.start()

        assertFalse(controller.isConfigured)
        assertEquals(TerminalError.NOT_CONFIGURED, controller.state.value.error)
        assertNull(controller.state.value.url)
    }

    @Test
    fun `a plain http address is refused rather than downgraded`() = runTest {
        // The page is handed a bearer token that opens the reader's whole academy account. Over
        // http anything on the path can read it, so a misconfigured address fails closed.
        val controller = controller("http://pro-chart.example/", scheduler = testScheduler)
        controller.start()

        assertFalse(controller.isConfigured)
        assertEquals(TerminalError.NOT_CONFIGURED, controller.state.value.error)
    }

    @Test
    fun `a configured address mints a token and hands both to the screen`() = runTest {
        val tokens = FakeTokens("abc.def.ghi")
        val controller = controller("https://terminal.example/", tokens, testScheduler)
        controller.start()

        assertTrue(controller.isConfigured)
        assertEquals("https://terminal.example", controller.state.value.url)
        assertEquals("abc.def.ghi", controller.state.value.token)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `the token is minted once, not on every recomposition`() = runTest {
        val tokens = FakeTokens()
        val controller = controller("https://terminal.example", tokens, testScheduler)
        controller.start()
        controller.start()
        controller.start()

        assertEquals(1, tokens.calls)
    }

    @Test
    fun `a closed academy is told apart from a failed mint`() = runTest {
        // Both are a 403 on the wire. One means "this server has the feature off", the other means
        // something went wrong — and only the second is worth a retry button.
        val disabled = controller(
            "https://terminal.example",
            FakeTokens(failure = IllegalStateException("""{"code":"academy_disabled"}""")),
            testScheduler,
        )
        disabled.start()
        assertEquals(TerminalError.DISABLED, disabled.state.value.error)

        val broken = controller(
            "https://terminal.example",
            FakeTokens(failure = IllegalStateException("502")),
            testScheduler,
        )
        broken.start()
        assertEquals(TerminalError.NO_TOKEN, broken.state.value.error)
    }

    @Test
    fun `loading clears when the page reports it finished`() = runTest {
        val controller = controller("https://terminal.example", scheduler = testScheduler)
        controller.start()
        assertTrue(controller.state.value.loading)

        controller.onLoaded()
        assertFalse(controller.state.value.loading)
    }

    @Test
    fun `a failed page keeps the address so retry has somewhere to go`() = runTest {
        val controller = controller("https://terminal.example", scheduler = testScheduler)
        controller.start()
        controller.onLoadFailed()

        assertEquals(TerminalError.LOAD_FAILED, controller.state.value.error)
        assertEquals("https://terminal.example", controller.state.value.url)

        controller.retry()
        assertNull(controller.state.value.error)
        assertEquals("https://terminal.example", controller.state.value.url)
    }

    @Test
    fun `a trailing slash does not change the origin the screen compares against`() = runTest {
        // The screen refuses navigation away from this prefix. With the slash left on, the
        // terminal's own first hop — the same host, no trailing slash — would be refused.
        assertEquals("https://a.example", normalisedUrl("https://a.example/"))
        assertEquals("https://a.example", normalisedUrl("  https://a.example///  "))
        assertNull(normalisedUrl(null))
        assertNull(normalisedUrl("   "))
        assertNull(normalisedUrl("ftp://a.example"))
    }

    @Test
    fun `the injected script cannot be ended by a token that contains a quote`() = runTest {
        // Academy tokens are JWTs and contain no quotes, which is exactly the assumption that
        // stops being true quietly. A token that could close the string literal would run whatever
        // followed it as script, inside a page holding the reader's session.
        val script = tokenInjectionScript("""a"b\c""" + "\n" + "</script>")

        assertFalse("the literal must not be closed early", script.contains("\"a\"b"))
        assertTrue(script.contains("""a\"b\\c"""))
        assertFalse("no raw newline inside the literal", script.contains("\\c\n<"))
        assertFalse("no closing tag survives", script.contains("</script>"))
        assertTrue(script.contains("cp_academy_token"))
    }
}
