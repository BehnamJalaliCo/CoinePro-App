package com.coinepro.feature.dom

/**
 * How one symbol's ladder was last being read: the aggregation in force, and which figure the size
 * columns were printing.
 *
 * ### Per symbol, because the right step is a property of the instrument
 *
 * A tenth is the useful bucket on a market quoted near eighty thousand and folds a market quoted at
 * half a cent into a single rung. A reader who sets the step on one instrument has said nothing at
 * all about the next one, and a global setting would make every symbol switch cost two taps — which
 * is the complaint `SymbolChartStateStore` was built to answer for the chart, in the same shape,
 * for the same reason.
 *
 * ### [step] is nullable and null is a choice, not an absence
 *
 * Null is the raw book, which is a real thing to want and is what the ladder opens on. A reader who
 * has deliberately gone back to the raw book must find it there next time rather than find whatever
 * step they had abandoned.
 */
data class DepthLadderPreference(
    val step: Double? = null,
    val figure: LadderFigure = LadderFigure.AMOUNT,
)

/**
 * Where a symbol's ladder preference is kept between visits.
 *
 * ### Why this is a port and not a store
 *
 * `core:datastore` already owns per-symbol view state — `SymbolChartStateStore`, keyed by ticker,
 * holding opaque strings so a preferences module never has to know another module's enums. This is
 * exactly that kind of state and belongs there rather than in a second file that writes a second
 * preferences key for the same reader on the same symbol. What stands in the way is only module
 * ownership: this feature cannot reach into `core:datastore` from here, so it names the two
 * operations it needs and lets whoever assembles the screen supply them.
 *
 * ### Null is a working screen, not a degraded one
 *
 * `DepthOfMarketScreen` takes this as a nullable parameter and defaults it to null, and with
 * nothing wired the reader's choice still survives recomposition and rotation — it is held in the
 * screen's own saved state. What it does not survive is leaving the screen. That is a real loss and
 * it is the reason to wire it, but it is not a broken screen, and the alternative — a required
 * parameter — would have made this feature undeliverable without a change in a module this work
 * does not own.
 *
 * Both calls suspend and neither is a flow. The preference is written by this screen and read by
 * this screen, once, when the symbol opens; a flow would be a subscription to a value nothing else
 * ever changes, and it would re-emit the screen's own write back at it mid-session.
 */
interface DepthLadderPreferences {

    /** What was stored for [symbol], or null if this reader has never set the ladder up for it. */
    suspend fun load(symbol: String): DepthLadderPreference?

    /**
     * Records [preference] against [symbol], replacing whatever was there.
     *
     * Called on every change rather than on leaving the screen. A ladder is left by the back
     * gesture and by the process being killed behind it, and a write that waits for a tidy exit is
     * a write that does not happen on the occasions readers notice.
     */
    suspend fun save(symbol: String, preference: DepthLadderPreference)
}
