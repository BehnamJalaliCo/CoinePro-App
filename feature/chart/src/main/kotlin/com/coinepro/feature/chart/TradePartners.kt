package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import com.coinepro.core.designsystem.R as DesignR

/**
 * The places a reader of this app can actually put a trade on, and how to get there.
 *
 * ### Why this file exists
 *
 * «معامله با کارگزار رو دقیقاً عین تریدینگ ویو بروکر (OneRoyal) لینک رفرال بذار و صرافی هم لینک
 * رفرال ال‌بانک و اوربیت رو بذار.» The hub's trade card used to be a shortcut to this app's own
 * terminal and nothing else, which on a build with no terminal was a dimmed tile. TradingView's
 * equivalent is a list of the venues it has an arrangement with, each with its mark, a line saying
 * what it is, and a way to open an account — and that is what the owner asked for, with their own
 * three venues in it.
 *
 * ### The referral code is a constant, and an empty one is not a broken link
 *
 * Each partner's [referral] is the owner's own code with that venue. Where one is set the link
 * carries it in whatever parameter that venue reads; where it is blank the link is the venue's own
 * registration page, which still works and still sends the reader where they were going — it simply
 * does not credit anybody. That is the right failure: a link that 404s because a code was missing
 * would cost the reader the account, and a code invented here would credit a stranger.
 *
 * ### These are three real companies, and the app says so plainly
 *
 * A partner list is an introduction, not an endorsement, and the sheet says which of the three is a
 * broker and which two are exchanges — because on the reader's side those are different kinds of
 * risk and different kinds of account. Nothing here promises a spread, a fee or a bonus: this app
 * does not know those and would be wrong about them within a month.
 */
internal data class TradePartner(
    /** Stable key, for a saved preference or a log line. Never shown. */
    val id: String,
    /** The venue's own name, in Latin, which is how it is written on its own site and its app. */
    val name: String,
    /** The venue's mark. Tinted to the primary ink where the artwork is a single-colour wordmark. */
    @DrawableRes val logo: Int,
    /** Whether the mark is a flat wordmark that takes the theme's ink, or artwork with its own colours. */
    val monochrome: Boolean,
    /**
     * Whether the artwork spells the venue's name, so the card must not print it a second time.
     *
     * OneRoyal ships a wordmark and the two exchanges ship square marks. A card that drew the
     * wordmark and then wrote "OneRoyal" under it would say the name twice, three points apart, in
     * two typefaces — which is what a logo sheet looks like when nobody checked.
     */
    val carriesName: Boolean,
    /** What kind of venue it is, which decides the line under the name. */
    val kind: TradePartnerKind,
    /** The registration page, without a code. */
    val signUp: String,
    /** The owner's code with this venue, or blank. See the note above. */
    val referral: String,
    /** The query parameter this venue reads a code from. Ignored while [referral] is blank. */
    val referralParameter: String,
) {
    /**
     * Where the button goes: the registration page, carrying the code when there is one.
     *
     * Appended by hand rather than through a URI builder because these three addresses are literals
     * in this file and neither the parameter nor the code contains anything that needs escaping —
     * a referral code is alphanumeric at every venue that issues one. A builder here would be four
     * lines of ceremony around a string concatenation.
     */
    val url: String
        get() = if (referral.isBlank()) {
            signUp
        } else {
            val join = if ('?' in signUp) '&' else '?'
            "$signUp$join$referralParameter=$referral"
        }
}

/** Broker or exchange: two different kinds of account, and the reader is told which. */
internal enum class TradePartnerKind { BROKER, EXCHANGE }

/**
 * The three, broker first.
 *
 * Order is the owner's: the broker is the one this app's forex side, its copy trading and its MT5
 * link are all built around — see `core:copytrade`, where `OneRoyal` is the broker every account
 * row names — and the two exchanges are the crypto side, of which LBank is already the venue the
 * chart's own prices come from.
 */
internal val TRADE_PARTNERS: List<TradePartner> = listOf(
    TradePartner(
        id = "oneroyal",
        name = "OneRoyal",
        // Traced from the wordmark on their own site — one colour, so it takes the theme's ink and
        // is legible on both grounds without a plate behind it.
        logo = DesignR.drawable.logo_oneroyal,
        monochrome = true,
        carriesName = true,
        kind = TradePartnerKind.BROKER,
        signUp = "https://www.oneroyal.com/en/open-live-account/",
        referral = ONEROYAL_REFERRAL,
        referralParameter = "ib",
    ),
    TradePartner(
        id = "lbank",
        name = "LBank",
        logo = DesignR.drawable.logo_lbank,
        monochrome = false,
        carriesName = false,
        kind = TradePartnerKind.EXCHANGE,
        signUp = "https://www.lbank.com/register/",
        referral = LBANK_REFERRAL,
        referralParameter = "icode",
    ),
    TradePartner(
        id = "ourbit",
        name = "Ourbit",
        logo = DesignR.drawable.logo_ourbit,
        monochrome = false,
        carriesName = false,
        kind = TradePartnerKind.EXCHANGE,
        signUp = "https://www.ourbit.com/register",
        referral = OURBIT_REFERRAL,
        referralParameter = "inviteCode",
    ),
)

/**
 * The owner's codes, in one place so supplying them is three edits and no thinking.
 *
 * Blank ships a working link that credits nobody, which is the honest default and is what these
 * are until the owner hands over the real ones. They are not secrets — a referral code is printed
 * on the page it links to — so they belong in the source rather than in a build config, and
 * `scan-secrets.sh` has nothing to say about them.
 */
private const val ONEROYAL_REFERRAL = ""
private const val LBANK_REFERRAL = ""
private const val OURBIT_REFERRAL = ""
