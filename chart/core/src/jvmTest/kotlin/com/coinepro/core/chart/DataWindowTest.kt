package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The data window's table (5.14.0): the bar, its change, and every study's value there. */
class DataWindowTest {

    private val series = CandleSeries(
        listOf(
            Candle(1_000L, 10.0, 12.0, 9.0, 11.0, 500.0),
            Candle(2_000L, 11.0, 13.0, 10.0, 12.1, 700.0),
            Candle(3_000L, 12.1, 12.5, 10.5, 10.9, 900.0),
        ),
    )

    @Test
    fun `the crosshair bar, its change from the previous close, and its volume`() {
        val reading = DataWindow.at(series, 1)!!
        assertEquals(2_000L, reading.time)
        assertEquals(12.1, reading.close, 1e-9)
        assertEquals(1.1, reading.change, 1e-9)
        assertEquals(10.0, reading.changePercent!!, 1e-9)
        assertEquals(700.0, reading.volume!!, 1e-9)
        assertTrue(reading.up)
    }

    @Test
    fun `no crosshair reads the newest bar, and an empty chart reads nothing`() {
        assertEquals(2, DataWindow.at(series, null)!!.index)
        assertEquals(2, DataWindow.at(series, 99)!!.index)
        assertNull(DataWindow.at(CandleSeries.EMPTY, null))
        // The first bar has no previous close and measures from its own open.
        assertEquals(1.0, DataWindow.at(series, 0)!!.change, 1e-9)
    }

    @Test
    fun `a named overlay is a row and a pane is one row with all its lines`() {
        val ema = ChartLine(Line.of(3) { if (it == 0) null else 11.5 }, 0xFF60A5FA, label = "EMA 2")
        val edge = ChartLine(Line.of(3) { 1.0 }, 0xFF000000)
        val macd = ChartPane(
            title = "MACD 12 26 9",
            lines = listOf(
                ChartLine(Line.of(3) { 0.5 }, 0xFF22C55E, label = "MACD"),
                ChartLine(Line.of(3) { 0.25 }, 0xFFF59E0B, label = "Signal"),
            ),
        )
        val rows = DataWindow.at(series, 0, overlays = listOf(ema, edge), panes = listOf(macd))!!.rows
        assertEquals(listOf("EMA 2", "MACD 12 26 9"), rows.map { it.label })
        assertEquals(listOf<Double?>(null), rows[0].values)
        assertEquals(listOf<Double?>(0.5, 0.25), rows[1].values)
    }
}
