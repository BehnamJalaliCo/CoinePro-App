package com.coinepro.feature.search

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.symbols.SymbolSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of the search field that answers with a screen rather than a market.
 *
 * Two things here are wrong silently and neither shows up in a render: a Persian keyword that
 * misses because the reader typed the same word with a space in it, and an availability rule that
 * offers somebody a screen their session cannot open. Both are what these tests are for.
 */
class AppSurfaceSearchTest {

    /** A signed-in member on the crypto side — the ordinary case, so the ordinary default. */
    private val member = SurfaceAccess(platform = MarketPlatform.TRADEYAR, signedIn = true)

    private fun ids(query: String, access: SurfaceAccess = member, limit: Int = 8) =
        AppSurfaceSearch.search(query, access, limit = limit).map { it.surface.id }

    // ── the words a Persian reader actually types ────────────────────────────────────────────

    @Test
    fun `a Persian name finds its section`() {
        assertEquals("journal", ids("ژورنال").first())
        assertEquals("screener", ids("اسکرینر").first())
        assertEquals("portfolio", ids("پرتفوی").first())
    }

    @Test
    fun `the space inside a two-word name does not have to be typed the way the catalogue spells it`() {
        // «دیده‌بان» carries a zero-width non-joiner. Nobody types one, and a reader who reaches
        // for the watchlist writes it as two words or as one — three spellings of a word this app
        // uses as a section title.
        assertEquals("watchlist", ids("دیده‌بان").first())
        assertEquals("watchlist", ids("دیده بان").first())
        assertEquals("watchlist", ids("دیدهبان").first())
    }

    @Test
    fun `an Arabic keyboard's letters find a Persian keyword`() {
        // ي and ك are different code points from ی and ک, and a phone set up in Arabic — or a
        // pasted string — sends the first pair. Without folding this query matches nothing at all.
        assertTrue("academy" in ids("آكادمي", fx(signedIn = true)))
    }

    @Test
    fun `an English query finds the same section as the Persian one`() {
        // The reader is one person who types both, often in the same minute, so the keyword list is
        // a union rather than a translation.
        assertEquals("backtest", ids("backtest").first())
        assertEquals("heatmap", ids("heatmap").first())
        assertEquals("heatmap", ids("heat map").first())
        assertEquals("journal", ids("journal").first())
    }

    // ── merging with the market list ─────────────────────────────────────────────────────────

    @Test
    fun `a query that names both a section and a market answers with both`() {
        // `ai` is exactly the AI section and is also inside DAI, which is a real market on the
        // crypto side. The two live in separate lists on purpose: the section block is capped and
        // headed, so it can never push the market the reader was typing off the screen.
        val universe = SymbolClassifier.classifyAll(listOf("BTCUSDT", "DAIUSDT", "ETHUSDT"))

        val markets = SymbolSearch.search(universe, "ai").map { it.meta.symbol }
        val sections = ids("ai")

        assertEquals("DAIUSDT", markets.first())
        assertEquals("ai", sections.first())
    }

    @Test
    fun `a ticker does not conjure a section`() {
        // The one way this feature could make the screen worse is by answering a market query with
        // suggestions. Nothing in the catalogue contains `btc`, and a scattered match is refused.
        assertEquals(emptyList<String>(), ids("btc"))
        assertEquals(emptyList<String>(), ids("eurusd"))
    }

    @Test
    fun `a scattered match is not offered, because a section row has no highlight to explain it`() {
        // `arn` appears in «ژورنال»'s Latin keyword `journal` as j-o-u-r-n-a-l → r…n…a, in order
        // and not together. A market row could show that hit underlined; this one cannot.
        assertFalse("journal" in ids("jrn"))
    }

    @Test
    fun `one letter is not yet a word`() {
        assertEquals(emptyList<String>(), ids("a"))
        assertEquals(emptyList<String>(), ids("ژ"))
        assertEquals(emptyList<String>(), ids(" "))
    }

    @Test
    fun `the section block is capped so the first market is still on the first screen`() {
        // «ا» starts half the catalogue; the cap is what stops a common letter-pair from turning
        // the results into a menu.
        assertTrue(AppSurfaceSearch.search("ا", member).size <= 4)
        assertTrue(AppSurfaceSearch.search("اس", member).size <= 4)
    }

    // ── availability ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a section this platform does not serve is not named at all`() {
        // TradeYar has no academy route. Telling a reader about it would be describing a screen
        // that cannot be opened from where they are standing.
        assertFalse("academy" in ids("آکادمی", member))
        assertFalse("academy" in ids("academy", member))
    }

    @Test
    fun `a section that needs an account is shown, and marked`() {
        // Shown, because learning that the app has an academy is the whole point of putting
        // sections in a search field. Marked, because opening it would answer 401.
        val guest = AppSurfaceSearch.search("آکادمی", fx(signedIn = false))

        assertEquals("academy", guest.single().surface.id)
        assertTrue(guest.single().locked)
    }

    @Test
    fun `the same section is open once there is a session`() {
        val member = AppSurfaceSearch.search("آکادمی", fx(signedIn = true))

        assertEquals("academy", member.single().surface.id)
        assertFalse(member.single().locked)
    }

    @Test
    fun `a capability this deployment does not report is dropped rather than locked`() {
        // A locked row says "sign in and this opens". For a feature the server does not offer there
        // is nothing the reader can do, so the honest answer is not to raise it.
        val without = member.copy(absent = setOf("ai-vision", "terminal"))

        assertFalse("ai-vision" in ids("تحلیل تصویر", without))
        assertFalse("terminal" in ids("ترمینال", without))
        assertTrue("terminal" in ids("ترمینال", member))
    }

    @Test
    fun `an equal match is broken by the catalogue's own order, not by chance`() {
        // Both of these hold «ریسک» — the toolkit as a calculator, the alert centre not at all —
        // so the pair that does match is separated by where they sit in `AppSurfaces.ALL`, which
        // is the only ordering in this file anybody chose.
        val catalogue = AppSurfaces.ALL.map { it.id }
        val hits = ids("ریسک")

        assertTrue(hits.isNotEmpty())
        assertEquals(hits.sortedBy { catalogue.indexOf(it) }, hits)
    }

    // ── the folding itself ───────────────────────────────────────────────────────────────────

    @Test
    fun `folding removes exactly the differences a reader cannot see`() {
        assertEquals("دیدهبان", foldForSearch("دیده‌بان"))
        assertEquals("دیدهبان", foldForSearch("دیده بان"))
        assertEquals("اکادمی", foldForSearch("آكادمي"))
        assertEquals("heatmap", foldForSearch("Heat Map"))
        assertEquals("papertrading", foldForSearch("paper-trading"))
    }

    @Test
    fun `every section in the catalogue can be found by its own first keyword`() {
        // The catalogue is hand-written and its keywords are the only route to any of it. A typo in
        // one of them is a whole screen that quietly stopped being discoverable.
        for (surface in AppSurfaces.ALL) {
            val access = SurfaceAccess(
                platform = surface.platform ?: MarketPlatform.TRADEYAR,
                signedIn = true,
            )
            val found = AppSurfaceSearch.search(surface.keywords.first(), access, limit = 32)
            assertTrue(
                "«${surface.keywords.first()}» does not reach ${surface.id}",
                surface.id in found.map { it.surface.id },
            )
        }
    }

    private fun fx(signedIn: Boolean) =
        SurfaceAccess(platform = MarketPlatform.COINEPRO_FX, signedIn = signedIn)
}
