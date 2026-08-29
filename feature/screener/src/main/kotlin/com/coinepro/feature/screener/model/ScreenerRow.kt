package com.coinepro.feature.screener.model

import com.coinepro.core.symbols.SymbolMeta

/**
 * One market as the screener sees it: everything a filter can ask about, and nothing else.
 *
 * ### Every figure is nullable, and that is the design
 *
 * Neither backend's snapshot carries a day's high, low, volume or change — the snapshot is a price
 * and a timestamp, and that is all it has ever been. Those figures come from the market's own bars,
 * which are a request per symbol; a catalogue of a thousand markets cannot be a thousand requests
 * before the first row draws. So a row starts with a price and fills in as it is resolved, and a
 * field that has not arrived is **null rather than zero**.
 *
 * The distinction is not pedantry. Zero volume is a claim — it says this market did not trade —
 * and a screener that fabricates it will happily report that four hundred instruments were flat all
 * day. Null says "not known yet", every filter treats it as "not a match", and the row appears when
 * the answer does.
 *
 * ### [indicators]
 *
 * Keyed by [ScreenerIndicatorId.normalisedKey], so the same map holds `rsi:14` and `rsi:2` side by
 * side without either one answering for the other. Empty until the row has been resolved, which is
 * the same nullability rule one level down.
 */
data class ScreenerRow(
    val meta: SymbolMeta,
    /** The live or catalogue price. The one figure that is almost always present. */
    val price: Double? = null,
    val changePercent: Double? = null,
    val changeAbsolute: Double? = null,
    val volume: Double? = null,
    /** Turnover in the quote currency — volume times the day's typical price. */
    val quoteVolume: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    /** Indicator readings, by [ScreenerIndicatorId.normalisedKey]. */
    val indicators: Map<String, Double> = emptyMap(),
    /** Which platform quotes it — `CRYPTO` or `FOREX`. Used by [ScreenerField.MARKET]. */
    val market: String? = null,
) {
    /** The symbol, which is this row's identity everywhere including the list's item key. */
    val symbol: String get() = meta.symbol

    /**
     * True once the day's bar has been read for this market.
     *
     * A row can be resolved and still have a null [volume] — the MT5 side reports none — so this
     * asks about the high and the low, which every bar has.
     */
    val resolved: Boolean get() = high != null && low != null

    /** The day's range as a share of its low. Null when the bar is not in yet, or the low is zero. */
    val rangePercent: Double?
        get() {
            val top = high ?: return null
            val bottom = low ?: return null
            if (bottom <= 0.0) return null
            return (top - bottom) / bottom * 100.0
        }

    /** How far under the day's high the price sits, as a positive percentage. */
    val distanceFromHigh: Double?
        get() {
            val top = high ?: return null
            val at = price ?: return null
            if (top <= 0.0) return null
            return (top - at) / top * 100.0
        }

    /** How far above the day's low the price sits, as a positive percentage. */
    val distanceFromLow: Double?
        get() {
            val bottom = low ?: return null
            val at = price ?: return null
            if (bottom <= 0.0) return null
            return (at - bottom) / bottom * 100.0
        }

    /**
     * The numeric value of one field, or null where it is not known.
     *
     * The single place a [ScreenerField] becomes a number. A filter, a sort and a table cell all go
     * through here, so a field added to the enum without a case is caught once rather than being
     * wrong in three places — and a categorical field answers null, which is what makes
     * `Numeric(ASSET_CLASS, …)` a filter that matches nothing rather than one that crashes.
     */
    fun valueOf(field: ScreenerField): Double? = when (field) {
        ScreenerField.LAST_PRICE -> price
        ScreenerField.CHANGE_PERCENT -> changePercent
        ScreenerField.CHANGE_ABSOLUTE -> changeAbsolute
        ScreenerField.VOLUME -> volume
        ScreenerField.QUOTE_VOLUME -> quoteVolume
        ScreenerField.HIGH -> high
        ScreenerField.LOW -> low
        ScreenerField.RANGE_PERCENT -> rangePercent
        ScreenerField.DISTANCE_FROM_HIGH -> distanceFromHigh
        ScreenerField.DISTANCE_FROM_LOW -> distanceFromLow
        ScreenerField.MARKET, ScreenerField.ASSET_CLASS, ScreenerField.QUOTE_CURRENCY -> null
        else -> field.indicatorKey?.let(indicators::get)
    }

    /**
     * The categorical value of one field, upper-cased, or null where the field is numeric or the
     * market has no answer — an index has no quote currency, and inventing `USD` for it would put
     * it in a filter a reader meant to exclude it from.
     */
    fun textOf(field: ScreenerField): String? = when (field) {
        ScreenerField.MARKET -> market?.uppercase()
        ScreenerField.ASSET_CLASS -> meta.category.name
        ScreenerField.QUOTE_CURRENCY -> meta.quote?.uppercase()
        else -> null
    }
}
