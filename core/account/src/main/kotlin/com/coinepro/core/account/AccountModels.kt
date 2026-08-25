package com.coinepro.core.account

/**
 * The account-facing reads behind the home screen, in the app's own terms.
 *
 * Every optional field here is optional because the server said it would be. The backend's rule is
 * that a value it cannot fill honestly is absent rather than zero, and the whole point is lost if
 * the client supplies a default on arrival — `null` has to survive all the way to the screen, which
 * draws a dash for it and a number for a real zero.
 */

/**
 * What the assistant has to say, and when the data behind it was true.
 *
 * [generatedAt] is the age of the *data*, not of the request. The server is explicit about this,
 * and it matters: a briefing regenerated from an hour-old quote is an hour-old claim no matter how
 * recently it was phrased.
 */
data class AccountBriefing(
    val body: String,
    val generatedAtEpochSeconds: Long,
)

/**
 * The account total and the positions behind it.
 *
 * [total] null means the bridge reported nothing — the app knows nothing about this account right
 * now. That is a different statement from a total of zero, which means the account is empty, and
 * the screen renders the two differently.
 */
data class AccountPortfolio(
    val total: Money? = null,
    val change: PortfolioChange? = null,
    val holdings: List<AccountHolding> = emptyList(),
    val asOfEpochSeconds: Long? = null,
)

data class Money(val amount: Double, val currency: String)

data class PortfolioChange(
    val amount: Double?,
    val percent: Double?,
    val period: String?,
)

/**
 * One position.
 *
 * [value] and [changePercent] are absent when the platform has no live quote for the symbol. The
 * position is still real and still shown; only what it is currently worth is unknown.
 */
data class AccountHolding(
    val symbol: String,
    val displayName: String,
    val quantity: Double,
    val quantityUnit: String?,
    val value: Double? = null,
    val changePercent: Double? = null,
)

/** How far identity verification has got, and what is still needed. */
data class KycStatus(
    val level: Int,
    val state: KycState,
    val requiredFields: List<String> = emptyList(),
    val submittedAtEpochSeconds: Long? = null,
    val reviewedAtEpochSeconds: Long? = null,
    /** Present on a rejection: the reviewer's own words, shown as written. */
    val reason: String? = null,
)

enum class KycState {
    NOT_STARTED,
    PENDING,
    APPROVED,
    REJECTED,
    ;

    companion object {
        /**
         * An unrecognised state resolves to [PENDING] rather than [APPROVED].
         *
         * Features are gated on approval, so a status the client cannot read must never open one.
         * Pending is the safe reading of "the server said something we do not understand".
         */
        fun fromWire(value: String?): KycState = when (value?.trim()?.lowercase()) {
            "not_started" -> NOT_STARTED
            "approved" -> APPROVED
            "rejected" -> REJECTED
            else -> PENDING
        }
    }
}
