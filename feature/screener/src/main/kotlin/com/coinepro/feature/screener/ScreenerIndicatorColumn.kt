package com.coinepro.feature.screener

import com.coinepro.core.common.BidiText
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerIndicatorId
import com.coinepro.feature.screener.model.ScreenerRow
import com.coinepro.feature.screener.model.ScreenerUnit

/**
 * A table column for an indicator that has no [ScreenerField] of its own — [115].
 *
 * ### Why the table grows a column a reader never asked for
 *
 * Because the alternative is a filter nobody can check. Once the sheet offers all eighty-three
 * indicators, a reader can write «TSI بیشتر از ۲۵» — and then look at a table of price, change and
 * volume with no TSI anywhere in it. They have no way to see *why* a market survived the condition,
 * no way to rank the survivors by how far past it they are, and no way to notice that the threshold
 * was an order of magnitude off. That is the shape of a feature that exists and cannot be used, and
 * this app has shipped forty-five of those once already.
 *
 * So every indicator condition on screen puts its reading in the table, automatically, next to the
 * columns the reader chose. It is derived from the filter list rather than stored beside it: there
 * is one source of truth about what is being filtered, and a column that could disagree with the
 * condition it came from would be worse than no column.
 *
 * ### The ones that already have a field are left alone
 *
 * Eight indicators are also [ScreenerField]s — RSI, ADX, the stochastic, and so on — and those can
 * be chosen as ordinary columns, sorted and saved into a screen. When one of them is already shown,
 * no second column is added for it: two columns with the same heading and the same number is the
 * kind of duplicate that makes a table look broken rather than thorough.
 */
data class ScreenerIndicatorColumn(
    /** The [ScreenerRow.indicators] key this column reads. Carries the period. */
    val key: String,
    val label: String,
    val unit: ScreenerUnit,
) {
    /** This column's value for a row, or null where the market has not been reduced yet. */
    fun valueOf(row: ScreenerRow): Double? = row.indicators[key]

    companion object {
        /**
         * The indicator columns implied by [filters], minus anything [columns] already shows.
         *
         * Order is the order the conditions were added, which is the order the reader wrote them in
         * and the only order that will not appear to shuffle as they work. Duplicates collapse: two
         * conditions on the same indicator and period — «RSI بیشتر از ۳۰» and «RSI کمتر از ۷۰» —
         * are one reading and therefore one column.
         *
         * An indicator this build cannot label is still given a column, under its raw id. A saved
         * screen from a later build naming an unknown indicator produces a filter that matches
         * nothing, and a column of em dashes beside it is how a reader finds that out instead of
         * concluding the market list is empty.
         */
        fun of(
            filters: List<ScreenerFilter>,
            columns: List<ScreenerField>,
        ): List<ScreenerIndicatorColumn> {
            val alreadyShown = columns.mapNotNull(ScreenerField::indicatorKey).toSet()
            return filters.filterIsInstance<ScreenerFilter.IndicatorFilter>()
                .map(ScreenerFilter.IndicatorFilter::key)
                .distinct()
                .filterNot(alreadyShown::contains)
                .map { key -> of(key) }
        }

        /**
         * One column for a normalised key.
         *
         * The period is read back out of the key rather than carried alongside it, for the reason
         * [ScreenerIndicatorId.normalisedKey] gives: the key is the one spelling everything agrees
         * on, and a second copy of the period is a second chance for the label and the value to
         * describe different readings.
         *
         * The period is appended to the heading in Latin digits, because it is part of a market
         * figure's name — «RSI 14» is read against another terminal — while the prose on this
         * screen stays Persian. It is isolated left-to-right, or a Persian heading ending in a
         * number reorders it to the wrong end of the words.
         */
        fun of(key: String): ScreenerIndicatorColumn {
            val separator = key.lastIndexOf(':')
            val id = if (separator < 0) key else key.substring(0, separator)
            val period = if (separator < 0) null else key.substring(separator + 1)
            val name = ScreenerIndicatorCatalog.labelOf(id)
            return ScreenerIndicatorColumn(
                key = key,
                label = if (period == null) name else name + " " + BidiText.isolateLtr(period),
                unit = ScreenerIndicatorCatalog.optionOf(id)?.unit ?: ScreenerUnit.PLAIN,
            )
        }
    }
}
