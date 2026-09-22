package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Turns a transport failure into the one thing the sign-in screens act on.
 *
 * The distinction that matters here is between a refusal and a non-answer. A server that said no
 * has spent one of the reader's small number of attempts and its wording explains why; a request
 * that never arrived has spent nothing and has no wording at all. Collapsing the two would let the
 * app show a server's voice to a reader whose request the server never saw.
 */
internal fun AppResult.Failure.toAuthFailure(): AuthFailure = AuthFailure(
    reason = when (kind) {
        ErrorKind.AUTH -> AuthFailureReason.REJECTED
        ErrorKind.VALIDATION -> AuthFailureReason.INVALID
        ErrorKind.RATE_LIMIT -> AuthFailureReason.RATE_LIMITED
        // `SERVER` used to sit on this line, and that contradicted the paragraph above it: a 5xx
        // **is** an answer. The request arrived, it was read, and the fault is on the other side —
        // so «the request was not judged» was a false sentence the app told about itself.
        ErrorKind.SERVER -> AuthFailureReason.SERVER_FAULT
        // A pinned handshake the app itself rejected is an `IOException` like any other, so it
        // arrives here as `NETWORK` and used to read as «no answer». It is the opposite: the
        // server answered the handshake and **we** hung up. Only the cause can tell them apart.
        ErrorKind.NETWORK ->
            if (cause.isCertificateRefusal()) {
                AuthFailureReason.UNTRUSTED
            } else {
                AuthFailureReason.UNREACHABLE
            }
        // **`UNKNOWN` is not a network failure and never was.** It is the catch-all for a
        // `Throwable` that is neither an HTTP status nor an `IOException` — a body the app could
        // not parse, a field a server stopped sending, an NPE out of a Gson type whose non-null
        // declaration Gson never enforced. Every one of those means an answer *arrived* and this
        // side dropped it, which is the opposite of «the request was not judged». Certificate
        // refusal is checked first because OkHttp can surface one outside `IOException` too.
        ErrorKind.UNKNOWN ->
            if (cause.isCertificateRefusal()) {
                AuthFailureReason.UNTRUSTED
            } else {
                AuthFailureReason.UNREADABLE
            }
    },
    // Only a real verdict carries wording worth repeating. A timeout's exception text is a
    // description of the app's own plumbing, and putting it on screen in the server's place would
    // tell the reader something about their credentials that nobody checked.
    message = message?.takeIf {
        it.isNotBlank() && kind != ErrorKind.NETWORK && kind != ErrorKind.UNKNOWN
    },
    retryAfterSeconds = retryAfterSeconds,
)

/**
 * Whether this failure is the app refusing a certificate rather than the network failing.
 *
 * The chain is walked because OkHttp wraps: a pin mismatch on a retried route arrives as a
 * `RouteException`-shaped `IOException` with the real `SSLPeerUnverifiedException` underneath, and
 * reading only the outermost throwable would miss exactly the case this exists for. The walk is
 * bounded — a cause chain that loops is a library bug, not a reason to hang the sign-in screen.
 */
private fun Throwable?.isCertificateRefusal(): Boolean {
    var current = this
    var hops = 0
    while (current != null && hops < 8) {
        if (current is SSLPeerUnverifiedException || current is CertificateException) return true
        val next = current.cause
        if (next === current) return false
        current = next
        hops++
    }
    return false
}
