package com.coinepro.feature.terminal

import com.coinepro.core.marketdata.AcademyTokenStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
    ) = TerminalController({ url }, tokens, TestScope(UnconfinedTestDispatcher(scheduler)))

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
    fun `the token rides in the fragment, encoded, and never in the query`() {
        val launch = launchUrl("https://terminal.coinepro.com", "abc.def-ghi_jkl")

        // A fragment is never sent to a server — not in the request line, not in `Referer`. A
        // query parameter would land in an access log, a proxy log and a CDN record.
        assertTrue(launch.startsWith("https://terminal.coinepro.com#t="))
        assertFalse("never a query parameter", launch.contains("?"))
        assertTrue(launch.endsWith("abc.def-ghi_jkl"))
    }

    @Test
    fun `a token carrying characters with meaning in a URL is encoded`() {
        // Nothing about a credential is the app's to assume. `+` in a fragment is a real plus, but
        // a terminal that decodes with `decodeURIComponent` would read one as a space.
        val launch = launchUrl("https://terminal.coinepro.com", "a+b/c=d&e#f")

        assertFalse(launch.substringAfter("#t=").contains("&"))
        assertFalse(launch.substringAfter("#t=").contains("#"))
        assertEquals(
            "a+b/c=d&e#f",
            java.net.URLDecoder.decode(launch.substringAfter("#t="), "UTF-8"),
        )
    }

    @Test
    fun `the address the origin check uses carries no credential`() = runTest {
        val controller = controller("https://terminal.coinepro.com", scheduler = testScheduler)
        controller.start()
        runCurrent()

        val state = controller.state.value
        // `url` is what every navigation is compared against, and what anything that logs a URL
        // would log. The token lives only on `launchUrl`.
        assertEquals("https://terminal.coinepro.com", state.url)
        assertFalse(state.url!!.contains("#"))
        assertTrue(state.launchUrl!!.contains("#t="))
    }
}

/**
 * The WebView's origin check.
 *
 * Every case here is a URL that the check this replaced — `target.startsWith(url)` — let through.
 * They are kept as tests rather than as a comment because the failure they describe is silent: the
 * page loads, looks like the terminal, and is handed the reader's academy token by `onPageStarted`.
 */
class TerminalOriginTest {

    private val terminal = "https://terminal.coinepro.com"

    @Test
    fun `the terminal's own pages are allowed`() {
        assertTrue(isTerminalUrl("https://terminal.coinepro.com", terminal))
        assertTrue(isTerminalUrl("https://terminal.coinepro.com/", terminal))
        assertTrue(isTerminalUrl("https://terminal.coinepro.com/bn/chart?symbol=XAUUSD", terminal))
        // The host is compared case-insensitively, because DNS is.
        assertTrue(isTerminalUrl("https://Terminal.CoinePro.com/bn", terminal))
    }

    @Test
    fun `a longer domain that merely starts with the terminal's is refused`() {
        // `startsWith` accepted this. The registrable domain is evil.tld and nothing about it is
        // the terminal.
        assertFalse(isTerminalUrl("https://terminal.coinepro.com.evil.tld/steal", terminal))
        assertFalse(isTerminalUrl("https://terminal.coinepro.community-news.example/x", terminal))
    }

    @Test
    fun `userinfo that impersonates the host is refused`() {
        // Everything before the @ is a username. The request goes to evil.tld — and `startsWith`
        // saw the terminal's address at the front of the string and allowed it.
        assertFalse(isTerminalUrl("https://terminal.coinepro.com@evil.tld/steal", terminal))
        assertFalse(isTerminalUrl("https://terminal.coinepro.com:pass@evil.tld/", terminal))
    }

    @Test
    fun `plain http is refused even on the right host`() {
        // The token is a bearer credential; over http anything on the path can read it.
        assertFalse(isTerminalUrl("http://terminal.coinepro.com/bn", terminal))
    }

    @Test
    fun `nothing is the terminal when no terminal is configured`() {
        assertFalse(isTerminalUrl("https://terminal.coinepro.com", null))
        assertFalse(isTerminalUrl("https://terminal.coinepro.com", ""))
        assertFalse(isTerminalUrl(null, terminal))
    }

    @Test
    fun `a configured address carrying userinfo is not usable at all`() {
        // Refused at the source rather than at each navigation, so there is no state in which the
        // app is pointed at one host and checking against another.
        assertNull(normalisedUrl("https://terminal.coinepro.com@evil.tld"))
        assertNull(terminalHost("https://terminal.coinepro.com@evil.tld"))
    }
}
