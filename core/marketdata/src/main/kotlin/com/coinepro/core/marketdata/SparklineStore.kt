package com.coinepro.core.marketdata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The last day of closes for a symbol, small enough to draw in a list row.
 *
 * A market list without a shape per row is a spreadsheet: the number says where the price is and
 * nothing says how it got there, which is the first thing anybody actually wants to know. That
 * shape is the reason this exists at all.
 *
 * It is a **store rather than a feed**. The prices in a row come from the live quote stream and
 * change every few seconds; this line is yesterday and does not. Fetching it on the quote's cadence
 * would be twenty-four hours of candles re-requested for every tick.
 *
 * Three rules keep the cost honest, and each of them is the answer to a way this could go wrong:
 *
 * - **Only what is on screen.** Rows ask as they scroll into view. A catalogue of a thousand
 *   markets is not eight hundred requests nobody looked at.
 * - **Once per symbol, ever, per run.** A row that scrolls off and back does not refetch, and a
 *   symbol whose fetch failed is not retried on every recomposition — a failing endpoint would
 *   otherwise turn a scroll into a denial-of-service on our own server.
 * - **[CONCURRENCY] at a time.** A fast scroll through fifty rows queues rather than opening fifty
 *   sockets, so the visible rows are the ones that get served first.
 */
class SparklineStore(
    private val gateway: CandleGateway,
    private val scope: CoroutineScope,
    /** The window each line covers. A day of hourly bars is the shape a reader means by "today". */
    private val timeframe: Timeframe = Timeframe.H1,
    private val bars: Int = DEFAULT_BARS,
) {

    private val _lines = MutableStateFlow<Map<String, List<Double>>>(emptyMap())

    /** Symbol to its closes, oldest first. A symbol absent here has nothing to draw yet. */
    val lines: StateFlow<Map<String, List<Double>>> = _lines.asStateFlow()

    /**
     * Symbols already asked for, whether or not the answer arrived.
     *
     * Deliberately not cleared on failure: see the class note. A symbol this platform does not
     * carry answers 4xx every time, and retrying it per frame is the worst thing this class could
     * do to the server.
     */
    private val asked = mutableSetOf<String>()
    private val gate = Semaphore(CONCURRENCY)

    /** Asks for [symbol]'s line if it has not been asked for already. Safe to call from a row. */
    fun request(symbol: String) {
        val ticker = symbol.trim().uppercase()
        if (ticker.isEmpty()) return
        synchronized(asked) { if (!asked.add(ticker)) return }
        scope.launch {
            gate.withPermit {
                val closes = runCatching {
                    gateway.load(ticker, timeframe, limit = bars).candles.map(OhlcBar::c)
                }.getOrNull()
                // A one-point answer is dropped rather than stored: the renderer would have to
                // decide what a single price looks like as a line, and every answer to that is a
                // shape the market did not make.
                if (closes != null && closes.size >= 2) {
                    _lines.update { it + (ticker to closes) }
                }
            }
        }
    }

    /** Forgets everything, for a platform switch. The other backend quotes different symbols. */
    fun clear() {
        synchronized(asked) { asked.clear() }
        _lines.value = emptyMap()
    }

    private companion object {
        /**
         * Twenty-four hourly closes.
         *
         * A day, and small enough that the whole page is a couple of kilobytes. More points in a
         * 56dp-wide line is detail nobody can see.
         */
        const val DEFAULT_BARS = 24

        /** How many lines may be in flight at once. */
        const val CONCURRENCY = 4
    }
}
