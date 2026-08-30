package com.coinepro.feature.menu

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The menu's promises, asserted rather than trusted.
 *
 * Three of them are the reason the screen exists at all: nothing an account would unlock is hidden
 * from a guest, nothing the platform cannot serve is offered to anybody, and the order a reader
 * learns does not move when a row is added.
 */
class MenuCatalogueTest {

    private val member = MenuAccess(platform = MarketPlatform.TRADEYAR, signedIn = true)
    private val guest = MenuAccess(platform = MarketPlatform.TRADEYAR, signedIn = false)

    private fun ids(access: MenuAccess): List<String> =
        MenuCatalogue.sections(access).flatMap { section -> section.items.map { it.entry.id } }

    @Test
    fun `every id is unique`() {
        val ids = MenuCatalogue.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every group has at least one row for a signed-in reader`() {
        val groups = MenuCatalogue.sections(member).map { it.group }
        assertEquals(MenuGroup.entries.toList(), groups)
    }

    @Test
    fun `group order is the enum's, not the catalogue's`() {
        // Shuffling the catalogue must not move a block: the reader learned where the account rows
        // are, and the day somebody appends a row is not the day that should change.
        val shuffled = MenuCatalogue.sections(member, MenuCatalogue.ALL.reversed())
        assertEquals(MenuGroup.entries.toList(), shuffled.map { it.group })
    }

    @Test
    fun `row order within a group is the catalogue's`() {
        val market = MenuCatalogue.sections(member).first { it.group == MenuGroup.MARKET }
        // Filtered by platform the same way the sections are. A row belonging to the other backend
        // is not a row this reader is missing — it is a room that does not exist on their side, and
        // the calendar is the case: TradeYar publishes no calendar route at all.
        val expected = MenuCatalogue.ALL
            .filter { it.group == MenuGroup.MARKET }
            .filter { it.platform == null || it.platform == member.platform }
            .map { it.id }
        assertEquals(expected, market.items.map { it.entry.id })
    }

    @Test
    fun `a row that belongs to the other backend is absent rather than locked`() {
        // The distinction the whole screen rests on. A locked row says "sign in and this is yours";
        // an absent one says nothing, which is the only honest thing to say about a feature the
        // reader's platform does not have. The calendar is forex-only because TradeYar has no
        // calendar route — it was leading a crypto reader to a screen that could never fill.
        assertTrue("calendar" !in ids(member))
        assertTrue("calendar" !in ids(guest))
        val forex = MenuAccess(platform = MarketPlatform.COINEPRO_FX, signedIn = true)
        assertTrue("calendar" in ids(forex))
    }

    @Test
    fun `a guest is shown exactly what a member is shown`() {
        // The whole argument of the screen. A guest menu that is shorter than a member's is a menu
        // that teaches a smaller app than the one they installed.
        assertEquals(ids(member), ids(guest))
    }

    @Test
    fun `account-only rows are locked for a guest and open for a member`() {
        val lockedForGuest = MenuCatalogue.sections(guest)
            .flatMap { it.items }
            .filter { it.locked }
            .map { it.entry.id }
            .toSet()
        val accountOnly = MenuCatalogue.ALL
            .filter { it.account && it.platform != MarketPlatform.COINEPRO_FX }
            .map { it.id }
            .toSet()
        assertEquals(accountOnly, lockedForGuest)
        assertTrue(MenuCatalogue.sections(member).flatMap { it.items }.none { it.locked })
    }

    @Test
    fun `a guest can open the watchlist, the toolkit and everything else that needs no account`() {
        val open = MenuCatalogue.sections(guest)
            .flatMap { it.items }
            .filterNot { it.locked }
            .map { it.entry.id }
        listOf(
            "watchlist", "search", "screener", "heatmap", "news",
            "paper-trade", "journal",
            "chart-studio", "backtest", "alerts", "tools",
            "safety", "profile", "notifications",
        ).forEach { id -> assertTrue(id, id in open) }
    }

    @Test
    fun `a guest is never offered the portfolio, and is never quietly denied it either`() {
        val portfolio = MenuCatalogue.sections(guest)
            .flatMap { it.items }
            .single { it.entry.id == "portfolio" }
        assertTrue(portfolio.locked)
    }

    @Test
    fun `a platform that does not serve a surface is not offered it`() {
        // The academy and copy trading are CoinePro-FX routes; TradeYar has neither.
        assertFalse("academy" in ids(member))
        assertFalse("copy-trade" in ids(member))

        val fx = MenuAccess(platform = MarketPlatform.COINEPRO_FX, signedIn = true)
        assertTrue("academy" in ids(fx))
        assertTrue("copy-trade" in ids(fx))
    }

    @Test
    fun `an absent capability is dropped rather than locked`() {
        val access = member.copy(absent = setOf("ai-vision", "ai-assistant", "terminal"))
        val shown = ids(access)
        assertFalse("ai-vision" in shown)
        assertFalse("ai-assistant" in shown)
        assertFalse("terminal" in shown)
        // And the block those three left survives, because it still has rows.
        assertTrue(MenuCatalogue.sections(access).any { it.group == MenuGroup.ANALYSIS })
    }

    @Test
    fun `a block with nothing left in it is not drawn`() {
        val emptied = MenuCatalogue.ALL.filterNot { it.group == MenuGroup.LEARN }
        val groups = MenuCatalogue.sections(member, emptied).map { it.group }
        assertFalse(MenuGroup.LEARN in groups)
    }

    @Test
    fun `signing in opens rows rather than revealing them`() {
        val before = MenuCatalogue.openCount(guest)
        val after = MenuCatalogue.openCount(member)
        assertTrue(after > before)
        assertEquals(ids(guest).size, ids(member).size)
    }

    @Test
    fun `the deletion row is the last one on the page and the only destructive one`() {
        val last = MenuCatalogue.sections(member).last().items.last()
        assertEquals("delete", last.entry.id)
        assertEquals(listOf("delete"), MenuCatalogue.ALL.filter { it.destructive }.map { it.id })
    }
}
