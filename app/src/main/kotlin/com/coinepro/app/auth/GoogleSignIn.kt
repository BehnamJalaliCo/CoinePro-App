package com.coinepro.app.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.coinepro.core.auth.GoogleSignInAttempt
import com.coinepro.core.auth.GoogleSignInRefusal
import com.coinepro.core.auth.googleSignInRefusal
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

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

    /**
     * Google will not mint a token for this build, or the phone has no Google account.
     *
     * Its own outcome rather than a [Failed] carrying a sentence, because the two callers want
     * different things from it: one shows copy, and the *host* — which is the only thing that knows
     * this install's package name and signing fingerprint — can say what to register and where.
     * That turns the one refusal nobody can act on into the one refusal anybody can fix in a
     * minute. See `AppIntegrity.fingerprints`.
     */
    data object Unregistered : GoogleSignInOutcome

    /** [message] is what to tell the reader — see `googleSignInRefusal`; it is not always Google's. */
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

    /**
     * Asks Google who this is, and asks twice before believing a no.
     *
     * The first ask is [GoogleSignInAttempt.ONE_TAP], because for a reader who has signed in before
     * it is the better experience by a wide margin: the sheet names their account and there is no
     * picker to wade through. Its cost is that Play services answers it out of state the app cannot
     * see, and on the first ask of a fresh install that state is not there yet — so it throws, and
     * the app used to hand that straight to the reader as a failure.
     *
     * It was never a no. Tapping again worked, every time, because the first ask is what put the
     * state there. That is a retry, and a retry the app can perform is not one to make the reader
     * perform — least of all with an error message in between telling them the build is broken.
     *
     * So the second ask happens here, inside the one tap, and it is a different question:
     * [GoogleSignInAttempt.ACCOUNT_PICKER] is the option meant for a button, and it opens the picker
     * without consulting any of that state. Only its refusal is the reader's to see. `core:auth`
     * owns the decision and is where the test for it lives.
     */
    suspend fun requestIdToken(serverClientId: String): GoogleSignInOutcome {
        val audience = serverClientId.trim()
        if (audience.isEmpty()) return GoogleSignInOutcome.Failed(null)
        val onPhone = askPlayServices(audience)
        // **The browser when Play services cannot answer (5.35.0).** In Iran, without a VPN, the
        // account sheet is refused by Google's side of Play services — the Bazaar build reported it —
        // while Google's own sign-in page opens in a browser like any other. So anything but a token
        // or the reader's own «cancel» is asked again there, with the same audience, and the answer
        // comes back through pro-chart.com's callback page. Only when that fails too is the phone's
        // answer the one the reader is shown.
        return when (onPhone) {
            is GoogleSignInOutcome.Token, GoogleSignInOutcome.Cancelled -> onPhone
            else -> when (val inBrowser = GoogleBrowserSignIn.request(context, audience)) {
                is GoogleSignInOutcome.Token, GoogleSignInOutcome.Cancelled -> inBrowser
                else -> onPhone
            }
        }
    }

    private suspend fun askPlayServices(audience: String): GoogleSignInOutcome {
        var attempt = GoogleSignInAttempt.ONE_TAP
        while (true) {
            when (val round = ask(attempt, audience)) {
                is Round.Done -> return round.outcome
                is Round.Refused -> when (
                    val refusal = googleSignInRefusal(attempt, round.type, round.message)
                ) {
                    is GoogleSignInRefusal.RetryWith ->
                        // A retry that does not change the question is an app asking Google the
                        // same thing for ever. The decision never returns one; this loop does not
                        // take its word for it.
                        if (refusal.attempt == attempt) {
                            return GoogleSignInOutcome.Failed(round.message)
                        } else {
                            attempt = refusal.attempt
                        }

                    GoogleSignInRefusal.Misconfigured -> return GoogleSignInOutcome.Unregistered

                    GoogleSignInRefusal.PassThrough ->
                        return GoogleSignInOutcome.Failed(round.message)
                }
            }
        }
    }

    /**
     * One round trip: either something to report, or a refusal for `core:auth` to weigh.
     *
     * Cancellation is caught by type rather than by reading the refusal, and finishes the tap here.
     * The reader closing the sheet is a decision; retrying it would reopen the sheet they just shut,
     * which is the one outcome worse than the message this change removes.
     */
    private suspend fun ask(attempt: GoogleSignInAttempt, audience: String): Round = try {
        val response = CredentialManager.create(context).getCredential(
            context = context,
            request = GetCredentialRequest.Builder()
                .addCredentialOption(option(attempt, audience))
                .build(),
        )
        Round.Done(read(response.credential))
    } catch (_: GetCredentialCancellationException) {
        Round.Done(GoogleSignInOutcome.Cancelled)
    } catch (error: GetCredentialException) {
        Round.Refused(error.type, error.errorMessage?.toString())
    }

    /**
     * The two shapes of the same question.
     *
     * `setFilterByAuthorizedAccounts(false)` stays on the One Tap option and is still right — with
     * it true the sheet is empty for every new reader. It was never the cause of the double tap,
     * though: the option itself consults prior state whatever the filter says, and that is what the
     * picker below is for.
     */
    private fun option(attempt: GoogleSignInAttempt, audience: String): CredentialOption =
        when (attempt) {
            GoogleSignInAttempt.ONE_TAP -> GetGoogleIdOption.Builder()
                .setServerClientId(audience)
                .setFilterByAuthorizedAccounts(false)
                .build()

            GoogleSignInAttempt.ACCOUNT_PICKER -> GetSignInWithGoogleOption.Builder(audience).build()
        }

    /** Both options answer with a Google ID token credential, so both are read the same way. */
    private fun read(credential: Credential): GoogleSignInOutcome =
        if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
                .takeIf { it.isNotBlank() }
                ?.let(GoogleSignInOutcome::Token)
                ?: GoogleSignInOutcome.Failed(null)
        } else {
            // Something other than a Google credential came back. Nothing here can use it, and
            // guessing would mean sending the server something it did not ask for.
            GoogleSignInOutcome.Failed(null)
        }

    private sealed interface Round {
        data class Done(val outcome: GoogleSignInOutcome) : Round

        data class Refused(val type: String, val message: String?) : Round
    }
}

/**
 * Google's sign-in page in the browser, answered through `pro-chart.com/terminal/google-callback.html`
 * (5.35.0) — the page the web build already uses, registered as a redirect for the same web client.
 *
 * ### How the token gets back, and why nothing else can take it
 *
 * The callback page cannot `postMessage` to an app, so it hands the token back as an `intent://`
 * link pinned to this package: Chrome resolves it to this app only, and the token rides in an
 * extra, never in a URI another app could register for. The page is told the package and a one-off
 * [state] through Google's own `state` parameter, and an answer is accepted only when it carries the
 * state this attempt made — so a link crafted elsewhere cannot sign a reader into somebody else's
 * account.
 *
 * ### When the reader simply comes back
 *
 * Returning to the app without an answer — the back button, a closed tab — is a cancel, decided a
 * moment after the app is resumed so an answer arriving with that very resume still wins.
 */
object GoogleBrowserSignIn {
    /** The callback the web build registered for the web client; the app reuses it. */
    private const val REDIRECT = "https://pro-chart.com/terminal/google-callback.html"
    private const val AUTH = "https://accounts.google.com/o/oauth2/v2/auth"

    /** The host the callback page's intent carries. Declared in the manifest. */
    const val RETURN_HOST = "google-signin"
    private const val EXTRA_TOKEN = "id_token"
    private const val EXTRA_STATE = "state"
    private const val EXTRA_ERROR = "error"
    private const val RESUME_GRACE_MS = 1_500L

    private class Pending(val state: String, val answer: CompletableDeferred<GoogleSignInOutcome>) {
        @Volatile var left = false
    }

    @Volatile private var pending: Pending? = null
    private val random = SecureRandom()

    suspend fun request(context: Context, audience: String): GoogleSignInOutcome {
        val state = ByteArray(18).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val nonce = ByteArray(12).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val attempt = Pending(state, CompletableDeferred())
        pending?.answer?.complete(GoogleSignInOutcome.Cancelled)
        pending = attempt
        val url = Uri.parse(AUTH).buildUpon()
            .appendQueryParameter("response_type", "id_token")
            .appendQueryParameter("prompt", "select_account")
            .appendQueryParameter("scope", "openid email profile")
            .appendQueryParameter("client_id", audience)
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("nonce", nonce)
            .appendQueryParameter("state", "app:${context.packageName}:$state")
            .build()
        val opened = try {
            context.startActivity(
                // Its own task, always: the callback's intent then brings this app's task forward and
                // reaches the activity already waiting (singleTop) instead of a second copy of it
                // stacked over the browser.
                Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
        if (!opened) {
            pending = null
            return GoogleSignInOutcome.Failed(null)
        }
        return try {
            attempt.answer.await()
        } finally {
            if (pending === attempt) pending = null
        }
    }

    /** The app went to the browser. Called from the activity's `onPause`. */
    fun onLeft() {
        pending?.left = true
    }

    /** The app is back. A pending attempt with no answer by then is the reader's cancel. */
    suspend fun onReturned() {
        val attempt = pending?.takeIf { it.left } ?: return
        delay(RESUME_GRACE_MS)
        attempt.answer.complete(GoogleSignInOutcome.Cancelled)
    }

    /**
     * The callback page's answer, if [intent] is one. True when it was — the caller reads nothing
     * else from it. An answer whose state is not this attempt's is dropped, not reported.
     */
    fun consume(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        if (uri.scheme != "coinepro" || uri.host != RETURN_HOST) return false
        val attempt = pending ?: return true
        if (intent.getStringExtra(EXTRA_STATE) != attempt.state) return true
        val token = intent.getStringExtra(EXTRA_TOKEN)?.takeIf { it.isNotBlank() }
        val error = intent.getStringExtra(EXTRA_ERROR)
        attempt.answer.complete(
            when {
                token != null -> GoogleSignInOutcome.Token(token)
                error == "access_denied" -> GoogleSignInOutcome.Cancelled
                else -> GoogleSignInOutcome.Failed(error)
            },
        )
        return true
    }
}

/*
 * What used to live here was `explain`: one pass over Credential Manager's wording, deciding on the
 * spot whether to show it. It is `googleSignInRefusal` in `core:auth` now, and it takes one more
 * argument — which attempt was refused — because that argument is the whole bug. The same
 * `TYPE_NO_CREDENTIAL` means "ask again properly" the first time and "there is genuinely nothing
 * here" the second, and a function that could not see the difference had to guess, and guessed
 * wrong on the tap that mattered.
 *
 * The copy it selects is unchanged and still names two causes, because Google reports them
 * identically: the app's signing certificate is not registered against the Google Cloud project
 * that issued the audience, or the phone has no Google account on it. `docs/PLAY_LISTING.md` carries
 * the console side, and «ایمنی و نسخه» in the app shows the fingerprint the console needs.
 */

/*
 * It *was* deliberately not a string resource, on the argument that a build-configuration fault is
 * for whoever installs the app rather than whoever uses it. That argument was wrong in practice:
 * this is a shipped release, a Persian reader is the one looking at it, and English inside an RTL
 * message box does not read as a note to a developer — it reads as the app being broken, with the
 * full stop rendered in the wrong place for good measure. So it is Persian now, it says what a
 * reader can actually do, and it keeps one clause of cause because the person testing this build is
 * the one who can fix it.
 */
