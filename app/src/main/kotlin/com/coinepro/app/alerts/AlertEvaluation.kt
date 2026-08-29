package com.coinepro.app.alerts

import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.LocalPriceAlert

/**
 * One indicator, identified the way an alert asks for it.
 *
 * The pair rather than the id alone, because «RSI 14 above 70» and «RSI 21 above 70» are two
 * different questions and computing one of them under a key shared with the other would answer the
 * second with the first's number. A null [period] means the indicator's own default, which is what
 * the chart catalogue resolves it to; it is kept as null rather than resolved here so that this
 * type stays free of the chart engine.
 */
data class AlertIndicatorKey(val indicatorId: String, val period: Int?)

/**
 * An indicator's output at the last two closed bars.
 *
 * Two values rather than one because the crossings are the reason anybody sets an indicator alert —
 * «tell me when RSI crosses 70», not «tell me while RSI is above 70», which is true for hours.
 * [previous] is null on a series too short to have a bar before the last one, and a crossing then
 * answers false rather than guessing a direction.
 */
data class AlertIndicatorReading(val previous: Double?, val current: Double)

/**
 * What one evaluation pass knows about one instrument.
 *
 * ### Why the indicator outputs and the drawn levels travel with the sample
 *
 * `core:notifications` cannot compute either of them — it deliberately does not depend on
 * `core:chart` — so [AlertTrigger.Indicator] and [AlertTrigger.DrawingTouch] are defined to receive
 * their numbers from the caller. This type is that carrier and [AlertConditions] is what routes each
 * trigger to the numbers that trigger is actually about. Filling it in is [AlertMarketSource]'s job,
 * which is where the chart engine and the alert model meet; [GuestAlertMarketSource] says why that
 * seam has to exist at all.
 *
 * ### Everything derived from bars is derived from **closed** bars
 *
 * [previousPrice], [closes] and [indicators] all come from bars that have finished. Including the
 * forming bar would make an indicator signal and then unsignal as the candle moved, which is the
 * behaviour that makes a chart's own alerts untrustworthy. [price] is the live quote, because that
 * is what a plain «above 65,000» alert is asking about; an alert that wants the close instead says
 * so with [AlertFrequency.ONCE_PER_BAR_CLOSE], and [atBarClose] is how it gets it.
 *
 * ### The two bar times are in milliseconds
 *
 * [AlertFrequency.shouldFire] compares them against a clock in epoch **milliseconds**, while
 * `core:marketdata` and both candle feeds speak epoch seconds. The conversion happens once, where
 * the bar is read, so that nothing downstream has to remember which unit it is holding.
 */
data class AlertSample(
    val symbol: String,
    /** The live price. What every price-scale trigger is compared against. */
    val price: Double,
    /**
     * The last closed bar's close, or null where no candles were loaded.
     *
     * Every crossing and every channel transition answers false without it, which is the
     * conservative direction: a missed crossing costs one notification and an invented one costs
     * the reader's trust in all of them.
     */
    val previousPrice: Double?,
    val changePercent24h: Double?,
    /** Closed bars' closes, oldest first, for a multi-bar [AlertTrigger.Move]. */
    val closes: List<Double> = emptyList(),
    /**
     * The opening instant of the bar now forming, in epoch milliseconds.
     *
     * Zero where no bar could be read, and that is a deliberate sentinel rather than a missing
     * value. [AlertFrequency.ONCE_PER_BAR] asks whether the last firing was before this bar opened;
     * with no bar to answer against, zero makes that false and the alert degrades to firing once
     * rather than once per pass. The failure it avoids is the loud one.
     */
    val barStart: Long = 0L,
    /** The opening instant of the last **closed** bar, or null where none was read. */
    val closedBarStart: Long? = null,
    /** The interval this sample was read on, for the audit line and for `{tf}` in a message. */
    val timeframe: String,
    val indicators: Map<AlertIndicatorKey, AlertIndicatorReading> = emptyMap(),
    /**
     * A drawn line's own price level at the last two closed bars, keyed by the drawing's id.
     *
     * Two entries, oldest first, which is the shape [AlertTrigger.DrawingTouch] documents: a line is
     * a price per moment, so a touch is a sign change of `price − level` and needs the level at both
     * ends. A drawing the reader has since deleted is simply absent and its alert never fires,
     * rather than firing against a level of zero.
     */
    val drawingLevels: Map<String, List<Double>> = emptyMap(),
) {

    /**
     * The same instrument read as a finished bar rather than as a live tick.
     *
     * ### This is what «only on bar close» actually means
     *
     * Not «evaluate the live price and hope the worker happened to wake on the boundary» — the
     * platform schedules background work at its own convenience and would never land on one. It is:
     * take the bar that has closed, use *its close* as the price, and measure everything else from
     * the bar before it. A wick through a level that the candle then closed back inside is
     * therefore not an event, which is the entire promise the setting makes.
     *
     * Null where no bar has closed yet, and the alert then does not fire. There is nothing to
     * report about a bar that has not happened.
     */
    fun atBarClose(): AlertSample? {
        val close = closes.lastOrNull() ?: return null
        val start = closedBarStart ?: return null
        return copy(
            price = close,
            previousPrice = closes.getOrNull(closes.size - 2),
            closes = closes.dropLast(1),
            barStart = start,
        )
    }
}

/**
 * What an alert needs fetched before it can be answered.
 *
 * Derived from the trigger rather than assumed, because the difference is a network call per
 * symbol. «Above 65,000» needs one number that the price route returns for every alert at once;
 * «RSI(14) crossing 70» needs a few hundred bars of that one symbol. Asking for candles on behalf
 * of an alert that does not need them would spend somebody else's bandwidth to save a `when`.
 */
data class AlertDataNeeds(
    /** Whether a candle series has to be loaded at all. */
    val candles: Boolean,
    val indicators: Set<AlertIndicatorKey>,
    val drawings: Set<String>,
) {

    /** The union of two sets of needs, for two alerts that happen to be on the same instrument. */
    operator fun plus(other: AlertDataNeeds): AlertDataNeeds = AlertDataNeeds(
        candles = candles || other.candles,
        indicators = indicators + other.indicators,
        drawings = drawings + other.drawings,
    )

    companion object {
        /** A quote and nothing else. */
        val QUOTE_ONLY = AlertDataNeeds(candles = false, indicators = emptySet(), drawings = emptySet())
    }
}

/**
 * Whether an alert is due, with each of its conditions asked about the number it is actually about.
 *
 * ### Why this exists beside [LocalPriceAlert.due]
 *
 * The domain's own `due` is the decision, and the repeat rules, the expiry, the bar policy and the
 * flat conditions all belong to it. What it cannot do on its own is *routing*: it takes one
 * `previous`, one `current` and one `series` and hands all three to whatever trigger the alert
 * carries. That is right for a price trigger and wrong for the two triggers that are not about the
 * price — an [AlertTrigger.Indicator] is compared against the indicator's own output, and an
 * [AlertTrigger.DrawingTouch] needs the drawn line's level in `series`. `core:notifications` says so
 * in as many words and leaves the choice of numbers to its caller. This is that caller.
 *
 * ### And why a multi-condition alert is asked one condition at a time
 *
 * Its conditions can be about different numbers — an RSI condition beside a price condition — so a
 * single call cannot serve them, which is exactly what [AlertTrigger.MultiCondition.evaluateAll]
 * exists to say. Rather than reimplement the repeat, expiry and frequency rules here in order to
 * AND them with that function's answer, each condition is put through [LocalPriceAlert.due] on a
 * copy of the alert carrying that condition alone. Every one of those calls applies the *identical*
 * policy gate — it is a pure function of fields `copy` does not touch — so ANDing the answers is
 * «the policy allows it, and every condition holds», with the rules living in exactly one place. It
 * is one call per condition, capped at five by the model itself.
 */
object AlertConditions {

    /**
     * Whether [alert] should fire for [sample] now.
     *
     * [alert] must already carry the firing state that applies to *this* symbol: for a watchlist
     * alert that is the per-symbol stamp rather than the alert-wide one, and substituting it is
     * [AlertEvaluator]'s job because only it knows which of the two stores the state came from.
     */
    fun due(alert: LocalPriceAlert, sample: AlertSample, nowEpochMillis: Long): Boolean {
        val onClose = alert.frequency == AlertFrequency.ONCE_PER_BAR_CLOSE
        val reading = if (onClose) sample.atBarClose() ?: return false else sample
        val trigger = alert.trigger
        if (trigger is AlertTrigger.MultiCondition) {
            return trigger.conditions.all { condition ->
                dueFor(alert.copy(trigger = condition), condition, reading, onClose, nowEpochMillis)
            }
        }
        return dueFor(alert, trigger, reading, onClose, nowEpochMillis)
    }

    private fun dueFor(
        alert: LocalPriceAlert,
        condition: AlertTrigger?,
        reading: AlertSample,
        barClosed: Boolean,
        nowEpochMillis: Long,
    ): Boolean = LocalPriceAlert.due(
        alert = alert,
        previous = previousFor(condition, reading),
        price = currentFor(condition, reading),
        series = seriesFor(condition, reading),
        changePercent24h = reading.changePercent24h,
        nowEpochMillis = nowEpochMillis,
        barStart = reading.barStart,
        barClosed = barClosed,
    )

    /**
     * The number this condition compares against its level.
     *
     * `NaN` where an indicator's output could not be produced — a series too short to warm it up, a
     * volume study on a feed with no volume column, an id this build does not know. Every comparison
     * against `NaN` is false, in Kotlin as in IEEE 754, so a missing reading refuses to fire without
     * any branch having to say so and without inventing a zero that would read as an oscillator
     * pinned to the floor.
     */
    private fun currentFor(condition: AlertTrigger?, reading: AlertSample): Double =
        when (condition) {
            is AlertTrigger.Indicator ->
                reading.indicators[AlertIndicatorKey(condition.indicatorId, condition.period)]?.current
                    ?: Double.NaN
            else -> reading.price
        }

    private fun previousFor(condition: AlertTrigger?, reading: AlertSample): Double? =
        when (condition) {
            is AlertTrigger.Indicator ->
                reading.indicators[AlertIndicatorKey(condition.indicatorId, condition.period)]?.previous
            else -> reading.previousPrice
        }

    /**
     * The series this condition reads, or null where it reads none.
     *
     * Three different things depending on the trigger, which is why the model leaves the choice
     * here: the closes for a multi-bar move, the drawn line's own levels for a touch, and nothing at
     * all for a comparison that reads one sample.
     */
    private fun seriesFor(condition: AlertTrigger?, reading: AlertSample): DoubleArray? =
        when (condition) {
            is AlertTrigger.Move -> reading.closes.takeIf { it.isNotEmpty() }?.toDoubleArray()
            is AlertTrigger.DrawingTouch ->
                reading.drawingLevels[condition.drawingId]?.takeIf { it.isNotEmpty() }?.toDoubleArray()
            else -> null
        }

    /**
     * What has to be fetched before [alert] can be answered.
     *
     * An alert with a [LocalPriceAlert.frequency] always needs candles even when its condition does
     * not: the bar policy is stated in bars, and «once per bar» cannot be decided without a bar.
     */
    fun needsOf(alert: LocalPriceAlert): AlertDataNeeds {
        val fromTrigger = needsOf(alert.trigger)
        return if (alert.frequency == null) fromTrigger else fromTrigger.copy(candles = true)
    }

    private fun needsOf(trigger: AlertTrigger?): AlertDataNeeds = when (trigger) {
        null -> AlertDataNeeds.QUOTE_ONLY
        is AlertTrigger.Price -> AlertDataNeeds.QUOTE_ONLY.copy(candles = trigger.op.needsPrevious)
        is AlertTrigger.Channel -> AlertDataNeeds.QUOTE_ONLY.copy(candles = trigger.op.needsPrevious)
        // Even a one-bar move is measured against the last close, so the cheapest move still wants
        // a bar behind it.
        is AlertTrigger.Move -> AlertDataNeeds.QUOTE_ONLY.copy(candles = true)
        is AlertTrigger.Indicator -> AlertDataNeeds(
            candles = true,
            indicators = setOf(AlertIndicatorKey(trigger.indicatorId, trigger.period)),
            drawings = emptySet(),
        )
        is AlertTrigger.DrawingTouch -> AlertDataNeeds(
            candles = true,
            indicators = emptySet(),
            drawings = setOf(trigger.drawingId),
        )
        is AlertTrigger.MultiCondition -> trigger.conditions
            .fold(AlertDataNeeds.QUOTE_ONLY) { needs, condition -> needs + needsOf(condition) }
    }
}

/**
 * Where a line the reader drew sits at a given moment.
 *
 * ### Why the level is recomputed rather than stored
 *
 * A trend line is a price *per moment*, not a price. Freezing its level into the alert at the
 * instant it was made would produce an alert that fires on a horizontal line the reader can plainly
 * see is not horizontal, and it would keep doing it for as long as the drawing existed.
 * [AlertTrigger.DrawingTouch] is written around that fact and asks its caller for the level at each
 * sample; this is where that is worked out.
 *
 * Pure arithmetic over the points as the reader placed them, so the awkward cases — a single point,
 * two anchors on the same bar, a time past both ends — are unit tests rather than a line on a chart
 * somebody has to squint at.
 */
object AlertDrawingLevel {

    /**
     * Tool ids that have no price level at all.
     *
     * A vertical line marks a moment, not a price, so «the price touched it» is not a question about
     * it. Answered with null rather than with the anchor's price, which would turn a time marker
     * into a silent horizontal alert at whatever height the reader happened to tap.
     */
    val TIME_ONLY_TOOLS: Set<String> = setOf("vline", "daterange", "fibtime", "fibtimeext", "timecycles")

    /**
     * The drawing's price at [atEpochSeconds], or null where it has none.
     *
     * One anchor is a horizontal level and stays where it is — which is what a horizontal line, a
     * price label and a note all are. Two or more are read as a straight line through the first two,
     * extended past both ends, because that is what a trend line drawn on a chart means and it is
     * what makes a touch two bars after the second anchor still a touch. Two anchors on the same
     * instant have no slope and fall back to the first price rather than dividing by zero.
     */
    fun levelAt(toolId: String, points: List<Pair<Long, Double>>, atEpochSeconds: Long): Double? {
        if (toolId in TIME_ONLY_TOOLS) return null
        val first = points.firstOrNull() ?: return null
        val second = points.getOrNull(1) ?: return first.second
        val span = second.first - first.first
        if (span == 0L) return first.second
        val slope = (second.second - first.second) / span.toDouble()
        return (first.second + slope * (atEpochSeconds - first.first)).takeIf(Double::isFinite)
    }
}
