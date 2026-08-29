package com.coinepro.core.watchlistsync

import com.coinepro.core.datastore.WatchlistStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the watchlist screen shows about sync, and nothing more than that.
 *
 * [available] is false on the platform that serves no such route, and it is the flag the screen
 * hides the whole control behind. A greyed-out button with an explanation is the wrong answer: on
 * CoinePro-FX this feature does not exist, and offering a disabled version of it advertises
 * something the reader cannot have.
 *
 * [lastSyncedAtMs] is this device's clock at the last write the server accepted, and zero means
 * never. It is the one thing on this state a reader checks when they are about to wipe a phone.
 *
 * [notice] is null before the first sync of the session. The counts beside it belong to the last
 * sync only — they are a report of what just happened, not a running total.
 */
data class WatchlistSyncState(
    val available: Boolean = false,
    val syncing: Boolean = false,
    val lastSyncedAtMs: Long = 0L,
    val notice: WatchlistSyncNotice? = null,
    val listsAdopted: Int = 0,
    val symbolsAdopted: Int = 0,
    val listsDropped: Int = 0,
    /** The server's cap, as it last stated it. Null until a response has carried one. */
    val maxBytes: Int? = null,
)

/**
 * Copying the reader's watchlists between their devices, through TradeYar's stored document.
 *
 * ### What a sync is
 *
 * One pass: read the document, merge it into what this device holds, write the result back. The
 * merge is [WatchlistMerge] and its rules — and the things they can still lose — are written out
 * there. Everything this file adds on top is about *when* that runs and what the reader is told
 * about it.
 *
 * ### Local never waits for this
 *
 * `WatchlistStore` is the source of truth for the screen and is not touched by anything here
 * except through `applyMerged`, which only ever adds lists and symbols or honours a deletion the
 * reader themselves made. So a reader with no network, no account, or on the platform that serves
 * no route at all has a watchlist that behaves exactly as it did before this module existed. A
 * failed sync is not an error state: it is a sync that has not happened yet.
 *
 * ### It cannot overwrite local work at a moment nobody asked for
 *
 * This is a property of the merge rather than of the trigger, and that is deliberate — a rule that
 * depends on sync only ever running when a button was pressed is a rule that breaks the first time
 * somebody adds a background refresh. Because no path through [WatchlistMerge] removes a list or a
 * symbol that no one deleted, a sync running unprompted cannot cost the reader anything. What it
 * *can* do is bring things in, which is why anything that arrives is counted and reported rather
 * than appearing silently.
 */
class WatchlistSyncController(
    private val gateway: WatchlistSyncGateway,
    private val store: WatchlistStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(WatchlistSyncState(available = gateway.supported))
    val state: StateFlow<WatchlistSyncState> = _state.asStateFlow()

    init {
        // The cursor is stored, so "last synced" survives the app being killed — which is the only
        // time the answer matters, since a reader asking it is usually about to trust the copy on
        // the server with the contents of a phone they are giving up.
        scope.launch {
            store.syncCursor().collect { cursor ->
                _state.update { it.copy(lastSyncedAtMs = cursor.syncedAtMs) }
            }
        }
    }

    /**
     * Runs one sync.
     *
     * Re-entrant calls are dropped rather than queued. Two syncs racing would each merge against a
     * document the other is about to replace, and the second would spend its whole attempt budget
     * losing conflicts to the first.
     */
    fun sync() {
        if (!gateway.supported) {
            _state.update { it.copy(notice = WatchlistSyncNotice.UNSUPPORTED) }
            return
        }
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        scope.launch {
            val outcome = runSync()
            _state.update {
                it.copy(
                    syncing = false,
                    notice = outcome.notice,
                    listsAdopted = outcome.listsAdopted,
                    symbolsAdopted = outcome.symbolsAdopted,
                    listsDropped = outcome.listsDropped,
                    maxBytes = outcome.maxBytes ?: it.maxBytes,
                )
            }
        }
    }

    private suspend fun runSync(): Outcome = try {
        exchange()
    } catch (error: WatchlistSyncUnsupportedException) {
        Outcome(WatchlistSyncNotice.UNSUPPORTED)
    } catch (error: WatchlistSyncTooLargeException) {
        Outcome(WatchlistSyncNotice.TOO_LARGE, maxBytes = error.maxBytes)
    } catch (error: IOException) {
        // A connection that never reached a verdict. Distinguished from every other failure because
        // it is the ordinary case for this audience rather than a fault, and because it is the one
        // failure where trying again in a minute is genuinely likely to work.
        Outcome(WatchlistSyncNotice.OFFLINE)
    } catch (error: Exception) {
        Outcome(WatchlistSyncNotice.REFUSED)
    }

    /**
     * Read, merge, write — with the write repeated while the server says the document has moved.
     *
     * The retry loop is what the `409` is for. The refusal carries the whole current document, so a
     * conflict costs a merge and a second write rather than a second round trip; two devices
     * syncing at the same moment settle in one extra pass. It is bounded at [MAX_WRITE_ATTEMPTS]
     * because an unbounded loop against a document somebody is editing continuously is a loop that
     * never ends, and because the honest answer after three losses is that nothing was lost —
     * everything this device holds is still on this device, and the next sync will carry it.
     */
    private suspend fun exchange(): Outcome {
        var document = gateway.read()
        var adoptedLists = 0
        var adoptedSymbols = 0
        var droppedLists = 0

        repeat(MAX_WRITE_ATTEMPTS) {
            val remote = WatchlistPayload.decode(document.payload)
            var result: WatchlistMergeResult? = null
            val merged = store.applyMerged { local ->
                WatchlistMerge.merge(local, remote).also { result = it }.snapshot
            }
            result?.let {
                adoptedLists += it.listsAdopted
                adoptedSymbols += it.symbolsAdopted
                droppedLists += it.listsDropped
            }

            val payload = WatchlistPayload.encode(merged)
            // Checked here as well as by the server. Sending a document that is certain to be
            // refused spends the reader's data on a round trip whose answer is already known, and
            // on a metered connection that is the difference the reader notices.
            document.maxBytes?.let { cap ->
                if (WatchlistPayload.sizeInBytes(payload) > cap) {
                    throw WatchlistSyncTooLargeException(cap)
                }
            }

            if (payload == document.payload) {
                // The merge produced exactly what is already stored, so there is nothing to send.
                // The version is still recorded: the server does hold this document, and claiming
                // otherwise would make the next sync's write look stale and cost a needless 409.
                store.recordSynced(document.version, now())
                return outcomeOf(adoptedLists, adoptedSymbols, droppedLists, uploaded = false, document.maxBytes)
            }

            try {
                val written = gateway.write(document.version, payload)
                store.recordSynced(written.version, now())
                return outcomeOf(
                    adoptedLists,
                    adoptedSymbols,
                    droppedLists,
                    uploaded = true,
                    written.maxBytes ?: document.maxBytes,
                )
            } catch (conflict: WatchlistSyncConflict) {
                // The refusal is supposed to carry the current document; re-reading is the fallback
                // for a deployment or a proxy that did not send one, and it is deliberately the
                // slow path rather than the normal one.
                document = conflict.current ?: gateway.read()
            }
        }
        return Outcome(WatchlistSyncNotice.REFUSED, adoptedLists, adoptedSymbols, droppedLists, document.maxBytes)
    }

    /**
     * Which of the four success sentences this sync earned.
     *
     * A removal outranks an arrival for the reason [WatchlistSyncNotice.REMOVED] gives: it is the
     * only outcome that takes something away, and everything that arrived is visible in the
     * switcher anyway.
     */
    private fun outcomeOf(
        lists: Int,
        symbols: Int,
        dropped: Int,
        uploaded: Boolean,
        maxBytes: Int?,
    ): Outcome {
        val notice = when {
            dropped > 0 -> WatchlistSyncNotice.REMOVED
            lists > 0 || symbols > 0 -> WatchlistSyncNotice.MERGED
            uploaded -> WatchlistSyncNotice.UPLOADED
            else -> WatchlistSyncNotice.UP_TO_DATE
        }
        return Outcome(notice, lists, symbols, dropped, maxBytes)
    }

    private data class Outcome(
        val notice: WatchlistSyncNotice,
        val listsAdopted: Int = 0,
        val symbolsAdopted: Int = 0,
        val listsDropped: Int = 0,
        val maxBytes: Int? = null,
    )

    private companion object {
        /** Three. See [exchange] for why it is bounded and why losing all three costs nothing. */
        const val MAX_WRITE_ATTEMPTS = 3
    }
}
