package com.coinepro.core.account

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.RetryAfter
import com.coinepro.core.network.ApiErrors
import java.io.IOException
import retrofit2.HttpException
import retrofit2.Retrofit

interface AccountGateway {
    /**
     * The home briefing, or null when the server has nothing to say.
     *
     * Null is a success, not a failure. The server returns 204 rather than filling the space, and
     * the screen has a resting state that says so honestly — a briefing claiming the market is calm
     * because no analysis ran would be worse than silence.
     */
    suspend fun briefing(): AppResult<AccountBriefing?>

    suspend fun portfolio(): AppResult<AccountPortfolio>

    suspend fun kyc(): AppResult<KycStatus>

    suspend fun submitKycLevel1(
        fullName: String,
        nationalId: String,
        birthDate: String,
        phone: String,
    ): AppResult<KycStatus>
}

class NetworkAccountGateway internal constructor(
    private val api: AccountApi,
) : AccountGateway {

    override suspend fun briefing(): AppResult<AccountBriefing?> = call {
        val response = api.briefing()
        if (response.code() == 204) return@call null
        if (!response.isSuccessful) throw HttpException(response)
        val dto = response.body()
        val body = dto?.body?.takeIf(String::isNotBlank)
        val generatedAt = dto?.generatedAt
        // A briefing with no timestamp is dropped rather than shown undated: the age is what tells
        // a reader whether to act on it, and an undated market claim reads exactly like a live one.
        if (body == null || generatedAt == null) null else AccountBriefing(body, generatedAt)
    }

    override suspend fun portfolio(): AppResult<AccountPortfolio> = call {
        val dto = api.portfolio()
        AccountPortfolio(
            total = dto.total?.let { money ->
                val amount = money.amount ?: return@let null
                Money(amount, money.currency.orEmpty().ifBlank { UNKNOWN_CURRENCY })
            },
            change = dto.change?.let { PortfolioChange(it.amount, it.percent, it.period) },
            holdings = dto.holdings.orEmpty().mapNotNull { holding ->
                val symbol = holding.symbol?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                AccountHolding(
                    symbol = symbol,
                    // Falling back to the symbol rather than to an empty row: the position is real
                    // and must stay visible even when the server has no display name for it.
                    displayName = holding.displayName?.takeIf(String::isNotBlank) ?: symbol,
                    quantity = holding.quantity ?: return@mapNotNull null,
                    quantityUnit = holding.quantityUnit?.takeIf(String::isNotBlank),
                    value = holding.value,
                    changePercent = holding.changePercent,
                )
            },
            asOfEpochSeconds = dto.asOf,
        )
    }

    override suspend fun kyc(): AppResult<KycStatus> = call { api.kyc().toStatus() }

    override suspend fun submitKycLevel1(
        fullName: String,
        nationalId: String,
        birthDate: String,
        phone: String,
    ): AppResult<KycStatus> = call {
        api.submitKycLevel1(
            KycLevel1Request(
                fullName = fullName.trim(),
                // Persian and Arabic-Indic digits are accepted by the server, but folding them here
                // keeps what the app sends identical to what the reader believes they typed.
                nationalId = nationalId.foldDigitsToLatin().filter(Char::isDigit),
                birthDate = birthDate.trim(),
                phone = phone.foldDigitsToLatin().filter { it.isDigit() || it == '+' },
            ),
        ).toStatus()
    }

    private fun KycDto.toStatus() = KycStatus(
        level = level,
        state = KycState.fromWire(status),
        requiredFields = requiredFields.orEmpty(),
        submittedAtEpochSeconds = submittedAt,
        reviewedAtEpochSeconds = reviewedAt,
        reason = reason?.takeIf(String::isNotBlank),
    )

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: HttpException) {
        val apiError = ApiErrors.from(error)
        val kind = when (error.code()) {
            401, 403 -> ErrorKind.AUTH
            400, 409, 422 -> ErrorKind.VALIDATION
            429 -> ErrorKind.RATE_LIMIT
            in 500..599 -> ErrorKind.SERVER
            else -> ErrorKind.UNKNOWN
        }
        AppResult.Failure(
            kind = kind,
            message = apiError.message,
            cause = error,
            retryAfterSeconds = if (kind == ErrorKind.RATE_LIMIT) {
                apiError.retryAfterSeconds
                    ?: RetryAfter.parseSeconds(error.response()?.headers()?.get("Retry-After"))
            } else {
                null
            },
        )
    } catch (error: IOException) {
        AppResult.Failure(ErrorKind.NETWORK, cause = error)
    } catch (error: Throwable) {
        AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
    }

    companion object {
        private const val UNKNOWN_CURRENCY = "USD"

        fun create(retrofit: Retrofit): NetworkAccountGateway =
            NetworkAccountGateway(retrofit.create(AccountApi::class.java))
    }
}

/**
 * Rewrites Persian and Arabic-Indic digits as Latin ones, leaving everything else alone.
 *
 * A Persian keyboard produces ۰-۹ by default, so a reader typing their national ID types characters
 * a naive `isDigit` filter would strip to nothing — the field would appear to reject a number they
 * can plainly see they entered.
 */
internal fun String.foldDigitsToLatin(): String = map { character ->
    when (character) {
        in '۰'..'۹' -> '0' + (character - '۰') // Persian
        in '٠'..'٩' -> '0' + (character - '٠') // Arabic-Indic
        else -> character
    }
}.joinToString("")
