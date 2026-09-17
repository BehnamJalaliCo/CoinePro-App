package com.coinepro.core.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The universe the markets tab is a list of** (run ΤΦΥ, F1–F3).
 *
 * The arithmetic, with no venue and no renderer in the way: what the app carries when nothing
 * answers, what happens when something does, and the four things a markets screen does to the
 * result. Every one of these is a rule a screen would otherwise have invented for itself.
 */
class SymbolUniverseTest {

    @Test
    fun `the bundled universe is the three hundred plus the markets we have flags for`() {
        val bundled = SymbolUniverse.bundled()
        assertEquals(
            "the bundled table is ${bundled.size} rows",
            BundledUniverse.CRYPTO.size + BundledUniverse.FOREX.size,
            bundled.size,
        )
        assertEquals(300, BundledUniverse.CRYPTO.size)
        // Every row is a market this app can classify, name and draw. A row that fell through to
        // `OTHER` would be one the markets tab could group nowhere and the search could not rank.
        val crypto = bundled.filter { it.type == SymbolCategory.CRYPTO }
        assertEquals(BundledUniverse.CRYPTO.size, crypto.size)
        assertTrue("a bundled row has no name", bundled.none { it.displayNameEn.isBlank() })
        assertTrue("a bundled row has no name in Persian", bundled.none { it.displayNameFa.isBlank() })
        // The venue says «this is one we are carrying», never a venue's name, so nothing can read a
        // bundled row as a quote.
        assertTrue(bundled.all { it.venue == SymbolUniverse.BUNDLED_VENUE })
        // And no turnover on any of them: a figure here would be a number this app made up.
        assertTrue(bundled.all { it.turnover24h == null })
    }

    @Test
    fun `a venue's own row wins, and the rest of the floor survives`() {
        val live = listOf(
            SymbolUniverse.of(SymbolClassifier.classify("BTCUSDT"), venue = "LBank", turnover24h = 9_000.0),
        )
        val merged = SymbolUniverse.merge(live)
        val bitcoin = merged.filter { it.id == "BTCUSDT" }
        assertEquals("the same market is listed twice", 1, bitcoin.size)
        assertEquals("LBank", bitcoin.single().venue)
        assertEquals(9_000.0, bitcoin.single().turnover24h!!, 0.0)
        // Everything the venue did not mention is still there — which on a venue answering with
        // nineteen symbols is the entire markets tab.
        assertTrue(merged.size > BundledUniverse.CRYPTO.size)
        assertTrue(merged.any { it.id == "ETHUSDT" && it.venue == SymbolUniverse.BUNDLED_VENUE })
    }

    @Test
    fun `nothing live leaves the floor standing rather than an empty list`() {
        assertEquals(SymbolUniverse.bundled().size, SymbolUniverse.merge(emptyList()).size)
    }

    @Test
    fun `turnover ranks, and a market with no figure sorts behind one that has`() {
        val rows = listOf(
            SymbolUniverse.of(SymbolClassifier.classify("ADAUSDT"), "LBank"),
            SymbolUniverse.of(SymbolClassifier.classify("ETHUSDT"), "LBank", turnover24h = 10.0),
            SymbolUniverse.of(SymbolClassifier.classify("BTCUSDT"), "LBank", turnover24h = 99.0),
        )
        assertEquals(listOf("BTCUSDT", "ETHUSDT", "ADAUSDT"), SymbolUniverse.ranked(rows).map { it.id })
    }

    @Test
    fun `search covers the whole universe and not the page that happens to be loaded`() {
        // The case F3 is about. `SOL` is nowhere near the first two hundred rows by ticker order,
        // and a search over a loaded page would not find it.
        val universe = SymbolUniverse.bundled()
        val hits = SymbolUniverse.search(universe, "solana")
        assertTrue("«solana» found nothing in a universe of ${universe.size}", hits.isNotEmpty())
        assertEquals("SOLUSDT", hits.first().id)
        // And the Persian name is searched too, in an app whose default language is Persian.
        assertEquals("BTCUSDT", SymbolUniverse.search(universe, "بیت‌کوین").first().id)
    }

    @Test
    fun `an empty query is the ranked browse list rather than nothing`() {
        val universe = SymbolUniverse.bundled()
        assertEquals(universe.size, SymbolUniverse.search(universe, "   ").size)
    }

    @Test
    fun `a filter nobody touched removes nothing`() {
        val universe = SymbolUniverse.bundled()
        assertEquals(universe.size, SymbolUniverse.filter(universe, SymbolUniverse.Filter()).size)
    }

    @Test
    fun `a filter narrows by type, by venue and by turnover`() {
        val rows = listOf(
            SymbolUniverse.of(SymbolClassifier.classify("BTCUSDT"), "LBank", turnover24h = 5_000.0),
            SymbolUniverse.of(SymbolClassifier.classify("XAUUSD"), "CoinePro FX"),
            SymbolUniverse.of(SymbolClassifier.classify("ETHUSDT"), "LBank", turnover24h = 10.0),
        )
        assertEquals(
            listOf("XAUUSD"),
            SymbolUniverse.filter(rows, SymbolUniverse.Filter(types = setOf(SymbolCategory.METAL))).map { it.id },
        )
        assertEquals(
            listOf("BTCUSDT", "ETHUSDT"),
            SymbolUniverse.filter(rows, SymbolUniverse.Filter(venues = setOf("LBank"))).map { it.id },
        )
        // A turnover floor is the one filter that *does* drop the unknowns, because «worth more than
        // a million a day» is a claim and a market that said nothing has not made it.
        assertEquals(
            listOf("BTCUSDT"),
            SymbolUniverse.filter(rows, SymbolUniverse.Filter(minTurnover = 1_000.0)).map { it.id },
        )
    }

    @Test
    fun `a change band keeps the rows nothing knows the change of`() {
        // The rule that stops a band emptying the whole non-crypto side, which reports no change at
        // all on this venue. A reader narrowing a list has not asked for «only the rows you have a
        // figure for».
        val rows = listOf(
            SymbolUniverse.of(SymbolClassifier.classify("BTCUSDT"), "LBank"),
            SymbolUniverse.of(SymbolClassifier.classify("ETHUSDT"), "LBank"),
        )
        val changes = mapOf("ETHUSDT" to -9.0)
        val kept = SymbolUniverse.filter(
            rows,
            SymbolUniverse.Filter(changeToPercent = -5.0),
        ) { changes[it.id] }
        assertEquals(listOf("BTCUSDT", "ETHUSDT"), kept.map { it.id })
        val dropped = SymbolUniverse.filter(
            rows,
            SymbolUniverse.Filter(changeFromPercent = 0.0),
        ) { changes[it.id] }
        assertEquals("the falling market survived an «up» band", listOf("BTCUSDT"), dropped.map { it.id })
    }

    @Test
    fun `pages are two hundred, and the edges do not throw`() {
        val universe = SymbolUniverse.bundled()
        assertEquals(200, SymbolUniverse.PAGE)
        assertEquals(200, SymbolUniverse.page(universe, 0, 200).size)
        assertEquals(universe[200].id, SymbolUniverse.page(universe, 200, 200).first().id)
        // Past the end, and a page that runs off it. Both are ordinary on a list being scrolled.
        assertTrue(SymbolUniverse.page(universe, universe.size + 50, 200).isEmpty())
        assertEquals(universe.size - 200, SymbolUniverse.page(universe, 200, universe.size).size)
        assertEquals(2, SymbolUniverse.pages(universe.take(201), 200))
    }

    @Test
    fun `a listing with no status is tradable, and a delisted one is not`() {
        val row = SymbolUniverse.of(SymbolClassifier.classify("BTCUSDT"), "LBank")
        assertTrue("a venue that sends no status hid its whole book", row.tradable)
        assertFalse(row.copy(status = SymbolStatus.DELISTED).tradable)
        assertFalse(row.copy(status = SymbolStatus.PAUSED).tradable)
    }

    @Test
    fun `the preferred quote keeps one listing per asset and never invents one`() {
        // Two books of the same coin on one venue — the case a reader sees as one market and the
        // exchange lists twice.
        val usdt = SymbolUniverse.of(SymbolClassifier.classify("BTCUSDT"), venue = "LBank")
        val usdc = SymbolUniverse.of(SymbolClassifier.classify("BTCUSDC"), venue = "LBank")
        // And one asset that is not quoted in the preferred unit at all.
        val gold = SymbolUniverse.of(SymbolClassifier.classify("XAUUSD"), venue = "CoinePro FX")

        val kept = SymbolUniverse.preferring(listOf(usdt, usdc, gold), "USDT")
        assertEquals(listOf("BTCUSDT", "XAUUSD"), kept.map { it.id })

        // The other way round, to prove it is choosing rather than always keeping the first.
        assertEquals(
            listOf("BTCUSDC", "XAUUSD"),
            SymbolUniverse.preferring(listOf(usdt, usdc, gold), "USDC").map { it.id },
        )
    }

    @Test
    fun `a quote nothing is listed in leaves every listing where it is`() {
        val usdt = SymbolUniverse.of(SymbolClassifier.classify("BTCUSDT"), venue = "LBank")
        val usdc = SymbolUniverse.of(SymbolClassifier.classify("BTCUSDC"), venue = "LBank")
        // Rial, which has no market on either backend. A reader who chose it is saying which
        // currency their head is in; they must not lose the list for it.
        val kept = SymbolUniverse.preferring(listOf(usdt, usdc), "IRR")
        assertEquals(listOf("BTCUSDT", "BTCUSDC"), kept.map { it.id })
        // And «no opinion» is the whole list, untouched.
        assertEquals(2, SymbolUniverse.preferring(listOf(usdt, usdc), null).size)
        assertEquals(2, SymbolUniverse.preferring(listOf(usdt, usdc), "  ").size)
    }

    @Test
    fun `the same ticker on two venues is two markets and both survive`() {
        // Collapsing these would hide one venue's price behind another's, which is the opposite of
        // what a markets list is for.
        val lbank = SymbolUniverse.of(SymbolClassifier.classify("BTCUSDT"), venue = "LBank")
        val other = SymbolUniverse.of(SymbolClassifier.classify("BTCUSDC"), venue = "CoinePro FX")
        assertEquals(2, SymbolUniverse.preferring(listOf(lbank, other), "USDT").size)
    }
}
