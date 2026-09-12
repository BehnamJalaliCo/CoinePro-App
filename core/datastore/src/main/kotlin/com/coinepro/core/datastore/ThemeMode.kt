package com.coinepro.core.datastore

/**
 * Which palette the app draws with.
 *
 * ### Why this is a setting and not just the system's business
 *
 * The app followed `isSystemInDarkTheme()` and offered no switch, on the reasonable argument that
 * the phone already has one and a second copy of a platform control is clutter. Reading a corpus
 * of Persian-language reviews of this category of app moved the argument: an explicit theme
 * control is the single most requested thing in them — ahead of chart features, ahead of speed,
 * ahead of support. It is not requested because people cannot find the system setting. It is
 * requested because a trading app is the one app somebody wants pinned dark while their phone
 * stays light, and following the system takes that choice away.
 *
 * ### Four values, not two
 *
 * [SYSTEM] has to exist and has to be the default, or the first launch has to guess. It is also
 * the only value that keeps working when the phone switches at sunset.
 *
 * [MIDNIGHT] is the fourth and arrived in run Ω2. It is not a second dark theme in the sense of a
 * second set of choices — it is the dark theme's ladder shifted down onto true black, so on an OLED
 * panel the page and the chart's pane are switched off rather than lit. It is a separate value here
 * rather than a boolean beside [DARK] because a reader picking a theme is picking one thing from a
 * list, and a list of three with a checkbox under one of them is a worse version of a list of four.
 */
enum class ThemeMode(
    /** Stable key for storage. Never localise, never reuse for a different meaning. */
    val id: String,
) {
    /** Follow the phone. The default, and what every install before this setting existed had. */
    SYSTEM("system"),

    /** Always the dark palette, whatever the phone says. */
    DARK("dark"),

    /**
     * Always the dark palette on true black — `CoineProMidnightPalette`.
     *
     * Dark by every question the app asks about it ([isDark]); black only in what it draws on.
     */
    MIDNIGHT("midnight"),

    /** Always the light palette. */
    LIGHT("light");

    /**
     * Whether this mode draws a dark palette, for the modes that know their own answer.
     *
     * Null for [SYSTEM], which is the whole of its meaning: it has no answer of its own and takes
     * the phone's. Callers that need a boolean read this and fall back to
     * `isSystemInDarkTheme()` — which keeps the one `when` over the four values in this file rather
     * than in every screen that needs to know.
     */
    val isDark: Boolean?
        get() = when (this) {
            SYSTEM -> null
            DARK, MIDNIGHT -> true
            LIGHT -> false
        }

    /** Whether this mode wants the true-black ladder. Only [MIDNIGHT] does. */
    val isMidnight: Boolean get() = this == MIDNIGHT

    companion object {
        /** Reads a stored id back, falling forward to [SYSTEM] for anything unrecognised. */
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
