package com.coinepro.feature.explore

import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Explore decides to show, with no composable and no server anywhere near it.
 *
 * The cases below are the ones that would otherwise only be visible on a device, on the right
 * platform, at the right moment: a chip strip that offers a category the platform does not quote, a
 * card whose price came from the wrong one of two sources, and a change percentage derived rather
 * than reported.
 */
class ExploreBoardTest {

    private fun row(symbol: String, price: Double? = 1.0, change: Double? = null): MarketSearchRow {
        val meta = SymbolClassifier.classify(symbol)
        return MarketSearchRow(
            meta = meta,
            quote = price?.let {
                MarketQuote(
                    instrument = Instrument(
                        symbol = meta.symbol,
                        displayName = meta.description,
                        marketType = if (meta.category == SymbolCategory.CRYPTO) MarketType.CRYPTO else MarketType.FOREX,
                    ),
                    price = it,
                    changePercent = change,
                    timestampEpochMillis = 0L,
                )
            },
            field = MatchField.NONE,
            highlight = null,
        )
    }

    private fun ticker(symbol: String, last: Double, change: Double?) =
        MarketTicker(symbol = symbol, last = last, changePercent24h = change)

    @Test
    fun `the chip strip is built from the catalogue, so a platform is never offered a category it has none of`() {
        // TradeYar quotes USDT pairs and nothing else. A declared strip would put «فارکس» and
        // «فلزات» in front of a crypto reader with nothing behind either.
        val crypto = listOf(row("BTCUSDT"), row("ETHUSDT"), row("SOLUSDT"))

        // One category is not a choice: «همه» and «کریپتو» would be the same list twice.
        assertEquals(emptyList<ExploreLens>(), ExploreBoard.lenses(crypto))

        val mixed = crypto + listOf(row("XAUUSD"), row("EURUSD"))
        val lenses = ExploreBoard.lenses(mixed)
        assertEquals(
            listOf(null, SymbolCategory.CRYPTO, SymbolCategory.METAL, SymbolCategory.FOREX),
            lenses.map(ExploreLens::category),
        )
        assertEquals(5, lenses.first().count)
        assertEquals(3, lenses[1].count)
    }

    @Test
    fun `the chip order is this app's, not the order the catalogue happened to arrive in`() {
        // A reader who has learned that «کریپتو» is the second chip must not find it third because
        // the server started quoting a new index.
        val reversed = listOf(row("US500"), row("EURUSD"), row("XAUUSD"), row("BTCUSDT"))

        assertEquals(
            listOf(null, SymbolCategory.CRYPTO, SymbolCategory.METAL, SymbolCategory.FOREX, SymbolCategory.INDEX),
            ExploreBoard.lenses(reversed).map(ExploreLens::category),
        )
    }

    @Test
    fun `an empty catalogue offers no chips rather than a strip of empty ones`() {
        assertEquals(emptyList<ExploreLens>(), ExploreBoard.lenses(emptyList()))
    }

    @Test
    fun `the day's rollup wins over the catalogue snapshot, which goes stale from the moment it lands`() {
        val cards = ExploreBoard.cards(
            rows = listOf(row("BTCUSDT", price = 60_000.0, change = 1.0)),
            tickers = mapOf("BTCUSDT" to ticker("BTCUSDT", last = 64_182.4, change = 2.5)),
        )

        val card = cards.single()
        assertEquals(64_182.4, card.price!!, 1e-9)
        assertEquals(2.5, card.changePercent!!, 1e-9)
    }

    @Test
    fun `with no rollup the catalogue quote is the answer, because CoinePro-FX serves no ticker route`() {
        val card = ExploreBoard.cards(
            rows = listOf(row("XAUUSD", price = 2_592.6, change = -0.42)),
        ).single()

        assertEquals(2_592.6, card.price!!, 1e-9)
        assertEquals(-0.42, card.changePercent!!, 1e-9)
    }

    @Test
    fun `a market with no reported move keeps its price and reports no move`() {
        // Never derived from last over open. A market that arrived without an open would otherwise
        // become a flat zero percent, which is a specific and wrong claim about a quiet market.
        val card = ExploreBoard.cards(
            rows = listOf(row("BTCUSDT", price = 60_000.0, change = null)),
            tickers = mapOf("BTCUSDT" to ticker("BTCUSDT", last = 64_182.4, change = null)),
        ).single()

        assertEquals(64_182.4, card.price!!, 1e-9)
        assertNull(card.changePercent)
    }

    @Test
    fun `a market with no price at all is not a card`() {
        // There would be nothing on it but a logo, in a row whose whole subject is figures.
        val cards = ExploreBoard.cards(
            rows = listOf(row("BTCUSDT", price = null), row("ETHUSDT", price = 3_100.0)),
        )

        assertEquals(listOf("ETHUSDT"), cards.map(ExploreCard::symbol))
    }

    @Test
    fun `a chip narrows the row to its own category`() {
        val rows = listOf(row("BTCUSDT"), row("XAUUSD"), row("ETHUSDT"))

        assertEquals(
            listOf("XAUUSD"),
            ExploreBoard.cards(rows, category = SymbolCategory.METAL).map(ExploreCard::symbol),
        )
    }

    @Test
    fun `the row keeps the catalogue's order rather than sorting by the day's move`() {
        // A strip that reordered itself on every five-second poll would move the card a reader was
        // reaching for out from under their finger. "Biggest mover" is the markets tab's question.
        val rows = listOf(row("BTCUSDT"), row("ETHUSDT"), row("SOLUSDT"))
        val tickers = mapOf(
            "BTCUSDT" to ticker("BTCUSDT", 64_000.0, change = 0.1),
            "ETHUSDT" to ticker("ETHUSDT", 3_100.0, change = 9.4),
            "SOLUSDT" to ticker("SOLUSDT", 140.0, change = -7.2),
        )

        assertEquals(
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"),
            ExploreBoard.cards(rows, tickers).map(ExploreCard::symbol),
        )
    }

    @Test
    fun `the row stops at its limit, because a scroller with no end is a second list`() {
        val rows = (1..40).map { row("BTCUSDT") }

        assertEquals(ExploreBoard.CARD_LIMIT, ExploreBoard.cards(rows).size)
    }

    @Test
    fun `nothing in this file can introduce a symbol the app has no artwork for`() {
        // The ticker table is eight hundred rows filtered by nothing. Iterating it to build a
        // movers row is exactly how lettered discs would reach a surface this app does not allow
        // them on — so a ticker for a market that is not in the catalogue contributes no card.
        val cards = ExploreBoard.cards(
            rows = listOf(row("BTCUSDT", price = 60_000.0)),
            tickers = mapOf(
                "BTCUSDT" to ticker("BTCUSDT", 64_000.0, 1.0),
                "NOTACOINUSDT" to ticker("NOTACOINUSDT", 0.4, 88.0),
            ),
        )

        assertEquals(1, cards.size)
        assertTrue(cards.none { it.symbol.contains("NOTACOIN") })
    }
}
