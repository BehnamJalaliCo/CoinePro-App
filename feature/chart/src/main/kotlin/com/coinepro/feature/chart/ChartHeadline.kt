package com.coinepro.feature.chart

import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.common.BidiText
import com.coinepro.core.symbols.MarketStatus
import kotlin.math.abs

/**
 * The move at the top of the chart, written the way a trader reads it.
 *
 * ### Why the absolute move had to be there
 *
 * The heading printed the price and the percentage and nothing between them, and a ratio on its own
 * is the one figure that cannot be checked against anything. A reader holding a position in a
 * single instrument thinks in the instrument's own units — «طلا ۵ دلار بالاست» — and converts to a
 * percentage only to compare two markets. Showing only the ratio makes the reader do the arithmetic
 * the screen already has both operands for, and they do it against a price that is still moving.
 *
 * So the second line of the heading is now `+5.12 (+1.63%)`: the move first, in the instrument's
 * own units and at the instrument's own precision, and the ratio behind it in brackets. That order
 * is not a borrowed layout — it is the order the two numbers answer questions in. Every terminal
 * lands on it for the same reason.
 *
 * ### Why this app cannot use `MarketNumberFormatter.signedPercent`
 *
 * Because it writes a hyphen. `DecimalFormat` formats −1.63 with U+002D, and this app's minus is
 * U+2212 everywhere else on a chart — see `ChartLegendOverlay`, which already keeps its own copy of
 * this rule for the plate drawn over the plot. A hyphen is narrower than a plus, so a fall and a
 * rise do not line up in the same column, and beside Persian copy it is read as punctuation
 * separating two numbers rather than as part of one. The heading and the legend now agree, which
 * they did not before: the same move was drawn twice on one screen with two different minus signs.
 *
 * ### Latin digits, one isolate
 *
 * A market figure, so Latin digits, and the whole run is isolated **once** rather than per number.
 * Isolating «+5.12» and «(+1.63%)» separately puts the bracket outside both isolates, and a
 * right-to-left paragraph then moves it to the far end of the row.
 *
 * Pure Kotlin and no Compose, so the thing that can actually be wrong — the sign, the precision and
 * the order — is a unit test rather than a screenshot.
 */
internal object ChartHeadline {

    /** What a figure the feed has not sent is written as. Never a dash in a column of signed moves. */
    const val NO_VALUE: String = "—"

    /**
     * U+2212 MINUS SIGN, not the hyphen a keyboard produces.
     *
     * The literal character rather than an escape, because it has a width and is legible in a diff
     * — unlike the bidi isolates, which `ChartLegendOverlay` writes as escapes for that reason.
     */
    private const val MINUS = "−"

    /** A change is always given to two decimals, whatever the instrument's own precision is. */
    private const val PERCENT_DECIMALS = 2

    /**
     * The sign, with U+2212 for a fall and nothing at all for a flat window.
     *
     * «+0.00» claims a rise that did not happen, and on a chart of a market that has not moved
     * since it opened that is the reading a reader would act on.
     */
    fun signOf(value: Double): String = when {
        value > 0.0 -> "+"
        value < 0.0 -> MINUS
        else -> ""
    }

    /**
     * A signed move in the instrument's own units, at [decimals] places, un-isolated.
     *
     * The magnitude is formatted from the absolute value and the sign put back by hand, because
     * `String.format` writes a hyphen for a negative and this app's minus is U+2212.
     */
    fun figure(value: Double, decimals: Int): String =
        if (!value.isFinite()) NO_VALUE else signOf(value) + formatPrice(abs(value), decimals)

    /** The same move as a percentage, un-isolated. Always signed, because a percentage here is a change. */
    fun percent(value: Double): String =
        if (!value.isFinite()) {
            NO_VALUE
        } else {
            signOf(value) + formatPrice(abs(value), PERCENT_DECIMALS) + "%"
        }

    /**
     * The whole second line: the absolute move, then the percentage in brackets.
     *
     * [price] sets the precision of the absolute figure, because a move is quoted at the same
     * precision as the thing it moved — a 0.0004 move on a pair quoted to four places is a real
     * move and rounds to 0.00 at the two decimals a percentage uses. Null [price] falls back to the
     * move's own magnitude, which is what a caller with no last price can honestly do.
     *
     * Either half may be missing and the line degrades to the other rather than to nothing: a feed
     * that sent one bar has a price and no window to measure a move across, and «—» beside a live
     * price would read as an outage.
     */
    fun move(absolute: Double?, percent: Double?, price: Double?): String {
        val decimals = decimalsFor(price ?: absolute ?: 0.0)
        val figure = absolute?.takeIf(Double::isFinite)?.let { figure(it, decimals) }
        val ratio = percent?.takeIf(Double::isFinite)?.let { percent(it) }
        val line = when {
            figure != null && ratio != null -> "$figure ($ratio)"
            figure != null -> figure
            ratio != null -> ratio
            else -> return NO_VALUE
        }
        return BidiText.isolateLtr(line)
    }

    /**
     * Which way the move goes, for the colour: true up, false down, null flat or absent.
     *
     * Derived from the absolute where there is one and from the percentage otherwise, so the figure
     * and its colour cannot disagree. Flat is null rather than false: a window that has not moved
     * is not a fall, and painting it in the sell colour says it was.
     */
    fun rising(absolute: Double?, percent: Double?): Boolean? {
        val value = absolute?.takeIf(Double::isFinite) ?: percent?.takeIf(Double::isFinite) ?: return null
        return when {
            value > 0.0 -> true
            value < 0.0 -> false
            else -> null
        }
    }
}

/**
 * A small fact about the chart's own state, drawn as a chip beside the instrument.
 *
 * ### Why there is not always a second one
 *
 * The heading has room for two and shows one most of the time, deliberately. `chartExclusions` in
 * this module already states the rule this follows — *an entry that is always present is an entry
 * nobody reads* — and the honest set of always-true facts about a chart is exactly one: whether the
 * market it draws is trading. Everything else here is a state the reader put the chart into, and a
 * chip for it appears when they did and not otherwise. Padding the row out to two would mean
 * inventing a state, or repeating a control that is already on the screen.
 *
 * ### Why the bar length is not one of them
 *
 * It is a chip in the terminal this was measured against, and it is drawn twice on this screen
 * already: as the selected key in the command band and in the caption under the plot. A third copy
 * in the heading would be the mistake `RangeChipRow` documents at length — two things that look the
 * same and answer different questions.
 */
internal enum class ChartHeaderState {
    /** The market this instrument trades on is open. Crypto is always open; forex keeps a week. */
    MARKET_OPEN,

    /** The venue is shut, on a day it normally trades. */
    MARKET_CLOSED,

    /** The forex week has ended. Distinct from closed, because it is not a fault and lasts two days. */
    MARKET_WEEKEND,

    /** Bar replay is running, so the price above is a moment in the past rather than the last one. */
    REPLAY,
}

/**
 * The chips for one chart, in reading order.
 *
 * Market state first and always, replay second and only when it is on — which is the order of how
 * much they change what the price above means. Replay is the louder of the two and comes second on
 * purpose: it is next to the figure it qualifies.
 */
internal fun chartHeaderStates(status: MarketStatus, replayOn: Boolean): List<ChartHeaderState> {
    val market = when {
        status.open -> ChartHeaderState.MARKET_OPEN
        status.weekend -> ChartHeaderState.MARKET_WEEKEND
        else -> ChartHeaderState.MARKET_CLOSED
    }
    return if (replayOn) listOf(market, ChartHeaderState.REPLAY) else listOf(market)
}
