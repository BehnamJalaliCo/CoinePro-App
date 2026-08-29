package com.coinepro.feature.alerts

import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertMessageTemplate
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AlertSound
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.PriceOp

/**
 * The kinds of condition the editor can build.
 *
 * Four, and `AlertTrigger.DrawingTouch` is deliberately absent. A drawing alert is made by pressing
 * a line on the chart, because that is the only place the line exists to be pointed at; offering a
 * fifth segment here would need a picker listing drawings by id, which is a list of hexadecimal
 * strings. The list and the audit log render such an alert perfectly well — it just is not made
 * from this sheet.
 */
enum class AlertTriggerKind {
    /** One level. One number field. */
    PRICE,

    /** A band. Two number fields, and the low really is the low. */
    CHANNEL,

    /** A move of a size, in price or in percent. One number field, unit chosen by the operator. */
    MOVE,

    /** A study's own output against a level. A picker, a period stepper, and one number field. */
    INDICATOR,
}

/**
 * One row of the editor's condition list.
 *
 * ### Text, not numbers
 *
 * [first] and [second] are what the reader has typed, kept exactly as typed. Parsing on every
 * keystroke and storing a `Double?` loses the difference between "empty", "minus", "0." and
 * "nonsense", so the field would jump under the reader's fingers as they type a decimal point. The
 * parse happens in [firstValue] and [secondValue], and it folds Persian digits first: a Persian
 * keyboard produces ۰-۹ by default, and `toDoubleOrNull` on those returns null — a field that looks
 * filled in and refuses to save.
 */
data class AlertConditionDraft(
    val kind: AlertTriggerKind = AlertTriggerKind.PRICE,
    val priceOp: PriceOp = PriceOp.CROSSING_UP,
    val channelOp: ChannelOp = ChannelOp.ENTERING,
    val moveOp: MoveOp = MoveOp.UP_PERCENT,
    /** The level, the band's low, or the indicator's level — whichever this [kind] asks for. */
    val first: String = "",
    /** The band's high. Only [AlertTriggerKind.CHANNEL] uses it; the others hide the field. */
    val second: String = "",
    val indicatorId: String = AlertIndicators.DEFAULT_ID,
    /** Null where the chosen study has no single lookback, which hides the stepper. */
    val period: Int? = AlertIndicators.defaultPeriodOf(AlertIndicators.DEFAULT_ID),
) {

    /** [first] as a number, or null while it is empty or half-typed. */
    val firstValue: Double? get() = parse(first)

    /** [second] as a number. Meaningful only for a channel. */
    val secondValue: Double? get() = parse(second)

    /**
     * Whether the two bounds are the wrong way round.
     *
     * Reported rather than silently swapped. `AlertTrigger.Channel` refuses an inverted pair on
     * purpose — quietly sorting them is how a screen ships with its two fields wired backwards and
     * nobody notices — and a reader who typed the high into the low box should be told, once,
     * under the field they typed it into.
     */
    val boundsInverted: Boolean
        get() {
            val low = firstValue ?: return false
            val high = secondValue ?: return false
            return low > high
        }

    /**
     * This row as a trigger, or null while it is incomplete.
     *
     * Never throws. Every `require` in `AlertTrigger`'s constructors is checked here first, because
     * this is called on every keystroke to decide whether the save button is live, and a half-typed
     * channel would otherwise take the sheet down.
     */
    fun build(): AlertTrigger? {
        val value = firstValue?.takeIf(Double::isFinite) ?: return null
        return when (kind) {
            // A level of zero or less is not a price on any instrument this app quotes; a field
            // holding one is a reader who has not finished typing rather than a condition.
            AlertTriggerKind.PRICE ->
                if (value > 0.0) AlertTrigger.Price(priceOp, value) else null

            AlertTriggerKind.CHANNEL -> {
                val high = secondValue?.takeIf(Double::isFinite) ?: return null
                if (value <= 0.0 || high <= 0.0 || value > high) return null
                AlertTrigger.Channel(channelOp, low = value, high = high)
            }

            // A move of zero is satisfied by standing still, so it would fire on the first tick.
            AlertTriggerKind.MOVE ->
                if (value > 0.0) AlertTrigger.Move(moveOp, value) else null

            // An indicator level may be negative — MACD and CCI both cross zero — so the only
            // rejected values here are the ones that are not numbers.
            AlertTriggerKind.INDICATOR -> {
                val lookback = period?.let(AlertIndicators::coercePeriod)
                AlertTrigger.Indicator(
                    indicatorId = indicatorId.ifBlank { AlertIndicators.DEFAULT_ID },
                    period = lookback,
                    op = priceOp,
                    value = value,
                )
            }
        }
    }

    private fun parse(raw: String): Double? =
        raw.foldDigitsToLatin().trim().takeIf(String::isNotBlank)?.toDoubleOrNull()

    companion object {

        /**
         * The row that represents an existing trigger, so editing starts from what is there.
         *
         * Null for a `AlertTrigger.DrawingTouch` and for a nested multi-condition, neither of which
         * this sheet builds. The caller hides «ویرایش» for those rather than opening an editor that
         * would quietly change what the alert means.
         */
        fun of(trigger: AlertTrigger): AlertConditionDraft? = when (trigger) {
            is AlertTrigger.Price -> AlertConditionDraft(
                kind = AlertTriggerKind.PRICE,
                priceOp = trigger.op,
                first = plain(trigger.value),
            )

            is AlertTrigger.Channel -> AlertConditionDraft(
                kind = AlertTriggerKind.CHANNEL,
                channelOp = trigger.op,
                first = plain(trigger.low),
                second = plain(trigger.high),
            )

            is AlertTrigger.Move -> AlertConditionDraft(
                kind = AlertTriggerKind.MOVE,
                moveOp = trigger.op,
                first = plain(trigger.amount),
            )

            is AlertTrigger.Indicator -> AlertConditionDraft(
                kind = AlertTriggerKind.INDICATOR,
                priceOp = trigger.op,
                first = plain(trigger.value),
                indicatorId = trigger.indicatorId,
                period = trigger.period,
            )

            is AlertTrigger.DrawingTouch -> null
            is AlertTrigger.MultiCondition -> null
        }

        /**
         * The row that represents one of the six flat conditions.
         *
         * The two 24-hour ones return null: they read the feed's own daily percentage, which no
         * trigger measures, and mapping them onto a rolling move would change what the alert does
         * behind the reader's back. Those alerts are paused, duplicated and deleted like any other
         * — they are simply not re-opened in a sheet that cannot express them.
         *
         * `ABOVE` and `BELOW` become the strict comparisons, and that is the one place editing an
         * old alert changes it: the flat pair is inclusive at the level, the strict pair is not. It
         * is a tick's worth of difference on a boundary the reader is re-typing anyway.
         */
        fun of(condition: LocalAlertCondition, value: Double): AlertConditionDraft? = when (condition) {
            LocalAlertCondition.ABOVE -> AlertConditionDraft(
                kind = AlertTriggerKind.PRICE,
                priceOp = PriceOp.GREATER_THAN,
                first = plain(value),
            )

            LocalAlertCondition.BELOW -> AlertConditionDraft(
                kind = AlertTriggerKind.PRICE,
                priceOp = PriceOp.LESS_THAN,
                first = plain(value),
            )

            LocalAlertCondition.PERCENT_UP -> AlertConditionDraft(
                kind = AlertTriggerKind.MOVE,
                moveOp = MoveOp.UP_PERCENT,
                first = plain(value),
            )

            LocalAlertCondition.PERCENT_DOWN -> AlertConditionDraft(
                kind = AlertTriggerKind.MOVE,
                moveOp = MoveOp.DOWN_PERCENT,
                first = plain(value),
            )

            LocalAlertCondition.CHANGE_24H_OVER -> null
            LocalAlertCondition.CHANGE_24H_UNDER -> null
        }

        /**
         * A stored number back into a field, without the app's own grouping.
         *
         * Deliberately not [AlertSentence.number]: that one groups thousands and pads to two
         * decimals for display, and putting `68,500.00` back into a numeric field gives the reader
         * a comma to delete before they can edit it.
         */
        private fun plain(value: Double): String =
            if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}

/**
 * Everything the editor sheet holds while it is open.
 *
 * Immutable, and rebuilt by the controller on every change, so the sheet has no state of its own to
 * fall out of step with. That matters more here than on most screens: the fields shown depend on
 * the trigger type, and a sheet keeping its own `remember`ed text would leave the old field's value
 * behind when the type changed.
 */
data class AlertDraft(
    /** The alert being changed, or null for a new one. */
    val editingId: String? = null,
    val symbol: String = "",
    /** At least one, at most [AlertTrigger.MultiCondition.MAX_CONDITIONS]. ANDed together. */
    val conditions: List<AlertConditionDraft> = listOf(AlertConditionDraft()),
    val frequency: AlertFrequency = AlertFrequency.ONCE,
    val channels: Set<AlertChannel> = AlertChannel.DEFAULTS,
    val message: String = "",
    /** What the reader has typed into the symbol picker. Not part of the alert. */
    val query: String = "",
    /** Whether the picker is open. It opens itself for a new alert, which has no symbol yet. */
    val pickingSymbol: Boolean = true,
) {

    /** Whether this sheet is changing an existing alert rather than making one. */
    val editing: Boolean get() = editingId != null

    /**
     * Whether another condition may be added.
     *
     * The screen states the cap *before* this goes false — «حداکثر ۵ شرط» sits under the button
     * from the first condition onwards — because a limit a reader meets by pressing a button that
     * suddenly does nothing reads as a bug, and one they were told about reads as a rule.
     */
    val canAddCondition: Boolean
        get() = conditions.size < AlertTrigger.MultiCondition.MAX_CONDITIONS

    /** Whether the reader's message is longer than a notification will show. */
    val messageTooLong: Boolean get() = message.length > AlertMessageTemplate.MAX_LENGTH

    /**
     * The whole condition, or null while any row of it is incomplete.
     *
     * One condition stays a bare trigger rather than becoming a one-element AND. They evaluate the
     * same, but the stored form and the sentence are both simpler, and a reader who never pressed
     * «+ شرط» should not find their alert described as a compound one.
     */
    fun trigger(): AlertTrigger? {
        val built = conditions.map { it.build() ?: return null }
        return if (built.size == 1) built.single() else AlertTrigger.MultiCondition(built)
    }

    /** Whether the sheet's one action may run. */
    val valid: Boolean
        get() = symbol.isNotBlank() && !messageTooLong && trigger() != null

    /**
     * The alert this draft describes.
     *
     * ### Two decisions worth stating
     *
     * **An edited alert is re-armed.** [LocalPriceAlert.lastFiredAtEpochMillis] is cleared, so a
     * spent one-shot the reader has just corrected starts waiting again. The alternative is an
     * alert that has been carefully edited and will never fire, which is the single most confusing
     * outcome this sheet could produce.
     *
     * **The flat [LocalPriceAlert.condition] is written anyway.** The field is not nullable, and an
     * evaluator that predates triggers reads it. Where the trigger maps onto one of the six flat
     * conditions it is written faithfully; where it does not — a channel, an absolute move — the
     * pair is the nearest single statement rather than a zero, and the trigger is what every
     * evaluator in this app actually reads.
     */
    fun toAlert(existing: LocalPriceAlert?, id: String, nowEpochMillis: Long): LocalPriceAlert? {
        val trigger = trigger() ?: return null
        val ticker = symbol.trim().uppercase()
        if (ticker.isEmpty()) return null
        val (condition, value) = flatMirror(trigger)
        return LocalPriceAlert(
            id = existing?.id ?: id,
            symbol = ticker,
            condition = condition,
            value = value,
            repeat = frequency.asRepeat(),
            referencePrice = existing?.referencePrice,
            active = true,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: nowEpochMillis,
            lastFiredAtEpochMillis = null,
            trigger = trigger,
            scope = AlertScope.Symbol(ticker),
            frequency = frequency,
            expiresAt = existing?.expiresAt,
            channels = channels,
            soundLevel = existing?.soundLevel ?: AlertSound.DEFAULT_LEVEL,
            message = message.trim().takeIf(String::isNotBlank),
        )
    }

    companion object {

        /**
         * The draft for an alert the reader has asked to change, or null where it cannot be shown.
         *
         * Null is a real answer and the caller respects it: a drawing alert and a 24-hour-change
         * alert are both perfectly valid alerts that this sheet cannot express, and the actions
         * menu hides «ویرایش» for them rather than offering a sheet that would rewrite them.
         */
        fun of(alert: LocalPriceAlert): AlertDraft? {
            val rows = conditionsOf(alert) ?: return null
            return AlertDraft(
                editingId = alert.id,
                symbol = alert.symbol,
                conditions = rows,
                frequency = alert.frequency ?: alert.repeat.asFrequency(),
                channels = alert.channels,
                message = alert.message.orEmpty(),
                pickingSymbol = false,
            )
        }

        private fun conditionsOf(alert: LocalPriceAlert): List<AlertConditionDraft>? {
            val trigger = alert.trigger
                ?: return AlertConditionDraft.of(alert.condition, alert.value)?.let(::listOf)
            if (trigger is AlertTrigger.MultiCondition) {
                return trigger.conditions.map { AlertConditionDraft.of(it) ?: return null }
            }
            return AlertConditionDraft.of(trigger)?.let(::listOf)
        }

        /**
         * The flat condition and level nearest to a trigger.
         *
         * Exact for a price level and for a percentage move. For a channel it is «بالای» the band's
         * high, which is the strictest single level the band implies — a fallback evaluator that
         * fired *more* often than the real trigger would be worse than one that fired less. For an
         * absolute move there is no level at all, so the amount is written as one and the trigger
         * is what matters.
         */
        private fun flatMirror(trigger: AlertTrigger): Pair<LocalAlertCondition, Double> =
            when (trigger) {
                is AlertTrigger.Price -> when (trigger.op) {
                    PriceOp.LESS_THAN, PriceOp.CROSSING_DOWN ->
                        LocalAlertCondition.BELOW to trigger.value
                    else -> LocalAlertCondition.ABOVE to trigger.value
                }

                is AlertTrigger.Channel -> LocalAlertCondition.ABOVE to trigger.high

                is AlertTrigger.Move -> when (trigger.op) {
                    MoveOp.UP_PERCENT -> LocalAlertCondition.PERCENT_UP to trigger.amount
                    MoveOp.DOWN_PERCENT -> LocalAlertCondition.PERCENT_DOWN to trigger.amount
                    MoveOp.UP -> LocalAlertCondition.ABOVE to trigger.amount
                    MoveOp.DOWN -> LocalAlertCondition.BELOW to trigger.amount
                }

                is AlertTrigger.Indicator -> LocalAlertCondition.ABOVE to trigger.value

                is AlertTrigger.DrawingTouch -> LocalAlertCondition.ABOVE to 0.0

                // The first condition, because an AND cannot be said in one flat comparison and the
                // first is the one the reader wrote first.
                is AlertTrigger.MultiCondition ->
                    trigger.conditions.firstOrNull()?.let(::flatMirror)
                        ?: (LocalAlertCondition.ABOVE to 0.0)
            }
    }
}

/**
 * The wall-clock repeat policy nearest to a bar-aware one.
 *
 * Written on every alert this sheet makes because [LocalPriceAlert.repeat] is not nullable and is
 * what governs an alert the tick evaluator picks up. The two bar policies both map to
 * [AlertRepeat.ALWAYS] with its own cooldown: they are "more than once", and the cooldown is the
 * tick evaluator's only way of saying that without a candle.
 */
private fun AlertFrequency.asRepeat(): AlertRepeat = when (this) {
    AlertFrequency.ONCE -> AlertRepeat.ONCE
    AlertFrequency.ONCE_PER_BAR,
    AlertFrequency.ONCE_PER_BAR_CLOSE,
    AlertFrequency.EVERY_TIME,
    -> AlertRepeat.ALWAYS
}

/**
 * The other direction, for an alert stored before frequencies existed.
 *
 * `DAILY` becomes [AlertFrequency.EVERY_TIME] rather than a bar policy, because a day is not a
 * number of bars and pretending it is would silently change how often the alert speaks. The reader
 * sees the honest answer in the sheet and can pick the one they meant.
 */
private fun AlertRepeat.asFrequency(): AlertFrequency = when (this) {
    AlertRepeat.ONCE -> AlertFrequency.ONCE
    AlertRepeat.DAILY -> AlertFrequency.EVERY_TIME
    AlertRepeat.ALWAYS -> AlertFrequency.EVERY_TIME
}
