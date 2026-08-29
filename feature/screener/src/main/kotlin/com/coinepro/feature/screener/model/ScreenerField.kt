package com.coinepro.feature.screener.model

/**
 * What kind of number a [ScreenerField] carries, and therefore how a row draws it.
 *
 * The renderer cannot work this out from the value: `2.41` is a price on `XAUUSD`, a percentage on
 * a day's move and a bare reading on RSI, and printing all three the same way is how a screener
 * stops being readable. So the unit travels with the field rather than being decided at the call
 * site, and there is exactly one place that maps a unit to a suffix.
 *
 * [TEXT] is the odd one and is deliberately in the same enum: a screener column can be an asset
 * class or a quote currency, and splitting categorical fields into a second type would mean two
 * column lists, two sort paths and two filter families for what a reader experiences as one table.
 */
enum class ScreenerUnit {
    /** A market price. Decimals follow the magnitude, as everywhere else in the app. */
    PRICE,

    /** A percentage. The renderer appends the sign. */
    PERCENT,

    /** A traded quantity or turnover, abbreviated once it passes a thousand. */
    VOLUME,

    /** A bare reading with no unit at all — RSI, ADX, a MACD histogram. */
    PLAIN,

    /** Not a number. Read with [ScreenerRow.textOf] and filtered with [ScreenerFilter.Category]. */
    TEXT,
}

/**
 * Every column a screener can show, filter on or sort by.
 *
 * ### Why one enum for both the quote figures and the indicator ones
 *
 * A reader does not experience «تغییر ٪» and «RSI» as different kinds of thing — both are numbers
 * per market that they want to sort a table by and put a threshold on. What differs is only where
 * the value comes from, and that is [indicatorId]: null means the figure falls out of the day's
 * bar, non-null means it has to be computed from a series first. Keeping them in one enum is what
 * lets [ScreenerScreen.columns] be a single list and the sort be a single comparator.
 *
 * ### The Persian names are here rather than in a string resource
 *
 * These are the labels of a table this module owns end to end — a filter sheet, a column header and
 * a saved screen's summary all name the same field, and a resource lookup would put the same
 * translation behind three `stringResource` calls that can drift apart. The screen's own chrome
 * (titles, buttons, empty copy) does live in `strings.xml`, where a second language belongs.
 *
 * Every label is prose, so it carries no digits at all; the *values* under it are market figures and
 * stay Latin. That split is the app's rule and this enum is where it would be easiest to break —
 * a label like «RSI 14» would bake a Latin figure into prose, so periods are never in a label.
 */
enum class ScreenerField(
    /** What the column header and the filter sheet call this. */
    val label: String,
    val unit: ScreenerUnit,
    /**
     * The indicator this field is derived from, or null for a field the day's bar already answers.
     *
     * An id rather than a function reference, because a saved screen has to survive being written
     * to disk and read back by a later build. See [ScreenerIndicatorId].
     */
    val indicatorId: String? = null,
    /** The lookback used when a filter on this field does not name one. Null where none applies. */
    val defaultPeriod: Int? = null,
) {
    // ── what the quote and the day's bar answer directly ────────────────────────────────────
    LAST_PRICE("آخرین قیمت", ScreenerUnit.PRICE),
    CHANGE_PERCENT("تغییر روزانه", ScreenerUnit.PERCENT),
    CHANGE_ABSOLUTE("تغییر مطلق", ScreenerUnit.PRICE),
    VOLUME("حجم", ScreenerUnit.VOLUME),
    QUOTE_VOLUME("ارزش معاملات", ScreenerUnit.VOLUME),
    HIGH("بیشترین", ScreenerUnit.PRICE),
    LOW("کمترین", ScreenerUnit.PRICE),

    /**
     * The day's range as a share of its low — how wide the bar is, not where the price sits in it.
     *
     * Worth a column of its own because it is the one figure that separates a market that moved
     * from a market that merely closed somewhere else. A pair can finish flat after a three percent
     * swing, and a screener that only offers «تغییر روزانه» cannot find it.
     */
    RANGE_PERCENT("دامنه روز", ScreenerUnit.PERCENT),

    /**
     * How far under the day's high the price is, as a positive percentage.
     *
     * Positive and never negative: at the high it is zero and it only grows going down. Signing it
     * would put a minus in front of every row in the column, which says nothing.
     */
    DISTANCE_FROM_HIGH("فاصله از سقف", ScreenerUnit.PERCENT),

    /** How far above the day's low the price is, as a positive percentage. The mirror of the above. */
    DISTANCE_FROM_LOW("فاصله از کف", ScreenerUnit.PERCENT),

    // ── the indicator-derived ones, which are [109] ─────────────────────────────────────────
    RSI("شاخص قدرت نسبی", ScreenerUnit.PLAIN, ScreenerIndicatorId.RSI, 14),
    ADX("شاخص روند", ScreenerUnit.PLAIN, ScreenerIndicatorId.ADX, 14),
    STOCHASTIC_K("استوکاستیک", ScreenerUnit.PLAIN, ScreenerIndicatorId.STOCHASTIC_K, 14),
    MACD_HISTOGRAM("هیستوگرام مکدی", ScreenerUnit.PLAIN, ScreenerIndicatorId.MACD_HISTOGRAM),
    ATR_PERCENT("نوسان روزانه", ScreenerUnit.PERCENT, ScreenerIndicatorId.ATR_PERCENT, 14),
    SMA_DISTANCE("فاصله از میانگین ساده", ScreenerUnit.PERCENT, ScreenerIndicatorId.SMA_DISTANCE, 50),
    EMA_DISTANCE("فاصله از میانگین نمایی", ScreenerUnit.PERCENT, ScreenerIndicatorId.EMA_DISTANCE, 50),
    BOLLINGER_PERCENT("جایگاه در باند بولینگر", ScreenerUnit.PLAIN, ScreenerIndicatorId.BOLLINGER_PERCENT, 20),

    // ── the categorical ones ────────────────────────────────────────────────────────────────
    /** Which backend quotes this market — `CRYPTO` or `FOREX`, as [com.coinepro.core.model.MarketType] spells it. */
    MARKET("بازار", ScreenerUnit.TEXT),

    /** The asset class, as `core:symbols` classified it: forex, crypto, metal, index, energy. */
    ASSET_CLASS("دسته", ScreenerUnit.TEXT),

    /** The leg the market is priced in — `USDT`, `USD`, `JPY`. Null on an index or a contract. */
    QUOTE_CURRENCY("ارز مبنا", ScreenerUnit.TEXT),
    ;

    /** True where the value has to be computed from a series before this field can be read. */
    val isDerived: Boolean get() = indicatorId != null

    /** True where the field is a number and so can be thresholded and sorted numerically. */
    val isNumeric: Boolean get() = unit != ScreenerUnit.TEXT

    /**
     * The key this field's value is filed under in [ScreenerRow.indicators].
     *
     * Null for a field that is not derived, which is the caller's signal to read the row's own
     * property instead of the map.
     */
    val indicatorKey: String?
        get() = indicatorId?.let { ScreenerIndicatorId.keyOf(it, defaultPeriod) }

    companion object {
        /**
         * What a new screen shows before the reader chooses columns.
         *
         * Three, and it is three rather than six because a phone row that holds six numbers holds
         * each of them in forty points, and a column of forty-point numbers cannot be compared —
         * which is the entire job of a screener table. The reader adds a fourth deliberately.
         */
        val DEFAULT_COLUMNS: List<ScreenerField> = listOf(LAST_PRICE, CHANGE_PERCENT, VOLUME)

        /** The fields a reader can put a numeric threshold on, in the order the sheet lists them. */
        val NUMERIC: List<ScreenerField> = entries.filter(ScreenerField::isNumeric)

        /** The categorical fields, which take a set of values rather than a threshold. */
        val CATEGORICAL: List<ScreenerField> = entries.filterNot(ScreenerField::isNumeric)
    }
}

/**
 * The indicator ids a screener filter may name.
 *
 * Plain string constants rather than an enum, because these are written into saved screens and read
 * back by builds that may not have all of them: an id this version does not recognise has to be
 * ignorable, and an enum would make it a parse failure that loses the whole screen. Exactly the
 * reasoning `core:datastore` gives for keeping chart ids as strings.
 */
object ScreenerIndicatorId {
    const val RSI = "rsi"
    const val ADX = "adx"
    const val STOCHASTIC_K = "stoch_k"
    const val MACD_HISTOGRAM = "macd_hist"
    const val ATR_PERCENT = "atr_percent"
    const val SMA_DISTANCE = "sma_distance"
    const val EMA_DISTANCE = "ema_distance"
    const val BOLLINGER_PERCENT = "bb_percent"

    /** Every id this build can compute, in the order the filter sheet offers them. */
    val ALL: List<String> = listOf(
        RSI, ADX, STOCHASTIC_K, MACD_HISTOGRAM, ATR_PERCENT, SMA_DISTANCE, EMA_DISTANCE, BOLLINGER_PERCENT,
    )

    /**
     * How one indicator reading is addressed inside [ScreenerRow.indicators].
     *
     * The period is part of the key and not an afterthought: «فاصله از میانگین ۲۰» and «فاصله از
     * میانگین ۲۰۰» are two different questions about the same market, and a map keyed on the id
     * alone would answer the second with the first's number and never look wrong.
     *
     * A null period means "the indicator's own default", and it is kept distinct from the default
     * spelled out — `rsi` and `rsi:14` are the same reading and the caller is expected to normalise
     * through [com.coinepro.feature.screener.model.ScreenerFilter.IndicatorFilter], which does.
     */
    fun keyOf(indicatorId: String, period: Int?): String =
        if (period == null) indicatorId else "$indicatorId:$period"

    /** The lookback [ScreenerField] declares for an indicator, or null where it takes none. */
    fun defaultPeriodOf(indicatorId: String): Int? =
        ScreenerField.entries.firstOrNull { it.indicatorId == indicatorId }?.defaultPeriod

    /**
     * [keyOf] with a null period filled in from the indicator's own default.
     *
     * This is the only key builder a filter should use. Without it a reader who left the period
     * box empty would be asking for `rsi` while the resolver had filed the answer under `rsi:14`,
     * and the filter would match nothing at all — silently, because a missing reading is not an
     * error, it is a row that has not been resolved yet.
     */
    fun normalisedKey(indicatorId: String, period: Int?): String =
        keyOf(indicatorId, period ?: defaultPeriodOf(indicatorId))
}
