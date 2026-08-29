package com.coinepro.core.chartevents

import com.coinepro.core.chart.ChartEvent

/**
 * What has already been fetched, so panning does not fetch it again.
 *
 * ### Why events are cached and candles are not
 *
 * A pan changes the visible range by a few bars at a time, and the chart asks for the range it is
 * looking at on every one of them. Candles have to be re-asked for — the last one is still moving —
 * but events do not: a headline published at nine o'clock is still published at nine o'clock, and
 * an economic release does not change its schedule between two frames of a drag. Without this the
 * feature would put a network call behind every finger movement, which is both a battery cost and a
 * visible one: marks that blink out and back as each answer replaces the last.
 *
 * ### Containment, not equality
 *
 * An entry answers any window it *contains*, not only the window it was fetched for. That is the
 * whole point — the controller deliberately fetches wider than the screen, so a pan of less than
 * half a view stays inside what is already held. Equality would make every entry a hit exactly once
 * and the cache decorative.
 *
 * ### An empty answer is an answer
 *
 * A window with no events caches as an empty list, not as a miss. A quiet week is the common case
 * on a low-importance filter, and treating it as "nothing cached" would refetch the quiet week on
 * every frame — the one case where the cache is most needed and least likely to be noticed missing.
 *
 * Not thread-safe by construction: it is confined to the controller that owns it, which touches it
 * only from its own scope. A lock here would be a lock nothing contends for.
 */
class ChartEventCache(
    /**
     * How long an entry stays usable, in seconds.
     *
     * Five minutes. Long enough that a reader panning around a chart for a minute never refetches,
     * short enough that a headline breaking while the chart is open reaches the axis without the
     * reader having to leave the screen and come back.
     */
    private val freshnessSeconds: Long = 300L,
    /**
     * How many symbols are remembered at once.
     *
     * Four, which covers flipping between a watchlist's top few without the map growing for the
     * life of the process. The oldest write is evicted, not the oldest read: the reader who goes
     * back to the first symbol is the reader whose events are most likely stale anyway.
     */
    private val symbols: Int = 4,
) {
    private data class Entry(
        val from: Long,
        val to: Long,
        val fetchedAt: Long,
        val events: List<ChartEvent>,
    )

    private val entries = LinkedHashMap<String, Entry>()

    /**
     * The events already held for this window, or null when nothing here can answer it.
     *
     * Null means "ask the feed". An empty list means "asked, and nothing happened in that window",
     * which is a different thing and must not send the caller back to the network.
     */
    fun hit(symbol: String, fromSeconds: Long, toSeconds: Long, now: Long): List<ChartEvent>? {
        val entry = entries[key(symbol)] ?: return null
        if (now - entry.fetchedAt >= freshnessSeconds) return null
        if (fromSeconds < entry.from || toSeconds > entry.to) return null
        return entry.events
    }

    /**
     * Record what the feed answered, and for which window it is the whole answer.
     *
     * [fromSeconds] and [toSeconds] are the window that was *asked for*, not the span of what came
     * back. Storing the span of the answer would be a quiet lie in the common case: two events an
     * hour apart inside a week-long request would claim to cover an hour, and every later window
     * would miss.
     */
    fun put(
        symbol: String,
        fromSeconds: Long,
        toSeconds: Long,
        events: List<ChartEvent>,
        now: Long,
    ) {
        val id = key(symbol)
        entries.remove(id)
        entries[id] = Entry(from = fromSeconds, to = toSeconds, fetchedAt = now, events = events)
        while (entries.size > symbols) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    /** Forget everything. For a sign-out, where the next reader must not see the last one's feed. */
    fun clear() {
        entries.clear()
    }

    /** Symbols are compared upper-cased, because `btcusdt` and `BTCUSDT` are one instrument. */
    private fun key(symbol: String): String = symbol.uppercase()
}
