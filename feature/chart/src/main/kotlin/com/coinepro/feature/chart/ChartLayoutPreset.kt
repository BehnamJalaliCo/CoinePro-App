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
) {
    /** One chart: the ordinary chart screen, which the panes screen hands back to. */
    ONE("1", 1, 1),

    /** Two side by side — the comparison layout, and the phone's only multi-chart shape in landscape. */
    TWO_ACROSS("2h", 2, 2),

    /** Two stacked: one instrument on two bar lengths, read top to bottom. */
    TWO_DOWN("2v", 2, 1),

    /** Three across. */
    THREE("3", 3, 3),

    /** Two by two. */
    FOUR("4", 4, 2),

    /** Three by two. */
    SIX("6", 6, 3),

    /** Four by two — three by three on a tablet, which is the most it can afford. */
    EIGHT("8", 8, 4),
    ;

    /** The rows this layout takes at its asked-for width. */
    val rows: Int get() = (count + columns - 1) / columns

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
        fun forCount(count: Int): ChartLayoutPreset =
            entries.firstOrNull { it.count == count } ?: entries.last { it.count <= count.coerceAtLeast(1) }

        /** The largest layout that fits [maxPanes], for a stored one the window cannot hold. */
        fun largestWithin(maxPanes: Int): ChartLayoutPreset =
            forCount(entries.filter { it.count <= maxPanes.coerceAtLeast(1) }.maxOf { it.count })
    }
}
