package com.coinepro.core.navigation

/**
 * The five bottom-navigation destinations, in display order.
 *
 * [route] is repository-owned identity and must stay stable — deep links, saved back-stack state
 * and the cross-phase consistency gate all key off it. [labelRes] is presentation and changes with
 * the reader's language, so it is deliberately not a constant here.
 *
 * ### Why this set, and why it is smaller than the one it replaces
 *
 * The bar held six: Home, Explore, Chart, Signals, AI and Community. Every one of them was a real
 * screen and none of them was wrong on its own, and together they were the fault the owner named:
 * «آپ شل حس Crypto/Fintech Dashboard می‌دهد». A bar that grows one position per feature is a
 * feature catalogue, and a feature catalogue is what a dashboard has instead of a workspace.
 *
 * The rule this set is built on is that **root navigation represents the jobs somebody opens the
 * app to do, not the modules the app contains.** There are five of those:
 *
 *  * [WATCHLIST] — *what am I watching?* The single most-used surface of every terminal there is,
 *    and until now it was a sub-screen two taps down. It is first because it is where a reader
 *    lands when they have no other question.
 *  * [CHART] — *show me the chart.* Unchanged, and deliberately: the chart is this app's strongest
 *    surface and the benchmark the rest is being brought up to.
 *  * [EXPLORE] — *what is happening?* The market, ranked, with the news, the calendar and the heat
 *    map behind it.
 *  * [IDEAS] — *is there an opportunity?* Signals and the community board are two answers to one
 *    question and had a tab each; they are one destination with two faces now.
 *  * [MENU] — *what else is there?* The directory. It is what stops this list from growing again.
 *
 * ### What left the bar, and what that does and does not mean
 *
 * **Home** is a portfolio and account dashboard — a balance, a subscription, shortcuts. That is a
 * thing a person visits, not a thing they open the app to do, and it was the first screen of every
 * launch. It keeps its route and moves into the menu.
 *
 * **AI** is this product's differentiator and the wrong shape for a tab: nobody opens an app to
 * "do some AI", they ask a question about *the thing in front of them*. It is contextual now — on
 * the chart, on a symbol, on a signal — with the full assistant still in the menu.
 *
 * **Signals** and **Community** merged into [IDEAS] rather than being removed.
 *
 * Removing a destination from this bar removes it from the bar and nothing else. Every route named
 * above still exists in the graph, still resolves from a saved back stack, and still answers a deep
 * link. See `CoineProApp.kt`, where the four are now plain route constants.
 */
enum class AppDestination(
    val route: String,
    val labelRes: Int,
    val mark: String,
) {
    /**
     * The reader's own list, promoted from a sub-screen to the app's front door.
     *
     * The route is the one it already had. A watchlist that was reachable only through the menu or
     * a search result was the clearest single symptom of a shell built around a dashboard: in
     * every terminal this product is measured against, the list a person curated themselves is the
     * first thing they see.
     */
    WATCHLIST("watchlist", R.string.nav_watchlist, "W"),

    CHART("chart-tab", R.string.nav_chart, "C"),

    /**
     * The markets, plus what is moving them.
     *
     * It replaced `MARKETS` in this position rather than being added beside it, and both halves of
     * that are deliberate. **Added** would have made another tab, and the premise of this bar is
     * that a reader learns five positions and they do not move. **This position** because Explore
     * is the markets screen with more on it: the same catalogue, ranked the same way, with the
     * day's move and a spark line on each card, plus the doors to news, the calendar and the heat
     * map that a reader previously had to go looking for.
     *
     * The full list did not go anywhere — Explore's own «همه‌ی بازارها» opens it, and the menu keeps
     * its row. A strip of cards is a taste of a catalogue and not the catalogue, and a reader who
     * came for all of them must not have to discover that the screen they used yesterday still
     * exists.
     *
     * The route is `explore` rather than `markets`: they are different destinations and a saved
     * back stack holding one must not resolve to the other.
     */
    EXPLORE("explore", R.string.nav_explore, "E"),

    /**
     * Signals and the community board, which are one question with two answers.
     *
     * They had a tab each, and the pair took a third of the bar to say "somebody thinks there is
     * an opportunity here" twice — once from a model and once from another reader. A new route
     * rather than reusing `signals`: this destination is neither of the two screens, it is the
     * frame that holds both, and a saved back stack that names `signals` must still open the
     * signals screen on its own rather than a tabbed page scrolled to it.
     */
    IDEAS("ideas", R.string.nav_ideas, "I"),

    /**
     * Everything else, grouped.
     *
     * The route is the one the menu already had, reached from an icon in the top bar. It is a tab
     * now because that icon was the only way to the thirty screens behind it, and an app whose
     * secondary surfaces live behind one unlabelled glyph is an app whose secondary surfaces are
     * not found. It is also what makes the four positions above it defensible: a directory is the
     * pressure valve that stops a bar from growing a seventh tab the next time a feature ships.
     */
    MENU("menu", R.string.nav_menu, "≡"),
}
