package com.coinepro.app.widget

/**
 * How many market rows fit, and what else the widget has room to say.
 *
 * ### Why this is a pure function in its own file
 *
 * Everything else about a widget needs a launcher to look at. This does not: it is arithmetic on
 * two numbers the system hands over, and it is the part most likely to be wrong in a way nobody
 * notices — a row too many is a row clipped in half at the bottom of somebody's home screen, on
 * one launcher, at one font size, and it will never appear in a screenshot taken here.
 *
 * ### The measurements
 *
 * A launcher cell is roughly 70dp on most phones and the system reports the widget's size in dp
 * excluding its own padding. [ROW_HEIGHT_DP] is the row this widget draws — a ticker, a price and
 * a change on one line, at the app's own row height — and [HEADER_HEIGHT_DP] is the full strip
 * carrying the wordmark, the freshness and the refresh control.
 *
 * ### The header is not free, so it has to earn three rows
 *
 * On a two-cell-high widget the header would cost a third of the glass, and the reader who put a
 * *price* widget on their home screen did not ask for a title bar. So it appears only once at
 * least [HEADER_MIN_ROWS] rows survive paying for it.
 *
 * The small widget is not left without a clock, though — it gets [FOOTER_HEIGHT_DP], half the
 * height, carrying the freshness alone. "When was this" is the one thing a price widget cannot
 * honestly leave out: a stale price and a live one look identical, and on a trading app that is
 * not a cosmetic problem.
 */
data class WidgetLayout(
    /** How many market rows to draw. Never zero — see [of]. */
    val rows: Int,
    /** Whether there is room for the full header: wordmark, freshness, refresh. */
    val header: Boolean,
    /** Whether the compact freshness strip is drawn instead. Never true alongside [header]. */
    val footer: Boolean,
    /** Whether each row can carry the market's name beside its ticker. */
    val names: Boolean,
) {
    companion object {

        /** One market row, at the app's own list-row height. */
        const val ROW_HEIGHT_DP = 44

        /** The full strip: wordmark, freshness, refresh. */
        const val HEADER_HEIGHT_DP = 36

        /** The compact strip: the freshness alone, for widgets too short for a header. */
        const val FOOTER_HEIGHT_DP = 18

        /** The widget's own padding, top and bottom together. */
        const val VERTICAL_PADDING_DP = 16

        /** How many rows must survive the header for it to be worth drawing. */
        const val HEADER_MIN_ROWS = 3

        /** Below this width a row cannot carry a ticker, a name, a price and a change. */
        const val NAMES_MIN_WIDTH_DP = 220

        /** What the largest supported widget draws. Past this it is a list, not a widget. */
        const val MAX_ROWS = 8

        /**
         * Decide from the size the system reported, in dp.
         *
         * Always at least one row. A widget that computes zero rows is a rectangle with nothing in
         * it, and the reader's conclusion is that the app is broken rather than that their widget
         * is small — so the smallest possible widget shows one market and nothing else, which is
         * still an answer.
         */
        fun of(widthDp: Int, heightDp: Int): WidgetLayout {
            val usable = (heightDp - VERTICAL_PADDING_DP).coerceAtLeast(0)
            val names = widthDp >= NAMES_MIN_WIDTH_DP

            val withHeader = ((usable - HEADER_HEIGHT_DP) / ROW_HEIGHT_DP).coerceAtMost(MAX_ROWS)
            if (withHeader >= HEADER_MIN_ROWS) {
                return WidgetLayout(rows = withHeader, header = true, footer = false, names = names)
            }

            val withFooter = ((usable - FOOTER_HEIGHT_DP) / ROW_HEIGHT_DP).coerceAtMost(MAX_ROWS)
            if (withFooter >= 1) {
                return WidgetLayout(rows = withFooter, header = false, footer = true, names = names)
            }

            // Smaller than one row plus a clock. One market, nothing else — the honest floor.
            return WidgetLayout(rows = 1, header = false, footer = false, names = names)
        }
    }
}
