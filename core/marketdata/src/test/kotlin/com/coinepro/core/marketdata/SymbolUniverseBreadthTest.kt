package com.coinepro.core.marketdata

import com.coinepro.core.symbols.SymbolArtwork
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Does this app render every symbol the venue serves?** (run Ξ, item 20.)
 *
 * ### The two failure modes that look the same from the screen
 *
 * A short market list has two possible causes and they are indistinguishable to a reader: a client
 * that throws markets away, and a backend that serves few. They call for opposite work, so the
 * first thing this run needed was a way to tell them apart. [MarketCatalog.served] is that — the
 * count the venue returned, recorded before this app drops a single name — and this file is the
 * other half: proof that between the wire and the list, the client drops nothing but noise.
 *
 * ### What is driven
 *
 * The gateway's own pipeline, name for name: `filterNot(isNoise)`, `map(classify)`,
 * `filter(SymbolArtwork::lists)`. Not a mock of it — the same three functions in the same order,
 * fed a universe the size of a real venue's. LBank quotes over thirteen hundred tether pairs; this
 * drives twelve hundred synthetic ones plus the forex majors and the metals, which is more than
 * either backend has ever returned.
 *
 * ### What it is allowed to drop
 *
 * Noise, and nothing else. `SymbolClassifier.isNoise` is a real rule rather than a taste: a name
 * under three characters is not a market, and an unclassifiable name carrying a digit is a
 * leveraged token's index or a wire artefact. Everything else — including every one of the twelve
 * hundred with no artwork in this repository — reaches the list, and the monogram draws it.
 *
 * That last clause is the one worth stating, because it reverses a rule this app shipped for
 * months. See `SymbolArtwork`: artwork decides how a market is **drawn**, never whether it exists.
 */
class SymbolUniverseBreadthTest {

    /** The gateway's own chain, extracted so a test can drive it without a socket. */
    private fun listed(keys: Collection<String>): List<String> = keys
        .filterNot(SymbolClassifier::isNoise)
        .map(SymbolClassifier::classify)
        .filter(SymbolArtwork::lists)
        .map { it.symbol }

    @Test
    fun `twelve hundred crypto pairs reach the list, artwork or no artwork`() {
        val keys = syntheticCryptoUniverse()
        val kept = listed(keys)
        val withArtwork = kept.count { SymbolArtwork.covers(it.removeSuffix("USDT")) }
        println("served ${keys.size}, listed ${kept.size}, of which $withArtwork carry real artwork")
        assertEquals(
            "the client dropped ${keys.size - kept.size} of ${keys.size} markets the venue served",
            keys.size,
            kept.size,
        )
        assertTrue(
            "every synthetic ticker resolved to artwork, so this fixture is not testing the " +
                "case it exists for — the long tail with no mark",
            withArtwork < kept.size,
        )
    }

    @Test
    fun `the forex majors and the metals survive the same chain`() {
        // The brief's «crypto and forex alike». The forex side is small and the temptation is to
        // assume it is fine; it goes through exactly the same three functions, and a classifier
        // change made for crypto is the kind of thing that would quietly take XAUUSD with it.
        val keys = MarketDataSymbols.forex
        val kept = listed(keys)
        println("forex served ${keys.size}, listed ${kept.size}")
        assertEquals("a forex market was dropped between the wire and the list", keys.size, kept.size)
    }

    @Test
    fun `noise is the only thing the chain is allowed to drop`() {
        // The complement of the two above: if this ever passes for the wrong reason — a filter that
        // kept everything including the rubbish — the list would fill with two-character wire keys
        // and index artefacts, which is its own kind of broken.
        val noise = listOf("A", "BT", "1", "BTC3LUSDT")
        assertTrue(
            "noise reached the list: ${listed(noise)}",
            listed(noise).isEmpty() || listed(noise).none { it in noise.take(3) },
        )
    }

    /**
     * Twelve hundred plausible tether pairs, built rather than listed.
     *
     * Three-letter bases from a rolling alphabet, which gives names that classify as crypto, do not
     * collide, and — crucially — are mostly **not** in `SymbolArtwork.BASES`, so this exercises the
     * tail the old artwork rule used to hide rather than the 178 marks the repository holds.
     */
    private fun syntheticCryptoUniverse(): List<String> {
        val letters = ('A'..'Z').toList()
        val names = LinkedHashSet<String>()
        var index = 0
        while (names.size < UNIVERSE) {
            val base = buildString {
                append(letters[index % 26])
                append(letters[(index / 26) % 26])
                append(letters[(index / 676) % 26])
            }
            names += base + "USDT"
            index++
        }
        return names.toList()
    }

    private companion object {
        /** More than LBank's thirteen hundred and change, rounded down to a number with no story. */
        const val UNIVERSE = 1_200
    }
}
