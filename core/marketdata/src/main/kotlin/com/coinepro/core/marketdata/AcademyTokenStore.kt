package com.coinepro.core.marketdata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * The second token CoinePro-FX's chart routes need.
 *
 * Every `/academy/…` route is scoped to an `AcademyStudent`, a different identity from the mobile
 * `User`, and
 * the app holds only the latter. `POST /user/academy-token` is the bridge their team built for
 * this: hand it the mobile bearer and it returns a twelve-hour academy-scoped token bound to the
 * same person — creating the student account on first use, or attaching to an existing one when
 * the verified email matches, so a reader who used the web academy keeps their progress.
 *
 * Held in memory only, and that is deliberate. It is a short-lived derived credential; writing it
 * to disk would add a second secret to protect for the sake of saving one request per twelve
 * hours. Signing out drops it with the process.
 *
 * (The route is written with an ellipsis rather than a wildcard star on purpose. Kotlin block
 * comments nest, so a slash-star sequence inside a KDoc opens a second comment that never closes
 * and swallows the rest of the file — with an error pointing at the last line, not the cause.)
 */
interface AcademyTokenStore {
    /** A valid token, minting or renewing one if the held one is missing or close to expiry. */
    suspend fun token(): String

    /** Forget the held token — on sign-out, or after a 401 says the server disagrees about it. */
    fun clear()
}

internal interface AcademyTokenApi {
    @POST("user/academy-token")
    suspend fun mint(): AcademyTokenDto
}

internal data class AcademyTokenDto(
    val token: String? = null,
    val expiresAt: String? = null,
    val studentId: Long? = null,
    val tier: String? = null,
)

/**
 * Thrown when the server has the feature switched off.
 *
 * `MOBILE_ACADEMY_TOKEN_ENABLED` is a flag their team added so this can be closed without touching
 * any route. It answers `403 {"code":"academy_disabled"}`, and the app should say the chart is
 * unavailable rather than that the reader is signed out — which is what a bare 403 would look like.
 */
class AcademyDisabledException : Exception("academy_disabled")

class NetworkAcademyTokenStore(
    retrofit: Retrofit,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AcademyTokenStore {

    private val api = retrofit.create(AcademyTokenApi::class.java)

    // One mint at a time. Without this, opening a chart and a symbol list together mints twice —
    // harmless server-side, since the route is idempotent, but two requests where one would do.
    private val lock = Mutex()

    @Volatile
    private var held: String? = null

    @Volatile
    private var expiresAtMillis: Long = 0

    /** The reader's academy tier, known only after a mint. Gates `chart/analyze`, not candles. */
    @Volatile
    var tier: String? = null
        private set

    override suspend fun token(): String {
        current()?.let { return it }
        return lock.withLock {
            // Checked again inside the lock: whoever was ahead has already minted one.
            current() ?: mint()
        }
    }

    override fun clear() {
        held = null
        expiresAtMillis = 0
        tier = null
    }

    private fun current(): String? =
        held?.takeIf { nowMillis() < expiresAtMillis - RENEW_MARGIN_MS }

    private suspend fun mint(): String {
        val response = api.mint()
        val token = response.token?.takeIf { it.isNotBlank() }
            ?: error("academy-token returned no token")
        held = token
        tier = response.tier
        expiresAtMillis = parseExpiry(response.expiresAt) ?: (nowMillis() + FALLBACK_LIFETIME_MS)
        return token
    }

    /**
     * ISO-8601 with an offset, which is what the route sends.
     *
     * A failure to parse falls back to a conservative lifetime rather than to forever: an unparsed
     * expiry that defaults to "never" produces a token that is silently dead for eleven hours.
     */
    private fun parseExpiry(text: String?): Long? = text?.let {
        runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
    }

    private companion object {
        /** Renewed this long before it actually expires, so a call in flight cannot straddle it. */
        const val RENEW_MARGIN_MS = 5 * 60 * 1_000L

        /** Well under the stated twelve hours, for the case where the expiry did not parse. */
        const val FALLBACK_LIFETIME_MS = 60 * 60 * 1_000L
    }
}
