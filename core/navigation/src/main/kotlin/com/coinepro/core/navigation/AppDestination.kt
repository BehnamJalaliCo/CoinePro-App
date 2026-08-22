package com.coinepro.core.navigation

enum class AppDestination(
    val route: String,
    val label: String,
    val mark: String,
) {
    HOME("home", "Home", "H"),
    SIGNALS("signals", "Signals", "S"),
    AI("ai", "AI", "AI"),
    TOOLS("tools", "Tools", "T"),
    ACTIVITY("activity", "Activity", "A"),
}
