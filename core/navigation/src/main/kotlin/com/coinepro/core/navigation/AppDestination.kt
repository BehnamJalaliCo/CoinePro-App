package com.coinepro.core.navigation

/**
 * The five bottom-navigation destinations, in display order.
 *
 * [route] is repository-owned identity and must stay stable — deep links, saved back-stack state
 * and the cross-phase consistency gate all key off it. [labelRes] is presentation and changes with
 * the reader's language, so it is deliberately not a constant here.
 */
enum class AppDestination(
    val route: String,
    val labelRes: Int,
    val mark: String,
) {
    HOME("home", R.string.nav_home, "H"),
    SIGNALS("signals", R.string.nav_signals, "S"),
    AI("ai", R.string.nav_ai, "AI"),
    TOOLS("tools", R.string.nav_tools, "T"),
    ACTIVITY("activity", R.string.nav_activity, "A"),
}
