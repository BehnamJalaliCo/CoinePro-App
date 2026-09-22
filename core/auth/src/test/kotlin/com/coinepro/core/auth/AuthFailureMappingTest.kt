package com.coinepro.core.auth

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import java.io.IOException
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The split that Cafe Bazaar's refusal of 4.88.0 asked for.
 *
 * The reviewer tapped «ورود یا ساخت حساب» and was shown «پاسخی نرسید. این یعنی درخواست بررسی
 * نشد» — a sentence that is true of a timeout and **false** of a 500. Both reached the same string,
 * so neither the reviewer nor anybody reading their report could tell which had happened, and the
 * two have opposite diagnoses: one is the reader's network, the other is ours.
 */
class AuthFailureMappingTest {

    @Test
    fun `a server fault is its own reason, and keeps the server's wording`() {
        val failure = AppResult.Failure(kind = ErrorKind.SERVER, message = "upstream timeout")
            .toAuthFailure()
        assertEquals(AuthFailureReason.SERVER_FAULT, failure.reason)
        assertEquals("upstream timeout", failure.message)
    }

    @Test
    fun `a network failure stays unreachable and carries no wording`() {
        val failure = AppResult.Failure(kind = ErrorKind.NETWORK, message = "timeout").toAuthFailure()
        assertEquals(AuthFailureReason.UNREACHABLE, failure.reason)
        // A timeout's text describes this app's own plumbing, not the reader's credentials.
        assertNull(failure.message)
    }

    @Test
    fun `an unknown failure stays unreachable`() {
        assertEquals(
            AuthFailureReason.UNREACHABLE,
            AppResult.Failure(kind = ErrorKind.UNKNOWN, message = "boom").toAuthFailure().reason,
        )
    }

    @Test
    fun `a pinned certificate the app refused is not a missing answer`() {
        val failure = AppResult.Failure(
            kind = ErrorKind.NETWORK,
            cause = SSLPeerUnverifiedException("Certificate pinning failure!"),
        ).toAuthFailure()
        assertEquals(AuthFailureReason.UNTRUSTED, failure.reason)
    }

    @Test
    fun `a pin failure wrapped by a retried route is still found`() {
        // OkHttp reports a failure on a retried route as an IOException with the real cause
        // underneath; reading only the outermost throwable would miss the one case this exists for.
        val wrapped = IOException(
            "Failed to connect",
            IOException("route", SSLPeerUnverifiedException("Certificate pinning failure!")),
        )
        assertEquals(
            AuthFailureReason.UNTRUSTED,
            AppResult.Failure(kind = ErrorKind.NETWORK, cause = wrapped).toAuthFailure().reason,
        )
    }

    @Test
    fun `a cause chain that loops does not hang the mapping`() {
        val outer = IOException("outer")
        val inner = IOException("inner")
        outer.initCause(inner)
        inner.initCause(outer)
        assertEquals(
            AuthFailureReason.UNREACHABLE,
            AppResult.Failure(kind = ErrorKind.NETWORK, cause = outer).toAuthFailure().reason,
        )
    }

    @Test
    fun `a refusal is still a refusal`() {
        val failure = AppResult.Failure(kind = ErrorKind.AUTH, message = "wrong password").toAuthFailure()
        assertEquals(AuthFailureReason.REJECTED, failure.reason)
        assertEquals("wrong password", failure.message)
    }
}
