package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.test.TestScope
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
        override suspend fun readToken(): String? = token
        override suspend fun writeToken(token: String) { this.token = token }
        override suspend fun clear() { token = null }
    }

    private class FakeGateway(
        var authConfig: AppResult<AuthConfig> = AppResult.Success(AuthConfig("CoineProBot")),
        private val me: AppResult<UserProfile> = AppResult.Failure(ErrorKind.AUTH),
    ) : AuthGateway {
        override suspend fun authConfig() = authConfig
        override suspend fun loginTelegram(payload: TelegramAuthPayload) = AppResult.Failure(ErrorKind.AUTH)
        override suspend fun me(): AppResult<UserProfile> = me
    }
}
