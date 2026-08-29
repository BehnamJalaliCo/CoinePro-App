package com.coinepro.feature.screener.model

/**
 * A saved screen: a name, the conditions behind it, how the table is ordered and what it shows.
 *
 * ### The name collides with the composable, and the sub-package is the reason
 *
 * `ScreenerScreen` is both the natural name for a saved screener and the name every other feature
 * module in this app gives its top-level composable — `MarketsScreen`, `SearchScreen`,
 * `ToolsScreen`. Renaming either one would be worse than the collision: a model called
 * `SavedScreenerConfiguration` reads like a framework, and a composable called `ScreenerPage` would
 * be the one screen in the app that does not match its neighbours. So the pure model lives in
 * `…screener.model` and the composable in `…screener`, and the one file that needs both imports
 * this one under an alias.
 *
 * ### There is no limit on how many of these a reader may keep
 *
 * Not an oversight, and not a number waiting to be tuned. The obvious competitor has seven
 * screeners on the web, none at all on a phone, and sells the saved-screen slots on the ones it
 * does have. This app gives the feature away: no cap on saved screens, no paywall, no gating, no
 * indicator held back for a paid tier. [ScreenerStore] carries a ceiling in the five hundreds
 * purely so that a bug in a caller cannot grow a preferences string without bound — it is a fuse,
 * not a product limit, and no person reaches it.
 *
 * ### [id] and [name] are different things
 *
 * The id is generated once and never changes, so renaming a screen does not orphan it in storage
 * and does not break the selection the screener is currently showing. The name is whatever the
 * reader typed and this module never invents, translates or trims it beyond refusing the control
 * characters the encoding uses as separators.
 */
data class ScreenerScreen(
    val id: String,
    val name: String,
    val filters: List<ScreenerFilter> = emptyList(),
    val sort: ScreenerSort = ScreenerSort.DEFAULT,
    val columns: List<ScreenerField> = ScreenerField.DEFAULT_COLUMNS,
) {
    /**
     * Every row that satisfies every filter, in this screen's order.
     *
     * Pure, so a saved screen can be evaluated in a test with no controller, no gateway and no
     * coroutine — which is the whole reason the filters are pure too.
     */
    fun apply(rows: List<ScreenerRow>): List<ScreenerRow> =
        sort.apply(rows.filter { ScreenerFilter.allMatch(filters, it) })

    /** The indicator readings this screen needs resolved before its filters can answer. */
    val requiredIndicators: Set<String> get() = ScreenerFilter.indicatorKeys(filters)
}

/**
 * The screens the app ships with, offered as starting points rather than as fixtures.
 *
 * They exist because an empty filter sheet is the hardest screen in any screener to face: a reader
 * who has never built one does not know that «تغییر روزانه بیشتر از ۳» is a sentence they are
 * allowed to write. Each of these is one or two conditions, so opening one and reading it is a
 * complete explanation of how the sheet works.
 *
 * A preset is applied by *copying* it into the reader's own screen. Nothing here is protected and
 * nothing is special: once applied it is an ordinary screen that can be edited, renamed and saved
 * beside the ones the reader wrote.
 */
object ScreenerPresets {

    /** Markets up the most today. The screen everybody builds first. */
    val gainers = ScreenerScreen(
        id = "preset_gainers",
        name = "بیشترین رشد امروز",
        filters = listOf(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 0.0)),
        sort = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = true),
    )

    /** The mirror, and the reason the sort direction is part of a saved screen. */
    val losers = ScreenerScreen(
        id = "preset_losers",
        name = "بیشترین افت امروز",
        filters = listOf(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.LT, 0.0)),
        sort = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = false),
    )

    /**
     * A fourteen-bar RSI under thirty — the textbook oversold reading.
     *
     * This is the preset that makes the point of [109] without a word of marketing: it is one tap,
     * it is free, and the product it is measured against does not offer it on a phone at all.
     */
    val oversold = ScreenerScreen(
        id = "preset_oversold",
        name = "اشباع فروش",
        filters = listOf(
            ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, period = 14, op = NumericOp.LT, value = 30.0),
        ),
        sort = ScreenerSort(ScreenerField.RSI, descending = false),
        columns = listOf(ScreenerField.LAST_PRICE, ScreenerField.CHANGE_PERCENT, ScreenerField.RSI),
    )

    /** The other end of the same reading. */
    val overbought = ScreenerScreen(
        id = "preset_overbought",
        name = "اشباع خرید",
        filters = listOf(
            ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, period = 14, op = NumericOp.GT, value = 70.0),
        ),
        sort = ScreenerSort(ScreenerField.RSI, descending = true),
        columns = listOf(ScreenerField.LAST_PRICE, ScreenerField.CHANGE_PERCENT, ScreenerField.RSI),
    )

    /**
     * Markets sitting within one percent of the day's high.
     *
     * A breakout screen, and the one that shows why [ScreenerField.DISTANCE_FROM_HIGH] is a column
     * rather than something a reader is expected to work out from a high and a price.
     */
    val nearHigh = ScreenerScreen(
        id = "preset_near_high",
        name = "نزدیک سقف روز",
        filters = listOf(
            ScreenerFilter.Numeric(ScreenerField.DISTANCE_FROM_HIGH, NumericOp.LTE, 1.0),
        ),
        sort = ScreenerSort(ScreenerField.DISTANCE_FROM_HIGH, descending = false),
        columns = listOf(ScreenerField.LAST_PRICE, ScreenerField.DISTANCE_FROM_HIGH, ScreenerField.RANGE_PERCENT),
    )

    /**
     * A trending market that is not yet stretched: ADX above twenty-five, RSI still under seventy.
     *
     * Two indicator conditions in one screen, which is the shape a real screen takes and the thing
     * a single-condition preset cannot demonstrate.
     */
    val trending = ScreenerScreen(
        id = "preset_trending",
        name = "روند قوی",
        filters = listOf(
            ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.ADX, period = 14, op = NumericOp.GT, value = 25.0),
            ScreenerFilter.IndicatorFilter(ScreenerIndicatorId.RSI, period = 14, op = NumericOp.LT, value = 70.0),
        ),
        sort = ScreenerSort(ScreenerField.ADX, descending = true),
        columns = listOf(ScreenerField.LAST_PRICE, ScreenerField.ADX, ScreenerField.RSI),
    )

    /** All of them, in the order the sheet offers them. */
    val all: List<ScreenerScreen> = listOf(gainers, losers, oversold, overbought, nearHigh, trending)
}
