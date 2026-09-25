package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The indicator list's keys must be unique, or a `LazyColumn` throws the moment the second copy
 * composes — which is what scrolling the «همه» list did while the catalogue interleaved its panes
 * (DIALOGS-01): «در پنل جدا» was emitted twice under `h-SEPARATE` and the app froze mid-scroll.
 */
class IndicatorListEntriesTest {

    @Test
    fun `every key is unique, grouped or not, with or without volume`() {
        for (hasVolume in listOf(true, false)) {
            for (grouped in listOf(true, false)) {
                val keys = indicatorListEntries(ChartCatalog.indicatorsFor(hasVolume), grouped).map { it.key }
                assertEquals("duplicate keys (volume=$hasVolume, grouped=$grouped)", keys.size, keys.toSet().size)
            }
        }
    }

    @Test
    fun `grouped, each pane has one heading and every study sits under its own`() {
        val entries = indicatorListEntries(ChartCatalog.INDICATORS, grouped = true)
        val headings = entries.filterIsInstance<IndicatorListEntry.Heading>().map { it.pane }
        assertEquals(IndicatorPane.entries.toList(), headings)
        var pane: IndicatorPane? = null
        for (entry in entries) {
            when (entry) {
                is IndicatorListEntry.Heading -> pane = entry.pane
                is IndicatorListEntry.Study -> assertEquals(entry.option.id, pane, entry.option.pane)
            }
        }
        assertEquals(ChartCatalog.INDICATORS.size, entries.count { it is IndicatorListEntry.Study })
    }

    @Test
    fun `a favourites list holding the same id twice still yields one row`() {
        val sma = ChartCatalog.INDICATORS.first { it.id == "sma" }
        val entries = indicatorListEntries(listOf(sma, sma), grouped = false)
        assertEquals(1, entries.size)
    }
}
