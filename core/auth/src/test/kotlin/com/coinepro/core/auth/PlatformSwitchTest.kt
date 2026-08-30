package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Switching market must never end a session.
 *
 * This is the third time the forex tab has been reported, and the two earlier rounds each fixed
 * something real and neither fixed this: the shell asked `states[activePlatform]`, so the tab that
 * says «فارکس» decided whether the reader had an account at all. Registration does not federate, so
 * every reader holds exactly one of the two sessions and the other tab was always a sign-out.
 *
 * The two halves are tested separately because they fail separately. [sessionForShell] is the rule
 * the shell reads; the controllers below are the proof that the rule is safe to read — that a
 * platform answering 401 to a token it has never seen cannot reach across and end the session that
 * *is* valid.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformSwitchTest {

    private class FakeStorage(private var token: String? = null) : SessionTokenStorage {
        private var refreshToken: String? = null
        override suspend fun readToken(): String? = token
        override suspend fun writeToken(token: String) {
            this.token = token
        }

        override suspend fun readRefreshToken(): String? = refreshToken

        override suspend fun writeRefreshToken(token: String) {
            refreshToken = token
        }

        override suspend fun clear() {
            token = null
            refreshToken = null
        }

        fun peek(): String? = token
    }

    private class FakeGateway(private val profileName: String) : AuthGateway {
        override suspend fun authConfig() = AppResult.Success(AuthConfig("bot"))
        override suspend fun loginTelegram(payload: TelegramAuthPayload) =
            AppResult.Failure(ErrorKind.UNKNOWN)

        override suspend fun me(): AppResult<UserProfile> =
            AppResult.Success(UserProfile(telegramId = 1L, name = profileName))
    }

    private fun signedIn(name: String) = SessionState.SignedIn(
        profile = UserProfile(telegramId = 1L, name = name),
        entitlement = UserProfile(telegramId = 1L, name = name).toEntitlementSnapshot(),
    )

    @Test
    fun `switching to a platform with no session keeps the reader signed in`() {
        val states = mapOf(
            MarketPlatform.COINEPRO_FX to SessionState.SignedOut,
            MarketPlatform.TRADEYAR to signedIn("reader"),
        )

        val onForex = states.sessionForShell(MarketPlatform.COINEPRO_FX)

        assertTrue("Tapping «فارکس» must not empty the shell", onForex is SessionState.SignedIn)
        assertEquals("reader", (onForex as SessionState.SignedIn).profile.name)
    }

    @Test
    fun `the platform on screen answers first when the reader holds both`() {
        val states = mapOf(
            MarketPlatform.COINEPRO_FX to signedIn("forex"),
            MarketPlatform.TRADEYAR to signedIn("crypto"),
        )

        assertEquals(
            "forex",
            (states.sessionForShell(MarketPlatform.COINEPRO_FX) as SessionState.SignedIn).profile.name,
        )
        assertEquals(
            "crypto",
            (states.sessionForShell(MarketPlatform.TRADEYAR) as SessionState.SignedIn).profile.name,
        )
    }

    @Test
    fun `nobody signed in anywhere is still signed out`() {
        val states = mapOf(
            MarketPlatform.COINEPRO_FX to SessionState.SignedOut,
            MarketPlatform.TRADEYAR to SessionState.SignedOut,
        )

        assertEquals(SessionState.SignedOut, states.sessionForShell(MarketPlatform.COINEPRO_FX))
    }

    @Test
    fun `the first frame of a cold start is loading rather than a flash of the guest shell`() {
        assertEquals(
            SessionState.Loading,
            emptyMap<MarketPlatform, SessionState>().sessionForShell(MarketPlatform.TRADEYAR),
        )
    }

    /**
     * A session still being revalidated is reported as itself, not as somebody else's.
     *
     * The screen for this state offers a retry against the platform that could not be confirmed, so
     * answering with the other platform's session would hide a problem the reader can fix.
     */
    @Test
    fun `a revalidation on the platform on screen is not papered over by the other session`() {
        val message = UiMessage.of(MessageKey.SESSION_NOT_REVALIDATED)
        val states = mapOf(
            MarketPlatform.COINEPRO_FX to SessionState.RevalidationRequired(message),
            MarketPlatform.TRADEYAR to SessionState.SignedOut,
        )

        assertEquals(
            SessionState.RevalidationRequired(message),
            states.sessionForShell(MarketPlatform.COINEPRO_FX),
        )
    }

    /**
     * The proof underneath the rule.
     *
     * A reader on the forex tab with no forex token makes forex requests that come back 401. That is
     * the ordinary consequence of the fix above, so it has to be harmless — and it is only harmless
     * because each platform carries its own [SessionMemory] and its own storage. If those were ever
     * shared, following the reader's session onto the other tab would turn every 401 from the
     * backend they have no account with into the end of the account they do have.
     */
    @Test
    fun `a 401 from the platform with no token cannot end the session that has one`() =
        runTest(UnconfinedTestDispatcher()) {
            val forexStorage = FakeStorage(null)
            val forexMemory = SessionMemory()
            val cryptoStorage = FakeStorage("crypto-token")
            val sessions = sessions(backgroundScope, forexStorage, forexMemory, cryptoStorage)
            sessions.start()
            runCurrent()

            // Every forex request in the shell answering at once, which is what a platform switch
            // produces: the feed, the balance, the signals and the notifications all at 401.
            repeat(4) { forexMemory.notifyUnauthorized() }
            runCurrent()

            assertEquals(setOf(MarketPlatform.TRADEYAR), sessions.signedIn.value)
            assertEquals("crypto-token", cryptoStorage.peek())
            assertTrue(
                "The forex switch must not have reached the crypto token",
                sessions.states.value.sessionForShell(MarketPlatform.COINEPRO_FX) is SessionState.SignedIn,
            )
        }

    // The unauthorized collector and the two stateIn shares never complete, so they belong to
    // backgroundScope; runTest would otherwise wait on them until it times out.
    private fun sessions(
        scope: CoroutineScope,
        forexStorage: FakeStorage,
        forexMemory: SessionMemory,
        cryptoStorage: FakeStorage,
    ): PlatformSessions = PlatformSessions(
        controllers = mapOf(
            MarketPlatform.COINEPRO_FX to
                SessionController(forexStorage, forexMemory, FakeGateway("forex"), scope),
            MarketPlatform.TRADEYAR to
                SessionController(cryptoStorage, SessionMemory(), FakeGateway("crypto"), scope),
        ),
        scope = scope,
    )
}
