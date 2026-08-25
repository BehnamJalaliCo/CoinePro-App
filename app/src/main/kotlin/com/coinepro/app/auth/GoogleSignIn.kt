package com.coinepro.app.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
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

    /** [message] is Google's own wording where it gave one, and is shown as written. */
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
            GoogleSignInOutcome.Failed(error.errorMessage?.toString())
        }
    }
}
