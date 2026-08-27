package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that keep one sign-in screen honest over two user tables.
 *
 * Each of these is here because getting it wrong produces a specific, bad outcome rather than a
 * failing assertion: an account created twice, a password sent to a server that had no business
 * seeing it, or a reader with a real account told their password is wrong.
 */
class FederatedEmailAuthGatewayTest {

    @Test
    fun `a legacy account signs in, and the session names the server that issued it`() = runTest {
        val home = FakeGateway(signIn = refusal())
        val legacy = FakeGateway(signIn = AppResult.Success(session(MarketPlatform.COINEPRO_FX)))
        val gateway = FederatedEmailAuthGateway(home, listOf(legacy))

        val result = gateway.signIn("reader@example.com", "password")

        assertTrue(result is AppResult.Success)
        assertEquals(
            "The token must be filed against the backend that minted it.",
            MarketPlatform.COINEPRO_FX,
            (result as AppResult.Success).value.platform,
        )
        assertEquals(1, home.signInCalls)
        assertEquals(1, legacy.signInCalls)
    }

    @Test
    fun `home answering first means the other server is never asked`() = runTest {
        val home = FakeGateway(signIn = AppResult.Success(session(MarketPlatform.TRADEYAR)))
        val legacy = FakeGateway(signIn = AppResult.Success(session(MarketPlatform.COINEPRO_FX)))
        val gateway = FederatedEmailAuthGateway(home, listOf(legacy))

        gateway.signIn("reader@example.com", "password")

        assertEquals(0, legacy.signInCalls)
    }

    /**
     * The narrow condition, and the reason it is narrow.
     *
     * Falling back sends the reader's password to a second host. Only the server saying *these
     * credentials are wrong* is evidence the account might live elsewhere; a timeout, a rate limit
     * and a 500 are all evidence of nothing.
     */
    @Test
    fun `nothing but a credential refusal reaches the other server`() = runTest {
        listOf(ErrorKind.NETWORK, ErrorKind.RATE_LIMIT, ErrorKind.SERVER, ErrorKind.VALIDATION)
            .forEach { kind ->
                val home = FakeGateway(signIn = AppResult.Failure(kind, "no"))
                val legacy = FakeGateway(signIn = AppResult.Success(session(MarketPlatform.COINEPRO_FX)))

                FederatedEmailAuthGateway(home, listOf(legacy)).signIn("a@b.com", "p")

                assertEquals("$kind must not spread the password", 0, legacy.signInCalls)
            }
    }

    @Test
    fun `both refusing reports home's wording, not the last server tried`() = runTest {
        val home = FakeGateway(signIn = refusal("home says no"))
        val legacy = FakeGateway(signIn = refusal("legacy says no"))

        val result = FederatedEmailAuthGateway(home, listOf(legacy)).signIn("a@b.com", "p")

        assertEquals("home says no", (result as AppResult.Failure).message)
    }

    /** A new account belongs to one server. Creating it twice is not a fallback. */
    @Test
    fun `registration never federates`() = runTest {
        val home = FakeGateway()
        val legacy = FakeGateway()
        val gateway = FederatedEmailAuthGateway(home, listOf(legacy))

        gateway.startRegistration("a@b.com", "p", "Reader")
        gateway.verifyRegistration("token", "123456")

        assertEquals(1, home.registerCalls)
        assertEquals(1, home.verifyCalls)
        assertEquals(0, legacy.registerCalls)
        assertEquals(0, legacy.verifyCalls)
    }

    /**
     * Recovery goes everywhere, because the route cannot say where the account is.
     *
     * Both servers answer a forgotten-password request identically whether or not the address is
     * registered — deliberately, so it cannot be used to test for accounts. Asking only home would
     * therefore look like it worked while sending nothing to somebody whose account is legacy.
     */
    @Test
    fun `a forgotten password is asked of every server`() = runTest {
        val home = FakeGateway()
        val legacy = FakeGateway()

        FederatedEmailAuthGateway(home, listOf(legacy)).requestPasswordReset("a@b.com")

        assertEquals(1, home.resetRequests)
        assertEquals(1, legacy.resetRequests)
    }

    private fun refusal(message: String = "ایمیل یا رمز عبور درست نیست.") =
        AppResult.Failure(ErrorKind.AUTH, message)

    private fun session(platform: MarketPlatform) = EmailAuthSession(
        tokens = AuthTokens("access", "refresh"),
        profile = UserProfile(telegramId = 0, name = "Reader"),
        platform = platform,
    )
}

private class FakeGateway(
    private val signIn: AppResult<EmailAuthSession> = AppResult.Failure(ErrorKind.AUTH, "no"),
) : EmailAuthGateway {
    var signInCalls = 0
    var registerCalls = 0
    var verifyCalls = 0
    var resetRequests = 0

    override suspend fun methods() = AppResult.Success(AuthMethods(emailPassword = true))

    override suspend fun startRegistration(
        email: String,
        password: String,
        fullName: String,
    ): AppResult<RegistrationChallenge> {
        registerCalls++
        return AppResult.Success(RegistrationChallenge("reg", null))
    }

    override suspend fun verifyRegistration(
        registrationToken: String,
        code: String,
    ): AppResult<EmailAuthSession> {
        verifyCalls++
        return signIn
    }

    override suspend fun signIn(email: String, password: String): AppResult<EmailAuthSession> {
        signInCalls++
        return signIn
    }

    override suspend fun signInWithGoogle(idToken: String): AppResult<EmailAuthSession> {
        signInCalls++
        return signIn
    }

    override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
        resetRequests++
        return AppResult.Success(Unit)
    }

    override suspend fun resetPassword(resetToken: String, newPassword: String): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun refresh(refreshToken: String): AppResult<AuthTokens> =
        AppResult.Success(AuthTokens("access", "refresh"))

    override suspend fun signOut(refreshToken: String): AppResult<Unit> = AppResult.Success(Unit)
}
