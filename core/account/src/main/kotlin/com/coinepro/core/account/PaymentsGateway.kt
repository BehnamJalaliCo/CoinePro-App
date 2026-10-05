package com.coinepro.core.account

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Hands a store's proof of payment to the server, which checks it with the store and writes the
 * period (5.24.0). Nothing is trusted from the phone: the app sends the purchase token and the
 * plan, and Pro is on only when the server says so through `entitlements`.
 *
 * On TradeYar, like the entitlements: one account holds the subscription for both platforms.
 */
interface PaymentsGateway {
    /** True when the server verified the purchase and wrote the period. */
    suspend fun claimBazaar(plan: String, purchaseToken: String): Boolean
}

internal interface PaymentsApi {
    @POST("api/mobile/v1/payments/bazaar")
    suspend fun bazaar(@Body body: BazaarClaimDto): Response<Unit>
}

internal data class BazaarClaimDto(val plan: String, val purchaseToken: String)

class NetworkPaymentsGateway internal constructor(private val api: PaymentsApi) : PaymentsGateway {

    override suspend fun claimBazaar(plan: String, purchaseToken: String): Boolean = try {
        // 409 is «this token was already recorded»: the period exists, which is what was asked.
        api.bazaar(BazaarClaimDto(plan, purchaseToken)).let { it.isSuccessful || it.code() == 409 }
    } catch (error: Throwable) {
        false
    }

    companion object {
        fun create(retrofit: Retrofit): NetworkPaymentsGateway =
            NetworkPaymentsGateway(retrofit.create(PaymentsApi::class.java))
    }
}
