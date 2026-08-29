package com.coinepro.feature.alerts

import com.coinepro.core.webhook.WebhookAttempt
import com.coinepro.core.webhook.WebhookDispatcher
import com.coinepro.core.webhook.WebhookEvent
import com.coinepro.core.webhook.WebhookStore
import com.coinepro.core.webhook.WebhookTarget
import com.coinepro.core.webhook.WebhookUrl
import com.coinepro.core.webhook.WebhookUrlRefusal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The webhook targets and their delivery log, behind the little of it this screen needs.
 *
 * ### Why the alert centre owns this at all
 *
 * A webhook is a delivery channel for an alert. Nothing else in this app fires one, nothing else
 * has a reason to read the log, and the question a reader asks about a webhook — «چرا ربات کاری
 * نکرد» — is asked in front of the alert that should have triggered it. A separate settings screen
 * would put the target three taps from the alert and the failure log somewhere else again.
 *
 * ### Narrow, and the secret never leaves through it
 *
 * `WebhookStore` also encodes, decodes and caps the log; `WebhookDispatcher` also fans out to every
 * target on a firing. Neither is this screen's business. What is here is what a person can do from
 * a sheet — list, save, delete, switch off, send one test — and one read of the log filtered to an
 * alert. [WebhookAttempt] cannot carry a secret and this interface never returns one on its own.
 */
interface AlertWebhooks {

    /** Every target the reader has. Empty is the ordinary case and is not a failure. */
    val targets: Flow<List<WebhookTarget>>

    /** One alert's delivery history, newest first, for its own audit sheet. */
    fun deliveriesFor(alertId: String): Flow<List<WebhookAttempt>>

    /** Saves one, and says whether the store took it. False means the URL was refused. */
    suspend fun save(target: WebhookTarget): Boolean

    /** Forgets one target. Its delivery history stays; see `WebhookAttempt`. */
    suspend fun delete(id: String)

    /** Switches one on or off without losing the URL the reader pasted. */
    suspend fun setEnabled(id: String, enabled: Boolean)

    /**
     * Posts one test event, on the reader's explicit request, and says what came back.
     *
     * Null where this build has no dispatcher. Everything else is a [WebhookAttempt], including
     * every failure — a test that fails leaves the same evidence a real delivery would, which is
     * the whole reason to offer the button.
     */
    suspend fun test(target: WebhookTarget): WebhookAttempt?
}

/**
 * No webhooks.
 *
 * The default, for a caller with no store to hand — the screenshot tests, and any build that shows
 * the alert centre before the module is wired. Nothing throws: the sheet then has no targets, the
 * audit sheet shows no deliveries, and every alert works exactly as it did.
 */
object NoWebhooks : AlertWebhooks {
    override val targets: Flow<List<WebhookTarget>> = flowOf(emptyList())
    override fun deliveriesFor(alertId: String): Flow<List<WebhookAttempt>> = flowOf(emptyList())
    override suspend fun save(target: WebhookTarget): Boolean = false
    override suspend fun delete(id: String) = Unit
    override suspend fun setEnabled(id: String, enabled: Boolean) = Unit
    override suspend fun test(target: WebhookTarget): WebhookAttempt? = null
}

/**
 * The real one, over `core:webhook`'s own store and dispatcher.
 *
 * Here rather than in the application module because it needs nothing Android: both collaborators
 * are plain classes and this is four forwarding calls plus the one decision worth writing down —
 * what a test send actually posts.
 */
class StoredWebhooks(
    private val store: WebhookStore,
    private val dispatcher: WebhookDispatcher,
    /** Injected so the test event's timestamp is assertable rather than waited for. */
    private val now: () -> Long = System::currentTimeMillis,
) : AlertWebhooks {

    override val targets: Flow<List<WebhookTarget>> = store.targets

    override fun deliveriesFor(alertId: String): Flow<List<WebhookAttempt>> =
        store.deliveriesFor(alertId)

    override suspend fun save(target: WebhookTarget): Boolean = store.save(target)

    override suspend fun delete(id: String) = store.delete(id)

    override suspend fun setEnabled(id: String, enabled: Boolean) = store.setEnabled(id, enabled)

    /**
     * Sends a recognisable test event.
     *
     * The alert id is [TEST_ALERT_ID] rather than a real alert's, and that matters in two places at
     * once: the receiver can tell a rehearsal from the real thing, and the record does not attach
     * itself to some alert's history where it would read as a firing that never happened.
     *
     * The symbol is a real, ordinary one so that a receiver parsing the body sees the shape it will
     * actually get; a placeholder like `TEST` would let somebody ship a parser that works on the
     * rehearsal and fails on the first real event.
     */
    override suspend fun test(target: WebhookTarget): WebhookAttempt = dispatcher.test(
        target = target,
        event = WebhookEvent(
            alertId = TEST_ALERT_ID,
            symbol = TEST_SYMBOL,
            firedAt = now(),
            price = TEST_PRICE,
            timeframe = TEST_TIMEFRAME,
        ),
    )

    companion object {
        /** The alert id a test send carries. Not any real alert's, so no history is polluted. */
        const val TEST_ALERT_ID = "webhook-test"

        private const val TEST_SYMBOL = "BTCUSDT"
        private const val TEST_PRICE = 68_500.0
        private const val TEST_TIMEFRAME = "H1"
    }
}

/**
 * What the reader is typing into the webhook sheet.
 *
 * ### The URL is judged while they are still looking at the field
 *
 * That is the whole of [urlRefusal], and it is not a nicety. A webhook accepted now and refused at
 * fire time fails six hours later, at the moment a level is finally hit, to somebody who has
 * stopped watching — which is precisely the failure the alert audit log exists to expose, one layer
 * out. `WebhookUrl.validate` already answers in a Persian sentence a person can act on, so the
 * sheet shows that sentence under the field rather than inventing its own.
 *
 * ### The secret is held, never shown
 *
 * [secret] is bound to a field marked secret, and nothing renders it back: not the target list, not
 * the delivery log, not an error. An existing target's secret is **not** loaded into this draft when
 * it is edited — [secretTouched] is how "leave it as it was" is told apart from "clear it", so a
 * blank field on an edit means unchanged rather than removed. Anything else would either display
 * the value or silently destroy it.
 */
data class WebhookDraft(
    /** The target being changed, or null for a new one. */
    val editingId: String? = null,
    val name: String = "",
    val url: String = "",
    /** What has been typed into the secret field this time. Never pre-filled from storage. */
    val secret: String = "",
    /** Whether the secret field has been touched at all. See the note on [secret]. */
    val secretTouched: Boolean = false,
    val enabled: Boolean = true,
) {

    /** Whether this sheet is changing a target rather than making one. */
    val editing: Boolean get() = editingId != null

    /**
     * Why the URL cannot be used, or null when it can.
     *
     * Null while the field is still empty *and* untouched would be nicer, but the store's own rule
     * is that an empty URL is a refusal, and the sheet only shows this once something has been
     * typed — see the composable. Keeping the rule here undivided is what stops the sheet and the
     * store disagreeing about what is acceptable.
     */
    val urlRefusal: WebhookUrlRefusal? get() = WebhookUrl.validate(url)

    /** Whether the save action may run. A target with no name is not identifiable in a log. */
    val valid: Boolean get() = name.isNotBlank() && urlRefusal == null

    /**
     * The target this draft describes, or null while it is incomplete.
     *
     * [existing] is the stored target being edited, and it is what carries the secret forward when
     * the reader did not touch that field. That is the one piece of state this sheet deliberately
     * cannot see.
     */
    fun toTarget(existing: WebhookTarget?, id: String, nowEpochMillis: Long): WebhookTarget? {
        if (!valid) return null
        return WebhookTarget(
            id = existing?.id ?: id,
            name = name.trim(),
            url = url.trim(),
            secret = if (secretTouched) secret.trim().takeIf(String::isNotEmpty) else existing?.secret,
            enabled = enabled,
            createdAt = existing?.createdAt ?: nowEpochMillis,
        )
    }

    companion object {

        /**
         * The draft for a target the reader has asked to change.
         *
         * The secret is left out on purpose and the field opens empty. Loading it would put a
         * credential on a screen — and into whatever a screenshot, a screen reader or a recording
         * happens to capture — in order to save somebody re-typing something they pasted once.
         */
        fun of(target: WebhookTarget): WebhookDraft = WebhookDraft(
            editingId = target.id,
            name = target.name,
            url = target.url,
            enabled = target.enabled,
        )
    }
}
