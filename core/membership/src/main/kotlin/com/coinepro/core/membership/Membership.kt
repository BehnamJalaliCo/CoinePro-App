package com.coinepro.core.membership

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.network.ApiErrors
import com.google.gson.annotations.SerializedName
import java.io.IOException
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Where a reader has got to in becoming a member.
 *
 * The vocabulary is the server's own, not a parallel set invented here — an account that reads
 * `approved` in the app and `pending` in the admin console is a support conversation nobody can
 * win. [UNKNOWN] exists for a state added server-side after this build shipped: the app then shows
 * the server's sentence and offers nothing, which is the only honest thing it can do about a state
 * it has never heard of.
 */
enum class MembershipStatus {
    AWAITING_UID,
    VERIFYING,
    APPROVED,

    /** A genuine sub-account whose balance has not reached the threshold. Recoverable, and common. */
    PENDING_DEPOSIT,

    /** The exchange says this account was not opened under CoinePro's link. Not recoverable. */
    REJECTED_REFERRAL,

    /**
     * The check itself failed — a timeout, the exchange unreachable.
     *
     * Deliberately not [REJECTED_REFERRAL]. A service outage is not evidence that somebody is not a
     * sub-account, and filing it as a rejection tells a reader with a perfectly good account to go
     * and open another one.
     */
    ERROR,
    PENDING,
    UNKNOWN,
}

/**
 * What the app may say about it.
 *
 * [messageFa] is the **only** string that reaches the reader. [note] is triage — it carries things
 * like `referral_status=false` and a balance — and the server's own instruction is that it is never
 * displayed. It is kept because it belongs in a bug report, and dropped from every screen.
 */
data class MembershipState(
    val status: MembershipStatus,
    val messageFa: String?,
    /** `"uid"` — the reader acts. `"wait"` — the server acts. Null — finished. */
    val nextStep: String?,
    val canResubmit: Boolean,
    val uid: String?,
    val exchange: String?,
    /** Triage only. Never rendered. */
    val note: String?,
) {
    /** Whether the reader still has something to do. Decides whether the form is drawn. */
    val awaitsReader: Boolean get() = nextStep == "uid" || canResubmit
}

interface MembershipGateway {
    suspend fun status(): AppResult<MembershipState>

    /**
     * Submits a UID for verification.
     *
     * [uid] is folded to Latin digits before it is sent. A Persian keyboard produces ۰-۹ by default,
     * so that is what a reader types — and the exchange, asked about `۱۲۳`, answers that it has
     * never heard of that account. The failure is invisible from both ends: the field looks right,
     * and the refusal says the account is not a sub-account.
     */
    suspend fun submitUid(exchange: String, uid: String): AppResult<MembershipState>
}

internal interface MembershipApi {
    @GET("api/mobile/v1/membership/status")
    suspend fun status(): MembershipDto

    @POST("api/mobile/v1/membership/uid")
    suspend fun submitUid(@Body body: SubmitUidRequest): MembershipDto
}

internal data class SubmitUidRequest(val exchange: String, val uid: String)

internal data class MembershipDto(
    val status: String? = null,
    @SerializedName(value = "message_fa", alternate = ["messageFa"])
    val messageFa: String? = null,
    @SerializedName(value = "next_step", alternate = ["nextStep"])
    val nextStep: String? = null,
    @SerializedName(value = "can_resubmit", alternate = ["canResubmit"])
    val canResubmit: Boolean = false,
    val uid: String? = null,
    val exchange: String? = null,
    val note: String? = null,
)

class NetworkMembershipGateway internal constructor(
    private val api: MembershipApi,
) : MembershipGateway {

    override suspend fun status(): AppResult<MembershipState> = call { api.status().toState() }

    override suspend fun submitUid(exchange: String, uid: String): AppResult<MembershipState> = call {
        api.submitUid(SubmitUidRequest(exchange = exchange, uid = uid.trim().foldDigitsToLatin())).toState()
    }

    private fun MembershipDto.toState() = MembershipState(
        status = status.toStatus(),
        messageFa = messageFa?.takeIf(String::isNotBlank),
        nextStep = nextStep?.takeIf(String::isNotBlank),
        canResubmit = canResubmit,
        uid = uid?.takeIf(String::isNotBlank),
        exchange = exchange?.takeIf(String::isNotBlank),
        note = note?.takeIf(String::isNotBlank),
    )

    private fun String?.toStatus(): MembershipStatus = when (this?.trim()?.lowercase()) {
        "awaiting_uid" -> MembershipStatus.AWAITING_UID
        "verifying" -> MembershipStatus.VERIFYING
        "approved" -> MembershipStatus.APPROVED
        "pending_deposit" -> MembershipStatus.PENDING_DEPOSIT
        "rejected_referral" -> MembershipStatus.REJECTED_REFERRAL
        "error" -> MembershipStatus.ERROR
        "pending" -> MembershipStatus.PENDING
        // A state this build has never heard of. Not mapped to the nearest neighbour, because the
        // nearest neighbour to an unknown state is a guess about somebody's membership.
        else -> MembershipStatus.UNKNOWN
    }

    private suspend fun <T> call(block: suspend () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (error: HttpException) {
        val apiError = ApiErrors.from(error)
        AppResult.Failure(
            kind = when (error.code()) {
                401, 403 -> ErrorKind.AUTH
                400, 409, 422 -> ErrorKind.VALIDATION
                429 -> ErrorKind.RATE_LIMIT
                in 500..599 -> ErrorKind.SERVER
                else -> ErrorKind.UNKNOWN
            },
            message = apiError.message,
            cause = error,
            retryAfterSeconds = apiError.retryAfterSeconds,
        )
    } catch (error: IOException) {
        AppResult.Failure(ErrorKind.NETWORK, cause = error)
    } catch (error: Throwable) {
        AppResult.Failure(ErrorKind.UNKNOWN, cause = error)
    }

    companion object {
        fun create(retrofit: Retrofit): NetworkMembershipGateway =
            NetworkMembershipGateway(retrofit.create(MembershipApi::class.java))
    }
}
