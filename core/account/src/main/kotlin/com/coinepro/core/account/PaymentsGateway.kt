package com.coinepro.core.account

import com.coinepro.core.network.ApiErrors
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Hands a store's proof of payment to the server, which checks it with the store and writes the
 * period (5.24.0). Nothing is trusted from the phone: the app sends the purchase token and the
 * plan, and Pro is on only when the server says so through `entitlements`.
 *
 * On TradeYar, like the entitlements: one account holds the subscription for both platforms.
 *
 * 5.25.0 adds the rest of what the Pro page draws: the plans with their prices, the reader's own
 * running period, and the site's way to pay — a USDT transfer on BSC, proved by its hash.
 */
interface PaymentsGateway {
    /** True when the server verified the purchase and wrote the period. */
    suspend fun claimBazaar(plan: String, purchaseToken: String): Boolean

    /** The plans and which ways to pay are open, or null where the server did not answer. */
    suspend fun plans(): PaymentPlans? = null

    /** The reader's running period, or null where there is none or the server did not answer. */
    suspend fun current(): ProPeriod? = null

    /** Claims one USDT (BEP20) transfer for [plan]. The server reads the chain; the hash is the proof. */
    suspend fun claimUsdt(plan: String, txHash: String): UsdtClaim = UsdtClaim.Refused(null)
}

/** `payments/plans`, as the Pro page needs it. */
data class PaymentPlans(
    val plans: List<PaymentPlan>,
    /** The wallet a USDT payment goes to, or null while the site's payment is not open. */
    val usdtWallet: String?,
    /** The network that wallet is on — `BEP20`. */
    val usdtNetwork: String?,
    /** Whether the server can check a Cafe Bazaar purchase. */
    val bazaar: Boolean,
)

data class PaymentPlan(
    /** `monthly`, `quarterly`, `yearly`. */
    val id: String,
    val days: Int,
    val priceToman: Long?,
    /** As the server writes it — `"15"` — so no float ever rounds a price. */
    val priceUsdt: String?,
)

/** A running Pro period. */
data class ProPeriod(val plan: String?, val endsAt: String?)

/** What a USDT claim came to. */
sealed interface UsdtClaim {
    /** Verified and written: Pro runs until [endsAt]. */
    data class Activated(val endsAt: String?) : UsdtClaim

    /** Refused, with the server's own sentence where it sent one — «این تراکنش هنوز تأیید کافی ندارد». */
    data class Refused(val message: String?, val needsSignIn: Boolean = false) : UsdtClaim
}

internal interface PaymentsApi {
    @POST("api/mobile/v1/payments/bazaar")
    suspend fun bazaar(@Body body: BazaarClaimDto): Response<Unit>

    @GET("api/mobile/v1/payments/plans")
    suspend fun plans(): Response<PlansDto>

    @GET("api/mobile/v1/entitlements")
    suspend fun entitlements(): Response<PeriodDto>

    @POST("api/mobile/v1/payments/usdt")
    suspend fun usdt(@Body body: UsdtClaimDto): Response<PeriodDto>
}

internal data class BazaarClaimDto(val plan: String, val purchaseToken: String)

internal data class UsdtClaimDto(val plan: String, val txHash: String)

internal data class PlansDto(
    val plans: List<PlanDto>? = null,
    val usdt: UsdtDto? = null,
    val bazaar: Boolean? = null,
)

internal data class PlanDto(
    val id: String? = null,
    val days: Int? = null,
    val priceToman: Long? = null,
    val priceUsdt: String? = null,
)

internal data class UsdtDto(val network: String? = null, val wallet: String? = null)

internal data class PeriodDto(
    val premium: Boolean? = null,
    val plan: String? = null,
    val endsAt: String? = null,
)

class NetworkPaymentsGateway internal constructor(private val api: PaymentsApi) : PaymentsGateway {

    override suspend fun claimBazaar(plan: String, purchaseToken: String): Boolean = try {
        // 409 is «this token was already recorded»: the period exists, which is what was asked.
        api.bazaar(BazaarClaimDto(plan, purchaseToken)).let { it.isSuccessful || it.code() == 409 }
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Throwable) {
        false
    }

    override suspend fun plans(): PaymentPlans? = try {
        api.plans().takeIf { it.isSuccessful }?.body()?.let { body ->
            PaymentPlans(
                plans = body.plans.orEmpty().mapNotNull { plan ->
                    val id = plan.id ?: return@mapNotNull null
                    PaymentPlan(id = id, days = plan.days ?: 0, priceToman = plan.priceToman, priceUsdt = plan.priceUsdt)
                },
                usdtWallet = body.usdt?.wallet?.takeIf { it.isNotBlank() },
                usdtNetwork = body.usdt?.network,
                bazaar = body.bazaar == true,
            )
        }
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Throwable) {
        null
    }

    override suspend fun current(): ProPeriod? = try {
        api.entitlements().takeIf { it.isSuccessful }?.body()
            ?.takeIf { it.premium == true }
            ?.let { ProPeriod(plan = it.plan, endsAt = it.endsAt) }
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Throwable) {
        null
    }

    override suspend fun claimUsdt(plan: String, txHash: String): UsdtClaim = try {
        val response = api.usdt(UsdtClaimDto(plan = plan, txHash = txHash.trim()))
        when {
            response.isSuccessful -> UsdtClaim.Activated(response.body()?.endsAt)
            response.code() == 401 -> UsdtClaim.Refused(null, needsSignIn = true)
            else -> UsdtClaim.Refused(ApiErrors.parse(response.errorBody()?.string()).message)
        }
    } catch (error: kotlinx.coroutines.CancellationException) {
        throw error
    } catch (error: Throwable) {
        UsdtClaim.Refused(null)
    }

    companion object {
        fun create(retrofit: Retrofit): NetworkPaymentsGateway =
            NetworkPaymentsGateway(retrofit.create(PaymentsApi::class.java))
    }
}
