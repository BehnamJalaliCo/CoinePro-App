package com.coinepro.core.webhook

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What happens when an alert fires — [142].
 *
 * ### One call, and it is the only one the alert engine has to know about
 *
 * `dispatch(event)`. Everything else — which webhooks are switched on, which URLs are still valid,
 * what to sign with, what to write down — is this module's business. That is deliberate: the alert
 * evaluator is the most delicate code in the app, and the last thing it should grow is a branch per
 * delivery channel.
 *
 * ### Every attempt is recorded, including the ones that were never made
 *
 * A webhook that silently fails is the same failure mode as an alert that silently fails, and that
 * is the thing this product is defined against. So [dispatch] returns the records *and* writes them,
 * and nothing about a failure is swallowed: a receiver that said no, a receiver that never answered,
 * a URL that is no longer acceptable — each is a line, in Persian, with a status and a latency,
 * readable next to the alert's own audit trail through [WebhookStore.deliveriesFor].
 *
 * ### The webhooks are posted in parallel and none of them waits for another
 *
 * A reader with three webhooks has one slow one. Serially that receiver's three seconds are added
 * to the other two; in parallel the whole fan-out costs what the slowest one costs. They are
 * independent deliveries of the same event and nothing about one depends on another.
 */
class WebhookDispatcher(
    private val store: WebhookStore,
    private val poster: WebhookPoster = WebhookPoster(),
) {

    /**
     * Posts [event] to every enabled webhook and records what happened to each.
     *
     * Returns the records rather than only writing them, so a caller that wants to react — an
     * alert's audit line saying the webhook failed, a screen showing the result of a test send —
     * does not have to read the log back to find out.
     *
     * An empty list means the reader has no webhook switched on, which is the ordinary case and is
     * not a failure: nothing is recorded, because there was nothing to attempt.
     */
    suspend fun dispatch(event: WebhookEvent): List<WebhookAttempt> {
        val targets = store.currentTargets().filter(WebhookTarget::enabled)
        if (targets.isEmpty()) return emptyList()
        val attempts = coroutineScope {
            targets.map { target -> async { poster.deliver(target, event) } }.map { it.await() }
        }
        store.recordAll(attempts)
        return attempts
    }

    /**
     * Sends one event to a single webhook, on the reader's explicit request.
     *
     * The «آزمایش» button behind the editor, and it is worth having for one reason: a webhook is
     * the only feature in the app whose correctness depends on a system nobody here controls. A
     * reader who pastes a URL should be able to find out *now* whether it works, not the next time
     * a market happens to reach a price.
     *
     * Recorded like any other delivery, so a test send that fails leaves the same evidence a real
     * one would.
     */
    suspend fun test(target: WebhookTarget, event: WebhookEvent): WebhookAttempt {
        val attempt = poster.deliver(target, event)
        store.record(attempt)
        return attempt
    }
}
