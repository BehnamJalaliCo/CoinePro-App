package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the depth ladder needs to draw itself, including the case where there is nothing to
 * draw.
 *
 * [unavailable] and [failed] are separate fields on purpose and must not be collapsed into one
 * nullable error. They lead to different screens: a feed without depth gets a sentence and no
 * button, while a feed that could not be reached gets a sentence *and* a retry. Merging them would
 * mean either offering a retry that can never succeed, or withholding one that would.
 *
 * [loading] is true only while a first snapshot is genuinely outstanding. Once any of the three
 * terminal answers has arrived — a book, a refusal, a failure — it is false and stays false, which
 * is the property that keeps a spinner from turning forever.
 */
data class OrderBookState(
    val symbol: String = "",
    val book: OrderBook? = null,
    val loading: Boolean = false,
    /** Why this feed has no book, when that is the correct answer. See [DepthUnavailableReason]. */
    val unavailable: DepthUnavailableReason? = null,
    /** Something went wrong and retrying is worth a try. Never set together with [unavailable]. */
    val failed: Boolean = false,
    /** The venue, for the provenance line under the ladder. */
    val sourceName: String = "",
) {
    /** Whether there is a book with at least one resting level in it — the only case that draws. */
    val hasDepth: Boolean get() = book != null && (book.bids.isNotEmpty() || book.asks.isNotEmpty())

    /**
     * A book that arrived with nothing in it.
     *
     * Treated as its own state rather than as depth of zero. It happens on a market that is closed
     * or halted, and the ladder says so in words instead of drawing eight empty rungs, which is a
     * picture of a market where nobody wants to trade at any price.
     */
    val emptyBook: Boolean get() = book != null && !hasDepth
}

/**
 * Drives the depth ladder for one symbol: one snapshot, then the live stream behind it.
 *
 * ### Why the snapshot and the stream are both here
 *
 * [OrderBookGateway.stream] emits only books it has, so on a feed that cannot start it completes
 * without ever emitting — which is correct, and on its own it leaves a screen with nothing to say.
 * So the first answer always comes from [OrderBookGateway.load], whose [AppResult] carries the
 * reason. The stream is opened only after that answer is a book. That ordering is the whole reason
 * this class exists rather than the screen collecting the flow directly.
 *
 * ### Switching symbols
 *
 * [start] with a new symbol cancels the old stream before anything else. Without that, two flows
 * poll at once and the ladder alternates between two markets at the poll cadence — every row
 * changing, nothing obviously wrong, and the price a tap hands back belonging to whichever book
 * arrived last.
 */
class OrderBookController(
    private val gateway: OrderBookGateway,
    private val scope: CoroutineScope,
    /** Levels asked of the venue. Wider than the ladder shows — see [OrderBookGateway.load]. */
    private val depth: Int = OrderBookGateway.DEFAULT_DEPTH,
) {
    private val _state = MutableStateFlow(OrderBookState(sourceName = gateway.sourceName))
    val state: StateFlow<OrderBookState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * The symbol this controller is currently open on, which is **not** the same question as
     * whether [job] is still running.
     *
     * The stream ends on its own when a feed stops answering, and the screen keeps its last book
     * with its own timestamp on it. Keying the guard on the job would then reopen the whole thing
     * on the next recomposition — a fresh request, a cleared ladder and a spinner — for a screen
     * that was deliberately left holding a stale book. [stop] clears this, so returning to the
     * screen does start again.
     */
    private var opened: String? = null

    /** Opens the book for [symbol]. Repeating the same symbol is a no-op, not a reload. */
    fun start(symbol: String) {
        val wanted = symbol.uppercase()
        if (opened == wanted) return
        opened = wanted
        job?.cancel()
        _state.value = OrderBookState(
            symbol = wanted,
            loading = true,
            sourceName = gateway.sourceName,
        )
        job = scope.launch { open(wanted) }
    }

    /**
     * Asks again after a transport failure.
     *
     * Does nothing when the feed has already said it publishes no depth. A retry there would fail
     * identically every time, and offering one is how a screen tells a reader that persistence
     * might help when it cannot.
     */
    fun refresh() {
        val symbol = _state.value.symbol
        if (symbol.isEmpty() || _state.value.unavailable != null) return
        job?.cancel()
        _state.update { it.copy(loading = true, failed = false) }
        job = scope.launch { open(symbol) }
    }

    /** Closes the stream. The last book stays on screen, which is what a paused ladder should show. */
    fun stop() {
        job?.cancel()
        job = null
        opened = null
    }

    private suspend fun open(symbol: String) {
        when (val first = gateway.load(symbol, depth)) {
            is AppResult.Success -> {
                _state.update {
                    it.copy(book = first.value, loading = false, unavailable = null, failed = false)
                }
                // Collected in the same job as the snapshot so that cancelling one cancels both.
                // The stream may complete on its own — a feed that stopped answering ends it — and
                // that is left as it is rather than restarted: the ladder then holds its last book,
                // which is stamped with its own time, and the reader can see it stopped moving.
                gateway.stream(symbol).collect { book ->
                    _state.update { it.copy(book = book, loading = false) }
                }
            }
            is AppResult.Failure -> {
                val reason = first.depthUnavailableReason
                _state.update {
                    it.copy(
                        loading = false,
                        unavailable = reason,
                        // Never both. A refusal is an answer, and marking it as a failure as well
                        // would put a retry button under a sentence that says retrying is pointless.
                        failed = reason == null,
                    )
                }
            }
        }
    }
}
