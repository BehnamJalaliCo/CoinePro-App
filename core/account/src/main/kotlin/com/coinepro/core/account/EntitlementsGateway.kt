package com.coinepro.core.account

import com.coinepro.core.model.MarketPlatform
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * What the server says this reader may reach (run Τ2, B2).
 *
 * ### One field, and that is the design
 *
 * The product decision is «everything, for everybody, until a hundred thousand installs», so the
 * document is `{"all": true}` and nothing else. Fifteen named capabilities would invite fourteen of
 * them to drift out of step with the gating code, and the gating code is what actually decides — see
 * [com.coinepro.core.common.Entitlements], which is deliberately one switch for the same reason.
 *
 * ### Every failure is an open app
 *
 * A 404 (no route on this deployment), a timeout, a body that will not parse, a field that is not a
 * boolean: all of them answer **null**, which `EntitlementStore` stores nothing for and
 * `Entitlements.applyAtStart` reads as «keep the local default». There is no path through this file
 * that closes a door on the strength of a request that did not work. That asymmetry is the point:
 * wrongly open costs the owner a subscription they were giving away anyway, and wrongly closed costs
 * a reader the product they installed.
 */
interface EntitlementsGateway {
    /** The served entitlement, or null where the server did not answer with one. */
    suspend fun fetch(): Boolean?
}

internal interface EntitlementsApi {
    @GET
    suspend fun entitlements(@Url path: String): Response<EntitlementsDto>
}

internal data class EntitlementsDto(val all: Boolean? = null)

class NetworkEntitlementsGateway internal constructor(
    private val api: EntitlementsApi,
    private val path: String,
) : EntitlementsGateway {

    override suspend fun fetch(): Boolean? = try {
        val response = api.entitlements(path)
        if (response.isSuccessful) response.body()?.all else null
    } catch (error: Throwable) {
        // Every throwable, and on purpose. A gateway whose only job is «open or keep the default»
        // has nothing useful to say about *which* way a request failed, and a `Failure` nobody can
        // act on is a result type for its own sake. The caller's contract is one nullable boolean.
        null
    }

    companion object {
        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkEntitlementsGateway =
            NetworkEntitlementsGateway(
                api = retrofit.create(EntitlementsApi::class.java),
                path = EntitlementPaths.of(platform),
            )
    }
}

/**
 * Where each deployment serves the document.
 *
 * The same split as [AccountPaths] and for the same reason: CoinePro-FX prefixes its mobile routes
 * with `user/mobile` and TradeYar with `api/mobile/v1`, and a path written once for both answers 404
 * on one of them. Neither serves this route today; `docs/runs/RUN_T2/BLOCKED.md §B2` is the contract.
 */
internal object EntitlementPaths {
    fun of(platform: MarketPlatform): String = when (platform) {
        MarketPlatform.COINEPRO_FX -> "user/mobile/entitlements"
        MarketPlatform.TRADEYAR -> "api/mobile/v1/entitlements"
    }
}
