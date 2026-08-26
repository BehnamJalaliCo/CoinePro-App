package com.coinepro.core.account

import com.coinepro.core.model.MarketPlatform
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * The account surface, per platform.
 *
 * Every path arrives as a `@Url` rather than being written into the annotation, because the two
 * deployments do not agree on the prefix — CoinePro-FX serves these under `user/mobile`, TradeYar
 * under `api/mobile/v1`. They used to be annotation constants carrying the CoinePro-FX shape for
 * both, which meant every one of these four calls answered 404 on the crypto platform. The names
 * after the prefix are identical, which is why [AccountPaths] builds them rather than listing them
 * twice.
 *
 * The briefing returns `Response<…>` rather than a bare body because 204 is a real answer here and
 * not an error: it means the server has nothing worth saying. Retrofit would hand a bare body back
 * as null, which is indistinguishable from a parse failure.
 */
internal interface AccountApi {
    @GET
    suspend fun briefing(@Url path: String): Response<BriefingDto>

    @GET
    suspend fun portfolio(@Url path: String): PortfolioDto

    @GET
    suspend fun kyc(@Url path: String): KycDto

    @POST
    suspend fun submitKycLevel1(@Url path: String, @Body body: KycLevel1Request): KycDto

    /**
     * Deletes the account.
     *
     * `Response<Unit>` rather than `Unit`, so the gateway can tell a 404 — this deployment does not
     * serve the route yet — apart from a refusal. The two need different words in front of a reader
     * who has just asked for their account to be destroyed.
     */
    @DELETE
    suspend fun deleteAccount(@Url path: String): Response<Unit>
}

/**
 * Where one platform's account routes live.
 *
 * `deleteAccount` is `docs/REQUEST4_ACCOUNT_DELETION.md`: Google Play requires an in-app deletion
 * route for any app with sign-up, and neither server serves one yet. The app asks the capability
 * flag first and only shows the button where the server said yes, so the path here is a contract
 * waiting to be met rather than a call that fails.
 */
internal class AccountPaths(private val prefix: String) {
    val briefing = "$prefix/briefing"
    val portfolio = "$prefix/portfolio"
    val kyc = "$prefix/kyc"
    val kycLevel1 = "$prefix/kyc/level1"
    val deleteAccount = "$prefix/account"

    companion object {
        fun of(platform: MarketPlatform): AccountPaths = when (platform) {
            MarketPlatform.COINEPRO_FX -> AccountPaths("user/mobile")
            MarketPlatform.TRADEYAR -> AccountPaths("api/mobile/v1")
        }
    }
}

internal data class KycLevel1Request(
    val fullName: String,
    val nationalId: String,
    val birthDate: String,
    val phone: String,
)

internal data class BriefingDto(
    val body: String? = null,
    val generatedAt: Long? = null,
    val streaming: Boolean = false,
)

/**
 * Every field is nullable, including `total`, because the documented "nothing to report" response
 * is a body carrying `total` alone with the rest of the keys absent.
 */
internal data class PortfolioDto(
    val total: MoneyDto? = null,
    val change: ChangeDto? = null,
    val holdings: List<HoldingDto>? = null,
    val asOf: Long? = null,
)

internal data class MoneyDto(val amount: Double? = null, val currency: String? = null)

internal data class ChangeDto(
    val amount: Double? = null,
    val percent: Double? = null,
    val period: String? = null,
)

internal data class HoldingDto(
    val symbol: String? = null,
    val displayName: String? = null,
    val quantity: Double? = null,
    val quantityUnit: String? = null,
    val value: Double? = null,
    val changePercent: Double? = null,
)

internal data class KycDto(
    val level: Int = 0,
    val status: String? = null,
    val requiredFields: List<String>? = null,
    val submittedAt: Long? = null,
    val reviewedAt: Long? = null,
    val reason: String? = null,
)
