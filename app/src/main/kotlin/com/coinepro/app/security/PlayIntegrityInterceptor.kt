package com.coinepro.app.security

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Response

/**
 * A Play Integrity verdict on the three requests that move money or credentials.
 *
 * ### What it does
 *
 * For a request whose path is one of [GATED_PATHS] — sign-in, saving an exchange's keys, executing
 * a signal — it asks Play for an integrity token bound to a nonce and sends the token in
 * `X-Play-Integrity` and the nonce in `X-Play-Integrity-Nonce`. The backend decodes the token with
 * Google, checks the nonce is the one it can recompute for this request, and decides. Everything
 * else on the wire is untouched.
 *
 * ### What it does not do
 *
 * It does not refuse. A token Play would not issue — no Play Services, an emulator, a network
 * that cannot reach Google — sends the request without the headers, and the backend's policy
 * decides what an unattested sign-in is worth. Refusing here would lock out every reader whose
 * phone has no Play, which on this app's market is not a corner case, and the signature check
 * (`ExpectedSigners`) is the second layer that stays.
 *
 * ### The nonce
 *
 * SHA-256 of the method, the path and the minute, base64url without padding — so the backend can
 * recompute it, so a token cannot be replayed onto another route, and so a captured one goes
 * stale in a minute. See `docs/security/INTEGRITY.md` for the contract.
 *
 * Off entirely when [cloudProject] is zero, which is every build without
 * `COINEPRO_PLAY_INTEGRITY_PROJECT`.
 */
class PlayIntegrityInterceptor(
    context: Context,
    private val cloudProject: Long,
    private val now: () -> Long = System::currentTimeMillis,
) : Interceptor {

    private val manager: IntegrityManager by lazy { IntegrityManagerFactory.create(context.applicationContext) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (cloudProject <= 0L || !isGated(request.method, request.url.encodedPath)) {
            return chain.proceed(request)
        }
        val nonce = nonceFor(request.method, request.url.encodedPath, now())
        val token = runCatching {
            Tasks.await(
                manager.requestIntegrityToken(
                    IntegrityTokenRequest.builder()
                        .setNonce(nonce)
                        .setCloudProjectNumber(cloudProject)
                        .build(),
                ),
                TOKEN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            ).token()
        }.getOrNull()
        if (token.isNullOrBlank()) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header(HEADER_TOKEN, token)
                .header(HEADER_NONCE, nonce)
                .build(),
        )
    }

    companion object {
        const val HEADER_TOKEN = "X-Play-Integrity"
        const val HEADER_NONCE = "X-Play-Integrity-Nonce"

        /** The routes that carry a verdict. Matched on the path's tail so both platforms' prefixes fit. */
        internal val GATED_PATHS = listOf("/login", "/execution/connections", "/executions", "/venues/lbank")

        private const val TOKEN_TIMEOUT_SECONDS = 10L
        private const val MINUTE_MILLIS = 60_000L

        /** Only a write to one of the gated paths: a GET of the connection list is not an attestation. */
        internal fun isGated(method: String, path: String): Boolean {
            val writes = method == "POST" || method == "PUT" || method == "PATCH"
            return writes && GATED_PATHS.any { gated -> path.trimEnd('/').endsWith(gated) }
        }

        /** SHA-256 of `METHOD path minute`, base64url, no padding: recomputable, route-bound, short-lived. */
        internal fun nonceFor(method: String, path: String, nowMillis: Long): String {
            val minute = nowMillis / MINUTE_MILLIS
            val digest = MessageDigest.getInstance("SHA-256").digest("$method $path $minute".toByteArray())
            return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
    }
}
