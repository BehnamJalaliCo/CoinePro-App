package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind

/**
 * One sign-in screen over two user tables.
 *
 * ### Why this exists
 *
 * CoinePro runs on two independent backends with two separate user tables. Until version 1.27.0 the
 * app registered and signed in against **CoinePro-FX**; from 1.27.0 it does both against
 * **TradeYar**, because that is where an account made in this app belongs — the mail is signed
 * *CoinePro* rather than *CoinePro Fx*, and the row is in the system that owns the account.
 *
 * That was the right change and it broke every account made before it. Those accounts are real,
 * their passwords are right, and TradeYar has never heard of them — so the server answers
 * `TYR-001 Auth Invalid Credentials`, and the reader is told, accurately from the server's point of
 * view and falsely from theirs, that their password is wrong. There is nothing they can do about
 * it: a password reset would be sent by the server that has no such user.
 *
 * So sign-in asks [home] first and, only when home says *these credentials are wrong*, asks each of
 * [legacy] in turn. A session that comes back carries the platform that issued it, so the caller
 * puts the token in the right session and the shell opens on the right backend.
 *
 * ### What deliberately does **not** federate
 *
 * **Registration.** A new account is made on [home] and nowhere else. Creating the same person in
 * two user tables is not a fallback, it is a second account they did not ask for, and it would put
 * the product right back where item three started.
 *
 * **Which server's capabilities the screen reads.** [methods] is home's. The screen draws the ways
 * in that home offers; a legacy sign-in is a recovery path, not a second product.
 *
 * ### The one thing worth being uncomfortable about
 *
 * A failed sign-in sends the reader's password to a second host. Both hosts are this product's own
 * and the credentials were already sent to one of them, so nothing leaves the product — but it is a
 * real second transmission and it is why the fallback is narrow: only on [ErrorKind.AUTH], which is
 * the server saying *wrong credentials* and nothing else. A network failure, a rate limit or a 500
 * stops at home, because none of those is evidence that the account lives elsewhere.
 */
class FederatedEmailAuthGateway(
    /** Where new accounts are made and where sign-in is tried first. TradeYar. */
    private val home: EmailAuthGateway,
    /** Older homes, tried in order when home refuses the credentials. CoinePro-FX. */
    private val legacy: List<EmailAuthGateway>,
) : EmailAuthGateway {

    override suspend fun methods(): AppResult<AuthMethods> = home.methods()

    override suspend fun startRegistration(
        email: String,
        password: String,
        fullName: String,
    ): AppResult<RegistrationChallenge> = home.startRegistration(email, password, fullName)

    override suspend fun verifyRegistration(
        registrationToken: String,
        code: String,
    ): AppResult<EmailAuthSession> = home.verifyRegistration(registrationToken, code)

    override suspend fun signIn(email: String, password: String): AppResult<EmailAuthSession> =
        firstThatAccepts { it.signIn(email, password) }

    override suspend fun signInWithGoogle(idToken: String): AppResult<EmailAuthSession> =
        firstThatAccepts { it.signInWithGoogle(idToken) }

    /**
     * Recovery goes to **every** backend, and the result reported is home's.
     *
     * The route answers identically whether or not the address is registered — that is deliberate
     * on both servers, so it cannot be used to test for accounts — which means asking home alone
     * would look like it worked and quietly send nothing to somebody whose account is legacy. The
     * cost of asking both is that a reader with an account on each gets two emails; the cost of
     * asking one is that a reader with a legacy account gets none and is told to check their inbox.
     */
    override suspend fun requestPasswordReset(email: String): AppResult<Unit> {
        val result = home.requestPasswordReset(email)
        legacy.forEach { it.requestPasswordReset(email) }
        return result
    }

    /**
     * The code came from one of the emails above and only its own server will accept it, so this
     * tries each in turn. A wrong code is refused by all of them and the reader sees home's reason.
     */
    override suspend fun resetPassword(
        resetToken: String,
        newPassword: String,
    ): AppResult<Unit> {
        val first = home.resetPassword(resetToken, newPassword)
        if (first is AppResult.Success || !first.isCredentialRefusal()) return first
        legacy.forEach { gateway ->
            val next = gateway.resetPassword(resetToken, newPassword)
            if (next is AppResult.Success) return next
        }
        return first
    }

    /**
     * Neither of these federates, and neither is reached in practice.
     *
     * A refresh and a sign-out are done by the [SessionController] that holds the token, which
     * already knows its own platform. They are here because the interface has them.
     */
    override suspend fun refresh(refreshToken: String): AppResult<AuthTokens> =
        home.refresh(refreshToken)

    override suspend fun signOut(refreshToken: String): AppResult<Unit> = home.signOut(refreshToken)

    private suspend fun firstThatAccepts(
        attempt: suspend (EmailAuthGateway) -> AppResult<EmailAuthSession>,
    ): AppResult<EmailAuthSession> {
        val first = attempt(home)
        if (first is AppResult.Success || !first.isCredentialRefusal()) return first
        legacy.forEach { gateway ->
            val next = attempt(gateway)
            if (next is AppResult.Success) return next
        }
        // Home's refusal, not the last one tried: home is the server the reader thinks they are
        // talking to, and its wording is the one the rest of the screen is written against.
        return first
    }
}

/** The server saying *wrong credentials* — a 401 or a 403 — and nothing else. */
private fun AppResult<*>.isCredentialRefusal(): Boolean =
    this is AppResult.Failure && kind == ErrorKind.AUTH
