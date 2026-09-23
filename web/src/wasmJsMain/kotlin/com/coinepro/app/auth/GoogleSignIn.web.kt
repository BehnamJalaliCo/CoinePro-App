@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.app.auth

import android.content.Context
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/*
 * Google sign-in, in a browser — the phone's `GoogleSignIn.kt` with Google Identity Services in
 * place of Credential Manager. The same two asks, in the same order and for the same reason: One
 * Tap first, for a reader Google already knows, then the account chooser (Google's own popup,
 * `response_type=id_token`) when One Tap declines to show. The audience is still the server's
 * client id from `auth/methods`; the token is still handed to the server unread.
 *
 * What a browser adds is that the page's origin has to be registered on that client id in Google's
 * console. Google says so when it is not — `unregistered_origin` — and that is reported as
 * [GoogleSignInOutcome.Unregistered], exactly the outcome the phone reports for an unregistered
 * package, so the same screen tells the owner the same thing: register this, here.
 */

sealed interface GoogleSignInOutcome {
    data class Token(val idToken: String) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data object Unregistered : GoogleSignInOutcome
    data class Failed(val message: String?) : GoogleSignInOutcome
}

private fun oneTapJs(clientId: String, done: (String?, String?) -> Unit): Unit = js(
    """(function () {
        function run() {
            try {
                google.accounts.id.initialize({ client_id: clientId, auto_select: false, cancel_on_tap_outside: true,
                    use_fedcm_for_prompt: true, callback: function (r) { done(r && r.credential ? r.credential : null, null); } });
                google.accounts.id.prompt(function (n) {
                    if (n.isNotDisplayed && n.isNotDisplayed()) done(null, 'not_displayed:' + (n.getNotDisplayedReason ? n.getNotDisplayedReason() : ''));
                    else if (n.isSkippedMoment && n.isSkippedMoment()) done(null, 'skipped:' + (n.getSkippedReason ? n.getSkippedReason() : ''));
                    else if (n.isDismissedMoment && n.isDismissedMoment() && n.getDismissedReason && n.getDismissedReason() !== 'credential_returned') done(null, 'dismissed');
                });
            } catch (e) { done(null, 'error:' + e); }
        }
        if (window.google && google.accounts && google.accounts.id) { run(); return; }
        var s = document.createElement('script');
        s.src = 'https://accounts.google.com/gsi/client'; s.async = true;
        s.onload = run; s.onerror = function () { done(null, 'error:script'); };
        document.head.appendChild(s);
    })()""",
)

private fun popupJs(clientId: String, done: (String?, String?) -> Unit): Unit = js(
    """(function () {
        var nonce = Math.random().toString(36).slice(2) + Date.now().toString(36);
        var redirect = new URL('google-callback.html', document.baseURI).href;
        var url = 'https://accounts.google.com/o/oauth2/v2/auth?response_type=id_token&prompt=select_account'
            + '&scope=' + encodeURIComponent('openid email profile')
            + '&client_id=' + encodeURIComponent(clientId)
            + '&redirect_uri=' + encodeURIComponent(redirect)
            + '&nonce=' + nonce;
        var w = window.open(url, 'google-sign-in', 'width=480,height=640');
        if (!w) { done(null, 'error:popup_blocked'); return; }
        var settled = false;
        function finish(t, e) { if (settled) return; settled = true; window.removeEventListener('message', onMessage); clearInterval(poll); done(t, e); }
        function onMessage(ev) {
            if (ev.origin !== window.location.origin || !ev.data || ev.data.kind !== 'google-id-token') return;
            finish(ev.data.idToken || null, ev.data.error || null);
        }
        window.addEventListener('message', onMessage);
        var poll = setInterval(function () { if (w.closed) finish(null, 'cancelled'); }, 500);
    })()""",
)

private suspend fun ask(block: ((String?, String?) -> Unit) -> Unit): Pair<String?, String?> =
    suspendCancellableCoroutine { c -> block { token, error -> if (c.isActive) c.resume(token to error) } }

class GoogleSignInClient(@Suppress("UNUSED_PARAMETER") context: Context) {
    suspend fun requestIdToken(serverClientId: String): GoogleSignInOutcome {
        val audience = serverClientId.trim()
        if (audience.isEmpty()) return GoogleSignInOutcome.Failed(null)
        val (tapToken, tapError) = ask { oneTapJs(audience, it) }
        if (tapToken != null) return GoogleSignInOutcome.Token(tapToken)
        if (tapError?.contains("unregistered_origin") == true || tapError?.contains("invalid_client") == true) {
            return GoogleSignInOutcome.Unregistered
        }
        val (token, error) = ask { popupJs(audience, it) }
        return when {
            token != null -> GoogleSignInOutcome.Token(token)
            error == "cancelled" || error == "access_denied" -> GoogleSignInOutcome.Cancelled
            error?.contains("redirect_uri_mismatch") == true || error?.contains("invalid_client") == true ||
                error?.contains("unauthorized_client") == true -> GoogleSignInOutcome.Unregistered
            else -> GoogleSignInOutcome.Failed(null)
        }
    }
}
