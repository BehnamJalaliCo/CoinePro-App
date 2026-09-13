package com.coinepro.core.datastore

/**
 * How much of this app a reader has asked to see (run Ω3).
 *
 * ### Why one question, asked once, at the very start
 *
 * This app has eighty-three indicators, fifty drawing tools, a scripting language, a depth-of-market
 * ladder, multi-chart layouts, a replay engine and a backtester. To somebody who has used a terminal
 * that is the reason to install it. To somebody opening their first chart it is a wall, and the wall
 * is met *before* they have seen a single candle — which is the moment the install is decided.
 *
 * The usual answers are both bad. Shipping the full surface and hoping people grow into it loses the
 * beginner in the first minute. Shipping a cut-down app and hiding the rest behind «advanced» loses
 * the trader, who reads a missing control as a missing feature and leaves to check whether the other
 * app still has it.
 *
 * So: **one question, three answers, and nothing is ever removed.** [SIMPLE] hides the controls a
 * beginner cannot use yet from the *chrome*; every one of them is still built, still tested, and
 * one tap away the moment they ask. [PRO] is the whole surface from the first frame. [TRADER] is
 * the middle and the default for anybody who skips the question, because it is the answer that is
 * wrong in the smallest way for the most people.
 *
 * ### Why this is not a permission or a tier
 *
 * Nothing here gates a feature. A reader in [SIMPLE] who taps «show me everything» is in [PRO] on
 * the next frame with their chart, their drawings and their alerts intact, and a reader in [PRO] can
 * go the other way just as fast. It is a statement about *what to put on the screen*, which is why
 * it lives beside [ThemeMode] rather than anywhere near an entitlement.
 *
 * ### Why absent is not the same as [TRADER]
 *
 * [chosen] distinguishes «this reader answered, and said Trader» from «nobody has been asked yet»,
 * and the first launch needs that difference: an install that predates this setting must not be shown
 * a question about a choice it has effectively already made, and a fresh install must not silently
 * get the default without being asked. `UserPreferencesStore.readerMode` resolves to [TRADER] either
 * way; `readerModeChosen` is what the first-run question reads.
 */
enum class ReaderMode(
    /** Stable key for storage. Never localise, never reuse for a different meaning. */
    val id: String,
) {
    /**
     * The chart, the Signal Layer, Explain, alerts and the watchlist. Nothing else in the chrome.
     *
     * What it hides: the drawing rail, the depth-of-market ladder, the NamaScript editor, multi-chart
     * layouts and the replay controls. Every one of those is still in the build and still reachable —
     * see `ReaderMode.showsAdvancedChrome`, which is the one place the hiding is decided.
     */
    SIMPLE("simple"),

    /**
     * Everything a trader reaches for, which is everything except the two surfaces that are a
     * project of their own: the script editor and the multi-chart workbench.
     *
     * The default, and the answer somebody who dismisses the question gets.
     */
    TRADER("trader"),

    /** The whole surface, from the first frame. */
    PRO("pro"),
    ;

    /**
     * Whether the chrome carries the drawing rail, the ladder, the editor, multi-chart and replay.
     *
     * One property rather than five, and one place rather than at each control: the failure this
     * prevents is a mode that hides four of the five and leaves the fifth, which reads as a bug in
     * the app rather than as a mode.
     */
    val showsAdvancedChrome: Boolean get() = this != SIMPLE

    /** Whether the script editor and the multi-chart workbench appear at all. */
    val showsWorkbench: Boolean get() = this == PRO

    /**
     * Whether a tap on a market opens the preview first, or the chart itself.
     *
     * The preview answers «what is this one doing» in one sheet — the price, six spans of shape, a
     * scrub and a reading — without the route, the candle request and the terminal layout a chart
     * costs. For most readers that is the question they were actually asking, and the chart is one
     * more tap away on the sheet.
     *
     * [PRO] skips it, and only [PRO]: somebody who asked for the whole surface from the first frame
     * taps a market because they want the chart, and a sheet in front of it is a toll on the thing
     * they do most. This is the «setting to skip for pros» — it is this setting, rather than a
     * fourth switch asking the same question in different words.
     */
    val opensPreviewOnTap: Boolean get() = this != PRO

    companion object {
        /** Reads a stored id back, falling forward to [TRADER] for anything unrecognised. */
        fun fromId(id: String?): ReaderMode = entries.firstOrNull { it.id == id } ?: TRADER
    }
}
