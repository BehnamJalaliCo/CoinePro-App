package com.coinepro.core.notifications

/**
 * One alert's dependence on one drawing.
 *
 * Flattened out of [AlertTrigger.DrawingTouch] — including the ones buried inside an
 * [AlertTrigger.MultiCondition] — so that «which alerts does this line carry» is a lookup rather
 * than a walk somebody has to remember to write twice.
 */
data class DrawingAlertLink(
    val alertId: String,
    /** Upper-cased, because the symbol arrives spelled both ways and this is a map key. */
    val symbol: String,
    /** The drawing's stored id **as a string**, which is how the trigger holds it. */
    val drawingId: String,
)

/**
 * What ties an alert to a line the reader drew, and what happens when the line goes.
 *
 * ### The failure this exists to end
 *
 * `GuestAlertMarketSource` resolves a drawing's level at each sample and — in its own words — «a
 * drawing the reader deleted … is left out entirely and its alert simply never fires». That is
 * correct evaluation and a silent failure: the alert sits in the centre under «فعال», reading as
 * armed, and it can never go off again. The reader finds out by not being told, which is the exact
 * shape of failure this product has spent four runs removing from its own client.
 *
 * So the dependence is made visible in the two places it can be seen: the chart says a drawing
 * carries alerts **before** deleting it, and the alert centre says an alert's drawing is gone
 * rather than drawing it as live.
 *
 * ### Everything here is pure, and one rule is load-bearing
 *
 * **Not knowing a symbol's drawings is not the same as knowing it has none.** [orphanIds] takes a
 * map whose *keys* are the symbols whose drawings were actually read; an alert on a symbol absent
 * from that map gets no verdict at all. Without that rule the first frame of the alert centre —
 * rendered before any drawing has loaded — would mark every drawing alert in the list as broken,
 * which is a worse lie than the silence it replaces.
 */
object AlertDrawingLinks {

    /**
     * Every alert-to-drawing tie in the list, in the list's own order.
     *
     * An alert whose trigger is a [AlertTrigger.MultiCondition] contributes one link per drawing
     * condition inside it: «RSI above 70 **and** price touches my trend line» depends on that line
     * exactly as much as a bare touch does, and an implementation that only looked at the top-level
     * trigger would have reported such an alert as depending on nothing.
     */
    fun linksOf(alerts: List<LocalPriceAlert>): List<DrawingAlertLink> = alerts.flatMap { alert ->
        drawingIdsOf(alert.trigger).map { drawingId ->
            DrawingAlertLink(
                alertId = alert.id,
                symbol = alert.symbol.trim().uppercase(),
                drawingId = drawingId,
            )
        }
    }

    /** The symbols whose drawings have to be read before this list can be judged. */
    fun symbolsOf(alerts: List<LocalPriceAlert>): Set<String> =
        linksOf(alerts).mapTo(LinkedHashSet(), DrawingAlertLink::symbol)

    /**
     * The alerts that watch one particular drawing.
     *
     * The symbol is compared case-insensitively and the id as a string, because that is how
     * [AlertTrigger.DrawingTouch] holds it and how `GuestAlertMarketSource` keys its resolved
     * levels — the two must be spelled the same way or the alert resolves to no level and silently
     * never fires, which is the whole subject of this file.
     */
    fun on(alerts: List<LocalPriceAlert>, symbol: String, drawingId: String): List<LocalPriceAlert> {
        val wanted = symbol.trim().uppercase()
        return alerts.filter { alert ->
            alert.symbol.trim().uppercase() == wanted && drawingId in drawingIdsOf(alert.trigger)
        }
    }

    /** The same, for the `Long` the chart keeps its drawings under. */
    fun on(alerts: List<LocalPriceAlert>, symbol: String, drawingId: Long): List<LocalPriceAlert> =
        on(alerts, symbol, drawingId.toString())

    /**
     * The ids of the alerts whose drawing is gone.
     *
     * [known] maps a symbol to the ids of the drawings that symbol still has, **and only for the
     * symbols that were actually read**. A symbol missing from the map is unread, not empty, and
     * its alerts are left alone — see the rule in this object's own documentation.
     *
     * An alert with several drawing conditions is an orphan as soon as **one** of them is missing:
     * a multi-condition is an AND, so a single unresolvable term makes the whole alert unable to
     * fire, and reporting it as healthy because its other condition still resolves would be
     * describing an alert that cannot go off as one that can.
     */
    fun orphanIds(
        alerts: List<LocalPriceAlert>,
        known: Map<String, Set<String>>,
    ): Set<String> {
        if (known.isEmpty()) return emptySet()
        val out = LinkedHashSet<String>()
        alerts.forEach { alert ->
            val drawingIds = drawingIdsOf(alert.trigger)
            if (drawingIds.isEmpty()) return@forEach
            val present = known[alert.symbol.trim().uppercase()] ?: return@forEach
            if (drawingIds.any { it !in present }) out += alert.id
        }
        return out
    }

    /**
     * The drawing ids one trigger depends on, in order and without repeats.
     *
     * Multi-conditions do not nest — [AlertTrigger.MultiCondition] refuses one that does — so one
     * level of descent is the whole of the recursion, and this stays a function rather than a
     * traversal with a depth bound.
     */
    fun drawingIdsOf(trigger: AlertTrigger?): Set<String> = when (trigger) {
        null -> emptySet()
        is AlertTrigger.DrawingTouch -> setOf(trigger.drawingId)
        is AlertTrigger.MultiCondition ->
            trigger.conditions.filterIsInstance<AlertTrigger.DrawingTouch>()
                .mapTo(LinkedHashSet(), AlertTrigger.DrawingTouch::drawingId)
        else -> emptySet()
    }
}
