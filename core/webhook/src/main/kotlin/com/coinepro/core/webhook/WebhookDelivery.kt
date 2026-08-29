package com.coinepro.core.webhook

/**
 * What one fired alert hands to its webhooks — [142].
 *
 * The alerts layer owns the alert; this module owns the posting. So the two meet on a small value
 * that carries only what a receiver could want, and `core:webhook` depends on nothing of
 * `core:notifications` — which is what lets the alert engine call this without a cycle, and what
 * lets this be tested with no alert at all.
 *
 * @param firedAt epoch milliseconds, supplied by the caller. Nothing here reads a clock, for the
 *   reason the alert audit gives: a delivery retried later must still say when the alert fired.
 */
data class WebhookEvent(
    /** The alert this came from. What joins a delivery record to the alert's own audit trail. */
    val alertId: String,
    val symbol: String,
    val firedAt: Long,
    /**
     * What the reader wrote as the alert's message, or blank.
     *
     * Sent as the body when it is not blank, exactly as typed — JSON if it parses as JSON and plain
     * text otherwise, which is [WebhookBody]'s rule and TradingView's. Blank falls back to
     * [defaultBody].
     */
    val message: String = "",
    val price: Double? = null,
    val timeframe: String? = null,
) {
    /** The body this event is posted as. See [message]. */
    val body: String get() = message.trim().ifEmpty { defaultBody() }

    /** The content type [body] is sent with. */
    val contentType: String get() = WebhookBody.contentTypeOf(body)

    /**
     * The envelope used when the reader wrote no message of their own.
     *
     * Composed here rather than by a serialisation library, because it is six fields and adding a
     * dependency to this module would put it on the classpath of everything that ever posts a
     * webhook. It is escaped properly — a symbol cannot contain a quote, but a timeframe read off a
     * server one day could, and a body that is announced as JSON and is not is a 400 the reader
     * cannot diagnose.
     *
     * `firedAt` is in the body rather than only in a header on purpose: the body is what the
     * signature covers, so this is the one timestamp a receiver can trust. See [WebhookSignature].
     */
    fun defaultBody(): String = buildString {
        append('{')
        append("\"alertId\":").append(quote(alertId)).append(',')
        append("\"symbol\":").append(quote(symbol)).append(',')
        append("\"firedAt\":").append(firedAt)
        price?.takeIf { it.isFinite() }?.let { append(",\"price\":").append(it) }
        timeframe?.takeIf(String::isNotBlank)?.let { append(",\"timeframe\":").append(quote(it)) }
        append('}')
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char.code < 0x20 -> append("\\u").append(String.format("%04x", char.code))
                else -> append(char)
            }
        }
        append('"')
    }
}

/**
 * How one delivery ended.
 *
 * Five outcomes and not a boolean, because "it did not work" is the answer that made this log
 * necessary in the first place. A reader whose webhook is silent needs to know whether the receiver
 * said no, took too long, or was never reachable — those have three different fixes, and only the
 * first of them is theirs to make.
 */
enum class WebhookOutcome(val id: String, val label: String) {
    /** The receiver answered 2xx. The only outcome that means the message arrived. */
    DELIVERED("delivered", "تحویل شد"),

    /** The receiver answered, and said no. [WebhookAttempt.status] carries what it said. */
    REJECTED("rejected", "گیرنده نپذیرفت"),

    /** Three seconds passed with no answer. See [WebhookPoster.TIMEOUT_SECONDS]. */
    TIMED_OUT("timeout", "پاسخی نرسید"),

    /** Nothing to time out: no network, no DNS, a TLS handshake that failed. */
    UNREACHABLE("unreachable", "اتصال برقرار نشد"),

    /**
     * Never sent, because the target's URL is not one this app will post to.
     *
     * Recorded rather than skipped silently. A target that was saved before a rule tightened, or
     * edited into something invalid, is exactly the case where a reader would otherwise sit waiting
     * for messages from a webhook that is not being tried at all.
     */
    BLOCKED("blocked", "نشانی پذیرفته نشد"),
    ;

    val delivered: Boolean get() = this == DELIVERED

    companion object {
        fun fromId(id: String?): WebhookOutcome? = entries.firstOrNull { it.id == id }
    }
}

/**
 * One attempt to deliver one alert to one webhook — the delivery log's row.
 *
 * ### Why this log exists at all
 *
 * Because a webhook that silently fails is the same failure as an alert that silently fails, and
 * that failure is the thing this product is defined against. Somebody who wires an alert to a bot
 * stops watching *twice over* — once because the alert will tell them, and once because the bot
 * will act. When the POST quietly 404s they learn neither, and there is nothing anywhere to look
 * at. `AlertAuditStore` says the same thing at length about notifications; this is the same record
 * one layer out, and the two are meant to be read side by side on the alert's own history sheet.
 *
 * ### What a row carries
 *
 * The attempt (which target, which alert, when), the status (what the receiver said), the latency
 * (how long it took — the number that says "your receiver is slow" rather than "your receiver is
 * broken") and the error, in Persian, when there was one.
 *
 * What it deliberately does not carry: the body, and the secret. The body can be several kilobytes
 * and would turn a log into a copy of every alert ever fired; the secret must never be written
 * anywhere, and a log is the most-read place in an app for a value to leak from.
 */
data class WebhookAttempt(
    val targetId: String,
    /**
     * The target's name as it was at the time.
     *
     * Copied in rather than looked up, for the reason the alert audit gives about prices: a record
     * that re-derives its own facts is not a record. A target renamed or deleted last week must not
     * silently rewrite the history of what was sent to it.
     */
    val targetName: String,
    val alertId: String,
    /** Epoch milliseconds, supplied by the caller. */
    val at: Long,
    val outcome: WebhookOutcome,
    /** The HTTP status the receiver answered with, or null where nothing answered. */
    val status: Int? = null,
    /** How long the whole call took, in milliseconds. Zero for an attempt that was never made. */
    val latencyMillis: Long = 0,
    /** A short Persian reason, on anything other than a delivery. Never the exception's own text. */
    val error: String? = null,
) {
    val delivered: Boolean get() = outcome.delivered
}
