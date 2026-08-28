package com.coinepro.feature.home

/**
 * What the assistant has to say on the home screen right now.
 *
 * Modelled as four distinct states rather than a nullable string because the difference between
 * them is the whole point of the screen: a reader must be able to tell "nothing has happened yet"
 * from "I am still looking" from "I cannot reach the service". Collapsing those into an empty
 * briefing would let a broken endpoint look like a quiet market.
 */
sealed interface HomeBriefing {

    /** No briefing has been requested yet. The card offers the two entry points instead. */
    data object Resting : HomeBriefing

    /** A briefing is being generated. The card shows the indeterminate bar. */
    data object Working : HomeBriefing

    /**
     * A briefing the server produced.
     *
     * [body] is server text and is rendered as written — the client only marks up the figures
     * already inside it and never rewrites, summarises or translates it. [ageLabel] is how old the
     * briefing is, and is required: an undated market claim is indistinguishable from a live one.
     */
    data class Ready(val body: String, val ageLabel: String) : HomeBriefing

    /** The briefing service could not be reached. [reason] is shown verbatim when present. */
    data class Unavailable(val reason: String? = null) : HomeBriefing
}

/**
 * The account total and what it is made of, already formatted.
 *
 * Formatting happens at the call site because only the caller knows the currency and the precision
 * the server reported; the screen must not re-round a balance.
 *
 * [changeLabel] carries the whole change phrase — amount, percentage and period together — rather
 * than three fields the screen would have to assemble. Word order differs between Persian and
 * English, so composing it here would bake one language's grammar into the layout.
 */
data class HomePortfolio(
    val totalLabel: String,
    val changeLabel: String,
    val isUp: Boolean,
    val holdings: List<HomeHolding> = emptyList(),
    /**
     * The account's equity, oldest first, for the line under the balance.
     *
     * A hero number says where the account *is* and nothing about how it got there, which is the
     * question anybody looking at their own balance is actually asking. The curve answers it in the
     * space the number already occupies.
     *
     * Empty is the ordinary case and draws nothing. It is deliberately not padded, interpolated or
     * stubbed: a line invented under a balance is a claim about somebody's money, and the reader
     * has no way to tell an invented one from a real one. It comes from the portfolio's own closed
     * trades — see `PortfolioMath` — so it appears once there is a history to draw.
     */
    val equity: List<Double> = emptyList(),
)

/**
 * One position in the account.
 *
 * [quantityLabel] is the amount held with its unit ("0.1482 BTC") and [valueLabel] is what that is
 * worth in the account currency. Both come formatted, for the same reason as [HomePortfolio].
 */
data class HomeHolding(
    val symbol: String,
    val displayName: String,
    val quantityLabel: String,
    val valueLabel: String,
    val changeLabel: String,
    val isUp: Boolean,
)

/**
 * A subscription the reader actually holds.
 *
 * Null everywhere means no subscription, and no subscription means nothing on screen. It is
 * deliberately not an empty state or an offer: nothing in this app is withheld from someone without
 * one — signals, execution and the rest are open to every signed-in account — so a card announcing
 * an absence would be selling, not informing, and would take the top of the home screen to do it.
 *
 * [planLabel] is the server's own name for the plan and is shown as written. The app has no better
 * name for a plan it did not define, and translating one would put words in the service's mouth.
 */
data class HomeSubscription(
    /** Null where the server named no plan — the card then names the membership instead. */
    val planLabel: String?,
    /** The expiry as the server dated it, already formatted; null when it does not expire. */
    val expiresLabel: String? = null,
    /**
     * Whole days left, when that could be worked out.
     *
     * Carried separately from [expiresLabel] because it is the part a reader acts on, and because
     * it is prose rather than a market figure — so it is written in Persian digits while the date
     * beside it stays Latin and comparable.
     */
    val daysRemaining: Int? = null,
    /** Whether the plan is close enough to its end to say so rather than only show a date. */
    val endingSoon: Boolean = false,
    /** The server's VIP flag, shown only when set. */
    val isVip: Boolean = false,
)

/** An open position the reader can jump into. */
data class HomeSignal(
    val id: Long,
    val title: String,
    val entryLabel: String,
    val stopLabel: String,
    val targetLabel: String,
    val progressLabel: String? = null,
    val isUp: Boolean = true,
)
