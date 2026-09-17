package com.coinepro.core.common

/**
 * The switches that decide what kind of product this build is.
 *
 * Not settings. A reader never sees these and nothing in the app writes them: they are the shape of
 * the release, set once here, read everywhere. Two of them exist, they are the whole of phase Φ's
 * positioning, and both were chosen so that the *gating code stays where it is* — turning either
 * back on is a word in this file, not a project.
 *
 * ### Why `var` and not `const`
 *
 * Because both states have to be tested. A `const val false` is a branch nothing can ever exercise,
 * and the half of the app that comes back when the owner flips it would be a half nobody has
 * compiled against in months. These are written once at process start (they are never written at
 * all in a shipping build) and read from everywhere, including composition — which is safe only
 * because they do not change while a screen is up. Nothing here is a `mutableStateOf` and nothing
 * here recomposes: a flag that moved under a reader would be a different feature.
 */
object FeatureFlags {

    /**
     * Whether this build offers a way to **trade forex** (F5).
     *
     * False, and that is the product decision: «خانه‌ی تحلیل کریپتو — با یک نگاه به طلا و دلار».
     * Gold and the dollar are what this app was built around and they are not going anywhere — they
     * keep their chart, their indicators, their drawings, their alerts, their scripts, their replay
     * and their watchlist row. What goes is the *account*: the introducing-broker links, the
     * MetaTrader connection, copy trading and every row that leads to one of them.
     *
     * They go **absent, never greyed**. A dimmed control is an advertisement for something the
     * reader cannot have, and on a screen about money it reads as a fault rather than a choice.
     *
     * Untouched either way (F6): the LBank connection that places crypto orders, the account
     * verification the crypto venue requires, every alert, every signal, and every read-only
     * forex, metal and index market.
     */
    var forexTrading: Boolean = FOREX_TRADING_DEFAULT

    /**
     * Whether **everything is unlocked for everybody** (F8).
     *
     * True until the hundred-thousandth install. Every wall in the app — the academy's levels, the
     * signal history, the saved-layout limit, anything marked for a paid tier — reads this and
     * opens. What it does *not* do is delete the wall: every `*_locked_tier` check is still where it
     * was, still compiled, and `EntitlementGateTest` drives them with this false so the day it goes
     * back the walls come back with it.
     *
     * **Server-fed, with this as the local default** (run Τ2, B2). `EntitlementStore` keeps whatever
     * the backend last served and `Entitlements.applyAtStart` writes it here before the first screen;
     * where nothing has ever been served — no route yet, a first launch offline — this value stands,
     * and it is `true`. A missing answer must never read as a locked app.
     */
    var allUnlocked: Boolean = ALL_UNLOCKED_DEFAULT

    /** What a shipping build starts with. Tests restore these; nothing else writes them. */
    const val FOREX_TRADING_DEFAULT: Boolean = false
    const val ALL_UNLOCKED_DEFAULT: Boolean = true

    /** Puts both back, for a test that changed one. */
    fun reset() {
        forexTrading = FOREX_TRADING_DEFAULT
        allUnlocked = ALL_UNLOCKED_DEFAULT
    }
}
