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
    MARKETS("markets", R.string.nav_markets, "M"),
    CHART("chart-tab", R.string.nav_chart, "C"),
    SIGNALS("signals", R.string.nav_signals, "S"),
    AI("ai", R.string.nav_ai, "AI"),
}
