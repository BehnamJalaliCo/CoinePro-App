package com.coinepro.feature.search

import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.watchlistsync.WatchlistSyncNotice
import com.coinepro.core.watchlistsync.WatchlistSyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

/**
 * The list behaviour the TradingView parity pass changed (LISTS-08, 13, 15, 21, 24), each as the
 * rule it is rather than as a screenshot.
 */
class ListParityTest {

    @Test
    fun `a row name that only repeats the ticker is dropped`() {
        val brew = meta("BREWUSDT", base = "BREW", description = "BREW")
        assertNull(rowNameOf(brew, "BREW"))
        assertNull(rowNameOf(brew, "BREWUSDT"))
        assertNull(rowNameOf(brew, " "))
        assertEquals("بیت‌کوین/تتر", rowNameOf(meta("BTCUSDT", base = "BTC"), "بیت‌کوین/تتر"))
    }

    @Test
    fun `top puts the quoted rows first and keeps each half in its order`() {
        val rows = listOf(row("XAUUSD", null), row("BTCUSDT", 1.0), row("EURUSD", null), row("ETHUSDT", 2.0))
        val arranged = arrangeMarkets(rows, MarketTickerStore.MarketTickerState(), MarketLens.NONE, null)
        assertEquals(listOf("BTCUSDT", "ETHUSDT", "XAUUSD", "EURUSD"), arranged.map { it.meta.symbol })
    }

    @Test
    fun `the companion catalogue prices only what the active one left empty`() {
        val primary = listOf(row("BTCUSDT", 100.0), row("XAUUSD", null))
        val companionQuotes = mapOf("XAUUSD" to quote("XAUUSD", 4285.0), "BTCUSDT" to quote("BTCUSDT", 1.0))
        val merged = withCompanionQuotes(primary, emptyList(), companionQuotes)

        // The active platform's own quote is never replaced.
        assertEquals(100.0, merged.first { it.meta.symbol == "BTCUSDT" }.quote?.price)
        assertEquals(4285.0, merged.first { it.meta.symbol == "XAUUSD" }.quote?.price)
    }

    @Test
    fun `a companion row joins the list only when it has a price`() {
        val merged = withCompanionQuotes(
            primary = listOf(row("BTCUSDT", 100.0)),
            companion = listOf(row("EURUSD", 1.14), row("GBPUSD", null)),
            companionQuotes = emptyMap(),
        )
        assertEquals(listOf("BTCUSDT", "EURUSD"), merged.map { it.meta.symbol })
    }

    @Test
    fun `the sync footer shows only for a problem or a stale list`() {
        val now = 10L * DAY
        assertFalse("never synced is not a problem", showsSyncFooter(WatchlistSyncState(available = true), now))
        assertFalse(
            "a fresh sync says nothing",
            showsSyncFooter(
                WatchlistSyncState(available = true, lastSyncedAtMs = now - 1_000, notice = WatchlistSyncNotice.UP_TO_DATE),
                now,
            ),
        )
        assertTrue(
            "a failure is said",
            showsSyncFooter(WatchlistSyncState(available = true, notice = WatchlistSyncNotice.OFFLINE), now),
        )
        assertTrue(
            "a day-old list is said",
            showsSyncFooter(WatchlistSyncState(available = true, lastSyncedAtMs = now - 2 * DAY), now),
        )
    }

    @Test
    fun `the heading lead follows the dense logo`() {
        val phone = headingLead(withRail = true, withHandle = false)
        val desktop = headingLead(withRail = true, withHandle = false, logo = 20.dp)
        assertEquals(LogoSize.value - 20f, phone.value - desktop.value, 0.01f)
    }

    private fun meta(symbol: String, base: String?, description: String = symbol) = SymbolMeta(
        symbol = symbol,
        canonical = symbol,
        category = SymbolCategory.CRYPTO,
        base = base,
        quote = "USDT",
        description = description,
        popular = false,
    )

    private fun quote(symbol: String, price: Double) = MarketQuote(
        instrument = Instrument(symbol, symbol, MarketType.CRYPTO),
        price = price,
        changePercent = null,
        timestampEpochMillis = 0L,
    )

    private fun row(symbol: String, price: Double?) = MarketSearchRow(
        meta = meta(symbol, base = symbol.take(3)),
        quote = price?.let { quote(symbol, it) },
        field = MatchField.NONE,
        highlight = null,
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1_000L
    }
}
