package com.coinepro.core.marketdata

import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.http.POST

/**
 * CoinePro-FX's guest credential — what opens the forex market to somebody with no account.
 *
 * Three properties of this token shape everything below, and all three are the server's decisions
 * rather than the app's:
 *
 * It **creates no account**. No row is written anywhere, which is the whole point: a reader who has
 * not signed up has not signed up, and a guest identity that persisted would be a tracking id
 * wearing a convenience's clothes.
 *
 * It **cannot be refreshed**. Minting a new one is one cheap unauthenticated request; a refresh
 * token that outlived the session would be exactly the durable identifier the design avoids. So
 * this store mints again rather than renewing, and holds nothing across a process death.
 *
 * Its **scope opens three chart routes and nothing else**. Every other academy route answers 401
 * with it, deliberately, so a route added next year is closed to guests without anyone remembering
 * to think about it.
 */
interface GuestTokenStore {
    /** A valid guest token, minting one if the held one is missing or close to expiry. */
    suspend fun token(): String

    /** Forget the held token — on sign-in, when the guest surface is no longer what is on screen. */
    fun clear()
}

internal interface GuestTokenApi {
    @POST("user/auth/guest")
    suspend fun mint(): GuestTokenDto
}

internal data class GuestTokenDto(
    val token: String? = null,
    @SerializedName(value = "expires_in", alternate = ["expiresIn"])
    val expiresIn: Long? = null,
    val scope: String? = null,
)

class NetworkGuestTokenStore private constructor(
    private val api: GuestTokenApi,
    private val nowMillis: () -> Long,
) : GuestTokenStore {

    constructor(
        retrofit: Retrofit,
        nowMillis: () -> Long = System::currentTimeMillis,
    ) : this(retrofit.create(GuestTokenApi::class.java), nowMillis)

    // One mint at a time. A guest opening the market and a chart together would otherwise mint
    // twice, and the route's rate limit is thirty tokens per ten minutes per address.
    private val lock = Mutex()

    @Volatile
    private var held: String? = null

    @Volatile
    private var expiresAtMillis: Long = 0

    override suspend fun token(): String {
        current()?.let { return it }
        return lock.withLock { current() ?: mint() }
    }

    override fun clear() {
        held = null
        expiresAtMillis = 0
    }

    private fun current(): String? =
        held?.takeIf { nowMillis() < expiresAtMillis - RENEW_MARGIN_MS }

    private suspend fun mint(): String {
        val response = api.mint()
        val token = response.token?.takeIf(String::isNotBlank)
            ?: error("guest token mint returned no token")
        held = token
        // The relative lifetime, measured from when the response arrived. A device whose clock is
        // wrong by an hour cancels out of the subtraction; an absolute stamp would not.
        expiresAtMillis = nowMillis() + (response.expiresIn?.takeIf { it > 0 } ?: FALLBACK_SECONDS) * 1_000
        return token
    }

    companion object {
        /** A store over a stubbed route, so the lifetime arithmetic can be tested without a server. */
        internal fun forTest(api: GuestTokenApi, nowMillis: () -> Long): NetworkGuestTokenStore =
            NetworkGuestTokenStore(api, nowMillis)

        /** Renewed this long before it dies, so a request in flight cannot straddle the boundary. */
        private const val RENEW_MARGIN_MS = 2 * 60 * 1_000L

        /** Well under the stated two hours, for a response that omitted the lifetime. */
        private const val FALLBACK_SECONDS = 30 * 60L
    }
}
