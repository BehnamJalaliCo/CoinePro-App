package com.coinepro.core.chart

/**
 * Measured text, kept between frames.
 *
 * ### Why the axis is where this matters
 *
 * Laying out a string is the most expensive thing this canvas does — it is font work, not
 * arithmetic — and the price axis does it five to twenty-four times *per frame* for labels that
 * have not changed. Panning a chart re-measures «2643.2» sixty times a second because the ladder
 * is anchored to round prices and those round prices persist across the pan; the numbers slide,
 * they do not renumber. That is exactly the shape a cache is for, and it is the trick every
 * terminal uses on its own axis.
 *
 * ### The trap, which is the reason the key is not the string
 *
 * A measured layout carries its *style* — the colour, the size, the weight — and drawing a cached
 * one paints it in the colour it was measured with. Keyed on the text alone, a colour template
 * switch or a bold month boundary would silently reuse the previous colour and the axis would go
 * on printing in the old palette until something else forced a re-measure. So the key is the pair,
 * and the caller passes both.
 *
 * Bounded, and least-recently-used rather than cleared wholesale: an unbounded map on a chart the
 * reader keeps panning grows one entry per distinct price label for the life of the screen, which
 * is a slow leak that never shows up in a profile as anything but memory.
 *
 * Generic in the value so it can be tested without a font engine — the eviction rule is the part
 * that can be wrong, and `TextLayoutResult` adds nothing to assert about it.
 */
internal class TextWidthCache<V>(private val capacity: Int = DEFAULT_CAPACITY) {

    /**
     * Insertion-ordered, with a hit moved to the end by hand — which *is* least-recently-used.
     *
     * It used to be the JVM's access-ordered `LinkedHashMap` with `removeEldestEntry`, and that
     * constructor and that hook exist on the JVM only; the browser's `LinkedHashMap` has neither.
     * A remove and a re-insert on a hit is the same order in O(1), on every target, and the eviction
     * rule the test pins is unchanged.
     */
    private val entries = LinkedHashMap<Any, V>(INITIAL_BUCKETS)

    /** How many measurements are being held. For the test that pins the eviction rule. */
    val size: Int get() = entries.size

    /**
     * The measurement for [key], measuring it only if it is not already held.
     *
     * [compute] is a lambda rather than a value so a hit never lays the text out at all — passing
     * an already-measured result would be paying the cost this class exists to avoid.
     */
    fun measure(key: Any, compute: () -> V): V {
        val held = entries.remove(key)
        if (held != null) {
            // Re-inserted, so it is now the most recently used.
            entries[key] = held
            return held
        }
        val measured = compute()
        entries[key] = measured
        if (entries.size > capacity) {
            // The first key is the one least recently touched.
            entries.remove(entries.keys.first())
        }
        return measured
    }

    private companion object {
        /**
         * Enough for a full axis ladder in four colours and then some.
         *
         * The working set is small — two dozen labels — and the reason for a cap well above it is
         * that a reader panning across a decade of history walks the ladder through hundreds of
         * distinct numbers, none of which will be wanted again.
         */
        const val DEFAULT_CAPACITY = 96
        const val INITIAL_BUCKETS = 32
    }
}
