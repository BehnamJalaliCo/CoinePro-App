package com.coinepro.app.widget

import com.coinepro.core.datastore.WidgetMarket
import com.coinepro.core.datastore.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single-symbol tile's two decisions.
 *
 * The assertion this file exists for is the missing market: a widget is configured once and then
 * lives for months, in which time the reader can unstar the instrument or the catalogue can drop
 * it. Drawing *something* there — the first row, the nearest match — would mean a tile that quietly
 * starts showing a different market, which is worse than one that says it cannot find this one.
 */
class SymbolWidgetPickTest {

    private fun market(symbol: String) = WidgetMarket(
        symbol = symbol,
        name = symbol,
        priceText = "100.00",
        changeText = "+1.00%",
        direction = 1,
    )

    private val snapshot = WidgetSnapshot(
        markets = listOf(market("BTCUSDT"), market("XAUUSD")),
        capturedAtEpochMillis = 1_700_000_000_000L,
    )

    @Test
    fun `the configured market is found`() {
        assertEquals("XAUUSD", SymbolWidgetPick.marketFor(snapshot, "XAUUSD")?.symbol)
    }

    @Test
    fun `case does not matter`() {
        // A symbol travels through a preference, a deep link and a feed, and exactly one of those
        // three has ever guaranteed its case.
        assertEquals("XAUUSD", SymbolWidgetPick.marketFor(snapshot, "xauusd")?.symbol)
        assertEquals("XAUUSD", SymbolWidgetPick.marketFor(snapshot, "  XauUsd ")?.symbol)
    }

    @Test
    fun `a market the snapshot does not carry is nothing, not the first row`() {
        // The whole reason this file exists.
        assertNull(SymbolWidgetPick.marketFor(snapshot, "ETHUSDT"))
    }

    @Test
    fun `an unconfigured widget picks nothing`() {
        assertNull(SymbolWidgetPick.marketFor(snapshot, null))
        assertNull(SymbolWidgetPick.marketFor(snapshot, ""))
        assertNull(SymbolWidgetPick.marketFor(snapshot, "   "))
    }

    @Test
    fun `an empty snapshot picks nothing`() {
        assertNull(SymbolWidgetPick.marketFor(WidgetSnapshot(), "XAUUSD"))
    }

    @Test
    fun `a full-size tile carries everything`() {
        val layout = SymbolWidgetPick.layoutFor(widthDp = 180, heightDp = 110)
        assertTrue(layout.name)
        assertTrue(layout.change)
        assertTrue(layout.freshness)
    }

    @Test
    fun `a narrow tile drops the name first`() {
        // The ticker already says which market this is, so the name is the cheapest thing to lose.
        val layout = SymbolWidgetPick.layoutFor(widthDp = 100, heightDp = 110)
        assertFalse(layout.name)
        assertTrue(layout.change)
        assertTrue(layout.freshness)
    }

    @Test
    fun `a short tile drops the freshness before the change`() {
        val layout = SymbolWidgetPick.layoutFor(widthDp = 180, heightDp = 70)
        assertTrue(layout.change)
        assertFalse(layout.freshness)
    }

    @Test
    fun `the smallest tile is the price alone`() {
        val layout = SymbolWidgetPick.layoutFor(widthDp = 60, heightDp = 40)
        assertFalse(layout.name)
        assertFalse(layout.change)
        assertFalse(layout.freshness)
    }

    @Test
    fun `a tile of no size still asks for the price`() {
        // A launcher that reports nothing must not produce a tile with nothing on it. The price is
        // never in the layout at all — it is always drawn — and this is the case that says so.
        val layout = SymbolWidgetPick.layoutFor(widthDp = 0, heightDp = 0)
        assertFalse(layout.name)
        assertFalse(layout.change)
        assertFalse(layout.freshness)
    }
}
