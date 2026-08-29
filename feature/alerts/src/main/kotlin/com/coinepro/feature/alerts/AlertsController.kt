package com.coinepro.feature.alerts

import com.coinepro.core.datastore.AlertAuditStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.StoredDrawing
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.notifications.AlertAuditEntry
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertMessageTemplate
import com.coinepro.core.notifications.AuditEvent
import com.coinepro.core.notifications.ChannelOp
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.MoveOp
import com.coinepro.core.notifications.PriceAlert
import com.coinepro.core.notifications.PriceOp
import com.coinepro.core.symbols.SymbolArtwork
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolSearch
import com.coinepro.core.webhook.WebhookAttempt
import com.coinepro.core.webhook.WebhookTarget
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    /**
     * Where this alert is decided.
     *
     * On the row rather than inferred at the call site, because every action the reader takes has
     * to go somewhere different for the two — pause reaches a preferences file for one and an HTTP
     * route for the other — and a screen that worked it out from the id each time would have five
     * places to get it wrong.
     */
    val venue: AlertVenue = AlertVenue.DEVICE,
) {
    /** Whether the reader has switched this one off. Drawn as a mark, not as its own section. */
    val paused: Boolean get() = !alert.active && kind == AlertSectionKind.ARMED

    /** The server's own id for a server alert, or null for one this phone decides. */
    val serverId: String? get() = ServerAlertRows.serverIdOf(alert.id)
}

/** One heading of the list, with its rows already ordered by [AlertGrouping]. */
data class AlertRowSection(val kind: AlertSectionKind, val rows: List<AlertRow>)

/** What the audit sheet is showing, including the fact that it has not loaded yet. */
data class AlertAuditView(
    val alert: LocalPriceAlert,
    val sentence: String,
    val entries: List<AlertAuditEntry> = emptyList(),
    /**
     * What this alert's webhooks did, newest first.
     *
     * Beside the notification's own log rather than on a screen of its own, because a reader whose
     * bot did nothing looks at the alert first. The two answer the same question about two
     * different recipients, and «اعلان رسید / گیرنده نپذیرفت» is one story on one sheet.
     */
    val deliveries: List<WebhookAttempt> = emptyList(),
    val loading: Boolean = true,
    /**
     * Where the alert is decided.
     *
     * The sheet needs it because the log is written by *this* app's evaluator and nothing else. A
     * server alert has no lines in it and never will, and an empty history under «هنوز چیزی ثبت
     * نشده» would read as an alert that has done nothing — when in fact it is being watched
     * somewhere this phone cannot see. The sheet says which.
     */
    val venue: AlertVenue = AlertVenue.DEVICE,
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

    /**
     * The server would not take it.
     *
     * One refusal rather than a transcription of the server's own error, because from this sheet
     * there is exactly one thing the reader can do about any of them: the route needs an account,
     * and a signed-out reader is the overwhelming case. The sheet stays open with what they typed,
     * and the device venue is still there and still works without an account — which is the whole
     * reason local alerts exist. See `LocalPriceAlert` for that argument.
     */
    SERVER_REFUSED,
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
    /** Whether the webhook sheet is open. */
    val webhooksOpen: Boolean = false,
    /** The reader's webhook targets, for that sheet. */
    val webhookTargets: List<WebhookTarget> = emptyList(),
    /** The target being made or changed, or null while the sheet is only listing them. */
    val webhookDraft: WebhookDraft? = null,
    /** What the last «آزمایش» came back with. Cleared when the editor is opened or closed. */
    val webhookTest: WebhookAttempt? = null,
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
    /**
     * The drawings on one symbol's chart, read when the editor needs them.
     *
     * A suspending supplier rather than the store itself, and read on demand rather than followed:
     * the drawing picker is open for a few seconds inside a sheet, and collecting every symbol's
     * drawings for the life of the screen would be following a preference file nobody is looking at.
     *
     * Defaults to nothing, and the editor then says the chart has no drawings rather than showing
     * an empty picker — which is also the honest answer for a reader who has never drawn on it.
     */
    private val drawingsOf: suspend (String) -> List<StoredDrawing> = { emptyList() },
    /**
     * The reader's named watchlists.
     *
     * A `Flow`, unlike the catalogue, because a list can be renamed or gain a symbol while the
     * editor is open — and the count on the scope row is the whole reason that row is answerable,
     * so it must not be a snapshot taken when the sheet opened.
     */
    private val watchlists: Flow<List<Watchlist>> = flowOf(emptyList()),
    /**
     * The server's alerts, or [NoServerAlerts] where there is no account layer.
     *
     * Present so that this screen is the *only* alert screen. See [AlertVenue] for why two of them
     * was a bug in itself rather than two features.
     */
    private val server: ServerAlerts = NoServerAlerts,
    /**
     * The reader's webhooks, or [NoWebhooks] where the module is not wired.
     *
     * Here rather than on a settings screen because a webhook is a delivery channel for an alert:
     * it is created next to the alerts it serves, and its failures are read on the alert's own
     * history sheet — which is the only place somebody thinks to look when a bot did nothing.
     */
    private val webhooks: AlertWebhooks = NoWebhooks,
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
        combine(store.alerts, server.alerts, watchlists, webhooks.targets, ui, ::compose)
            .stateIn(scope, SharingStarted.Eagerly, AlertsUiState())

    init {
        // The server's list is asked for once, here, rather than by the screen. A composable that
        // refreshed on first composition would refresh again on every rotation, and the alert
        // centre is reached from four places; a controller that is a singleton asks once.
        scope.launch { runCatching { server.refresh() } }
    }

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
        // A server alert is switched off on the server. Routing it to the local store instead would
        // write a row nothing reads and leave the alert armed on the backend — the reader would
        // watch it go quiet on screen and then be woken by it.
        val serverId = row.serverId
        if (serverId != null) {
            undoAction = { server.setActive(serverId, previous) }
            scope.launch { server.setActive(serverId, !paused) }
            return
        }
        // Set before the write rather than after it, so an undo tapped in the second the toaster is
        // on screen is never dropped for arriving before the disk did.
        //
        // The undo writes its own line. It has to: the log's whole claim is that it says what state
        // the alert is in and when it changed, and a reversal that left «به تعویق افتاد» standing
        // over an alert that is armed again would be the one place the record is knowingly wrong.
        undoAction = {
            store.setActive(id, previous)
            record(row.alert, if (previous) AuditEvent.ARMED else AuditEvent.SNOOZED)
        }
        scope.launch {
            store.setActive(id, !paused)
            // Switching an alert back on is re-arming it, and an alert that has just been re-armed
            // must not still be holding the stamps that say it has already spoken. Only on the way
            // back on: pausing one is temporary and its stamps are still true when it resumes
            // without the reader having asked for anything to be forgotten.
            if (!paused) forgetFireState(id)
            // Pausing is [AuditEvent.SNOOZED] rather than an event of its own, and that is what the
            // case means: "the reader put it aside for a while rather than deleting it". Nothing in
            // this app snoozes a *firing* — a notification has no such action — so this is the one
            // thing a reader does that the case was written for, and leaving it unwritten would
            // leave an alert that went quiet for a fortnight with nothing in its history to say so.
            record(row.alert, if (paused) AuditEvent.SNOOZED else AuditEvent.ARMED)
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
        // Guarded rather than routed. `AlertCenterActions` already hides «تکثیر» for a server
        // alert — the server's route takes a create, not a copy, and a duplicate that quietly
        // became a *device* alert would be a copy that stops working when the phone is asleep.
        if (row.venue != AlertVenue.DEVICE) return
        val id = newId()
        val copy = row.alert.copy(
            id = id,
            active = true,
            createdAtEpochMillis = now(),
            lastFiredAtEpochMillis = null,
        )
        // Removing an id the store never took is a no-op there, so this is safe to arm before the
        // write even in the case below where the write is refused.
        // The history line is written only where there was something to undo. This lambda is armed
        // before the write and survives a refusal, so an unconditional «حذف شد» here would record
        // the removal of a copy the store never accepted.
        undoAction = {
            val stored = store.current().any { it.id == id }
            store.remove(id)
            if (stored) record(copy, AuditEvent.DELETED)
        }
        scope.launch {
            if (store.add(copy)) {
                // A copy is a new alert and its history has to start where it started. Left out,
                // the one alert in the list a reader made by *not* using the editor would be the
                // one whose log begins at its first firing — which is the bug the rest of this is
                // fixing.
                record(copy, AuditEvent.CREATED)
            } else {
                ui.update { it.copy(refusal = AlertRefusal.LIST_FULL) }
            }
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
        val serverId = ServerAlertRows.serverIdOf(id)
        val alert = currentAlerts().firstOrNull { it.id == id }
        scope.launch {
            if (serverId != null) {
                server.delete(serverId)
                return@launch
            }
            store.remove(id)
            forgetFireState(id)
            // Written after the removal and *without* touching the rest of the history.
            //
            // `AlertAuditStore.removeFor` exists and is deliberately not called here. The store's
            // own documentation makes the two different things: deleting an alert is the reader
            // saying they no longer want it evaluated, and forgetting its history is the reader
            // saying they no longer want it recorded. Erasing the log along with the alert would
            // also erase this very line the instant after writing it, which is the one shape that
            // cannot be right whichever way the argument goes.
            //
            // The consequence is worth stating plainly: this sheet is opened from a row, a deleted
            // alert has no row, so nothing in the app reads these lines today. They are kept
            // because the log is a record and a record that edits itself when its subject leaves is
            // not one — and because `AlertAuditStore.entries` is the whole log, so any surface that
            // ever asks "what happened to the alerts I used to have" already has the answer. Losing
            // them costs nothing until such a surface exists and everything the moment it does.
            alert?.let { record(it, AuditEvent.DELETED) }
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
                drawings = emptyList(),
                draft = AlertDraft(symbol = ticker, pickingSymbol = ticker.isEmpty()),
            )
        }
        loadDrawings(ticker)
    }

    /**
     * Opens the editor on an existing alert, where the alert can be expressed by it.
     *
     * Does nothing for a drawing alert or a 24-hour-change alert. The menu hides «ویرایش» for those
     * — see [AlertDraft.of] — so this is a guard rather than a path a reader can reach.
     */
    fun editAlert(row: AlertRow) {
        if (row.venue != AlertVenue.DEVICE) return
        val draft = AlertDraft.of(row.alert) ?: return
        closeAudit()
        ui.update { it.copy(actionsFor = null, refusal = null, draft = draft, drawings = emptyList()) }
        loadDrawings(draft.symbol)
    }

    /** Abandons the sheet. What was typed is dropped; nothing is written until save. */
    fun closeEditor() {
        ui.update { it.copy(draft = null, refusal = null, drawings = emptyList()) }
    }

    /** Opens or closes the symbol picker inside the sheet. */
    fun setPickingSymbol(picking: Boolean) {
        editDraft { it.copy(pickingSymbol = picking, query = if (picking) it.query else "") }
    }

    /** What the reader has typed into the picker. Ranking is [SymbolSearch]'s, not this class's. */
    fun setQuery(query: String) {
        editDraft { it.copy(query = query) }
    }

    /**
     * Chooses the instrument and closes the picker, which has done its job.
     *
     * The drawings go with it. They belong to one symbol — a trend line on another instrument is a
     * line through unrelated numbers, which is why `ChartDrawingStore` keys them by symbol — so a
     * picker still holding the previous symbol's lines would offer an alert that can never resolve
     * a level and would therefore simply never fire.
     *
     * A drawing condition already chosen is cleared for the same reason, rather than left pointing
     * at an id this symbol has no drawing for.
     */
    fun setSymbol(symbol: String) {
        val ticker = symbol.trim().uppercase()
        editDraft { draft ->
            draft.copy(
                symbol = ticker,
                pickingSymbol = false,
                query = "",
                conditions = draft.conditions.map { row ->
                    if (row.kind == AlertTriggerKind.DRAWING) row.copy(drawingId = "") else row
                },
                // The scope is left alone on purpose: somebody who picked a named list has said
                // what the alert is about, and which instrument they arrived from is not it.
                // The venue is not left alone, because the server quotes some markets and not
                // others — an alert silently left pointing at a server that cannot see the new
                // symbol would be refused at save with nothing on screen having changed.
                venue = if (draft.venue == AlertVenue.SERVER && !server.supports(ticker)) {
                    AlertVenue.DEVICE
                } else {
                    draft.venue
                },
            )
        }
        ui.update { it.copy(drawings = emptyList()) }
        loadDrawings(ticker)
    }

    /**
     * Reads the chosen symbol's drawings into the sheet.
     *
     * Fire and forget into [scope], and the result is dropped where the reader has since chosen a
     * different symbol — a slow preferences read finishing after the reader moved on must not
     * repopulate the picker with the previous instrument's lines.
     */
    private fun loadDrawings(symbol: String) {
        val ticker = symbol.trim().uppercase()
        if (ticker.isEmpty()) return
        scope.launch {
            val options = runCatching { AlertDrawings.optionsOf(drawingsOf(ticker)) }.getOrDefault(emptyList())
            ui.update { extras ->
                if (extras.draft?.symbol != ticker) extras else extras.copy(drawings = options)
            }
        }
    }

    /**
     * Chooses which of the reader's drawings this condition watches.
     *
     * By id, and the id is the store's own — see [AlertDrawingOption.id] for why the spelling has
     * to match what the evaluator keys its resolved levels by.
     */
    fun setDrawing(index: Int, drawingId: String) {
        editCondition(index) { it.copy(drawingId = drawingId) }
    }

    /**
     * Chooses what the alert is about: this symbol, or one named list.
     *
     * Null is «همین نماد». A list scope forces the device venue, because the server's route takes
     * one ticker and has no concept of a list — offering the pair together would let the reader
     * build something that silently became an alert on one symbol.
     */
    fun setScopeList(listId: String?) {
        editDraft { draft ->
            val chosen = listId?.takeIf(String::isNotBlank)
            draft.copy(
                scopeListId = chosen,
                venue = if (chosen == null) draft.venue else AlertVenue.DEVICE,
            )
        }
    }

    /**
     * Sets how loud this one alert is.
     *
     * The step, not a raw float, so the one position that changes the notification's output channel
     * is a named choice rather than a place on a slider. See [AlertLoudness].
     */
    fun setLoudness(loudness: AlertLoudness) {
        editDraft { it.copy(soundLevel = loudness.level) }
    }

    /**
     * Moves the alert between the device and the server.
     *
     * Refuses the server where the draft could not be expressed as one — the control is hidden in
     * that case, so this is a guard rather than a reachable state, and refusing is better than
     * accepting a venue the save would then have to undo.
     */
    fun setVenue(venue: AlertVenue) {
        editDraft { draft ->
            if (venue == AlertVenue.SERVER && !canUseServer(draft)) draft else draft.copy(venue = venue)
        }
    }

    /**
     * Whether the server venue may be offered for this draft.
     *
     * Three things at once, and all three are the reader's own doing rather than a failure: the
     * server has to quote the instrument, the condition has to be one it can state, and a watchlist
     * scope has no server spelling at all.
     */
    fun canUseServer(draft: AlertDraft): Boolean =
        server.supports(draft.symbol.trim().uppercase()) && ServerAlertRows.requestOf(draft) != null

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
        if (draft.venue == AlertVenue.SERVER) {
            saveOnServer(draft)
            return
        }
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
                // The reader's own half of the history, and the half that was missing: until this
                // line the log opened at the alert's first firing, so «کِی ساخته شد» and «چه چیزی
                // عوض شد» had no answer anywhere in the app. [AlertAuditTrail.save] decides both
                // whether this save is worth a line and which line — a save that changed nothing
                // writes nothing, because the editor is opened to read an alert far more often than
                // to change one and «ذخیره» is how a reader closes it.
                AlertAuditTrail.save(existing, alert)?.let { write -> record(alert, write) }
                ui.update { it.copy(draft = null, refusal = null, drawings = emptyList()) }
            } else {
                // Replacing an alert that is already stored cannot fail, so this is only reachable
                // for a new alert on a full list. The sheet stays open with what the reader typed
                // still in it.
                ui.update { it.copy(refusal = AlertRefusal.LIST_FULL) }
            }
        }
    }

    /**
     * Hands the alert to the server instead of to this phone.
     *
     * ### Only a creation, and that is the server's shape rather than a shortcut
     *
     * Its route has a create, a pause and a delete, and no update. So the editor makes server
     * alerts and the actions menu hides «ویرایش» for them — see [AlertCenterActions.forRow]. An
     * edit built out of a delete and a create would leave the reader with neither if the second
     * call failed, and it would fail exactly when the network is bad, which is exactly when
     * somebody is re-checking their alerts.
     *
     * ### A refusal keeps the sheet open
     *
     * With what they typed still in it, and with the device venue one tap away — which needs no
     * account and is the whole reason local alerts exist.
     */
    private fun saveOnServer(draft: AlertDraft) {
        val request = draft.serverRequest() ?: return
        undoAction = null
        scope.launch {
            val written = runCatching { server.create(request) }.getOrDefault(false)
            ui.update {
                if (written) {
                    it.copy(draft = null, refusal = null, drawings = emptyList())
                } else {
                    it.copy(refusal = AlertRefusal.SERVER_REFUSED)
                }
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
        ui.update {
            it.copy(
                actionsFor = null,
                auditFor = id,
                auditEntries = emptyList(),
                auditDeliveries = emptyList(),
                auditLoading = true,
            )
        }
        auditJob = scope.launch {
            // Two collectors under one job, because they are two halves of one answer: what the
            // notification did, and what the webhooks did. Cancelling the sheet cancels both.
            launch {
                audit.entriesFor(id).collect { entries ->
                    ui.update { current ->
                        if (current.auditFor != id) {
                            current
                        } else {
                            current.copy(auditEntries = entries, auditLoading = false)
                        }
                    }
                }
            }
            webhooks.deliveriesFor(id).collect { deliveries ->
                ui.update { current ->
                    if (current.auditFor != id) current else current.copy(auditDeliveries = deliveries)
                }
            }
        }
    }

    /** Closes the history and stops following it, so no collector outlives the sheet. */
    fun closeAudit() {
        auditJob?.cancel()
        auditJob = null
        ui.update {
            it.copy(
                auditFor = null,
                auditEntries = emptyList(),
                auditDeliveries = emptyList(),
                auditLoading = false,
            )
        }
    }

    // ── webhooks ────────────────────────────────────────────────────────────────────────────

    /**
     * Opens the list of webhook targets.
     *
     * From the alert centre's own header, because that is where the alerts they serve are. A
     * webhook has no meaning apart from an alert firing, and a settings screen for it would put it
     * three taps away from the only thing that makes it do anything.
     */
    fun openWebhooks() {
        closeAudit()
        ui.update { it.copy(actionsFor = null, webhooksOpen = true, webhookDraft = null, webhookTest = null) }
    }

    /** Closes the sheet and everything inside it. */
    fun closeWebhooks() {
        ui.update { it.copy(webhooksOpen = false, webhookDraft = null, webhookTest = null) }
    }

    /** Opens an empty target editor inside the sheet. */
    fun newWebhook() {
        ui.update { it.copy(webhooksOpen = true, webhookDraft = WebhookDraft(), webhookTest = null) }
    }

    /**
     * Opens an existing target for editing.
     *
     * Its secret is deliberately not loaded; see [WebhookDraft.of]. The field opens empty and an
     * untouched empty field means «leave it as it was».
     */
    fun editWebhook(target: WebhookTarget) {
        ui.update { it.copy(webhooksOpen = true, webhookDraft = WebhookDraft.of(target), webhookTest = null) }
    }

    /** Leaves the editor without writing anything, back to the list of targets. */
    fun closeWebhookEditor() {
        ui.update { it.copy(webhookDraft = null, webhookTest = null) }
    }

    /** The reader's own name for the target. What a failed delivery is identified by in the log. */
    fun setWebhookName(name: String) {
        editWebhookDraft { it.copy(name = name) }
    }

    /** The URL, judged by `WebhookUrl` on every keystroke so the refusal sits under the field. */
    fun setWebhookUrl(url: String) {
        editWebhookDraft { it.copy(url = url) }
    }

    /**
     * The shared secret.
     *
     * [WebhookDraft.secretTouched] is set here and nowhere else, and it is what tells a blank field
     * on an edit apart from a request to remove the secret. Nothing reads the value back out to a
     * screen.
     */
    fun setWebhookSecret(secret: String) {
        editWebhookDraft { it.copy(secret = secret, secretTouched = true) }
    }

    /** Whether this target is posted to at all. Kept rather than deleted; see `WebhookTarget`. */
    fun setWebhookEnabled(enabled: Boolean) {
        editWebhookDraft { it.copy(enabled = enabled) }
    }

    /**
     * Writes the target and returns to the list.
     *
     * The store refuses a URL it will not post to, as a second gate behind the field's own — so a
     * refusal here leaves the sheet open rather than storing something that would silently never
     * fire.
     */
    fun saveWebhook() {
        val draft = ui.value.webhookDraft ?: return
        if (!draft.valid) return
        val existing = draft.editingId?.let { id -> state.value.webhookTargets.firstOrNull { it.id == id } }
        val target = draft.toTarget(existing = existing, id = newId(), nowEpochMillis = now()) ?: return
        scope.launch {
            if (webhooks.save(target)) {
                ui.update { it.copy(webhookDraft = null, webhookTest = null) }
            }
        }
    }

    /** Removes one target. Its delivery history stays, so past failures remain explicable. */
    fun deleteWebhook(id: String) {
        scope.launch { webhooks.delete(id) }
    }

    /** Switches one target on or off from the list, without opening it. */
    fun toggleWebhook(target: WebhookTarget) {
        scope.launch { webhooks.setEnabled(target.id, !target.enabled) }
    }

    /**
     * Posts one test event to the target as the sheet currently describes it.
     *
     * ### Tested as typed, not as stored
     *
     * The point of the button is to find out whether a URL somebody has just pasted works — *now*,
     * rather than the next time a market happens to reach a price. Testing the stored version would
     * answer a question about the previous URL.
     *
     * An edit with the secret field untouched still signs with the stored secret, because that is
     * what a real delivery would do and a test that signed differently would prove nothing.
     */
    fun testWebhook() {
        val draft = ui.value.webhookDraft ?: return
        if (!draft.valid) return
        val existing = draft.editingId?.let { id -> state.value.webhookTargets.firstOrNull { it.id == id } }
        val target = draft.toTarget(existing = existing, id = TEST_TARGET_ID, nowEpochMillis = now()) ?: return
        ui.update { it.copy(webhookTest = null) }
        scope.launch {
            val attempt = runCatching { webhooks.test(target) }.getOrNull()
            ui.update { current ->
                if (current.webhookDraft == null) current else current.copy(webhookTest = attempt)
            }
        }
    }

    private fun editWebhookDraft(transform: (WebhookDraft) -> WebhookDraft) {
        ui.update { extras ->
            val draft = extras.webhookDraft ?: return@update extras
            extras.copy(webhookDraft = transform(draft), webhookTest = null)
        }
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────

    /**
     * Writes one line of an alert's history.
     *
     * ### Only for alerts this phone decides
     *
     * Every caller is already inside a device-only branch, and that is not an accident of where the
     * calls landed. The audit log is written by this app's own evaluator against this app's own
     * store; a server alert is paused and deleted over an HTTP route by something that keeps its
     * own record, and the sheet says so in as many words rather than showing an empty timeline. A
     * «حذف شد» written here for a server alert would be the app reporting on a decision it did not
     * make and cannot see the rest of.
     *
     * ### A failed write must not undo the reader's action
     *
     * The log is a record *of* the change, not part of it. If the disk is full, the alert has still
     * been deleted and the reader has still been shown that it is gone; letting the failure out of
     * this coroutine would take the whole controller's scope down with it over a line of history.
     * So it is swallowed here, deliberately, and the missing line is the honest consequence — the
     * store's own decoder takes the same position on a half-written row.
     */
    private suspend fun record(alert: LocalPriceAlert, write: AlertAuditWrite) {
        runCatching {
            audit.record(write.entryFor(alert, at = now(), timeframe = timeframeOf(alert)))
        }
    }

    /** The same, for the events that carry no note of their own. */
    private suspend fun record(alert: LocalPriceAlert, event: AuditEvent) {
        record(alert, AlertAuditWrite(event))
    }

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

    /**
     * The whole screen, from the two lists and the sheet state.
     *
     * ### The two venues are grouped together, not stacked
     *
     * Server alerts are converted to rows and then handed to [AlertGrouping] with the device ones,
     * so an alert that fired an hour ago is under «تازه اجرا شده» whichever thing decided it. That
     * is the point of unifying the surface: the reader's question after a move is "did any of my
     * alerts go off", and a screen that answered it twice, in two orders, would be the old two
     * screens with one title on top.
     *
     * ### Only device alerts count towards the cap
     *
     * [LocalPriceAlert.MAX_ALERTS] is a limit on one preferences file — see the constant for why —
     * and the server's list is not in it. Counting server rows towards it would refuse a local
     * alert because of storage nobody on this phone is using.
     */
    private fun compose(
        alerts: List<LocalPriceAlert>,
        serverAlerts: List<PriceAlert>,
        lists: List<Watchlist>,
        targets: List<WebhookTarget>,
        extras: Extras,
    ): AlertsUiState {
        val stamp = now()
        val venues = HashMap<String, AlertVenue>(alerts.size + serverAlerts.size)
        alerts.forEach { venues[it.id] = AlertVenue.DEVICE }
        val converted = serverAlerts.map { alert ->
            ServerAlertRows.asLocal(alert).also { venues[it.id] = AlertVenue.SERVER }
        }
        val sections = AlertGrouping.group(alerts + converted, stamp).map { section ->
            AlertRowSection(
                kind = section.kind,
                rows = section.alerts.map { alert ->
                    AlertRow(
                        alert = alert,
                        sentence = AlertSentence.render(alert) { id -> lists.firstOrNull { it.id == id }?.name },
                        timeframe = timeframeOf(alert),
                        kind = section.kind,
                        venue = venues[alert.id] ?: AlertVenue.DEVICE,
                    )
                },
            )
        }
        val rows = sections.flatMap(AlertRowSection::rows)
        val byId = rows.associateBy { it.alert.id }
        val draft = extras.draft?.copy(
            drawings = extras.drawings,
            lists = lists.map { AlertListOption(id = it.id, name = it.name, count = it.symbols.size) },
        )
        return AlertsUiState(
            loading = false,
            sections = sections,
            total = rows.size,
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
                    deliveries = extras.auditDeliveries,
                    loading = extras.auditLoading,
                    venue = row.venue,
                )
            },
            refusal = extras.refusal,
            webhooksOpen = extras.webhooksOpen,
            webhookTargets = targets,
            webhookDraft = extras.webhookDraft,
            webhookTest = extras.webhookTest,
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
        /** The drawings loaded for the draft's current symbol. Cleared when the symbol changes. */
        val drawings: List<AlertDrawingOption> = emptyList(),
        val actionsFor: String? = null,
        val confirmingDelete: String? = null,
        val auditFor: String? = null,
        val auditEntries: List<AlertAuditEntry> = emptyList(),
        val auditDeliveries: List<WebhookAttempt> = emptyList(),
        val auditLoading: Boolean = false,
        val refusal: AlertRefusal? = null,
        val webhooksOpen: Boolean = false,
        val webhookDraft: WebhookDraft? = null,
        val webhookTest: WebhookAttempt? = null,
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

        /**
         * The id a target carries while it is only being tested.
         *
         * Never written to the store — [saveWebhook] takes a fresh one — so a test send cannot
         * leave a half-made target behind if the reader walks away from the sheet.
         */
        const val TEST_TARGET_ID = "webhook-draft"
    }
}
