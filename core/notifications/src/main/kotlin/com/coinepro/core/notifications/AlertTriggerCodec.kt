package com.coinepro.core.notifications

/**
 * The stored form of an [AlertTrigger].
 *
 * ### Why the codec lives here and not in the store
 *
 * The store's job is one row per alert in one preference. The shape of a trigger is this module's
 * business, and a trigger is the one field in that row that is not a scalar — it has cases, and one
 * of its cases has a list inside it. Putting its encoding beside the type means a new case is one
 * file to change rather than two modules to keep in step, and it means the round trip can be tested
 * without a DataStore.
 *
 * ### Two separators, both control characters
 *
 * `core:datastore` already uses `;` between rows and `|` between fields, so a trigger may contain
 * neither. It uses ASCII's unit separator between a trigger's own parts and the record separator
 * between the conditions of a [AlertTrigger.MultiCondition] — the same characters, for the same
 * reason, that `ChartDrawingStore` picked: nothing a reader can type and nothing a number can
 * contain. Leaf triggers never contain a record separator, which is what lets the multi case be
 * decoded by splitting on it.
 *
 * ### Decoding cannot throw, and that rule is absolute
 *
 * This value is read on every launch of the alerts screen. A trigger case a later release renamed,
 * a channel whose bounds arrived swapped, a half-written preference — every one of them decodes to
 * null, and the alert falls back to its legacy [LocalAlertCondition]. The alternative is a screen
 * the reader cannot open without clearing the app's data, losing every other alert to fix one.
 */
object AlertTriggerCodec {

    /** Between a trigger's own parts. ASCII unit separator. */
    private const val PART = "\u001F"

    /** Between two conditions of a multi-condition. ASCII record separator. */
    private const val CONDITION = "\u001E"

    /**
     * The stored form, or an empty string where the trigger cannot be written.
     *
     * Empty rather than an exception, because the only way to reach it is an indicator or drawing
     * id that contains one of the separators, and no id this app generates does. An alert that
     * loses its trigger still has its legacy condition and still fires; an alert that took the
     * screen down with it does not.
     */
    fun encode(trigger: AlertTrigger?): String {
        if (trigger == null) return ""
        val encoded = when (trigger) {
            is AlertTrigger.Price -> listOf(
                AlertTrigger.Price.ID,
                trigger.op.id,
                trigger.value.toString(),
            ).joinToString(PART)

            is AlertTrigger.Channel -> listOf(
                AlertTrigger.Channel.ID,
                trigger.op.id,
                trigger.low.toString(),
                trigger.high.toString(),
            ).joinToString(PART)

            is AlertTrigger.Move -> listOf(
                AlertTrigger.Move.ID,
                trigger.op.id,
                trigger.amount.toString(),
                trigger.bars.toString(),
            ).joinToString(PART)

            is AlertTrigger.Indicator -> listOf(
                AlertTrigger.Indicator.ID,
                trigger.indicatorId,
                trigger.period?.toString().orEmpty(),
                trigger.op.id,
                trigger.value.toString(),
            ).joinToString(PART)

            is AlertTrigger.DrawingTouch -> listOf(
                AlertTrigger.DrawingTouch.ID,
                trigger.drawingId,
            ).joinToString(PART)

            is AlertTrigger.MultiCondition -> {
                val parts = trigger.conditions.map(::encode)
                if (parts.any(String::isEmpty)) return ""
                AlertTrigger.MultiCondition.ID + PART + parts.joinToString(CONDITION)
            }
        }
        return if (encoded.any { it == ';' || it == '|' }) "" else encoded
    }

    /** The trigger a row was written with, or null for anything this version cannot read. */
    fun decode(raw: String?): AlertTrigger? {
        val text = raw?.takeIf(String::isNotBlank) ?: return null
        val head = text.substringBefore(PART)
        val tail = text.substringAfter(PART, missingDelimiterValue = "")
        return when (head) {
            AlertTrigger.MultiCondition.ID -> decodeMulti(tail)
            else -> decodeLeaf(head, tail.split(PART))
        }
    }

    private fun decodeMulti(tail: String): AlertTrigger? {
        val conditions = tail
            .split(CONDITION)
            .filter(String::isNotBlank)
            .map { decode(it) ?: return null }
        if (conditions.isEmpty() || conditions.size > AlertTrigger.MultiCondition.MAX_CONDITIONS) return null
        if (conditions.any { it is AlertTrigger.MultiCondition }) return null
        return AlertTrigger.MultiCondition(conditions)
    }

    private fun decodeLeaf(head: String, parts: List<String>): AlertTrigger? = when (head) {
        AlertTrigger.Price.ID -> {
            val op = PriceOp.fromId(parts.getOrNull(0))
            val value = parts.getOrNull(1)?.toDoubleOrNull()
            if (op == null || value == null) null else AlertTrigger.Price(op, value)
        }

        AlertTrigger.Channel.ID -> {
            val op = ChannelOp.fromId(parts.getOrNull(0))
            val low = parts.getOrNull(1)?.toDoubleOrNull()
            val high = parts.getOrNull(2)?.toDoubleOrNull()
            // Checked here rather than caught, because Channel's own require is the contract a
            // caller must keep and a stored row is not a caller.
            if (op == null || low == null || high == null || low > high) {
                null
            } else {
                AlertTrigger.Channel(op, low, high)
            }
        }

        AlertTrigger.Move.ID -> {
            val op = MoveOp.fromId(parts.getOrNull(0))
            val amount = parts.getOrNull(1)?.toDoubleOrNull()
            val bars = parts.getOrNull(2)?.toIntOrNull() ?: 1
            if (op == null || amount == null || bars < 1) null else AlertTrigger.Move(op, amount, bars)
        }

        AlertTrigger.Indicator.ID -> {
            val indicatorId = parts.getOrNull(0)?.takeIf(String::isNotBlank)
            val period = parts.getOrNull(1)?.toIntOrNull()
            val op = PriceOp.fromId(parts.getOrNull(2))
            val value = parts.getOrNull(3)?.toDoubleOrNull()
            if (indicatorId == null || op == null || value == null || (period != null && period < 1)) {
                null
            } else {
                AlertTrigger.Indicator(indicatorId, period, op, value)
            }
        }

        AlertTrigger.DrawingTouch.ID -> parts.getOrNull(0)
            ?.takeIf(String::isNotBlank)
            ?.let { AlertTrigger.DrawingTouch(it) }

        else -> null
    }
}
