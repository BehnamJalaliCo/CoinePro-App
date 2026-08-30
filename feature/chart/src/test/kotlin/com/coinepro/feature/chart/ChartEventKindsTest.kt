package com.coinepro.feature.chart

import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.EventVisibility
import com.coinepro.core.datastore.ChartEventPrefsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The translation between the filter the chart draws with and the row on disk.
 *
 * Two modules name the same five kinds and neither may depend on the other, so this mapping is the
 * only thing keeping them in step — and a mapping that is wrong in one direction is a setting that
 * appears to save and comes back as something else.
 */
class ChartEventKindsTest {

    @Test
    fun `every kind the chart draws has a spelling on disk`() {
        // A kind with no id would be a switch that cannot be stored, which the reader meets as a
        // switch that will not stay on.
        assertTrue(EventKind.entries.all { ChartEventKinds.idOf(it) != null })
        assertEquals(
            EventKind.entries.size,
            EventKind.entries.mapNotNull(ChartEventKinds::idOf).distinct().size,
        )
    }

    @Test
    fun `a stored row comes back as the filter it was written from`() {
        val stored = setOf(ChartEventPrefsStore.KIND_NEWS, ChartEventPrefsStore.KIND_ECONOMIC)

        assertEquals(EventVisibility.Default, ChartEventKinds.visibility(stored))
    }

    @Test
    fun `a row with nothing in it is every kind off, which is a real choice`() {
        assertEquals(EventVisibility.Nothing, ChartEventKinds.visibility(emptySet()))
    }

    @Test
    fun `an id this build has never heard of is dropped and never guessed at`() {
        // The store keeps a newer build's sixth kind on purpose. This build cannot draw one, and
        // mapping it onto a kind it can draw would put marks on the axis nobody asked for.
        val decoded = ChartEventKinds.visibility(setOf(ChartEventPrefsStore.KIND_NEWS, "buyback"))

        assertEquals(setOf(EventKind.NEWS), decoded.kinds)
    }

    @Test
    fun `only the switches that moved are written`() {
        // Writing all five on every tap would rewrite the row four times for nothing and give four
        // chances to race a concurrent edit.
        val from = EventVisibility.Default
        val to = from.with(EventKind.ECONOMIC, false)

        assertEquals(listOf(ChartEventPrefsStore.KIND_ECONOMIC to false), ChartEventKinds.changes(from, to))
        assertTrue(ChartEventKinds.changes(from, from).isEmpty())
    }
}
