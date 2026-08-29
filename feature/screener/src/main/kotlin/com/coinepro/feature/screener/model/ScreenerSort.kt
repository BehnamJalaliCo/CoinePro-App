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
) {

    /** The order this sort imposes, with unresolved rows pinned to the end. */
    val comparator: Comparator<ScreenerRow> = Comparator { first, second ->
        if (field.isNumeric) compareNumeric(first, second) else compareText(first, second)
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
    fun toggled(next: ScreenerField): ScreenerSort =
        if (next == field) copy(descending = !descending) else ScreenerSort(next, descending = true)

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
