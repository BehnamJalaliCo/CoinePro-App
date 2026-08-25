package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControllerTest {
    @Test
    fun coldStartWithoutTokenIsSignedOutWithReadyLoginConfig() = runTest {
        val controller = controller(FakeStorage(null), FakeGateway())
        controller.restore()
        assertTrue(controller.state.value is SessionState.SignedOut)
        assertEquals(LoginConfigState.Ready("CoineProBot"), controller.loginConfigState.value)
    }

    @Test
    fun loginConfigFailureBecomesVisibleRetryableError() = runTest {
        val gateway = FakeGateway(authConfig = AppResult.Failure(ErrorKind.NETWORK))
        val controller = controller(FakeStorage(null), gateway)

        controller.restore()
        assertTrue(controller.state.value is SessionState.SignedOut)
        assertTrue(controller.loginConfigState.value is LoginConfigState.Error)

        gateway.authConfig = AppResult.Success(AuthConfig("CoineProBot"))
        controller.prepareLogin()
        assertEquals(LoginConfigState.Ready("CoineProBot"), controller.loginConfigState.value)
    }

    @Test
    fun coldStartRestoresServerValidatedSession() = runTest {
        val profile = profile(isPaid = true, panelAllowed = true)
        val controller = controller(FakeStorage("token"), FakeGateway(me = AppResult.Success(profile)))
        controller.restore()
        val state = controller.state.value as SessionState.SignedIn
        assertTrue(state.entitlement.hasPaidPanelAccess)
    }

    @Test
    fun unauthorizedSessionIsClearedAndLoginConfigPrepared() = runTest {
        val storage = FakeStorage("expired")
        val controller = controller(
            storage,
            FakeGateway(me = AppResult.Failure(ErrorKind.AUTH)),
        )
        controller.restore()
        assertTrue(controller.state.value is SessionState.SignedOut)
        assertEquals(null, storage.token)
        assertTrue(controller.loginConfigState.value is LoginConfigState.Ready)
    }

    @Test
    fun networkFailureKeepsProtectedFeaturesLockedUntilRevalidation() = runTest {
        val storage = FakeStorage("existing")
        val controller = controller(
            storage,
            FakeGateway(me = AppResult.Failure(ErrorKind.NETWORK)),
        )
        controller.restore()
        assertTrue(controller.state.value is SessionState.RevalidationRequired)
        assertEquals("existing", storage.token)
    }

    @Test
    fun freeUserDoesNotReceivePaidEntitlement() = runTest {
        val controller = controller(FakeStorage("token"), FakeGateway(me = AppResult.Success(profile())))
        controller.restore()
        val state = controller.state.value as SessionState.SignedIn
        assertFalse(state.entitlement.hasPaidPanelAccess)
    }

    @Test
    fun `an expired access token is renewed rather than ending the session`() = runTest {
        val storage = FakeStorage("expired").apply { refreshToken = "refresh-1" }
        val memory = SessionMemory()
        val emailAuth = FakeEmailAuthGateway(
            AppResult.Success(AuthTokens(accessToken = "fresh", refreshToken = "refresh-2")),
        )
        val controller = SessionController(
            storage,
            memory,
            FakeGateway(me = AppResult.Success(profile())),
            backgroundScope,
            emailAuth,
        )
        controller.start()
        runCurrent()
        assertTrue(controller.state.value is SessionState.SignedIn)

        memory.notifyUnauthorized()
        runCurrent()

        assertTrue(
            "A token that simply aged out is the ordinary case, not the end of the session",
            controller.state.value is SessionState.SignedIn,
        )
        assertEquals("fresh", storage.token)
        assertEquals("refresh-2", storage.refreshToken)
        assertEquals("fresh", memory.token())
        assertEquals("refresh-1", emailAuth.refreshedWith)
    }

    @Test
    fun `a refused renewal ends the session and clears both tokens`() = runTest {
        val storage = FakeStorage("expired").apply { refreshToken = "revoked" }
        val memory = SessionMemory()
        val controller = SessionController(
            storage,
            memory,
            FakeGateway(me = AppResult.Success(profile())),
            backgroundScope,
            FakeEmailAuthGateway(AppResult.Failure(ErrorKind.AUTH)),
        )
        controller.start()
        runCurrent()

        memory.notifyUnauthorized()
        runCurrent()

        assertTrue(controller.state.value is SessionState.SignedOut)
        assertEquals(null, storage.token)
        assertEquals(null, storage.refreshToken)
    }

    @Test
    fun `a renewal that never reached the server leaves the session alone`() = runTest {
        val storage = FakeStorage("current").apply { refreshToken = "refresh-1" }
        val memory = SessionMemory()
        val controller = SessionController(
            storage,
            memory,
            FakeGateway(me = AppResult.Success(profile())),
            backgroundScope,
            FakeEmailAuthGateway(AppResult.Failure(ErrorKind.NETWORK)),
        )
        controller.start()
        runCurrent()

        memory.notifyUnauthorized()
        runCurrent()

        assertTrue(
            "A dropped connection proves nothing about whether the server still honours the session",
            controller.state.value is SessionState.SignedIn,
        )
        assertEquals("current", storage.token)
    }

    @Test
    fun `a session with no way to renew itself ends on the first refusal`() = runTest {
        val storage = FakeStorage("telegram-token")
        val memory = SessionMemory()
        val controller = SessionController(
            storage,
            memory,
            FakeGateway(me = AppResult.Success(profile())),
            backgroundScope,
        )
        controller.start()
        runCurrent()

        memory.notifyUnauthorized()
        runCurrent()

        assertTrue(controller.state.value is SessionState.SignedOut)
        assertEquals(null, storage.token)
    }

    @Test
    fun `an adopted email session stores both tokens before it reports being signed in`() = runTest {
        val storage = FakeStorage(null)
        val memory = SessionMemory()
        val controller = SessionController(storage, memory, FakeGateway(), this)

        controller.adoptSession(
            EmailAuthSession(
                tokens = AuthTokens(accessToken = "access", refreshToken = "refresh"),
                profile = profile(isPaid = true, panelAllowed = true),
            ),
        )

        val state = controller.state.value as SessionState.SignedIn
        assertTrue(state.entitlement.hasPaidPanelAccess)
        assertEquals("access", storage.token)
        assertEquals(
            "Without the refresh token the session would end at the first expiry",
            "refresh",
            storage.refreshToken,
        )
        assertEquals("access", memory.token())
    }

    private fun TestScope.controller(storage: FakeStorage, gateway: FakeGateway) =
        SessionController(storage, SessionMemory(), gateway, this)

    private fun profile(isPaid: Boolean = false, panelAllowed: Boolean = false) = UserProfile(
        telegramId = 1,
        name = "Test",
        isVip = isPaid,
        isPaid = isPaid,
        panelAllowed = panelAllowed,
        panelApproved = panelAllowed,
        plan = if (isPaid) "monthly" else "free",
    )

    private class FakeStorage(var token: String?) : SessionTokenStorage {
        var refreshToken: String? = null
        override suspend fun readToken(): String? = token
        override suspend fun writeToken(token: String) { this.token = token }
        override suspend fun readRefreshToken(): String? = refreshToken
        override suspend fun writeRefreshToken(token: String) { refreshToken = token }
        override suspend fun clear() { token = null; refreshToken = null }
    }

    private class FakeGateway(
        var authConfig: AppResult<AuthConfig> = AppResult.Success(AuthConfig("CoineProBot")),
        private val me: AppResult<UserProfile> = AppResult.Failure(ErrorKind.AUTH),
    ) : AuthGateway {
        override suspend fun authConfig() = authConfig
        override suspend fun loginTelegram(payload: TelegramAuthPayload) = AppResult.Failure(ErrorKind.AUTH)
        override suspend fun me(): AppResult<UserProfile> = me
    }

    /** Only [refresh] is exercised here; the rest of the flow has its own tests. */
    private class FakeEmailAuthGateway(
        private val result: AppResult<AuthTokens>,
    ) : EmailAuthGateway {
        var refreshedWith: String? = null

        override suspend fun refresh(refreshToken: String): AppResult<AuthTokens> {
            refreshedWith = refreshToken
            return result
        }

        override suspend fun methods() = AppResult.Failure(ErrorKind.NETWORK)
        override suspend fun startRegistration(email: String, password: String, fullName: String) =
            AppResult.Failure(ErrorKind.NETWORK)
        override suspend fun verifyRegistration(registrationToken: String, code: String) =
            AppResult.Failure(ErrorKind.NETWORK)
        override suspend fun signIn(email: String, password: String) = AppResult.Failure(ErrorKind.NETWORK)
        override suspend fun signInWithGoogle(idToken: String) = AppResult.Failure(ErrorKind.NETWORK)
        override suspend fun requestPasswordReset(email: String) = AppResult.Failure(ErrorKind.NETWORK)
        override suspend fun resetPassword(resetToken: String, newPassword: String) =
            AppResult.Failure(ErrorKind.NETWORK)
        override suspend fun signOut(refreshToken: String) = AppResult.Failure(ErrorKind.NETWORK)
    }
}
