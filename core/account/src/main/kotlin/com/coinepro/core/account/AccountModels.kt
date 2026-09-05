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

/**
 * The document a reader verifies with.
 *
 * Three kinds, and the wire spelling of each is fixed here rather than derived from the enum name,
 * because a rename in Kotlin must never change what the server receives.
 */
enum class KycDocumentType(val wire: String) {
    NATIONAL_ID("national_id"),
    PASSPORT("passport"),
    DRIVER_LICENCE("driver_licence"),
    ;

    companion object {
        fun fromWire(value: String?): KycDocumentType? = entries.firstOrNull { it.wire == value?.trim()?.lowercase() }
    }
}

/**
 * What a reader submits for level-one verification.
 *
 * ### Region-aware, not Iran-shaped
 *
 * The form used to be four fields with «کد ملی» hard-wired as the second, which is the right form
 * for an Iranian reader and a wrong one for everybody else — a reader in Berlin has no national id
 * in that sense and was asked for one anyway. The identity is now a country and a document: an
 * Iranian reader chooses the national card and types the same ten digits; anyone else names their
 * country and a passport or licence. The server's existing contract is untouched — see
 * [isIranianNationalId] and `KycLevel1Request` — so this is a wider door, not a moved one.
 *
 * [country] is ISO 3166-1 alpha-2, upper case. [birthDate] is passed through untouched, in whatever
 * calendar the reader wrote it: the server reads Jalali and Gregorian both, and a conversion here
 * would put a second implementation of a famously fiddly calendar in front of a field whose refusal
 * message says nothing about dates.
 */
data class KycIdentity(
    val fullName: String,
    val country: String,
    val documentType: KycDocumentType,
    val documentNumber: String,
    val birthDate: String,
    val phone: String,
) {
    /** The one case the server's original contract was written for: an Iranian national card. */
    val isIranianNationalId: Boolean
        get() = country.equals(IRAN, ignoreCase = true) && documentType == KycDocumentType.NATIONAL_ID

    companion object {
        const val IRAN = "IR"
    }
}

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
