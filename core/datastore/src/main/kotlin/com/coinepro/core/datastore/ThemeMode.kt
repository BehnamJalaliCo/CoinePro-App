package com.coinepro.core.datastore

/**
 * Which of the two palettes the app draws with.
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
 * ### Three values, not two
 *
 * [SYSTEM] has to exist and has to be the default, or the first launch has to guess. It is also
 * the only value that keeps working when the phone switches at sunset.
 */
enum class ThemeMode(
    /** Stable key for storage. Never localise, never reuse for a different meaning. */
    val id: String,
) {
    /** Follow the phone. The default, and what every install before this setting existed had. */
    SYSTEM("system"),

    /** Always the dark palette, whatever the phone says. */
    DARK("dark"),

    /** Always the light palette. */
    LIGHT("light");

    companion object {
        /** Reads a stored id back, falling forward to [SYSTEM] for anything unrecognised. */
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
