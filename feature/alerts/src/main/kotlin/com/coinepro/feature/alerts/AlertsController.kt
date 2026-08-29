package com.coinepro.feature.alerts

import com.coinepro.core.datastore.AlertAuditStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertMessageTemplate
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.PriceOp
import com.coinepro.core.symbols.SymbolArtwork
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolSearch
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One alert as the list draws it.
 *
 * The sentence is rendered here rather than in the composable because it is a pure function over
 * the alert and nothing else — computing it during composition would redo the same work on every
 * scroll frame, and it would put the one piece of this feature that has to be right somewhere a
 * unit test cannot reach.
 */
data class AlertRow(
    val alert: LocalPriceAlert,
    /** The condition as a Persian sentence. See [AlertSentence] for the digit rule. */
    val sentence: String,
    /** The bar this alert is evaluated on, where anything knows one. Null hides the label. */
    val timeframe: String?,
    val kind: AlertSectionKind,
) {
    /** Whether the reader has switched this one off. Drawn as a mark, not as its own section. */
    val paused: Boolean get() = !alert.active && kind == AlertSectionKind.ARMED
}

/** One heading of the list, with its rows already ordered by [AlertGrouping]. */
data class AlertRowSection(val kind: AlertSectionKind, val rows: List<AlertRow>)

/** What the audit sheet is showing, including the fact that it has not loaded yet. */
data class AlertAuditView(
    val alert: LocalPriceAlert,
    val sentence: String,
    val entries: List<AlertAuditEntry> = emptyList(),
    val loading: Boolean = true,
)

/**
 * The one thing that can stop a save, worth its own type because it is not the reader's mistake.
 *
 * An incomplete form is reported by the action being dim and by the field's own supporting line. A
 * full list is different: the form is correct and the app is refusing, so it says so in words.
 */
enum class AlertRefusal {
    /** The phone already holds [LocalPriceAlert.MAX_ALERTS] of them. */
    LIST_FULL,
}

/** Everything the three surfaces draw, in one immutable value. */
data class AlertsUiState(
    val loading: Boolean = true,
    val sections: List<AlertRowSection> = emptyList(),
    val total: Int = 0,
    /** Whether the store will refuse another. Shown before the reader fills a form in. */
    val full: Boolean = false,
    val draft: AlertDraft? = null,
    /** The symbol picker's current results. Empty unless the editor is open. */
    val symbolMatches: List<SymbolMeta> = emptyList(),
    val actionsFor: AlertRow? = null,
    val confirmingDelete: AlertRow? = null,
    val audit: AlertAuditView? = null,
    val refusal: AlertRefusal? = null,
) {
    /** Whether the list has nothing in it at all, as against having nothing in one section. */
    val empty: Boolean get() = !loading && sections.isEmpty()
}

/**
 * The alerts screen's state and every change a reader can make to it.
 *
 * ### Why the store is the source of truth and this holds no copy of the list
 *
 * `LocalAlertStore` exposes a `Flow`, and the same alerts are written by the background worker when
 * one fires. A controller that kept its own list would show a fired alert as still waiting until
 * something happened to refresh it — which is precisely the failure the audit log exists to explain
 * — so the list here is derived from the store's flow on every emission and never cached.
 *
 * What *is* held locally is the part that is not an alert: which sheet is open, what the reader has
 * typed into it, and what the last undoable action was. Those are combined with the store's flow
 * rather than merged into it, so opening a sheet does not touch the reader's data.
 *
 * ### No blocking calls
 *
 * Every write is a `suspend` call launched into [scope]. Nothing here reads a clock or a store
 * synchronously, and the two pieces that decide what the screen says — [AlertSentence] and
 * [AlertGrouping] — are pure functions taking the clock as an argument.
 */
class AlertsController(
    private val store: LocalAlertStore,
    private val audit: AlertAuditStore,
    /**
     * The markets a reader may put an alert on.
     *
     * A supplier rather than a list because the catalogue arrives from the network after this
     * controller is built, and rather than a `Flow` because the picker only reads it while the
     * editor is open. Everything it returns is classified and then filtered through
     * [SymbolArtwork.covers]: a symbol with no artwork must never reach a list in this app, and a
     * picker is the one place a blank disc would be created rather than merely shown.
     */
    private val catalogOf: () -> List<String>,
    private val scope: CoroutineScope,
    /**
     * The bar an alert is evaluated on.
     *
     * A function because the timeframe is not a field of `LocalPriceAlert`: an alert made from a
     * chart has one and an alert made from a market row does not, and the app knows which is which
     * from the chart state it already keeps. Returning null is the honest answer for an alert with
     * no timeframe, and the row then omits the label rather than showing an invented one.
     */
    private val timeframeOf: (LocalPriceAlert) -> String? = { null },
    /**
     * Drops the evaluator's own bookkeeping for one alert id.
     *
     * A function rather than the store itself because that store belongs to the application module,
     * which this feature cannot see and should not: the evaluator's per-symbol stamps are not part
     * of an alert and no screen has any business reading them. What the screen *does* know is the
     * moments they stop meaning anything — an alert deleted, one switched back on, one edited —
     * and it has to say so. Left behind, a deleted alert's stamps outlive it in a file that is read
     * whole on every launch, and the next alert that happens to be given the same id inherits them:
     * it would arrive already believing it had fired on those symbols, and stay silent.
     *
     * Defaults to doing nothing, so a test that does not care about the evaluator need not fake it.
     */
    private val forgetFireState: suspend (String) -> Unit = {},
    /** Injected so the grouping boundaries are testable without waiting a day. */
    private val now: () -> Long = System::currentTimeMillis,
    /** Hexadecimal, because the store's delimited format reserves `;` and `|`. */
    private val newId: () -> String = { UUID.randomUUID().toString().replace("-", "").take(ID_LENGTH) },
) {

    private val ui = MutableStateFlow(Extras())

    /**
     * The catalogue, classified once per distinct list.
     *
     * Classifying a few thousand tickers on every keystroke is real work for no gain, and the
     * catalogue changes about once a session. The memo is keyed on the supplied list's identity,
     * so a caller that hands back a new list gets a fresh classification and one that hands back
     * the same list gets the cached one; being wrong here only ever costs a recomputation.
     */
    private var catalogSource: List<String> = emptyList()
    private var catalogCache: List<SymbolMeta> = emptyList()

    /** Undoes the last reversible change. Delete is not in here; it asks first instead. */
    private var undoAction: (suspend () -> Unit)? = null

    private var auditJob: Job? = null

    val state: StateFlow<AlertsUiState> =
        combine(store.alerts, ui) { alerts, extras -> compose(alerts, extras) }
            .stateIn(scope, SharingStarted.Eagerly, AlertsUiState())

    // ── the list ────────────────────────────────────────────────────────────────────────────

    /** Opens the long-press menu for one alert. */
    fun openActions(row: AlertRow) {
        ui.update { it.copy(actionsFor = row.alert.id) }
    }

    /** Closes it again, leaving the alert as it was. */
    fun closeActions() {
        ui.update { it.copy(actionsFor = null) }
    }

    /**
     * Switches an alert off, or back on.
     *
     * Reversible, and the screen offers the reversal through the toaster rather than asking first.
     * Pausing an alert costs nothing if it was a mistake — the alert is still there — and a
     * confirmation dialog in front of a harmless, undoable action teaches the reader to dismiss
     * dialogs without reading them.
     */
    fun setPaused(row: AlertRow, paused: Boolean) {
        val id = row.alert.id
        val previous = row.alert.active
        closeActions()
        // Set before the write rather than after it, so an undo tapped in the second the toaster is
        // on screen is never dropped for arriving before the disk did.
        undoAction = { store.setActive(id, previous) }
        scope.launch {
            store.setActive(id, !paused)
            // Switching an alert back on is re-arming it, and an alert that has just been re-armed
            // must not still be holding the stamps that say it has already spoken. Only on the way
            // back on: pausing one is temporary and its stamps are still true when it resumes
            // without the reader having asked for anything to be forgotten.
            if (!paused) forgetFireState(id)
        }
    }

    /**
     * Copies an alert, armed and never fired.
     *
     * The common way somebody makes their second alert on an instrument: the same condition at a
     * different level. The copy is active even where the original was paused, because a reader who
     * duplicates a paused alert is making a new one rather than making another paused one.
     */
    fun duplicate(row: AlertRow) {
        closeActions()
        val id = newId()
        val copy = row.alert.copy(
            id = id,
            active = true,
            createdAtEpochMillis = now(),
            lastFiredAtEpochMillis = null,
        )
        // Removing an id the store never took is a no-op there, so this is safe to arm before the
        // write even in the case below where the write is refused.
        undoAction = { store.remove(id) }
        scope.launch {
            if (!store.add(copy)) ui.update { it.copy(refusal = AlertRefusal.LIST_FULL) }
        }
    }

    /** Asks before deleting. The only action on this screen that does. */
    fun requestDelete(row: AlertRow) {
        ui.update { it.copy(actionsFor = null, confirmingDelete = row.alert.id) }
    }

    /** Walks away from the question, which is always the safe answer to it. */
    fun cancelDelete() {
        ui.update { it.copy(confirmingDelete = null) }
    }

    /**
     * Removes the alert the reader confirmed.
     *
     * No undo is offered afterwards, on purpose. The question was asked in front of the action, and
     * a screen that both asks and then offers to take it back is one that has decided the question
     * was not worth asking.
     */
    fun confirmDelete() {
        val id = ui.value.confirmingDelete ?: return
        ui.update { it.copy(confirmingDelete = null, auditFor = null) }
        undoAction = null
        scope.launch {
            store.remove(id)
            forgetFireState(id)
        }
    }

    /** Runs the last reversible change backwards. Does nothing where there is none. */
    fun undo() {
        val action = undoAction ?: return
        undoAction = null
        scope.launch { action() }
    }

    /** Clears a refusal the reader has read. */
    fun dismissRefusal() {
        ui.update { it.copy(refusal = null) }
    }

    // ── the editor ──────────────────────────────────────────────────────────────────────────

    /**
     * Opens an empty editor.
     *
     * [symbol] is pre-filled when the reader arrived from an instrument — from a chart or a market
     * row — and the picker then stays shut, because they have already answered the question it
     * asks.
     */
    fun openEditor(symbol: String? = null) {
        val ticker = symbol?.trim()?.uppercase().orEmpty()
        // The audit sheet is closed with it: two modal sheets stacked on one another is a state
        // the reader cannot reason about, and the editor is the one they just asked for.
        closeAudit()
        ui.update {
            it.copy(
                actionsFor = null,
                refusal = null,
                draft = AlertDraft(symbol = ticker, pickingSymbol = ticker.isEmpty()),
            )
        }
    }

    /**
     * Opens the editor on an existing alert, where the alert can be expressed by it.
     *
     * Does nothing for a drawing alert or a 24-hour-change alert. The menu hides «ویرایش» for those
     * — see [AlertDraft.of] — so this is a guard rather than a path a reader can reach.
     */
    fun editAlert(row: AlertRow) {
        val draft = AlertDraft.of(row.alert) ?: return
        closeAudit()
        ui.update { it.copy(actionsFor = null, refusal = null, draft = draft) }
    }

    /** Abandons the sheet. What was typed is dropped; nothing is written until save. */
    fun closeEditor() {
        ui.update { it.copy(draft = null, refusal = null) }
    }

    /** Opens or closes the symbol picker inside the sheet. */
    fun setPickingSymbol(picking: Boolean) {
        editDraft { it.copy(pickingSymbol = picking, query = if (picking) it.query else "") }
    }

    /** What the reader has typed into the picker. Ranking is [SymbolSearch]'s, not this class's. */
    fun setQuery(query: String) {
        editDraft { it.copy(query = query) }
    }

    /** Chooses the instrument and closes the picker, which has done its job. */
    fun setSymbol(symbol: String) {
        editDraft { it.copy(symbol = symbol.trim().uppercase(), pickingSymbol = false, query = "") }
    }

    /**
     * Changes what kind of condition a row is.
     *
     * The row is rebuilt rather than copied, so the fields of the kind being left behind do not
     * survive into the kind being chosen. A channel's high left over inside a price condition is
     * invisible — the field is hidden — and would be saved.
     */
    fun setConditionKind(index: Int, kind: AlertTriggerKind) {
        editCondition(index) { row ->
            if (row.kind == kind) {
                row
            } else {
                AlertConditionDraft(
                    kind = kind,
                    priceOp = row.priceOp,
                    channelOp = row.channelOp,
                    moveOp = row.moveOp,
                )
            }
        }
    }

    /** How this row compares a number to a level. See `PriceOp` for the boundary rules. */
    fun setPriceOp(index: Int, op: PriceOp) {
        editCondition(index) { it.copy(priceOp = op) }
    }

    /** How this row compares a price to a band: entering it, leaving it, or being in or out. */
    fun setChannelOp(index: Int, op: ChannelOp) {
        editCondition(index) { it.copy(channelOp = op) }
    }

    /** The direction of the move, and whether its number is a price or a percentage. */
    fun setMoveOp(index: Int, op: MoveOp) {
        editCondition(index) { it.copy(moveOp = op) }
    }

    /** The row's first number, as typed. Parsed only when something asks for it. */
    fun setFirst(index: Int, text: String) {
        editCondition(index) { it.copy(first = text) }
    }

    /** The band's high. Only a channel row shows the field this writes to. */
    fun setSecond(index: Int, text: String) {
        editCondition(index) { it.copy(second = text) }
    }

    /** Chooses the study, and moves the stepper to that study's own usual lookback. */
    fun setIndicator(index: Int, indicatorId: String) {
        editCondition(index) {
            it.copy(indicatorId = indicatorId, period = AlertIndicators.defaultPeriodOf(indicatorId))
        }
    }

    /** Moves the period stepper. Clamped rather than refused; see [AlertIndicators.coercePeriod]. */
    fun setPeriod(index: Int, period: Int) {
        editCondition(index) {
            if (it.period == null) it else it.copy(period = AlertIndicators.coercePeriod(period))
        }
    }

    /**
     * Adds a condition, up to the cap.
     *
     * Silent at the cap because the screen has already said what the cap is, from the first
     * condition onwards, and a message that appears only once the button stops working tells the
     * reader something they could have known before they pressed it.
     */
    fun addCondition() {
        editDraft { draft ->
            if (!draft.canAddCondition) draft else draft.copy(conditions = draft.conditions + AlertConditionDraft())
        }
    }

    /** Removes one condition. The last one stays: an alert with no condition is not an alert. */
    fun removeCondition(index: Int) {
        editDraft { draft ->
            if (draft.conditions.size <= 1) {
                draft
            } else {
                draft.copy(conditions = draft.conditions.filterIndexed { at, _ -> at != index })
            }
        }
    }

    /** How often the alert may speak, in bars. See `AlertFrequency` for why not in minutes. */
    fun setFrequency(frequency: AlertFrequency) {
        editDraft { it.copy(frequency = frequency) }
    }

    /** Turns one delivery channel on or off. Turning all of them off is a choice, not an error. */
    fun toggleChannel(channel: AlertChannel) {
        editDraft { draft ->
            val next = if (channel in draft.channels) draft.channels - channel else draft.channels + channel
            draft.copy(channels = next)
        }
    }

    /** The reader's own wording. Length is checked by the draft, not truncated here. */
    fun setMessage(message: String) {
        editDraft { it.copy(message = message) }
    }

    /**
     * Appends a placeholder to the reader's message.
     *
     * Offered as a chip rather than typed because `{symbol}` typed by hand is `{sybmol}` often
     * enough to matter, and a mistyped placeholder is not reported anywhere: it renders as itself
     * in the notification, in front of the reader, at the moment their level is hit.
     */
    fun appendPlaceholder(placeholder: String) {
        editDraft { draft ->
            val separator = if (draft.message.isEmpty() || draft.message.endsWith(" ")) "" else " "
            val next = draft.message + separator + placeholder
            if (next.length > AlertMessageTemplate.MAX_LENGTH) draft else draft.copy(message = next)
        }
    }

    /**
     * Writes the alert and closes the sheet.
     *
     * An edit goes through `upsert` rather than a removal and an insertion. The store rewrites the
     * whole list either way, so the pair was two writes for one change, and between them the
     * reader's alert was not stored at all — which the background evaluator reads across. `upsert`
     * also cannot be refused for an alert that is already in the list, so an edit still succeeds
     * with the list at its cap; only a genuinely new alert can be turned away.
     */
    fun save() {
        val draft = ui.value.draft ?: return
        if (!draft.valid) return
        val existing = draft.editingId?.let { id -> currentAlerts().firstOrNull { it.id == id } }
        val alert = draft.toAlert(existing = existing, id = newId(), nowEpochMillis = now()) ?: return
        undoAction = null
        scope.launch {
            val written = if (existing != null) store.upsert(alert) else store.add(alert)
            // An edit re-arms the alert — `AlertDraft.toAlert` clears the last firing on purpose —
            // so the evaluator's stamps for it are about a condition the reader has just changed
            // and must go with it. Leaving them would let a corrected alert stay silent on exactly
            // the symbols it had already spoken about.
            if (written && existing != null) forgetFireState(alert.id)
            if (written) {
                ui.update { it.copy(draft = null, refusal = null) }
            } else {
                // Replacing an alert that is already stored cannot fail, so this is only reachable
                // for a new alert on a full list. The sheet stays open with what the reader typed
                // still in it.
                ui.update { it.copy(refusal = AlertRefusal.LIST_FULL) }
            }
        }
    }

    // ── the audit sheet ─────────────────────────────────────────────────────────────────────

    /**
     * Opens one alert's history and starts following it.
     *
     * Following rather than reading once: an alert can fire while its own log is on screen, and a
     * sheet that showed a snapshot would be the one place in the app where the reader watches
     * nothing happen during the event they opened it for.
     */
    fun openAudit(row: AlertRow) {
        val id = row.alert.id
        auditJob?.cancel()
        ui.update { it.copy(actionsFor = null, auditFor = id, auditEntries = emptyList(), auditLoading = true) }
        auditJob = scope.launch {
            audit.entriesFor(id).collect { entries ->
                ui.update { current ->
                    if (current.auditFor != id) current else current.copy(auditEntries = entries, auditLoading = false)
                }
            }
        }
    }

    /** Closes the history and stops following it, so no collector outlives the sheet. */
    fun closeAudit() {
        auditJob?.cancel()
        auditJob = null
        ui.update { it.copy(auditFor = null, auditEntries = emptyList(), auditLoading = false) }
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────

    private fun currentAlerts(): List<LocalPriceAlert> = state.value.sections.flatMap { section ->
        section.rows.map(AlertRow::alert)
    }

    private fun editDraft(transform: (AlertDraft) -> AlertDraft) {
        ui.update { extras ->
            val draft = extras.draft ?: return@update extras
            extras.copy(draft = transform(draft), refusal = null)
        }
    }

    private fun editCondition(index: Int, transform: (AlertConditionDraft) -> AlertConditionDraft) {
        editDraft { draft ->
            if (index !in draft.conditions.indices) {
                draft
            } else {
                draft.copy(
                    conditions = draft.conditions.mapIndexed { at, row ->
                        if (at == index) transform(row) else row
                    },
                )
            }
        }
    }

    private fun compose(alerts: List<LocalPriceAlert>, extras: Extras): AlertsUiState {
        val stamp = now()
        val sections = AlertGrouping.group(alerts, stamp).map { section ->
            AlertRowSection(
                kind = section.kind,
                rows = section.alerts.map { alert ->
                    AlertRow(
                        alert = alert,
                        sentence = AlertSentence.render(alert),
                        timeframe = timeframeOf(alert),
                        kind = section.kind,
                    )
                },
            )
        }
        val rows = sections.flatMap(AlertRowSection::rows)
        val byId = rows.associateBy { it.alert.id }
        val draft = extras.draft
        return AlertsUiState(
            loading = false,
            sections = sections,
            total = alerts.size,
            full = alerts.size >= LocalPriceAlert.MAX_ALERTS,
            draft = draft,
            symbolMatches = if (draft == null) emptyList() else matches(draft.query),
            actionsFor = extras.actionsFor?.let(byId::get),
            confirmingDelete = extras.confirmingDelete?.let(byId::get),
            audit = extras.auditFor?.let(byId::get)?.let { row ->
                AlertAuditView(
                    alert = row.alert,
                    sentence = row.sentence,
                    entries = extras.auditEntries,
                    loading = extras.auditLoading,
                )
            },
            refusal = extras.refusal,
        )
    }

    /**
     * The picker's results.
     *
     * An empty query is the browse list rather than nothing, so somebody who does not know the
     * ticker can still find their market; [SymbolSearch] ranks both cases.
     */
    private fun matches(query: String): List<SymbolMeta> =
        SymbolSearch.search(catalog(), query).take(PICKER_LIMIT).map { it.meta }

    private fun catalog(): List<SymbolMeta> {
        val source = catalogOf()
        if (source !== catalogSource) {
            catalogSource = source
            catalogCache = SymbolClassifier.classifyAll(source).filter { SymbolArtwork.covers(it) }
        }
        return catalogCache
    }

    /**
     * The parts of the screen that are not the reader's data.
     *
     * Sheets are remembered by alert id rather than by value, so a sheet open on an alert that
     * fires — or that the background worker rewrites — redraws against the new version instead of
     * against the copy it was opened with. An alert that disappears closes its own sheet.
     */
    private data class Extras(
        val draft: AlertDraft? = null,
        val actionsFor: String? = null,
        val confirmingDelete: String? = null,
        val auditFor: String? = null,
        val auditEntries: List<AlertAuditEntry> = emptyList(),
        val auditLoading: Boolean = false,
        val refusal: AlertRefusal? = null,
    )

    private companion object {
        /** Sixteen hexadecimal characters. Short enough to read in a preferences file by eye. */
        const val ID_LENGTH = 16

        /**
         * How many markets the picker lists at once.
         *
         * The catalogue runs to thousands and a picker is not a market screen. Fifty is more than
         * anybody scrolls before they type, and typing is what the search field is for.
         */
        const val PICKER_LIMIT = 50
    }
}
