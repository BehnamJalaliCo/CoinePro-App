package com.coinepro.feature.alerts

import com.coinepro.core.datastore.StoredDrawing
import com.coinepro.core.notifications.AlertTrigger

/**
 * One of the reader's drawings, as the editor offers it.
 *
 * ### Why the level is on the option and not only in the sentence
 *
 * The stored drawings of one symbol are a list of numeric ids. Offering them as `1731...` and
 * `1732...` is offering a choice nobody can make — it is exactly the objection [AlertTriggerKind]
 * used to record against ever putting drawings in this sheet. What makes the choice answerable is
 * the pair the reader already has in their head: *what kind of line it is* and *what price it is
 * at*. So the option carries both, and the row prints them together.
 */
data class AlertDrawingOption(
    /**
     * The drawing's stored id, as a string.
     *
     * A string because that is what [AlertTrigger.DrawingTouch] holds and what the evaluator keys
     * its resolved levels by — `GuestAlertMarketSource` builds that map with `drawing.id.toString()`
     * — so the two must be spelled the same way or the alert resolves to no level and silently
     * never fires.
     */
    val id: String,
    /** The chart tool this was drawn with, e.g. `trend`. Never localised. */
    val toolId: String,
    /** The Persian name of the tool, or the id upper-cased for one this build has no name for. */
    val label: String,
    /**
     * The drawing's price at its **last anchor**, which is where the reader put it.
     *
     * Not "where the line is now": a trend line is a price per moment and the evaluator resolves it
     * again at every sample — that is the whole reason [AlertTrigger.DrawingTouch] takes its level
     * in `series` rather than storing one. What this number is for is recognition, so the reader can
     * tell their two trend lines apart, and the sheet says so under the picker rather than letting
     * somebody read it as the level the alert will fire at.
     */
    val level: Double,
)

/**
 * Which drawings can be alerted on, and what they are called.
 *
 * ### A menu, not a geometry engine
 *
 * The same split [AlertIndicators] makes against the chart catalogue, for the same reason:
 * `feature:alerts` does not depend on `core:chart` and must not start, or this module ends up with
 * a second copy of the drawing engine and the day the two disagree the reader gets an alert about a
 * touch that is not on the chart in front of them. So the tool *names* here are a short menu with a
 * fallback, and the tool *ids* are the chart's own.
 *
 * ### The level arithmetic is duplicated, and that is deliberate — with a rule
 *
 * `AlertDrawingLevel` in the application module resolves a drawing's level at an arbitrary moment,
 * because that is what evaluating a touch needs. This resolves it at one moment only — the last
 * anchor — because that is what naming a drawing in a picker needs, and the application module is
 * not on this module's classpath. Both read the same straight line through the first two points, so
 * they agree by construction. The trap, written down so it is not rediscovered: if one of them ever
 * learns a tool-specific shape the other must learn it too, or the picker will name a level the
 * alert will never fire at.
 */
object AlertDrawings {

    /**
     * Tools that mark a moment rather than a price.
     *
     * The same set `AlertDrawingLevel` refuses to resolve. A vertical line has an anchor with a
     * price in it — wherever the reader's finger happened to be — and offering it here would make a
     * silent horizontal alert at a height nobody chose.
     */
    val TIME_ONLY_TOOLS: Set<String> = setOf("vline", "daterange", "fibtime", "fibtimeext", "timecycles")

    /**
     * The Persian name of the tools somebody actually alerts on.
     *
     * Short on purpose, like [AlertIndicators.ALL]. Ninety-one tool names in this module would be a
     * copy of the chart's catalogue that has to be kept in step with it; these are the lines and
     * bands people draw in order to be told when price reaches them, and anything else the reader
     * has drawn still appears — under its id — rather than being hidden from them.
     */
    private val NAMES: Map<String, String> = mapOf(
        "hline" to "خط افقی",
        "hray" to "نیم‌خط افقی",
        "pricelabel" to "برچسب قیمت",
        "pricenote" to "یادداشت قیمت",
        "trend" to "خط روند",
        "ray" to "نیم‌خط",
        "extline" to "خط امتدادیافته",
        "channel" to "کانال موازی",
        "flattop" to "سقف/کف تخت",
        "regression" to "کانال رگرسیون",
        "rect" to "مستطیل",
        "fib" to "بازگشت فیبوناچی",
        "pricerange" to "دامنهٔ قیمت",
        "longshort" to "موقعیت خرید/فروش",
    )

    /**
     * The drawings of one symbol that an alert can be put on, newest first.
     *
     * Newest first because the line somebody has just drawn is the line they are about to alert on;
     * the store keeps them in the order they were placed, which puts that one at the bottom of a
     * list the reader then has to scroll.
     *
     * A drawing with no price — a time marker, or one whose points did not survive a half-written
     * record — is left out rather than offered with a zero in it. It is not a choice the reader can
     * act on, and a row reading «0» in a picker of prices reads as a fault in the feed.
     */
    fun optionsOf(drawings: List<StoredDrawing>): List<AlertDrawingOption> = drawings
        .sortedByDescending(StoredDrawing::id)
        .mapNotNull { drawing ->
            val level = levelOf(drawing) ?: return@mapNotNull null
            AlertDrawingOption(
                id = drawing.id.toString(),
                toolId = drawing.toolId,
                label = labelOf(drawing.toolId),
                level = level,
            )
        }

    /** The Persian name of a tool, or its id upper-cased where this build has no name for it. */
    fun labelOf(toolId: String): String = NAMES[toolId] ?: toolId.uppercase()

    /**
     * A drawing's price at its last anchor, or null where it has none.
     *
     * One point is a level and stays where it is. Two or more are read as the straight line through
     * the first two, evaluated at the last anchor's own instant — which for an ordinary two-point
     * line is simply the second point's price, and for a three-point channel is where that line has
     * reached by the time of the third. Two anchors on the same instant have no slope and fall back
     * to the first price rather than dividing by zero.
     */
    fun levelOf(drawing: StoredDrawing): Double? {
        if (drawing.toolId in TIME_ONLY_TOOLS) return null
        val points = drawing.points
        val first = points.firstOrNull() ?: return null
        val second = points.getOrNull(1) ?: return first.second.takeIf(Double::isFinite)
        val last = points.last()
        val span = second.first - first.first
        if (span == 0L) return first.second.takeIf(Double::isFinite)
        val slope = (second.second - first.second) / span.toDouble()
        return (first.second + slope * (last.first - first.first)).takeIf(Double::isFinite)
    }
}
