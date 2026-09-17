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
 *  * [RASAD] — *what is happening?* The briefing: an assistant that reads the board and says three
 *    sentences about it. The position was Explore, and run ΤΦΥ (U5, U7) moved that screen's content
 *    under the markets tabs, because it was the markets screen with more on it.
 *  * [COMMUNITY] — *is there an opportunity?* Signals and the community board are two answers to one
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
 * **Signals** and **Community** merged into [COMMUNITY] rather than being removed.
 *
 * **Explore** left in run ΤΦΥ (U7). Its catalogue is the markets surface's tabs and its three
 * rooms — news, the calendar, the heat map — are drawn above those tabs. The route is still
 * registered and still resolves.
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
     * **رَصد** — the briefing, and the one seat this product's differentiator was not holding.
     *
     * The position used to be Explore: the catalogue with the day's move and a spark line on each
     * card, plus the doors to news, the calendar and the heat map. That was a good screen in the
     * wrong place, and run ΤΦΥ (U5, U7) says why: **it is the markets screen with more on it**, and
     * the markets screen now carries the same content under its own tabs — «برتر», «پرطرفدار»,
     * «حجم», «فارکس», «فلزات». Two doors onto one catalogue is one door too many, and the one that
     * had to go is the one that was not the catalogue.
     *
     * What takes the seat is the thing no other terminal has: an assistant that reads the board and
     * says three sentences about it. It was on the home screen, which left this bar in run Ω2 for
     * being a dashboard — and it took the briefing down with it, which was the mistake. The route is
     * `home`, unchanged, so a saved back stack and a deep link both still resolve; what changes is
     * that the screen is in the bar under the name of the thing a reader actually opens it for.
     *
     * `explore` is still a route and still registered. It is reached from the markets surface now
     * rather than from the bar — see `EXPLORE_ROUTE` in `CoineProApp.kt`.
     */
    RASAD("home", R.string.nav_rasad, "R"),

    /**
     * **انجمن** — the board, with the signal list as its second face.
     *
     * The route and the frame are unchanged; the **name** is not. It was «ایده‌ها», which is a word
     * for the pair and a word for neither: a reader looking for what other readers are saying does
     * not look for «ideas», they look for the forum. Run ΤΦΥ (U7) names it what it is and opens it
     * on the board, and the signal list is the switch beside it — one tap, where it has always been.
     *
     * A route of its own rather than a redirect, still: `signals` and `community` are both routes,
     * and a saved back stack naming one must open that screen alone rather than a tabbed page.
     */
    COMMUNITY("ideas", R.string.nav_community, "C"),

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
