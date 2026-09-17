package com.coinepro.app

import com.coinepro.core.common.FeatureFlags
import com.coinepro.core.model.MarketPlatform
import com.coinepro.feature.menu.MenuCatalogue
import com.coinepro.feature.menu.MenuGroup
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **`FeatureFlags.forexTrading = false` has to be airtight** (run Τ2, B3).
 *
 * The flag's own promise is that with it off every door to a forex *account* is **absent** rather
 * than dimmed — the introducing brokers, the MetaTrader connection, copy trading, and every row
 * that leads to one of them. A dimmed control is an advertisement for something the reader cannot
 * have, and on a screen about money it reads as a fault.
 *
 * ### What this walks, and what it deliberately does not
 *
 * The shell's menu decides reachability on this app: `AppDestination` is five tabs, and everything
 * else arrives through the directory or through a card on a screen the directory reaches. So what
 * this walks is the shell's own hiding rule — the `absent` set it hands the directory — **read out
 * of `CoineProApp.kt` rather than retyped here**, because a second list of ids is a list free to be
 * shorter than the code, which is the exact hole the file exists to close.
 *
 * It does not compose the graph. A Robolectric walk of forty destinations would be ten minutes of
 * CI to assert what a pure set already answers, and `NavigationDepthTest` is what holds the graph
 * itself honest. The chart's partner row is the one forex door that is not a menu row, and it is
 * covered one module over by `TradePartnersFlagTest`, where `tradePartners()` is visible.
 *
 * ### What is not in the forbidden set, and why
 *
 * `membership_open_ourbit`. The brief lists it beside the MetaTrader keys, and it is not the same
 * kind of thing: the membership journey is the **crypto** venue's sub-account check, on TradeYar,
 * and `FeatureFlags.forexTrading`'s own note keeps it either way — «the account verification the
 * crypto venue requires» is F6's explicit exception. Removing it with this flag would take the
 * crypto sign-up away from a build whose whole positioning is crypto. `docs/runs/RUN_T2/CHECKLIST.md`
 * carries the row as narrowed rather than met, with this sentence.
 */
class ForexSurfaceReachabilityTest {

    @After
    fun restore() = FeatureFlags.reset()

    /** The menu ids the shell hides while this build offers no forex account. */
    private fun hiddenWhenTradingIsOff(): Set<String> {
        val source = File("src/main/kotlin/com/coinepro/app/CoineProApp.kt").readText()
        // Read out of the shell rather than typed here: a second list is a list free to be shorter
        // than the code, which is the exact hole this file exists to close.
        return Regex("""if \(!tradingOffered\(activePlatform\)\) \{([^}]*)\}""")
            .findAll(source)
            .flatMap { block -> Regex("""add\("([^"]+)"\)""").findAll(block.groupValues[1]) }
            .map { it.groupValues[1] }
            .toSet()
    }

    /** Every id the directory can draw for [platform], with nothing hidden. */
    private fun offeredIds(platform: MarketPlatform): Set<String> =
        MenuCatalogue.ALL
            .filter { it.platform == null || it.platform == platform }
            .map { it.id }
            .toSet()

    @Test
    fun `with the flag off no menu row leads to a forex account`() {
        FeatureFlags.forexTrading = false
        val hidden = hiddenWhenTradingIsOff()
        FORBIDDEN_IDS.forEach { id ->
            assertTrue(
                "«$id» is still reachable from the menu with forex trading off — it leads to a " +
                    "broker account this build does not open",
                id in hidden,
            )
        }
    }

    @Test
    fun `with the flag on every one of them returns`() {
        // The half of the app that is compiled and never runs in a shipping build. Without this
        // the day the owner turns the flag back on is the day somebody discovers it rotted.
        //
        // The rows themselves never left the catalogue — hiding is the shell's `absent` set, and
        // the entries stay where they are so that turning the flag back on is one word. The chart's
        // own partner row is the same argument one module over, and `TradePartnersFlagTest` holds
        // it there because `tradePartners()` is internal to `feature:chart`.
        FeatureFlags.forexTrading = true
        val forex = offeredIds(MarketPlatform.COINEPRO_FX)
        FORBIDDEN_IDS.forEach { id ->
            assertTrue("«$id» is not in the catalogue at all — it cannot come back", id in forex)
        }
    }

    @Test
    fun `the forbidden rows are trading rows, so the reader loses nothing else`() {
        // A guard on this file rather than on the app: if somebody adds an id to the shell's hidden
        // set that is not about trading, the flag has quietly grown a second meaning.
        val trading = MenuCatalogue.ALL
            .filter { it.id in hiddenWhenTradingIsOff() }
            .map { it.group }
            .toSet()
        assertFalse("the hidden set is empty — the reader broke, not the shell", trading.isEmpty())
        assertEquals(
            "forex trading hid a row outside the trading block: $trading",
            setOf(MenuGroup.TRADE),
            trading,
        )
    }

    private companion object {
        /**
         * The rows that must not survive the flag.
         *
         * `connections` is the MetaTrader login and the broker list — the twenty-two
         * `connections_mt5_*` strings live on that screen and nowhere else, so it going takes them
         * with it. `copy-trade` mirrors verified signals onto a MetaTrader account, which a build
         * that opens none has nothing to do with.
         */
        val FORBIDDEN_IDS = listOf("connections", "copy-trade")
    }
}
