package com.coinepro.core.navigation

/**
 * The five bottom-navigation destinations, in display order.
 *
 * [route] is repository-owned identity and must stay stable — deep links, saved back-stack state
 * and the cross-phase consistency gate all key off it. [labelRes] is presentation and changes with
 * the reader's language, so it is deliberately not a constant here.
 *
 * The set is the owner's, and two things about it are worth writing down. **Markets and Chart earn
 * tabs** because they are the two surfaces somebody opens this app to look at; before this they
 * were both several taps deep behind a search field. **Tools lost its tab** to the AI section and
 * moved into Home — the toolkit is a place people visit deliberately, once, and a permanent fifth
 * of the bar for a calculator was the wrong trade against a screen the product is built around.
 */
enum class AppDestination(
    val route: String,
    val labelRes: Int,
    val mark: String,
) {
    HOME("home", R.string.nav_home, "H"),

    /**
     * The markets, plus what is moving them.
     *
     * It replaced `MARKETS` in this position rather than being added beside it, and both halves of
     * that are deliberate. **Added** would have made a sixth tab, and the premise of this bar is
     * that a reader learns five positions and they do not move. **This position** because Explore
     * is the markets screen with more on it: the same catalogue, ranked the same way, with the
     * day's move and a spark line on each card, plus the doors to news, the calendar and the heat
     * map that a reader previously had to go looking for.
     *
     * The full list did not go anywhere — Explore's own «همهٔ بازارها» opens it, and the menu keeps
     * its row. A strip of cards is a taste of a catalogue and not the catalogue, and a reader who
     * came for all of them must not have to discover that the screen they used yesterday still
     * exists.
     *
     * The route is `explore` rather than `markets`: they are different destinations and a saved
     * back stack holding one must not resolve to the other.
     */
    EXPLORE("explore", R.string.nav_explore, "E"),
    CHART("chart-tab", R.string.nav_chart, "C"),
    SIGNALS("signals", R.string.nav_signals, "S"),
    AI("ai", R.string.nav_ai, "AI"),
}
