package com.coinepro.core.account

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * CoinePro's `user/mobile` account reads, as captured from the running server.
 *
 * The briefing returns `Response<…>` rather than a bare body because 204 is a real answer here and
 * not an error: it means the server has nothing worth saying. Retrofit would hand a bare body back
 * as null, which is indistinguishable from a parse failure.
 */
internal interface AccountApi {
    @GET("user/mobile/briefing")
    suspend fun briefing(): Response<BriefingDto>

    @GET("user/mobile/portfolio")
    suspend fun portfolio(): PortfolioDto

    @GET("user/mobile/kyc")
    suspend fun kyc(): KycDto

    @POST("user/mobile/kyc/level1")
    suspend fun submitKycLevel1(@Body body: KycLevel1Request): KycDto
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
