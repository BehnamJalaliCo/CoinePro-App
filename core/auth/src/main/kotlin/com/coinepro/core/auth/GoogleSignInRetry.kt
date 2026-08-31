package com.coinepro.core.auth

/**
 * Which shape of Google request an attempt used.
 *
 * Credential Manager has two of them and they are not interchangeable, which is the whole of this
 * file's reason to exist.
 *
 * [ONE_TAP] is `GetGoogleIdOption`. It is the *automatic* flow: Play services decides, from state
 * the app cannot see, whether it has anything worth showing — which accounts are on the phone, which
 * of them have used this app before, whether the reader dismissed the sheet recently and is inside
 * the cool-off that follows. When it decides it has nothing, it does not draw a sheet; it throws.
 *
 * [ACCOUNT_PICKER] is `GetSignInWithGoogleOption`. It is the *button* flow: the reader asked for it
 * by name, so it always opens the full picker and consults none of that state.
 *
 * The app draws a button — «ادامه با گوگل» — and asked for the automatic flow. That mismatch is the
 * bug: the first tap consults state that has not been established yet, is refused, and the refusal
 * is shown to the reader as though sign-in were broken. Tapping again works, because by then the
 * first attempt has left Play services in a state where it will show the sheet. The reader is doing
 * the retry by hand, and getting an error message for their trouble the first time.
 */
enum class GoogleSignInAttempt {
    /** `GetGoogleIdOption`. Silent for a returning reader, which is why it is still tried first. */
    ONE_TAP,

    /** `GetSignInWithGoogleOption`. Always draws the picker; consults no prior state. */
    ACCOUNT_PICKER,
}

/**
 * What the app should do about a Credential Manager refusal.
 *
 * Deliberately not "an error message or null": the first refusal is usually not something to tell
 * the reader at all, and modelling it as a message forces the caller to decide whether to show it at
 * the one place that has the least information.
 */
sealed interface GoogleSignInRefusal {
    /** Not the reader's problem yet. Ask again, in the same tap, with [attempt]. */
    data class RetryWith(val attempt: GoogleSignInAttempt) : GoogleSignInRefusal

    /**
     * Google will not mint a token for this build, or there is no Google account on the phone.
     * The two arrive identically and the app's own copy names both — see `auth_google_not_registered`.
     */
    data object Misconfigured : GoogleSignInRefusal

    /** Google's own wording describes the reader's situation. Show it as written. */
    data object PassThrough : GoogleSignInRefusal
}

/**
 * Decides what a refusal means, given which attempt produced it.
 *
 * Split out of the Android call site and given a test because it is the half that can be wrong
 * without anybody noticing: the call site either shows a sheet or does not, and a device tells you
 * within a second. Whether a *first* refusal is worth reporting is a judgement, it is made once per
 * tap, and getting it wrong is exactly the shipped bug — the reader is shown «حساب گوگلی…» copy for
 * a condition that clears itself if you simply ask again.
 *
 * The rule for [GoogleSignInAttempt.ONE_TAP] is unconditional, and that is on purpose. Play services
 * reports "no account has authorised this app yet", "the reader dismissed this sheet an hour ago"
 * and "this build's certificate is not registered" through overlapping exception types and free
 * text; there is no way from inside the app to tell the first two — which the picker fixes — from
 * the third, which it does not. So the first refusal is never shown. The cost of being wrong is one
 * extra call inside a tap the reader already made; the cost of the alternative is the bug.
 *
 * Cancellation never reaches here. The reader closing the sheet is a decision, not a refusal, and it
 * is caught by type at the call site — retrying it would reopen the sheet they just dismissed, which
 * is the one outcome worse than the error message this file removes.
 *
 * @param type the exception's `type` string, e.g. `android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL`.
 * @param message the exception's `errorMessage`, which is Play services' free text and may be absent.
 */
fun googleSignInRefusal(
    attempt: GoogleSignInAttempt,
    type: String?,
    message: String?,
): GoogleSignInRefusal = when (attempt) {
    GoogleSignInAttempt.ONE_TAP ->
        GoogleSignInRefusal.RetryWith(GoogleSignInAttempt.ACCOUNT_PICKER)

    GoogleSignInAttempt.ACCOUNT_PICKER ->
        if (looksMisconfigured(type, message)) {
            GoogleSignInRefusal.Misconfigured
        } else {
            GoogleSignInRefusal.PassThrough
        }
}

/**
 * The markers that mean "Google had nothing to give this build".
 *
 * Substrings of the type and of the free text together, because Play services puts the useful part
 * in whichever of the two it feels like: the type carries `TYPE_NO_CREDENTIAL`, and the status code
 * — `10:` for an unregistered certificate — only ever appears in the message, appended to a sentence
 * that names the developer console.
 *
 * `10:` is matched with its colon so that it cannot catch a `10` inside a timestamp or an id.
 */
private val MISCONFIGURED_MARKERS = listOf(
    "developer console",
    "10:",
    "no credentials available",
    "type_no_credential",
)

private fun looksMisconfigured(type: String?, message: String?): Boolean {
    val text = (message.orEmpty() + " " + type.orEmpty()).lowercase()
    return MISCONFIGURED_MARKERS.any { it in text }
}
