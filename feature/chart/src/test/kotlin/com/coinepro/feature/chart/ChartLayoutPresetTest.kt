package com.coinepro.feature.chart

import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProWindowClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLayoutPresetTest {

    @Test
    fun `ids are unique and stable`() {
        val ids = ChartLayoutPreset.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // What is on readers' tablets. Renaming an entry is fine; changing an id is a migration.
        assertEquals(listOf("1", "2h", "2v", "3", "4", "6", "8"), ids)
        ChartLayoutPreset.entries.forEach { assertEquals(it, ChartLayoutPreset.byId(it.id)) }
    }

    @Test
    fun `every layout fits its own grid`() {
        ChartLayoutPreset.entries.forEach { preset ->
            assertTrue(preset.id, preset.columns in 1..preset.count)
            assertTrue(preset.id, preset.rows * preset.columns >= preset.count)
            assertTrue(preset.id, (preset.rows - 1) * preset.columns < preset.count)
        }
    }

    @Test
    fun `the largest layout is the tablet's pane cap`() {
        assertEquals(CoineProWindowClass.TABLET_MAX_PANES, ChartLayoutPreset.EIGHT.count)
        assertEquals(ChartLayoutPreset.EIGHT, ChartLayoutPreset.largestWithin(CoineProWindowClass.TABLET_MAX_PANES))
        assertEquals(ChartLayoutPreset.TWO_ACROSS, ChartLayoutPreset.largestWithin(CoineProWindowClass.PHONE_MAX_PANES))
    }

    @Test
    fun `a phone offers only the pair and a tablet offers everything past one`() {
        assertEquals(listOf(ChartLayoutPreset.TWO_ACROSS, ChartLayoutPreset.TWO_DOWN), ChartLayoutPreset.offered(2))
        assertEquals(6, ChartLayoutPreset.offered(8).size)
        assertTrue(ChartLayoutPreset.ONE !in ChartLayoutPreset.offered(8))
    }

    @Test
    fun `a bare count means the first layout with that count`() {
        assertEquals(ChartLayoutPreset.TWO_ACROSS, ChartLayoutPreset.forCount(2))
        assertEquals(ChartLayoutPreset.FOUR, ChartLayoutPreset.forCount(4))
        // Five panes — a count the old row allowed — reads as the largest named layout under it.
        assertEquals(ChartLayoutPreset.FOUR, ChartLayoutPreset.forCount(5))
    }

    @Test
    fun `the width has the last word on columns`() {
        // A landscape tablet: 1280dp affords three columns.
        assertEquals(1, gridColumns(1280.dp, 2, ChartLayoutPreset.TWO_DOWN.columns))
        assertEquals(2, gridColumns(1280.dp, 2, ChartLayoutPreset.TWO_ACROSS.columns))
        assertEquals(3, gridColumns(1280.dp, 8, ChartLayoutPreset.EIGHT.columns))
        // A portrait tablet: 840dp affords two.
        assertEquals(2, gridColumns(840.dp, 4, ChartLayoutPreset.FOUR.columns))
        assertEquals(2, gridColumns(840.dp, 3, ChartLayoutPreset.THREE.columns))
        // A phone: one, whatever was asked.
        assertEquals(1, gridColumns(411.dp, 2, ChartLayoutPreset.TWO_ACROSS.columns))
    }
}
