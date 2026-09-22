package com.coinepro.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The seam the archive carries a layout through.
 *
 * There is deliberately no second encoder here, and this test is what makes that matter visible: a
 * field added to [ChartLayout] is written by the store and must come back through this codec
 * unchanged. A parallel encoder would pass its own tests for ever while the backup it produced
 * quietly stopped carrying the reader's scripts.
 */
class ChartLayoutArchiveCodecTest {

    private fun layout() = ChartLayout(
        id = "layout-1",
        name = "روند",
        symbol = "XAUUSD",
        timeframe = "H4",
        chartType = "CANDLES",
        indicators = listOf("ema", "rsi"),
        indicatorPeriods = mapOf("ema" to 21, "rsi" to 14),
        indicatorParams = mapOf("rsi" to mapOf("overbought" to 70.0)),
        scaleMode = "LOG",
        colourTemplate = "dark",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_100_000L,
    )

    @Test
    fun `a layout comes back as itself`() {
        val original = layout()
        val record = ChartLayoutArchiveCodec.encode(original)
        assertNotNull("the store refused a layout it stores every day", record)
        assertEquals(original, ChartLayoutArchiveCodec.decode(record!!))
    }

    @Test
    fun `it is the store's own encoder, not a copy of it`() {
        // The whole point of the seam. If these two ever differ, an archive is being written by a
        // codec the store does not exercise, which is the one that drifts.
        val original = layout()
        assertEquals(
            ChartLayoutStore.encodeLayout(original),
            ChartLayoutArchiveCodec.encode(original),
        )
    }

    @Test
    fun `a layout the store would refuse is refused here too`() {
        // A blank id, and a name carrying a separator — the two the store itself will not write.
        assertNull(ChartLayoutArchiveCodec.encode(layout().copy(id = "")))
        assertNull(ChartLayoutArchiveCodec.encode(layout().copy(name = "ro\u001End")))
    }

    @Test
    fun `text that is not a layout is refused`() {
        assertNull(ChartLayoutArchiveCodec.decode(""))
    }
}
