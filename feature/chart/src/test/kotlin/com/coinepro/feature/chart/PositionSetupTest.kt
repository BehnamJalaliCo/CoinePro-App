package com.coinepro.feature.chart

import com.coinepro.core.papertrade.PaperPosition
import com.coinepro.core.papertrade.PaperSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the chart is allowed to claim about an open position.
 *
 * The one that matters is the timestamp: the bars carry unix seconds and the paper book carries
 * milliseconds, and a factor of a thousand in this conversion does not draw the zone in the wrong
 * place — it draws it a few thousand years off the plot, which looks exactly like the feature not
 * working.
 */
class PositionSetupTest {

    private val opened = 1_735_689_600_000L // 2025-01-01T00:00:00Z, in milliseconds.

    private fun position(
        side: PaperSide = PaperSide.BUY,
        entry: Double = 100.0,
        stopLoss: Double? = 95.0,
        takeProfit: Double? = 115.0,
        openedAtEpochMillis: Long = opened,
    ) = PaperPosition(
        id = 1L,
        symbol = "BTCUSDT",
        side = side,
        size = 1.0,
        entry = entry,
        openedAtEpochMillis = openedAtEpochMillis,
        stopLoss = stopLoss,
        takeProfit = takeProfit,
    )

    @Test
    fun `the anchor is the opening moment in seconds, not milliseconds`() {
        val overlay = positionOverlay(position())!!
        assertEquals(1_735_689_600L, overlay.issuedAt)
    }

    @Test
    fun `a position opened inside a bar is anchored to that bar rather than the next one`() {
        // 10:00:59.999 belongs to the 10:00 bar. Rounding would put it on the 10:01 bar, which is a
        // bar the position did not exist for.
        val overlay = positionOverlay(position(openedAtEpochMillis = opened + 59_999L))!!
        assertEquals(1_735_689_659L, overlay.issuedAt)
    }

    @Test
    fun `an open position never carries a close, so its zone runs to the live edge`() {
        assertNull(positionOverlay(position())!!.closedAt)
    }

    @Test
    fun `a long keeps its stop below and its target above`() {
        val overlay = positionOverlay(position())!!
        assertTrue(overlay.isLong)
        assertEquals(95.0, overlay.stopLoss!!, 0.0)
        assertEquals(listOf(115.0), overlay.takeProfits)
    }

    @Test
    fun `a short is a short`() {
        val overlay = positionOverlay(position(side = PaperSide.SELL, stopLoss = 105.0, takeProfit = 85.0))!!
        assertTrue(!overlay.isLong)
        // And the bands invert without a branch, which is `setupBands`' whole argument.
        assertEquals(105.0, overlay.stopLoss!!, 0.0)
    }

    @Test
    fun `a position with no protection still draws its entry`() {
        val overlay = positionOverlay(position(stopLoss = null, takeProfit = null))!!
        assertEquals(100.0, overlay.entry, 0.0)
        assertNull(overlay.stopLoss)
        assertTrue(overlay.takeProfits.isEmpty())
    }

    @Test
    fun `a target label is not carried when there is no target`() {
        val overlay = positionOverlay(position(takeProfit = null), "ورود", "حد ضرر", "حد سود")!!
        assertEquals("ورود", overlay.entryLabel)
        assertTrue(overlay.targetLabels.isEmpty())
    }

    @Test
    fun `labels reach the renderer in order`() {
        val overlay = positionOverlay(position(), "ورود", "حد ضرر", "حد سود")!!
        assertEquals("حد ضرر", overlay.stopLabel)
        assertEquals(listOf("حد سود"), overlay.targetLabels)
    }

    @Test
    fun `a position with no usable entry draws nothing`() {
        assertNull(positionOverlay(position(entry = Double.NaN)))
        assertNull(positionOverlay(position(entry = 0.0)))
    }

    @Test
    fun `a position with no opening moment draws nothing`() {
        // Rather than an unanchored overlay, which the renderer draws as bare lines across the whole
        // plot — the exact picture this work exists to remove.
        assertNull(positionOverlay(position(openedAtEpochMillis = 0L)))
    }

    @Test
    fun `a nonsensical level is dropped rather than drawn`() {
        val overlay = positionOverlay(position(stopLoss = Double.NaN, takeProfit = -1.0))!!
        assertNull(overlay.stopLoss)
        assertTrue(overlay.takeProfits.isEmpty())
    }
}
