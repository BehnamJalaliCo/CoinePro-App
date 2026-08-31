package com.coinepro.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tap that had to be made twice.
 *
 * «روی ورود با گوگل باید دوباره بزنی تا اجرا بشه» — the first «ادامه با گوگل» showed an error and the
 * second signed in. The error was Play services refusing the automatic flow before any account had
 * authorised this app, and the second tap worked because the first had already moved that state on.
 * The reader was performing the retry by hand.
 *
 * What is pinned here is the decision, not the sheet: a first refusal is never the reader's problem,
 * and a refusal from the picker — which consults no prior state and cannot be fixed by asking again
 * — is. Getting that backwards in either direction is the bug returning: one way the error comes
 * back, the other way a genuinely unregistered build retries for ever and says nothing.
 */
class GoogleSignInRetryTest {

    @Test
    fun `a first refusal is never shown to the reader`() {
        val verdict = googleSignInRefusal(
            attempt = GoogleSignInAttempt.ONE_TAP,
            type = "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL",
            message = "No credentials available",
        )

        assertEquals(GoogleSignInRefusal.RetryWith(GoogleSignInAttempt.ACCOUNT_PICKER), verdict)
    }

    @Test
    fun `a first refusal retries even when it reads like a misconfigured build`() {
        // The two are indistinguishable from inside the app, and the picker is the only way to find
        // out which it was. One extra call inside a tap the reader already made.
        val verdict = googleSignInRefusal(
            attempt = GoogleSignInAttempt.ONE_TAP,
            type = "androidx.credentials.TYPE_GET_CREDENTIAL_UNKNOWN",
            message = "During begin sign in, failure response from one tap: 10: Developer console is not set up correctly.",
        )

        assertEquals(GoogleSignInRefusal.RetryWith(GoogleSignInAttempt.ACCOUNT_PICKER), verdict)
    }

    @Test
    fun `a first refusal with nothing in it still retries`() {
        val verdict = googleSignInRefusal(GoogleSignInAttempt.ONE_TAP, type = null, message = null)

        assertEquals(GoogleSignInRefusal.RetryWith(GoogleSignInAttempt.ACCOUNT_PICKER), verdict)
    }

    @Test
    fun `the picker refusing for want of a credential is worth saying`() {
        val verdict = googleSignInRefusal(
            attempt = GoogleSignInAttempt.ACCOUNT_PICKER,
            type = "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL",
            message = null,
        )

        assertEquals(GoogleSignInRefusal.Misconfigured, verdict)
    }

    @Test
    fun `the console status code is read out of the free text, whatever the type says`() {
        val verdict = googleSignInRefusal(
            attempt = GoogleSignInAttempt.ACCOUNT_PICKER,
            type = "androidx.credentials.TYPE_GET_CREDENTIAL_UNKNOWN",
            message = "During begin sign in, failure response from one tap: 10: Developer console is not set up correctly.",
        )

        assertEquals(GoogleSignInRefusal.Misconfigured, verdict)
    }

    @Test
    fun `a bare ten is not a status code`() {
        // `10` without its colon is a length, an index or a timestamp. Matching it would put the
        // «ثبت نشده» copy on top of an unrelated failure and hide what Google actually said.
        val verdict = googleSignInRefusal(
            attempt = GoogleSignInAttempt.ACCOUNT_PICKER,
            type = "androidx.credentials.TYPE_GET_CREDENTIAL_UNKNOWN",
            message = "Timed out after 10 seconds",
        )

        assertEquals(GoogleSignInRefusal.PassThrough, verdict)
    }

    @Test
    fun `anything that describes the reader's own situation is passed through as written`() {
        val verdict = googleSignInRefusal(
            attempt = GoogleSignInAttempt.ACCOUNT_PICKER,
            type = "androidx.credentials.TYPE_GET_CREDENTIAL_INTERRUPTED",
            message = "خطای شبکه",
        )

        assertEquals(GoogleSignInRefusal.PassThrough, verdict)
    }

    @Test
    fun `the markers are matched however Play services capitalises them`() {
        val verdict = googleSignInRefusal(
            attempt = GoogleSignInAttempt.ACCOUNT_PICKER,
            type = "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL",
            message = "NO CREDENTIALS AVAILABLE",
        )

        assertEquals(GoogleSignInRefusal.Misconfigured, verdict)
    }
}
