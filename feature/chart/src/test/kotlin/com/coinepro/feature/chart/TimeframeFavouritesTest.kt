package com.coinepro.feature.chart

import com.coinepro.core.datastore.IntervalFavouritesStore
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.marketdata.customOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which bar lengths sit under the reader's thumb.
 *
 * The store owns the hard part — telling a never-stored setting from one the reader deliberately
 * emptied — and this file owns everything that needs to know what an interval is. The two failures
 * worth guarding are a strip that grew wider than the phone and one whose contents shuffle between
 * launches, and both are silent on a screen.
 */
class TimeframeFavouritesTest {

    @Test
    fun `the default is the store's own and not a second copy of it`() {
        // Two lists that have to agree is one too many, and a disagreement would only surface on
        // the reader's first star.
        assertEquals(IntervalFavouritesStore.DEFAULT_FAVOURITES, TimeframeFavourites.DEFAULT)
    }

    @Test
    fun `the strip stops growing at the width of a phone`() {
        val full = List(TimeframeFavourites.MAX) { Timeframe.entries[it].wire }
        assertFalse(TimeframeFavourites.canStar(full))
        assertTrue(TimeframeFavourites.canStar(full.dropLast(1)))
    }

    @Test
    fun `a reader may empty the bar entirely because the store records that as a choice`() {
        // Unlike the cap, this is a setting the store explicitly supports as a sentinel. Refusing it
        // here would be this screen overruling the setting it is reading.
        assertTrue(TimeframeFavourites.canUnstar(listOf("H1")))
        assertFalse(TimeframeFavourites.canUnstar(emptyList()))
    }

    @Test
    fun `the strip keeps the store's order rather than sorting itself`() {
        val starred = listOf("D1", "M1", "H4")
        val shown = TimeframeFavourites.resolve(starred, ChartInterval.Preset(Timeframe.M1))
        assertEquals(starred, shown.map { it.wire })
    }

    @Test
    fun `the length in force is always drawn even when it is not starred`() {
        val selected = ChartInterval.Preset(Timeframe.H3)
        val shown = TimeframeFavourites.resolve(listOf("M1", "D1"), selected)
        assertTrue(shown.contains(selected))
        assertEquals(3, shown.size)
    }

    @Test
    fun `a typed interval appears while it is in force and is never duplicated`() {
        val custom = ChartInterval.Custom(checkNotNull(customOf("205")))
        val shown = TimeframeFavourites.resolve(listOf("H1"), custom)
        assertEquals(custom, shown.last())
        assertEquals(2, shown.size)
    }

    @Test
    fun `a deliberately emptied bar still shows what is in force`() {
        val selected = ChartInterval.Preset(Timeframe.H1)
        assertEquals(listOf(selected), TimeframeFavourites.resolve(emptyList(), selected))
    }

    @Test
    fun `a wire this build cannot resolve costs one pill and not the strip`() {
        val shown = TimeframeFavourites.resolve(
            listOf("M5", "M7", "H4"),
            ChartInterval.Preset(Timeframe.M5),
        )
        assertEquals(listOf("M5", "H4"), shown.map { it.wire })
    }

    @Test
    fun `a struck-out preset leaves the picker`() {
        val presets = listOf(Timeframe.M1, Timeframe.M5, Timeframe.H1).map { ChartInterval.Preset(it) }
        val offered = TimeframeFavourites.offered(
            presets,
            hidden = setOf("M5"),
            selected = ChartInterval.Preset(Timeframe.H1),
        )
        assertEquals(listOf("M1", "H1"), offered.map { it.wire })
    }

    @Test
    fun `the length in force is never struck out of the picker that sets it`() {
        // Otherwise the one route off a length is the one route the reader has closed.
        val presets = listOf(Timeframe.M1, Timeframe.M5).map { ChartInterval.Preset(it) }
        val offered = TimeframeFavourites.offered(
            presets,
            hidden = setOf("M5"),
            selected = ChartInterval.Preset(Timeframe.M5),
        )
        assertTrue(offered.any { it.wire == "M5" })
    }
}
