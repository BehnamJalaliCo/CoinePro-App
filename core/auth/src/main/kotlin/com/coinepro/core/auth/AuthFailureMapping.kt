package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind

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
        ErrorKind.NETWORK, ErrorKind.SERVER, ErrorKind.UNKNOWN -> AuthFailureReason.UNREACHABLE
    },
    // Only a real verdict carries wording worth repeating. A timeout's exception text is a
    // description of the app's own plumbing, and putting it on screen in the server's place would
    // tell the reader something about their credentials that nobody checked.
    message = message?.takeIf {
        it.isNotBlank() && kind != ErrorKind.NETWORK && kind != ErrorKind.UNKNOWN
    },
    retryAfterSeconds = retryAfterSeconds,
)
