package com.coinepro.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.coinepro.app.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * What came back from asking Google who this is.
 *
 * Three outcomes rather than a nullable token, because they are three different things to a reader.
 * A cancellation is not a failure and must not be reported as one — the reader closed the sheet on
 * purpose, and an error message would tell them something went wrong when nothing did.
 */
sealed interface GoogleSignInOutcome {
    /** An ID token to hand to the server. The app never reads its claims; only the server verifies. */
    data class Token(val idToken: String) : GoogleSignInOutcome

    data object Cancelled : GoogleSignInOutcome

    /** [message] is what to tell the reader. See `explain` for why it is not always Google's. */
    data class Failed(val message: String?) : GoogleSignInOutcome
}

/**
 * Google sign-in through Credential Manager.
 *
 * Credential Manager rather than the older `GoogleSignInClient`: that one is deprecated, and this
 * is what shows the account sheet on a modern device without the app handling an activity result.
 *
 * The audience is the server's own client id, taken from `auth/methods` rather than compiled in.
 * That matters for more than tidiness — the two backends are separate deployments with separate
 * Google configuration, and a token minted for one has an `aud` the other will refuse. Asking the
 * server which audience it expects is the only way one app can sign in to both.
 */
class GoogleSignInClient(private val context: Context) {

    suspend fun requestIdToken(serverClientId: String): GoogleSignInOutcome {
        val audience = serverClientId.trim()
        if (audience.isEmpty()) return GoogleSignInOutcome.Failed(null)

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(audience)
            // False, so the sheet also offers accounts that have never used this app. Limiting it
            // to previously authorised ones shows an empty sheet to every new reader, which looks
            // like sign-in being broken rather than like a first visit.
            .setFilterByAuthorizedAccounts(false)
            .build()

        return try {
            val response = CredentialManager.create(context).getCredential(
                context = context,
                request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
            val credential = response.credential
            if (
                credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                token.takeIf { it.isNotBlank() }
                    ?.let(GoogleSignInOutcome::Token)
                    ?: GoogleSignInOutcome.Failed(null)
            } else {
                // Something other than a Google credential came back. Nothing here can use it, and
                // guessing would mean sending the server something it did not ask for.
                GoogleSignInOutcome.Failed(null)
            }
        } catch (_: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (error: GetCredentialException) {
            GoogleSignInOutcome.Failed(explain(context, error))
        }
    }
}

/**
 * What to show a reader when Credential Manager refuses.
 *
 * Google's own wording is passed through for anything that plainly describes the reader's
 * situation. One family is replaced, and the replacement has to name **two** causes rather than
 * one, because Google reports them with the same exception and there is no way from inside the app
 * to tell them apart:
 *
 *  * the app's signing certificate is not registered against the Google Cloud project that issued
 *    the audience, so Google will not mint a token for this build whoever is signed in; or
 *  * the phone genuinely has no Google account on it.
 *
 * The first version of this message named only the first cause. That was right while the console
 * was known to be missing an Android client and wrong the moment it is not: a reader with no Google
 * account would be told the app is broken, and would have no idea that adding an account fixes it.
 * Naming both is the only honest wording, and it costs nothing — the reader can check one and act
 * on the other.
 *
 * `docs/PLAY_LISTING.md` carries the console side, and «ایمنی و نسخه» in the app shows the
 * fingerprint the console needs.
 */
private fun explain(context: Context, error: GetCredentialException): String? {
    val text = (error.errorMessage?.toString().orEmpty() + " " + error.type).lowercase()
    val misconfigured = listOf(
        "developer console",
        "10:",
        "no credentials available",
        "type_no_credential",
    ).any { it in text }
    return if (misconfigured) {
        context.getString(R.string.auth_google_not_registered)
    } else {
        error.errorMessage?.toString()
    }
}

/*
 * It *was* deliberately not a string resource, on the argument that a build-configuration fault is
 * for whoever installs the app rather than whoever uses it. That argument was wrong in practice:
 * this is a shipped release, a Persian reader is the one looking at it, and English inside an RTL
 * message box does not read as a note to a developer — it reads as the app being broken, with the
 * full stop rendered in the wrong place for good measure. So it is Persian now, it says what a
 * reader can actually do, and it keeps one clause of cause because the person testing this build is
 * the one who can fix it.
 */
