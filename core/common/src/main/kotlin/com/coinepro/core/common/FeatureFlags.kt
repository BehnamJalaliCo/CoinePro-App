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
     * and their watchlist row. What goes is the *account*: the introducing-broker links and every
     * row that leads to one.
     *
     * They go **absent, never greyed**. A dimmed control is an advertisement for something the
     * reader cannot have, and on a screen about money it reads as a fault rather than a choice.
     *
     * **Copy trading is not on this flag's list any more, and that is not an omission** (run Ψ).
     * It used to be the largest thing the flag hid. The product no longer has the feature at all:
     * the screen, the controller, the gateway, the MetaTrader card on Connections and the two
     * modules behind them are deleted rather than switched off. On the forex side this app is a
     * gold signal and a chart — see `ForexSignalScope` — and nothing mirrors anybody's orders onto
     * a broker account. Turning this flag back on would restore the broker links; it would not
     * restore copy trading, because there is nothing left to restore.
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

    /**
     * Whether this is the **store build** (5.22.0): the Android app as Cafe Bazaar publishes it.
     *
     * Bazaar publishes finance apps — wallets, exchanges and anything near them — only from a
     * company developer account, and refused 5.19.4 for its membership and exchange-UID screens.
     * The owner's answer is a store build without everything of that kind: membership and the
     * exchange UID, broker and exchange connections, LBank live trading, the portfolio, identity
     * verification, signals, and the AI setup builder, assistant and chart-image analysis.
     *
     * They go **absent, never greyed**, as [forexTrading] does: no menu row, no search result, no
     * card, no button, and a route that pops itself if anything still reaches it. True only on
     * Android — `CoineProApplication.onCreate` sets it — so the web, whose start-up never runs that
     * class, keeps every one of them. See [STORE_RESTRICTED_SURFACES].
     */
    var storeRestricted: Boolean = STORE_RESTRICTED_DEFAULT

    /** What a shipping build starts with. Tests restore these; nothing else writes them. */
    const val FOREX_TRADING_DEFAULT: Boolean = false
    const val ALL_UNLOCKED_DEFAULT: Boolean = true
    const val STORE_RESTRICTED_DEFAULT: Boolean = false

    /**
     * The menu and search ids the store build drops. One list, read by both the menu and the
     * search screen, so the two cannot disagree about what the store build is.
     */
    val STORE_RESTRICTED_SURFACES: Set<String> = setOf(
        "membership",
        "connections",
        "portfolio",
        "verify",
        "signals",
        "ai",
        "ai-vision",
        "ai-assistant",
        // The web terminal is the whole site in a WebView — every surface above, one tap in.
        "terminal",
    )

    /**
     * Where the store build sends a reader for the rest: the site carries every surface above.
     * Named in the menu, the tutorials and the safety page — never beside a price or a plan.
     */
    const val FULL_SITE_URL: String = "https://pro-chart.com"

    /**
     * The web's desktop chart page reads left to right whatever the language (5.23.0), exactly as
     * TradingView's does: the toolbar starts at the left edge with the symbol, the bottom bar with the
     * ranges. Set by the web entry only; the phone and the tablet keep the reader's direction.
     */
    var desktopShellLtr: Boolean = false

    /**
     * Whether this is the browser build (5.25.1). Set by the web entry only. For copy that is true
     * of an app and false of the site — «Telegram sign-in works on the web version» was being shown
     * on the web version itself.
     */
    var webBuild: Boolean = false

    /**
     * Whether Pro can be bought (5.24.0; on since 5.25.0 — Cafe Bazaar on the phone, USDT on the
     * site). Off: the Pro page shows «به‌زودی» on every plan.
     *
     * Not the paywall. Buying is open while the server's `PAYWALL_ENABLED` is still off, so every
     * tool stays free for everybody and a buyer's period is already running when the limits arrive.
     */
    const val billingLive: Boolean = true

    /** Puts them all back, for a test that changed one. */
    fun reset() {
        forexTrading = FOREX_TRADING_DEFAULT
        allUnlocked = ALL_UNLOCKED_DEFAULT
        storeRestricted = STORE_RESTRICTED_DEFAULT
        desktopShellLtr = false
    }
}
