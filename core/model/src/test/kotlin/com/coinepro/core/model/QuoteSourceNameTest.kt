package com.coinepro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names a chart is allowed to print beside a price.
 *
 * `CandleGateway.sourceName` states the rule these obey — **never invent provenance** — and the
 * only interesting case is the one where there is nothing honest to say. A feed whose `source`
 * field the app does not recognise must produce no label at all, because a wrong venue name is
 * worse than none: a reader can act on it, hold this chart against the wrong feed, find a
 * difference that means nothing, and conclude exactly what the label exists to disprove.
 */
class QuoteSourceNameTest {

    @Test
    fun `a recognised venue is named the way the venue spells it`() {
        assertEquals("Finnhub", QuoteSource.FINNHUB.displayName)
        assertEquals("LBank", QuoteSource.LBANK.displayName)
    }

    @Test
    fun `an unrecognised venue is silent rather than called unknown`() {
        assertEquals("", QuoteSource.UNKNOWN.displayName)
    }

    @Test
    fun `every source either has a real name or says nothing`() {
        // A new venue added to the enum without a name here would otherwise reach a screen as
        // whatever `when` fell through to. Kotlin's exhaustiveness stops the fall-through; this
        // stops a placeholder being typed in to satisfy it.
        val placeholders = setOf("unknown", "none", "n/a", "-", "?", "نامشخص")
        QuoteSource.entries.forEach { source ->
            val name = source.displayName
            assertTrue(
                "$source is labelled with a placeholder rather than a venue",
                name.isEmpty() || name.lowercase() !in placeholders,
            )
        }
    }
}
