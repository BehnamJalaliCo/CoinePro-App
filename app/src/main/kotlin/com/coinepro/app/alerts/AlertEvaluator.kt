package com.coinepro.app.alerts

import com.coinepro.core.common.AppResult
import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AlertMessageTemplate
import com.coinepro.core.notifications.AlertScope
import com.coinepro.core.notifications.AuditEvent
import com.coinepro.core.notifications.LocalPriceAlert

/**
 * The alert list, as the evaluator needs it.
 *
 * A port rather than `LocalAlertStore` itself, and the reason is the test rather than the
 * abstraction: the store is a DataStore-backed class, and the decisions this file makes — did it
 * fire twice in one bar, was the state left alone when the network failed — are the ones that must
 * be checked at their boundaries without a device. Four small interfaces buy every one of those
 * cases a plain fake.
 */
interface AlertRepository {

    /** Every stored alert, active or not. Expiry has to be recorded for alerts nobody will fire. */
    suspend fun all(): List<LocalPriceAlert>

    /** Stamps the alerts that fired, exactly as [LocalPriceAlert.fired] defines it. */
    suspend fun markFired(fired: List<LocalPriceAlert>, atEpochMillis: Long)
}

/** The current contents of a named watchlist. Resolved per pass; see [AlertScope.Watchlist]. */
interface AlertMembership {

    /** The symbols in the named list right now, or nothing for a list that no longer exists. */
    suspend fun members(listId: String): List<String>
}

/**
 * Firing state that the alert row itself cannot hold.
 *
 * ### Two cases need it, and both would be bugs without it
 *
 * A **watchlist** alert is one row covering forty symbols, and [AlertScope.Watchlist] is explicit
 * that each of them fires independently. One stamp on the row cannot express that: the first symbol
 * to move would consume the alert for the other thirty-nine.
 *
 * An alert with an [com.coinepro.core.notifications.AlertFrequency] needs it for a different and
 * less obvious reason. [LocalPriceAlert.fired] deactivates any alert whose
 * [com.coinepro.core.notifications.AlertRepeat] is `ONCE` — which is the default, and which an
 * alert made from the chart never changes because it states its policy in [LocalPriceAlert.frequency]
 * instead. Stamping such an alert through the store would switch off a «once per bar» alert after
 * its first bar, and the reader would see an alert that worked once and then went quiet.
 *
 * So those two are stamped here and the alert row is left alone; the plain price alerts that have
 * always been stamped through the store still are, so nothing the alerts screen already shows
 * changes.
 */
interface AlertFireStates {

    /** Every alert's state, keyed by [LocalPriceAlert.id]. */
    suspend fun current(): Map<String, AlertFireState>

    /** Writes the states given, replacing each alert's row and leaving every other alert's alone. */
    suspend fun write(states: List<AlertFireState>)
}

/**
 * What has happened to one alert that its own row does not record.
 *
 * [expiredRecordedAt] exists so that [AuditEvent.EXPIRED] is written once rather than on every
 * fifteen-minute pass for the rest of the alert's life. The audit log could be searched for it
 * instead, but the log trims at five hundred lines and a busy reader would eventually push the
 * entry off the end and start collecting duplicates of it.
 */
data class AlertFireState(
    val alertId: String,
    val lastFiredBySymbol: Map<String, Long> = emptyMap(),
    val expiredRecordedAt: Long? = null,
)

/** One instrument's worth of fetching, as the trigger on it asks for. */
data class AlertMarketRequest(val symbol: String, val needs: AlertDataNeeds)

/**
 * Where the numbers come from.
 *
 * Failure is for the whole pass rather than per symbol, because that is what it means: the price
 * route was unreachable, and a route that was unreachable once says nothing at all about whether
 * any alert should have fired. Returning a partial map would let a missing symbol look like a
 * condition that did not hold, which is the difference between an alert that is late and an alert
 * that is silently gone.
 */
interface AlertMarketSource {

    /** One sample per symbol that could be read, or one failure standing for the whole pass. */
    suspend fun read(requests: List<AlertMarketRequest>): AppResult<Map<String, AlertSample>>
}

/** The audit log, narrowed to the one thing the evaluator does with it. */
interface AlertAuditLog {

    /** Appends several lines in one write, since a pass that fires at all usually fires a few. */
    suspend fun record(entries: List<AlertAuditEntry>)
}

/**
 * One alert that has fired, on one symbol, with the text the reader will see.
 *
 * The body is rendered at the moment of firing rather than at the moment of display, so the audit
 * line and the notification carry the same sentence and a re-read of the log months later shows
 * what was actually sent.
 */
data class FiredAlert(
    val alert: LocalPriceAlert,
    val symbol: String,
    val price: Double,
    val timeframe: String,
    val atEpochMillis: Long,
    /**
     * The reader's own wording with the facts filled in, or the bare symbol and price where they
     * wrote none. The notification wraps the second case in the app's own Persian sentence, which
     * is where that prose belongs; see `AndroidAlertDeliverer`.
     */
    val body: String,
)

/**
 * What became of one delivery.
 *
 * There is deliberately no third answer for «the reader's own settings suppressed it». Quiet hours
 * and a category switched off are perfectly good reasons not to show a notification and terrible
 * reasons to leave no trace: somebody asking why they were not told at three in the morning is
 * asking exactly that question, and [Failed] with the reason in its text is the honest answer. What
 * the log must never contain is a firing with nothing after it.
 */
sealed interface AlertDeliveryOutcome {

    /** It reached the reader, by at least one of the channels the alert asked for. */
    data object Delivered : AlertDeliveryOutcome

    /** It did not. [reason] is short prose, and it is written into the audit line's note. */
    data class Failed(val reason: String) : AlertDeliveryOutcome
}

/** How a fired alert reaches the reader. Implemented on Android; faked in tests. */
interface AlertDeliverer {

    /**
     * Attempts one delivery and says what became of it.
     *
     * Implementations should answer rather than throw, but the evaluator does not rely on that:
     * an exception is caught and recorded as a failure, because the log must not have a firing in
     * it with nothing after it.
     */
    suspend fun deliver(fired: FiredAlert): AlertDeliveryOutcome
}

/** What one pass did, for the worker to turn into a WorkManager result. */
sealed interface AlertPassResult {

    /** Nothing to evaluate. Not a failure and not worth a retry. */
    data object Idle : AlertPassResult

    /** The pass ran. Counts are for the caller's own diagnostics, not for the reader. */
    data class Completed(val fired: Int, val expired: Int) : AlertPassResult

    /**
     * The market could not be read, so nothing was decided and nothing was written.
     *
     * The distinction this whole class exists to make: an alert that could not be evaluated is
     * still armed. Consuming it — stamping it, deactivating it, writing a firing — because a
     * request timed out would lose the reader's alert to a dropped packet.
     */
    data class Unavailable(val reason: String) : AlertPassResult
}

/**
 * Decides which alerts are due, delivers them, and writes down what happened.
 *
 * ### Everything that is hard about alerts is in this class, and none of it is Android
 *
 * No context, no notification manager, no DataStore, no clock: every one of those arrives through a
 * port or as a parameter. That is what makes the cases that actually go wrong testable — an alert
 * firing twice inside one bar, a watchlist member consuming its neighbours' alert, a delivery that
 * threw, a network failure landing on a reader's carefully-set alert — instead of being things
 * somebody discovers during a move that mattered.
 *
 * ### The order of writes, and why it is that order
 *
 * [AuditEvent.FIRED] is written first, because it records the *decision*, and a decision that was
 * made must be recorded whether or not the delivery that follows works. Then the firing state is
 * stamped, before anything is shown: if the process dies between the stamp and the notification the
 * reader loses one alert, whereas stamping afterwards would re-fire the same alert on every pass
 * for as long as the condition held, which is the failure that empties an inbox. Delivery comes
 * last and its outcome — [AuditEvent.DELIVERED] or [AuditEvent.DELIVERY_FAILED] — is recorded even
 * when the deliverer throws. A swallowed delivery exception is the exact shape of «alerts not
 * working», and it is the one thing this class refuses to do.
 */
class AlertEvaluator(
    private val alerts: AlertRepository,
    private val membership: AlertMembership,
    private val fireStates: AlertFireStates,
    private val market: AlertMarketSource,
    private val audit: AlertAuditLog,
    private val deliverer: AlertDeliverer,
) {

    /**
     * Runs one pass.
     *
     * [nowEpochMillis] is a parameter rather than a clock read, so «does a once-per-day alert fire
     * after twenty-three hours» is an assertion rather than a wait.
     */
    suspend fun evaluate(nowEpochMillis: Long): AlertPassResult {
        val stored = alerts.all()
        if (stored.isEmpty()) return AlertPassResult.Idle

        val states = fireStates.current().toMutableMap()
        val expired = recordExpiries(stored, states, nowEpochMillis)

        val candidates = stored.filter { it.active && !it.hasExpired(nowEpochMillis) }
        if (candidates.isEmpty()) {
            return if (expired == 0) AlertPassResult.Idle else AlertPassResult.Completed(fired = 0, expired = expired)
        }

        val coverage = coverageOf(candidates)
        if (coverage.isEmpty()) {
            return if (expired == 0) AlertPassResult.Idle else AlertPassResult.Completed(fired = 0, expired = expired)
        }

        val samples = when (val result = market.read(requestsFor(coverage))) {
            is AppResult.Success -> result.value
            // Nothing is written on this path, deliberately. See [AlertPassResult.Unavailable].
            is AppResult.Failure -> return AlertPassResult.Unavailable(result.message ?: result.kind.name)
        }

        val fired = coverage.flatMap { (alert, symbols) ->
            symbols.mapNotNull { symbol -> fire(alert, symbol, samples[symbol], states, nowEpochMillis) }
        }

        if (fired.isEmpty()) {
            return if (expired == 0) AlertPassResult.Idle else AlertPassResult.Completed(fired = 0, expired = expired)
        }

        audit.record(fired.map { entry(it, AuditEvent.FIRED) })
        stampFirings(fired, states, nowEpochMillis)
        deliver(fired)
        return AlertPassResult.Completed(fired = fired.size, expired = expired)
    }

    /**
     * Writes [AuditEvent.EXPIRED] for each alert whose own expiry has just passed.
     *
     * Once per alert, ever. An expiry the reader typed is the one way an alert can stop working
     * without them touching it, so it is the one thing the log has to be able to show them; a
     * repeated entry every fifteen minutes would bury the rest of the log inside a week.
     */
    private suspend fun recordExpiries(
        stored: List<LocalPriceAlert>,
        states: MutableMap<String, AlertFireState>,
        nowEpochMillis: Long,
    ): Int {
        val newly = stored.filter { alert ->
            alert.hasExpired(nowEpochMillis) && states[alert.id]?.expiredRecordedAt == null
        }
        if (newly.isEmpty()) return 0
        audit.record(
            newly.map { alert ->
                AlertAuditEntry(
                    alertId = alert.id,
                    event = AuditEvent.EXPIRED,
                    at = nowEpochMillis,
                    symbol = alert.symbol,
                )
            },
        )
        val written = newly.map { alert ->
            val state = states[alert.id] ?: AlertFireState(alert.id)
            state.copy(expiredRecordedAt = nowEpochMillis).also { states[alert.id] = it }
        }
        fireStates.write(written)
        return newly.size
    }

    /**
     * Which symbols each alert covers, resolved now.
     *
     * Membership is read per pass and never captured, which is the whole promise of a watchlist
     * alert: a symbol starred this morning is covered this afternoon without the reader re-making
     * anything, and one un-starred stops being watched rather than leaving a stale alert pointing at
     * something they deliberately dropped.
     */
    private suspend fun coverageOf(candidates: List<LocalPriceAlert>): List<Pair<LocalPriceAlert, List<String>>> {
        val listIds = candidates.mapNotNull { (it.effectiveScope as? AlertScope.Watchlist)?.listId }.distinct()
        val lists = listIds.associateWith { membership.members(it) }
        return candidates
            .map { alert -> alert to alert.symbols { listId -> lists[listId].orEmpty() }.distinct() }
            .filter { (_, symbols) -> symbols.isNotEmpty() }
    }

    /** One request per symbol, carrying the union of what every alert on it needs. */
    private fun requestsFor(coverage: List<Pair<LocalPriceAlert, List<String>>>): List<AlertMarketRequest> {
        val needs = LinkedHashMap<String, AlertDataNeeds>()
        coverage.forEach { (alert, symbols) ->
            val required = AlertConditions.needsOf(alert)
            symbols.forEach { symbol ->
                needs[symbol] = needs[symbol]?.plus(required) ?: required
            }
        }
        return needs.map { (symbol, required) -> AlertMarketRequest(symbol, required) }
    }

    /**
     * Whether this alert fires for this symbol, with the firing state that applies to this symbol.
     *
     * A symbol with no sample is not a symbol whose condition failed; it is one the feed did not
     * carry, and it is passed over silently rather than counted either way.
     */
    private fun fire(
        alert: LocalPriceAlert,
        symbol: String,
        sample: AlertSample?,
        states: Map<String, AlertFireState>,
        nowEpochMillis: Long,
    ): FiredAlert? {
        if (sample == null) return null
        val stated = if (isStampedOnTheRow(alert)) {
            alert
        } else {
            alert.copy(lastFiredAtEpochMillis = states[alert.id]?.lastFiredBySymbol?.get(symbol))
        }
        if (!AlertConditions.due(stated, sample, nowEpochMillis)) return null
        return FiredAlert(
            alert = alert,
            symbol = symbol,
            price = sample.price,
            timeframe = sample.timeframe,
            atEpochMillis = nowEpochMillis,
            body = AlertMessageTemplate.render(
                message = alert.message,
                symbol = symbol,
                price = sample.price,
                at = nowEpochMillis,
                timeframe = sample.timeframe,
            ),
        )
    }

    /**
     * Whether this alert's firing belongs on the alert row rather than in [AlertFireStates].
     *
     * Only the plain, single-symbol, repeat-governed alerts — the ones the store has always stamped
     * and the alerts screen already shows a «last fired» for. See [AlertFireStates] for the two
     * kinds that must not be stamped there and what goes wrong when they are.
     */
    private fun isStampedOnTheRow(alert: LocalPriceAlert): Boolean =
        alert.frequency == null && alert.effectiveScope is AlertScope.Symbol

    private suspend fun stampFirings(
        fired: List<FiredAlert>,
        states: MutableMap<String, AlertFireState>,
        nowEpochMillis: Long,
    ) {
        val (onRow, perSymbol) = fired.partition { isStampedOnTheRow(it.alert) }
        if (onRow.isNotEmpty()) {
            alerts.markFired(onRow.map(FiredAlert::alert).distinctBy(LocalPriceAlert::id), nowEpochMillis)
        }
        if (perSymbol.isEmpty()) return
        val written = perSymbol.groupBy { it.alert.id }.map { (alertId, firings) ->
            val state = states[alertId] ?: AlertFireState(alertId)
            val stamped = state.copy(
                lastFiredBySymbol = state.lastFiredBySymbol + firings.associate { it.symbol to nowEpochMillis },
            )
            states[alertId] = stamped
            stamped
        }
        fireStates.write(written)
    }

    /**
     * Delivers each firing and records what happened to it.
     *
     * The `runCatching` is the point of the method. A deliverer that throws — a notification the
     * system refused, a permission revoked between the check and the post — must still produce a
     * line in the log, because the reader's question afterwards is not «did it throw», it is
     * «was I told», and that has an answer either way.
     */
    private suspend fun deliver(fired: List<FiredAlert>) {
        val outcomes = fired.map { firing ->
            val outcome = runCatching { deliverer.deliver(firing) }
                .getOrElse { failure ->
                    AlertDeliveryOutcome.Failed(failure.message ?: failure::class.java.simpleName)
                }
            when (outcome) {
                AlertDeliveryOutcome.Delivered -> entry(firing, AuditEvent.DELIVERED)
                is AlertDeliveryOutcome.Failed -> entry(firing, AuditEvent.DELIVERY_FAILED, outcome.reason)
            }
        }
        audit.record(outcomes)
    }

    private fun entry(fired: FiredAlert, event: AuditEvent, note: String? = null) = AlertAuditEntry(
        alertId = fired.alert.id,
        event = event,
        at = fired.atEpochMillis,
        symbol = fired.symbol,
        price = fired.price,
        timeframe = fired.timeframe,
        note = note,
    )
}
