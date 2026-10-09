package com.coinepro.feature.chart

import com.coinepro.core.designsystem.CoineProWindowClass

/**
 * The named multi-chart layouts: how many charts, and how they are cut.
 *
 * TradingView's picker offers 1, 2 across, 2 down, 3, 4, 6 and 8, and a reader who has learned
 * that «4» is a two-by-two grid should find the same here. Until 4.49.0 the panes screen took a
 * bare count and let the width decide the columns, which made «2» a side-by-side on a landscape
 * tablet and a stack on a portrait one — the right answer for the glass, but never a choice the
 * reader could make. A preset is that choice: [columns] is what the layout *asks for*, and
 * [gridColumns] gives it as much of that as the width can afford.
 *
 * [id] is what is stored. Stable by contract, because it outlives the app's process on a reader's
 * tablet; the enum name is not written anywhere.
 */
enum class ChartLayoutPreset(
    val id: String,
    /** How many charts. */
    val count: Int,
    /** How many across, when the glass allows it. */
    val columns: Int,
    /**
     * TradingView's mixed grids (5.28.0): how many charts each row holds, top to bottom, where the
     * rows are not all the same — one wide chart over two, three over one. Null for a plain grid.
     * A window too narrow for the widest row falls back to the plain grid of [columns].
     */
    val rowSpec: List<Int>? = null,
) {
    /** One chart: the ordinary chart screen, which the panes screen hands back to. */
    ONE("1", 1, 1),

    /** Two side by side — the comparison layout, and the phone's only multi-chart shape in landscape. */
    TWO_ACROSS("2h", 2, 2),

    /** Two stacked: one instrument on two bar lengths, read top to bottom. */
    TWO_DOWN("2v", 2, 1),

    /** Three across. */
    THREE("3", 3, 3),

    /** Three stacked: one instrument on three bar lengths, read top to bottom (5.12.0). */
    THREE_DOWN("3v", 3, 1),

    /** Two by two. */
    FOUR("4", 4, 2),

    /** Four across, in one row — the widest glass only reads it (5.12.0). */
    FOUR_ACROSS("4h", 4, 4),

    /** Four stacked (5.12.0). */
    FOUR_DOWN("4v", 4, 1),

    /** Three by two. */
    SIX("6", 6, 3),

    /** Four by two — three by three on a tablet, which is the most it can afford. */
    EIGHT("8", 8, 4),

    /** TradingView's larger grids, offered only on a desktop-wide window (5.16.0). */
    NINE("9", 9, 3),

    TWELVE("12", 12, 4),

    SIXTEEN("16", 16, 4),

    /** One wide chart over two (5.28.0). */
    ONE_OVER_TWO("1+2", 3, 2, listOf(1, 2)),

    /** Two over one wide chart. */
    TWO_OVER_ONE("2+1", 3, 2, listOf(2, 1)),

    /** One wide chart over three. */
    ONE_OVER_THREE("1+3", 4, 3, listOf(1, 3)),

    /** Three over one wide chart. */
    THREE_OVER_ONE("3+1", 4, 3, listOf(3, 1)),

    /** Two over three. */
    TWO_OVER_THREE("2+3", 5, 3, listOf(2, 3)),

    /** Three over two. */
    THREE_OVER_TWO("3+2", 5, 3, listOf(3, 2)),

    /** One wide chart over four. */
    ONE_OVER_FOUR("1+4", 5, 4, listOf(1, 4)),

    /** Four over two. */
    FOUR_OVER_TWO("4+2", 6, 4, listOf(4, 2)),

    /** Four over three. */
    FOUR_OVER_THREE("4+3", 7, 4, listOf(4, 3)),
    ;

    /** The rows this layout takes at its asked-for width. */
    val rows: Int get() = rowSpec?.size ?: ((count + columns - 1) / columns)

    companion object {
        /** The layouts a window that can hold [maxPanes] charts may offer, past the single chart. */
        fun offered(maxPanes: Int): List<ChartLayoutPreset> =
            entries.filter { it.count in CoineProWindowClass.PHONE_MAX_PANES..maxPanes }

        fun byId(id: String?): ChartLayoutPreset? = entries.firstOrNull { it.id == id }

        /**
         * The layout a bare count means, for a record written before there were presets or a
         * count clamped by a smaller window: the first preset with that many charts, which for two
         * is side by side — what the width-decided grid gave a landscape tablet.
         */
        fun forCount(count: Int): ChartLayoutPreset {
            // Plain grids only: a bare count never meant a mixed one, which a reader picks by shape.
            val plain = entries.filter { it.rowSpec == null }
            plain.firstOrNull { it.count == count }?.let { return it }
            // The largest count under it, and of the layouts with that count the first — the
            // grid, not a stacked variant added later (5.12.0).
            val nearest = plain.filter { it.count <= count.coerceAtLeast(1) }.maxOf { it.count }
            return plain.first { it.count == nearest }
        }

        /** The largest layout that fits [maxPanes], for a stored one the window cannot hold. */
        fun largestWithin(maxPanes: Int): ChartLayoutPreset =
            forCount(entries.filter { it.rowSpec == null && it.count <= maxPanes.coerceAtLeast(1) }.maxOf { it.count })
    }
}
