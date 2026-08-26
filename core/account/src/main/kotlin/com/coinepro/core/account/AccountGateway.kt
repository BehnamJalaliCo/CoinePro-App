package com.coinepro.core.account

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.RetryAfter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.model.MarketPlatform
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

    /**
     * Submits level-1 verification.
     *
     * [birthDate] is passed through untouched. CoinePro-FX accepts a Jalali date and converts it
     * server-side, so the app must **not** convert: an Iranian reader knows their birthday in the
     * Jalali calendar, and a client-side conversion would put a second implementation of a famously
     * fiddly calendar in the path of a field whose refusal message says nothing about dates. One
     * conversion, on the side that owns the answer.
     */
    suspend fun submitKycLevel1(
        fullName: String,
        nationalId: String,
        birthDate: String,
        phone: String,
    ): AppResult<KycStatus>

    /**
     * Destroys the account and everything attributable to it.
     *
     * [DeletionOutcome.Unsupported] is not a failure and must not be reported as one. It means this
     * deployment does not serve the route, and the screen then shows the out-of-app route instead —
     * which is a working answer, where "something went wrong" in front of someone who has just
     * asked to be forgotten is not.
     */
    suspend fun deleteAccount(): AppResult<DeletionOutcome>
}

/** What came back from asking for the account to be deleted. See [AccountGateway.deleteAccount]. */
enum class DeletionOutcome {
    /** Gone. The caller must sign out — the token it holds now names nobody. */
    DELETED,

    /** This server has no deletion route. Not an error; the reader is shown the other way. */
    UNSUPPORTED,
}

class NetworkAccountGateway internal constructor(
    private val api: AccountApi,
    private val paths: AccountPaths,
) : AccountGateway {

    override suspend fun briefing(): AppResult<AccountBriefing?> = call {
        val response = api.briefing(paths.briefing)
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
        val dto = api.portfolio(paths.portfolio)
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

    override suspend fun kyc(): AppResult<KycStatus> = call { api.kyc(paths.kyc).toStatus() }

    override suspend fun submitKycLevel1(
        fullName: String,
        nationalId: String,
        birthDate: String,
        phone: String,
    ): AppResult<KycStatus> = call {
        api.submitKycLevel1(
            paths.kycLevel1,
            KycLevel1Request(
                fullName = fullName.trim(),
                // Persian and Arabic-Indic digits are accepted by the server, but folding them here
                // keeps what the app sends identical to what the reader believes they typed.
                nationalId = nationalId.foldDigitsToLatin().filter(Char::isDigit),
                // Digits folded but the calendar left alone: the server reads Jalali and Gregorian
                // both, and Persian numerals are what a Persian keyboard produces for either.
                birthDate = birthDate.foldDigitsToLatin().trim(),
                phone = phone.foldDigitsToLatin().filter { it.isDigit() || it == '+' },
            ),
        ).toStatus()
    }

    override suspend fun deleteAccount(): AppResult<DeletionOutcome> = call {
        val response = api.deleteAccount(paths.deleteAccount)
        when {
            response.isSuccessful -> DeletionOutcome.DELETED
            // 404 is the route not existing; 405 is the path existing for another verb. Both mean
            // this deployment has not built deletion yet, which is a different thing from refusing.
            response.code() == 404 || response.code() == 405 -> DeletionOutcome.UNSUPPORTED
            else -> throw HttpException(response)
        }
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

        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkAccountGateway =
            NetworkAccountGateway(retrofit.create(AccountApi::class.java), AccountPaths.of(platform))
    }
}

