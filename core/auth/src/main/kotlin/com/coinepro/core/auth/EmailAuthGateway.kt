package com.coinepro.core.auth

import com.coinepro.core.common.AppResult

/**
 * Every call the email-first identity flow makes, stated in domain terms.
 *
 * Written as an interface first on purpose. The backend reports its real paths and JSON keys when a
 * part is finished, and writing DTOs before that report arrives means writing them against a
 * document rather than against the running server — the exact mistake the server prompt was built
 * to prevent. Everything above this line can be finished and tested now; only the implementation
 * waits.
 *
 * Two rules hold for every method. A failure carries the server's own message untouched, because
 * the app cannot know why a particular server refused and must not narrate on its behalf. And
 * nothing here ever reports success it did not receive — a request that did not reach a verdict is
 * [AuthFailureReason.UNREACHABLE], never an optimistic sign-in.
 */
interface EmailAuthGateway {
    /** Which ways in this deployment offers, so the app draws only the ones that can work. */
    suspend fun methods(): AppResult<AuthMethods>

    /**
     * Begins registration and sends a verification code.
     *
     * Succeeds identically whether or not the address is already registered. That is the server's
     * design and the app must preserve it: a client that shortcut the flow for a known address
     * would rebuild the account-existence oracle the server went to the trouble of removing.
     */
    suspend fun startRegistration(
        email: String,
        password: String,
        fullName: String,
    ): AppResult<RegistrationChallenge>

    /** Completes registration with the emailed code, returning a session. */
    suspend fun verifyRegistration(
        registrationToken: String,
        code: String,
    ): AppResult<EmailAuthSession>

    /** Requests another code for a registration already in progress. */
    suspend fun resendRegistrationCode(registrationToken: String): AppResult<RegistrationChallenge>

    suspend fun signIn(email: String, password: String): AppResult<EmailAuthSession>

    /** Verifies a Google ID token server-side. The app never trusts the token's own claims. */
    suspend fun signInWithGoogle(idToken: String): AppResult<EmailAuthSession>

    /** Starts password recovery. Reports the same result for every address, for the reason above. */
    suspend fun requestPasswordReset(email: String): AppResult<Unit>

    /**
     * Completes password recovery.
     *
     * The token comes either from the verified App Link the recovery email carries or from the
     * reader pasting it, and neither route is trusted here — the server is what decides whether it
     * is still valid, single-use and unexpired.
     */
    suspend fun resetPassword(resetToken: String, newPassword: String): AppResult<Unit>

    /** Exchanges a refresh token for a new pair. The old one is spent by this call. */
    suspend fun refresh(refreshToken: String): AppResult<AuthTokens>

    /** Revokes the refresh token server-side. Local state is cleared regardless of the outcome. */
    suspend fun signOut(refreshToken: String): AppResult<Unit>
}
