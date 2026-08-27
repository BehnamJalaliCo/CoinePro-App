package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EmailAuthControllerTest {

    @Test
    fun `no method is offered until the server says which exist`() = runTest {
        val gateway = FakeEmailAuthGateway()
        val controller = controller(gateway)

        assertFalse(controller.state.value.methodsKnown)
        assertFalse(controller.state.value.methods.any)

        gateway.methods = AppResult.Success(AuthMethods(emailPassword = true, google = false))
        controller.loadMethods()
        runCurrent()

        assertTrue(controller.state.value.methodsKnown)
        assertTrue(controller.state.value.methods.emailPassword)
        assertFalse("A method the server disabled must not be offered", controller.state.value.methods.google)
    }

    @Test
    fun `registration moves to the code step and starts the server's cooldown`() = runTest {
        val gateway = FakeEmailAuthGateway()
        gateway.registration = AppResult.Success(RegistrationChallenge("reg-1", cooldownSeconds = 3))
        val controller = controller(gateway)

        controller.startRegistration("reader@example.com", "a-long-enough-password", "Reader")
        runCurrent()

        assertEquals(EmailAuthStep.VERIFY_CODE, controller.state.value.step)
        assertEquals("reader@example.com", controller.state.value.pendingEmail)
        assertEquals(EmailAuthNotice.CODE_SENT, controller.state.value.notice)
        assertEquals(3, controller.state.value.resendAvailableIn)
    }

    @Test
    fun `the cooldown runs down and then stops`() = runTest {
        val gateway = FakeEmailAuthGateway()
        gateway.registration = AppResult.Success(RegistrationChallenge("reg-1", cooldownSeconds = 2))
        val controller = controller(gateway)

        controller.startRegistration("reader@example.com", "a-long-enough-password", "Reader")
        runCurrent()
        advanceTimeBy(1_100)
        assertEquals(1, controller.state.value.resendAvailableIn)

        advanceTimeBy(1_100)
        assertEquals(0, controller.state.value.resendAvailableIn)
    }

    @Test
    fun `starting over inside the cooldown does nothing`() = runTest {
        val gateway = FakeEmailAuthGateway()
        gateway.registration = AppResult.Success(RegistrationChallenge("reg-1", cooldownSeconds = 30))
        val controller = controller(gateway)

        controller.startRegistration("reader@example.com", "a-long-enough-password", "Reader")
        runCurrent()

        controller.startOver()
        runCurrent()

        assertEquals(
            "The server's cooldown governs starting again, so the step must not change yet",
            EmailAuthStep.VERIFY_CODE,
            controller.state.value.step,
        )

        advanceTimeBy(31_000)
        controller.startOver()
        runCurrent()
        assertEquals(EmailAuthStep.REGISTER, controller.state.value.step)
    }

    @Test
    fun `a rate limit carries the server's wait and blocks further attempts until it passes`() = runTest {
        val gateway = FakeEmailAuthGateway()
        gateway.signIn = AppResult.Failure(
            kind = ErrorKind.RATE_LIMIT,
            message = "بیش از حد تلاش شد.",
            retryAfterSeconds = 2,
        )
        val controller = controller(gateway)

        controller.signIn("reader@example.com", "wrong-password")
        runCurrent()

        assertEquals(AuthFailureReason.RATE_LIMITED, controller.state.value.failure?.reason)
        assertEquals("بیش از حد تلاش شد.", controller.state.value.failure?.message)
        assertEquals(2, controller.state.value.retryAvailableIn)
        assertTrue(controller.state.value.waiting)

        val attempts = gateway.signInCalls
        controller.signIn("reader@example.com", "wrong-password")
        runCurrent()
        assertEquals("An attempt during the wait must not be spent", attempts, gateway.signInCalls)

        advanceTimeBy(2_100)
        assertFalse(controller.state.value.waiting)
    }

    @Test
    fun `a request that never reached a verdict shows no server wording`() = runTest {
        val gateway = FakeEmailAuthGateway()
        gateway.signIn = AppResult.Failure(ErrorKind.NETWORK, message = "timeout at okhttp3.internal")
        val controller = controller(gateway)

        controller.signIn("reader@example.com", "password-goes-here")
        runCurrent()

        assertEquals(AuthFailureReason.UNREACHABLE, controller.state.value.failure?.reason)
        assertNull(
            "Plumbing text must never be shown as though the server had said it",
            controller.state.value.failure?.message,
        )
    }

    @Test
    fun `a rejected sign-in never reports a session`() = runTest {
        val gateway = FakeEmailAuthGateway()
        gateway.signIn = AppResult.Failure(ErrorKind.AUTH, message = "ایمیل یا رمز عبور نادرست است.")
        var authenticated = 0
        val controller = controller(gateway) { authenticated++ }

        controller.signIn("reader@example.com", "wrong-password")
        runCurrent()

        assertEquals(0, authenticated)
        assertEquals(AuthFailureReason.REJECTED, controller.state.value.failure?.reason)
    }

    @Test
    fun `recovery reports the same notice regardless of whether the address exists`() = runTest {
        val gateway = FakeEmailAuthGateway()
        val controller = controller(gateway)

        controller.requestPasswordReset("known@example.com")
        runCurrent()
        val first = controller.state.value.notice

        controller.goTo(EmailAuthStep.FORGOT_PASSWORD)
        controller.requestPasswordReset("unknown@example.com")
        runCurrent()

        assertEquals(EmailAuthNotice.RESET_REQUESTED, first)
        assertEquals(first, controller.state.value.notice)
    }

    @Test
    fun `verifying with a registration this install no longer holds returns to the start`() = runTest {
        val gateway = FakeEmailAuthGateway()
        val controller = controller(gateway)

        controller.verifyCode("123456")
        runCurrent()

        assertEquals(EmailAuthStep.REGISTER, controller.state.value.step)
        assertEquals(0, gateway.verifyCalls)
    }

    @Test
    fun `a successful reset returns to sign-in and says the password changed`() = runTest {
        val gateway = FakeEmailAuthGateway()
        val controller = controller(gateway)

        controller.resetPassword("reset-token", "a-brand-new-password")
        runCurrent()

        assertEquals(EmailAuthStep.SIGN_IN, controller.state.value.step)
        assertEquals(EmailAuthNotice.PASSWORD_CHANGED, controller.state.value.notice)
    }

    /**
     * The address is lower-cased before it leaves.
     *
     * A phone keyboard capitalises the first letter of a field often enough that the same person
     * registers as `Reader@…` and signs in as `reader@…`. A server that compares the local part
     * exactly then answers, correctly from its own point of view, that the password is wrong — and
     * there is no case in which a reader means two different accounts by two spellings of one
     * address.
     */
    @Test
    fun `the address is normalised on every step that sends one`() = runTest {
        val gateway = FakeEmailAuthGateway()
        val controller = controller(gateway)

        controller.signIn("  Reader@Example.COM ", "password")
        runCurrent()
        assertEquals("reader@example.com", gateway.lastEmail)

        controller.startRegistration("  Reader@Example.COM ", "password", " Reader ")
        runCurrent()
        assertEquals("reader@example.com", gateway.lastEmail)

        controller.requestPasswordReset("  Reader@Example.COM ")
        runCurrent()
        assertEquals("reader@example.com", gateway.lastEmail)
    }

    /**
     * A registration survives the app being killed while the reader is in their inbox.
     *
     * Without this the token lives only in memory: they come back to a sign-in screen for an
     * account that was never created, try to sign in, and are told the credentials are wrong.
     */
    @Test
    fun `an unfinished registration is picked back up`() = runTest {
        val memory = FakeRegistrationMemory()
        val gateway = FakeEmailAuthGateway()
        gateway.registration = AppResult.Success(RegistrationChallenge("reg-9", cooldownSeconds = null))

        val first = controller(gateway, memory)
        first.startRegistration("reader@example.com", "a-long-enough-password", "Reader")
        runCurrent()
        assertEquals(PendingRegistration("reg-9", "reader@example.com"), memory.pending)

        // A second controller stands in for the process having been killed and restarted.
        val second = controller(gateway, memory)
        second.resume()
        runCurrent()

        assertEquals(EmailAuthStep.VERIFY_CODE, second.state.value.step)
        assertEquals("reader@example.com", second.state.value.pendingEmail)

        second.verifyCode("123456")
        runCurrent()
        assertNull("A completed registration must not be resumed again", memory.pending)
    }

    @Test
    fun `starting over forgets the abandoned registration`() = runTest {
        val memory = FakeRegistrationMemory()
        val gateway = FakeEmailAuthGateway()
        val controller = controller(gateway, memory)

        controller.startRegistration("reader@example.com", "a-long-enough-password", "Reader")
        runCurrent()
        controller.startOver()
        runCurrent()

        assertNull(memory.pending)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        gateway: FakeEmailAuthGateway,
        memory: RegistrationMemory? = null,
        onAuthenticated: (EmailAuthSession) -> Unit = {},
    ) = EmailAuthController(gateway, this, { onAuthenticated(it) }, memory)
}

private class FakeRegistrationMemory : RegistrationMemory {
    var pending: PendingRegistration? = null

    override suspend fun save(pending: PendingRegistration?) {
        this.pending = pending
    }

    override suspend fun load(): PendingRegistration? = pending
}

private class FakeEmailAuthGateway : EmailAuthGateway {
    /** The last address that actually went out, so normalisation can be asserted on. */
    var lastEmail: String? = null
    var methods: AppResult<AuthMethods> = AppResult.Success(AuthMethods(emailPassword = true))
    var registration: AppResult<RegistrationChallenge> =
        AppResult.Success(RegistrationChallenge("reg", null))
    var signIn: AppResult<EmailAuthSession> = AppResult.Success(session())

    var signInCalls = 0
    var verifyCalls = 0

    override suspend fun methods() = methods

    override suspend fun startRegistration(email: String, password: String, fullName: String) =
        registration.also { lastEmail = email }

    override suspend fun verifyRegistration(registrationToken: String, code: String):
        AppResult<EmailAuthSession> {
        verifyCalls++
        return signIn
    }

    override suspend fun signIn(email: String, password: String): AppResult<EmailAuthSession> {
        signInCalls++
        lastEmail = email
        return signIn
    }

    override suspend fun signInWithGoogle(idToken: String) = signIn

    override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
        lastEmail = email
        return AppResult.Success(Unit)
    }

    override suspend fun resetPassword(resetToken: String, newPassword: String): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun refresh(refreshToken: String): AppResult<AuthTokens> =
        AppResult.Success(AuthTokens("access", "refresh"))

    override suspend fun signOut(refreshToken: String): AppResult<Unit> = AppResult.Success(Unit)
}

private fun session() = EmailAuthSession(
    tokens = AuthTokens("access", "refresh", accessValidForSeconds = 1_800),
    profile = UserProfile(telegramId = 0, name = "Reader"),
    platform = MarketPlatform.TRADEYAR,
)
