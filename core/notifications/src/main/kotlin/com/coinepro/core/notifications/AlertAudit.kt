package com.coinepro.core.notifications

/**
 * Something that happened to one alert.
 *
 * ### The one that matters is [DELIVERY_FAILED]
 *
 * An alert that fires and is never delivered is worse than no alert, and the reason is not
 * technical. Somebody who sets an alert *stops watching* — that is the entire value of the feature,
 * and it is why they trusted it. If the notification is dropped, they do not find out that they
 * were not told; they find out that the move happened without them, hours later, and they have no
 * way to tell whether the app failed or whether they set it wrong. Every complaint of the form
 * "alerts not working" is somebody who cannot distinguish those two, because nothing in the app
 * ever wrote down which one it was.
 *
 * This enum is that record. [FIRED] is the app deciding; [DELIVERED] is the notification actually
 * reaching the system; [DELIVERY_FAILED] is the honest third answer that every alerts feature in
 * this market omits and then gets reviewed for.
 *
 * ### And why the rest of the lifecycle is here too
 *
 * Because "did it fire?" is rarely the real question. The real question is usually "why did this
 * fire at three in the morning", and answering it needs to know that the alert was edited on
 * Tuesday, or that it was snoozed, or that it expired and was re-armed. A log with only firings in
 * it answers half a question.
 */
enum class AuditEvent(val id: String) {
    /** The reader made it. */
    CREATED("created"),

    /** The reader changed it. The [AlertAuditEntry.note] carries what changed, in plain words. */
    EDITED("edited"),

    /** It became eligible to fire — switched on, or re-armed after a one-shot was reset. */
    ARMED("armed"),

    /** Its condition was satisfied and the app decided to notify. */
    FIRED("fired"),

    /** The notification reached the system. The only event that means the reader was told. */
    DELIVERED("delivered"),

    /** It fired and could not be delivered. The reason this log exists. */
    DELIVERY_FAILED("delivery_failed"),

    /** The reader put it aside for a while rather than deleting it. */
    SNOOZED("snoozed"),

    /** It passed its own expiry and stopped being evaluated. */
    EXPIRED("expired"),

    /** The reader removed it. Kept in the log after the alert itself is gone; see the store. */
    DELETED("deleted"),
    ;

    companion object {
        fun fromId(id: String?): AuditEvent? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One line of an alert's history.
 *
 * ### Why the market state is copied in rather than looked up
 *
 * [price] and [timeframe] are what the market was doing at [at], recorded then. Looking them up
 * later gives a different answer — that is what a market is — and an audit line that re-derives its
 * own facts is not an audit line. A two-hundred-and-thirteen-point request on the largest chart
 * app's own forum asks for exactly this and has no reply: to see the state an alert fired on.
 *
 * Both are nullable because not every event has them. A [AuditEvent.DELETED] has no price, and an
 * alert that is not from a chart has no timeframe; writing a zero in either place would be
 * inventing a reading, which is the same mistake [LocalAlertCondition.CHANGE_24H_OVER] refuses to
 * make with a missing 24-hour figure.
 *
 * [note] is short prose for the reader — the delivery error, what an edit changed. It is the one
 * free-text field, so the store escapes it rather than trusting it.
 */
data class AlertAuditEntry(
    val alertId: String,
    val event: AuditEvent,
    /** Epoch milliseconds, supplied by the caller. Nothing in this type reads a clock. */
    val at: Long,
    val symbol: String,
    val price: Double? = null,
    val timeframe: String? = null,
    val note: String? = null,
)
