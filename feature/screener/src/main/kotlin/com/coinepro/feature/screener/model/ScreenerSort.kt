package com.coinepro.feature.screener.model

/**
 * Which column the table is ordered by, and which way.
 *
 * ### Stability is a promise this class makes
 *
 * [apply] sorts with [List.sortedWith], which is a stable sort, and every caller depends on that
 * without saying so. Two markets with the same change percent — and on a quiet day there are dozens
 * of exact ties at `0.00` — must keep the order they came in, which is the liquidity order
 * `core:symbols` put them in. An unstable sort would shuffle those ties on every recomposition,
 * so a reader watching a list where nothing moved would see the rows swap places as prices ticked.
 * That is the single most unsettling thing a market table can do, and it is invisible in a test
 * that only checks the top row.
 *
 * ### A market with no value sorts last, both ways
 *
 * Not treated as zero and not moved to the top when the order flips. A null is "not resolved yet",
 * and the reader asking for the biggest volume is not asking to be shown the markets whose volume
 * is unknown. Flipping to ascending must not promote them either — «کمترین حجم» would otherwise
 * become a list of everything the app has not read yet, which is the same complaint from the other
 * end. `core:symbols` makes the same call in `SymbolRanking.byLiquidity`, for the same reason.
 */
data class ScreenerSort(
    val field: ScreenerField,
    /** Descending by default: a screener is nearly always read from the biggest number down. */
    val descending: Boolean = true,
    /**
     * The indicator reading this sort orders by, or null to order by [field] — [115].
     *
     * A second addressing mode rather than a second sort type, because a sort is one thing to a
     * reader: the column with the arrow on it. Since the sheet began offering every indicator in
     * the chart's catalogue, the table can show a column that has no [ScreenerField] at all —
     * «TSI 25», «Aroon 14» — and a sort that could only name a field would leave exactly those
     * columns unsortable, which is the one thing a table column is for.
     *
     * When it is set, [field] is left at whatever it was and ignored. It is kept rather than made
     * nullable so that clearing an indicator sort has somewhere to fall back to, and so that a
     * screen saved by a later build always decodes into a usable order.
     */
    val indicatorKey: String? = null,
) {

    /** The order this sort imposes, with unresolved rows pinned to the end. */
    val comparator: Comparator<ScreenerRow> = Comparator { first, second ->
        when {
            indicatorKey != null -> compareIndicator(first, second)
            field.isNumeric -> compareNumeric(first, second)
            else -> compareText(first, second)
        }
    }

    /** [rows] in this order. Stable: rows that compare equal keep the order they arrived in. */
    fun apply(rows: List<ScreenerRow>): List<ScreenerRow> = rows.sortedWith(comparator)

    /**
     * What a tap on a column header produces.
     *
     * Tapping the column already sorted flips the direction; tapping another column moves the sort
     * to it and starts descending, because that is the direction somebody who just chose "volume"
     * means. Starting a new column ascending would make the first tap on «حجم» show the markets
     * that barely traded.
     */
    fun toggled(next: ScreenerField): ScreenerSort = when {
        // Moving off an indicator column onto a field is a new column even when the field happens
        // to be the one this sort was parked on, so it starts descending like any other.
        indicatorKey != null -> ScreenerSort(next, descending = true)
        next == field -> copy(descending = !descending)
        else -> ScreenerSort(next, descending = true)
    }

    /**
     * The same gesture on an indicator column: tap to sort by it, tap again to flip.
     *
     * A separate entry point rather than an overload taking a nullable key, because the two are
     * different acts at the call site — a heading knows which kind of column it is — and a nullable
     * parameter would make "sort by nothing" expressible, which is not a state the table has.
     */
    fun toggledIndicator(key: String): ScreenerSort =
        if (key == indicatorKey) copy(descending = !descending) else copy(indicatorKey = key, descending = true)

    /**
     * The same rule as [compareNumeric], over a reading rather than a field.
     *
     * A market whose reading has not been computed yet — or whose history is too short for the
     * lookback — sorts last in both directions, exactly as an unresolved figure does. Ascending on
     * «RSI» must not become a list of everything the app has not reduced yet.
     */
    private fun compareIndicator(first: ScreenerRow, second: ScreenerRow): Int {
        val key = indicatorKey ?: return 0
        val a = first.indicators[key]?.takeIf(Double::isFinite)
        val b = second.indicators[key]?.takeIf(Double::isFinite)
        return when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            descending -> b.compareTo(a)
            else -> a.compareTo(b)
        }
    }

    private fun compareNumeric(first: ScreenerRow, second: ScreenerRow): Int {
        val a = first.valueOf(field)?.takeIf(Double::isFinite)
        val b = second.valueOf(field)?.takeIf(Double::isFinite)
        return when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            descending -> b.compareTo(a)
            else -> a.compareTo(b)
        }
    }

    private fun compareText(first: ScreenerRow, second: ScreenerRow): Int {
        val a = first.textOf(field)
        val b = second.textOf(field)
        return when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            descending -> b.compareTo(a)
            else -> a.compareTo(b)
        }
    }

    companion object {
        /**
         * What a screen with no opinion is sorted by.
         *
         * The day's move, biggest first, because a reader who opens a screener without setting one
         * up is asking "what is happening", and that column answers it. Volume would answer "what
         * is large", which is a question whose answer is the same every day.
         */
        val DEFAULT: ScreenerSort = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = true)
    }
}
