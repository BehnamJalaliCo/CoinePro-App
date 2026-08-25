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
 * The account total, already formatted.
 *
 * Formatting happens at the call site because only the caller knows the currency and the precision
 * the server reported; the screen must not re-round a balance.
 */
data class HomePortfolio(
    val totalLabel: String,
    val changeLabel: String,
    val isUp: Boolean,
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
