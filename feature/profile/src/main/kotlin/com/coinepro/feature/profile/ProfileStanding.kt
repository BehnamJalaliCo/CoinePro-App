package com.coinepro.feature.profile

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coinepro.core.account.KycState
import com.coinepro.core.account.KycStatus
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.membership.MembershipJourney
import com.coinepro.core.membership.MembershipStatus
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale

/**
 * The three facts about a reader that only a server can answer, turned into rows.
 *
 * These live here — in the module that draws them, behind functions — rather than in the wiring at
 * the call site, and that is the whole point. Rule one of this screen is that the app never claims
 * what the server did not say, and a rule enforced by a snippet somebody copies into a navigation
 * graph is a rule that survives until the next person edits the snippet. Enforced by a function,
 * it survives.
 *
 * Every one of them can return a fact with a null value, and each does so for a reason worth
 * naming rather than a shared "if anything went wrong". There is no path here that turns silence
 * into a verdict.
 */

/**
 * Where the reader stands as a member.
 *
 * Four distinct silences, all of which must read as unknown and not as "not a member":
 *
 * * `journey == null` — nothing asked. The membership status is only fetched where the screen that
 *   needs it is opened, so a profile visited first legitimately knows nothing.
 * * `loading` — asked, not yet answered. Three hundred milliseconds of «نامشخص» is honest; three
 *   hundred milliseconds of «تأیید نشده» is a lie that corrects itself, which is the kind readers
 *   remember and repeat to support.
 * * `!statusKnown` — the read failed, or the status is a word added server-side after this build
 *   shipped. [MembershipStatus.UNKNOWN] deliberately has no Persian label here: giving it one would
 *   be inventing a standing for a state nobody has defined.
 * * the status is known — and then, and only then, the server's own vocabulary is printed.
 *
 * [MembershipJourney.serverMessage] rides along as the detail line, verbatim. It is the only prose
 * about *this account* anything is allowed to print, and the triage `note` is not carried on the
 * journey at all, so it cannot leak here by accident.
 */
@Composable
fun membershipFact(
    journey: MembershipJourney?,
    onOpen: (() -> Unit)? = null,
): ProfileFact {
    val known = knownMembershipStatus(journey)
    return ProfileFact(
        label = stringResource(R.string.profile_standing_membership),
        value = known?.let { stringResource(it.labelRes()) },
        tone = known.tone(),
        // Shown under an unknown value too, because when the read fails the server's last sentence
        // is often the only thing that explains why.
        detail = journey?.serverMessage,
        onOpen = onOpen,
    )
}

/**
 * How far identity verification has got.
 *
 * A null [status] is the app never having been told, and it stays unknown. What it is *not* is
 * [KycState.NOT_STARTED]: "you have not verified" and "we could not read whether you have verified"
 * are different sentences, and the first one sends somebody to fill in a form they may already have
 * filled in.
 *
 * The detail line is the server's own material and nothing else — the reviewer's words on a
 * rejection, the day a pending submission was received, the level that was granted. On a rejection
 * that carried no reason the line is simply absent; there is no house sentence standing in for a
 * reviewer.
 */
@Composable
fun verificationFact(
    status: KycStatus?,
    onOpen: (() -> Unit)? = null,
): ProfileFact = ProfileFact(
    label = stringResource(R.string.profile_standing_verification),
    value = verificationLabel(status)?.let { stringResource(it) },
    tone = when (status?.state) {
        KycState.APPROVED -> ProfileFactTone.SETTLED
        KycState.PENDING -> ProfileFactTone.WAITING
        KycState.REJECTED -> ProfileFactTone.REFUSED
        KycState.NOT_STARTED, null -> ProfileFactTone.NEUTRAL
    },
    detail = when (status?.state) {
        KycState.REJECTED -> status.reason
        KycState.PENDING -> status.submittedAtEpochSeconds?.let {
            stringResource(R.string.profile_standing_verification_submitted, jalaliDay(it))
        }
        KycState.APPROVED -> status.level
            .takeIf { it > 0 }
            ?.let { stringResource(R.string.profile_standing_verification_level, it.toPersianDigits()) }
        else -> null
    },
    onOpen = onOpen,
)

/**
 * The plan, as the server named it.
 *
 * [planLabel] is the server's own Persian name for whatever this account is on; there is no table
 * of plan names in this app and there must not be one, because a plan renamed on the server would
 * then be renamed everywhere except in front of the reader paying for it. Null means no plan was
 * named, which is not the same as no plan — hence unknown rather than «رایگان».
 *
 * The expiry is drawn as a Jalali date with the remaining days beside it: the date is what a reader
 * checks against a receipt, the count is what they act on. Latin digits are wrong for the count and
 * right for nothing else here, so the days are Persian — they are prose — and the date is already
 * Persian-scripted by [PersianDateTime].
 */
@Composable
fun planFact(
    planLabel: String?,
    expiresLabel: String? = null,
    daysRemaining: Int? = null,
    endingSoon: Boolean = false,
    vip: Boolean = false,
    onOpen: (() -> Unit)? = null,
): ProfileFact = ProfileFact(
    label = stringResource(if (vip) R.string.profile_standing_plan_vip else R.string.profile_standing_plan),
    value = planLabel,
    tone = when {
        planLabel == null -> ProfileFactTone.NEUTRAL
        endingSoon -> ProfileFactTone.WAITING
        else -> ProfileFactTone.SETTLED
    },
    detail = when {
        expiresLabel != null && daysRemaining != null -> stringResource(
            R.string.profile_standing_plan_expires_in,
            expiresLabel,
            daysRemaining.toPersianDigits(),
        )
        expiresLabel != null -> stringResource(R.string.profile_standing_plan_expires, expiresLabel)
        else -> null
    },
    onOpen = onOpen,
)

/**
 * A win rate, for the detail line of a record row.
 *
 * A market figure and therefore Latin, isolated so the per-cent sign does not swap ends with the
 * number in a right-to-left paragraph — the same treatment every percentage in this app gets. Null
 * in, null out: a win rate over no decided trades is not zero per cent, and the record card says
 * nothing rather than «۰٪».
 */
@Composable
fun winRateDetail(percent: Double?): String? = percent?.let {
    val figure = DecimalFormat("0.0", DecimalFormatSymbols(Locale.US)).format(it)
    stringResource(R.string.profile_record_win_rate, BidiText.isolateLtr("$figure%"))
}

/** The reader's own vocabulary for the rows the app — not a server — can count. */
object ProfileLabels {
    @get:StringRes val Email: Int get() = R.string.profile_standing_email

    @get:StringRes val Journal: Int get() = R.string.profile_record_journal
    @get:StringRes val PaperTrading: Int get() = R.string.profile_record_paper
    @get:StringRes val ClosedTrades: Int get() = R.string.profile_record_closed
    @get:StringRes val Academy: Int get() = R.string.profile_record_academy

    /**
     * «%1$s روز پیاپی مطالعه» — a study streak, in Persian digits because it is a prose count.
     *
     * Formatted by the caller because only it holds the academy's answer, and offered here so the
     * one row on this page with a number *inside* its detail line does not have the sentence
     * written twice in two places.
     */
    @get:StringRes val AcademyStreak: Int get() = R.string.profile_record_academy_detail

    @get:StringRes val Watchlists: Int get() = R.string.profile_library_watchlists
    @get:StringRes val ChartLayouts: Int get() = R.string.profile_library_layouts
    @get:StringRes val IndicatorTemplates: Int get() = R.string.profile_library_indicators
    @get:StringRes val DrawingTemplates: Int get() = R.string.profile_library_drawings
    @get:StringRes val Intervals: Int get() = R.string.profile_library_intervals
    @get:StringRes val PriceAlerts: Int get() = R.string.profile_library_alerts
    @get:StringRes val Scripts: Int get() = R.string.profile_library_scripts
}

/**
 * The only membership status this screen is allowed to name, or null.
 *
 * Split out of [membershipFact] so the rule can be tested without a renderer. Three separate
 * silences collapse to the same null here — nothing asked, nothing answered yet, and an answer this
 * build cannot read — and that is correct: they differ in *why* the app does not know, and not at
 * all in what it may print.
 */
internal fun knownMembershipStatus(journey: MembershipJourney?): MembershipStatus? =
    journey?.takeIf { !it.loading && it.statusKnown }?.status

/**
 * The word for a verification state, or null when the app was never told one.
 *
 * A null [status] is not [KycState.NOT_STARTED]. The distinction is the entire reason this
 * function exists rather than an elvis operator at the call site: "you have not verified" sends
 * somebody to fill in a form, and doing that to a reader whose approved status merely failed to
 * load is how a second, duplicate submission arrives at a reviewer.
 */
@StringRes
internal fun verificationLabel(status: KycStatus?): Int? = status?.state?.labelRes()

/**
 * The status words, matching `feature:membership` exactly.
 *
 * They are written out again rather than reached for across modules, and the duplication is
 * deliberate: with a non-transitive R class the alternative is the navigation graph importing
 * another feature's generated resources, which couples two screens through a class neither of them
 * declares. What matters is that the *words* agree — a reader who reads «در انتظار واریز» here and
 * something else one tap later has been given two answers about one account.
 */
@StringRes
private fun MembershipStatus.labelRes(): Int = when (this) {
    MembershipStatus.AWAITING_UID -> R.string.profile_membership_awaiting_uid
    MembershipStatus.VERIFYING -> R.string.profile_membership_verifying
    MembershipStatus.APPROVED -> R.string.profile_membership_approved
    MembershipStatus.PENDING_DEPOSIT -> R.string.profile_membership_pending_deposit
    MembershipStatus.REJECTED_REFERRAL -> R.string.profile_membership_rejected_referral
    MembershipStatus.ERROR -> R.string.profile_membership_error
    MembershipStatus.PENDING -> R.string.profile_membership_pending
    // Never reached: `membershipFact` filters this out before asking for a label, because a state
    // this build has never heard of has no honest short word. The branch exists so that adding a
    // status to the enum is a compile error here rather than a silent new label somewhere.
    MembershipStatus.UNKNOWN -> R.string.profile_fact_unknown
}

internal fun MembershipStatus?.tone(): ProfileFactTone = when (this) {
    MembershipStatus.APPROVED -> ProfileFactTone.SETTLED
    MembershipStatus.PENDING_DEPOSIT, MembershipStatus.ERROR -> ProfileFactTone.WAITING
    MembershipStatus.REJECTED_REFERRAL -> ProfileFactTone.REFUSED
    MembershipStatus.AWAITING_UID -> ProfileFactTone.WAITING
    MembershipStatus.VERIFYING, MembershipStatus.PENDING -> ProfileFactTone.WAITING
    MembershipStatus.UNKNOWN, null -> ProfileFactTone.NEUTRAL
}

@StringRes
private fun KycState.labelRes(): Int = when (this) {
    KycState.NOT_STARTED -> R.string.profile_verify_not_started
    KycState.PENDING -> R.string.profile_verify_pending
    KycState.APPROVED -> R.string.profile_verify_approved
    KycState.REJECTED -> R.string.profile_verify_rejected
}

/**
 * A Jalali day from a wire timestamp.
 *
 * [PersianDateTime] answers `—` for a date it cannot represent rather than throwing, which is what
 * makes it safe to hand a server's number to directly.
 */
private fun jalaliDay(epochSeconds: Long): String =
    PersianDateTime.numericDay(Instant.ofEpochSecond(epochSeconds))
