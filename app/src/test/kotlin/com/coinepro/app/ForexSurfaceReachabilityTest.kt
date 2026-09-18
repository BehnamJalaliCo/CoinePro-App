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

    // ------------------------------------------------------- run Ξ, item 19: the routes themselves

    /**
     * **The menu is not the only way to a screen** (run Ξ, item 19).
     *
     * B3 took the rows out of the directory, which is where a reader would have found them. It did
     * not stop a deep link, a restored back stack, or one future call site that forgets the guard.
     * So both addresses now check for themselves, and this reads the check out of the shell rather
     * than trusting that somebody remembered it.
     */
    @Test
    fun `both broker routes refuse to draw themselves with the flag off`() {
        val source = File("src/main/kotlin/com/coinepro/app/CoineProApp.kt").readText()
        listOf("CONNECTIONS_ROUTE", "COPY_TRADE_ROUTE").forEach { route ->
            val block = Regex("""composable\($route\) \{(.*?)
            \}""", RegexOption.DOT_MATCHES_ALL)
                .find(source)
                ?.groupValues
                ?.get(1)
            assertTrue("no composable($route) block in the shell at all", block != null)
            assertTrue(
                "composable($route) draws its screen without asking tradingOffered() — a deep " +
                    "link or a restored back stack still reaches a broker account",
                block!!.contains("tradingOffered(activePlatform)"),
            )
            assertTrue(
                "composable($route) checks the flag and then draws anyway — there is no else",
                block.contains("popBackStack()"),
            )
        }
    }

    /**
     * The brief names three families of string — the MetaTrader keys, the copy-trading account
     * block, and the broker's own name — and asks that the flag take all of them.
     *
     * The way that is true is **where they live**: every one of them is drawn on one of the two
     * screens above and on no other, so the two guards take the lot. This is what would fail the
     * day somebody puts «موجودی حساب کپی» on a card in the menu, which is precisely the kind of
     * change that would slip a broker surface back into a build that opens no broker account.
     */
    @Test
    fun `every broker string is drawn on one of the two guarded screens and nowhere else`() {
        val allowed = setOf("feature/connections", "feature/copytrade")
        val offenders = sequenceOf(File("../app/src/main"), File("../core"), File("../feature"))
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .filter { file ->
                val path = file.path.replace('\\', '/')
                allowed.none { it in path } && "/test/" !in path
            }
            .filter { file ->
                BROKER_STRING_FAMILIES.any { family ->
                    Regex("""R\.string\.$family""").containsMatchIn(file.readText())
                }
            }
            .map { it.path }
            .toList()
        assertTrue(
            "a broker string is drawn outside the two guarded screens: $offenders",
            offenders.isEmpty(),
        )
        // And the search works. A walk over a path that does not exist finds nothing and passes,
        // which is the failure mode of every test written this way; this is what says the regular
        // expressions match something real.
        val guarded = listOf(
            File("../feature/copytrade/src/main/kotlin/com/coinepro/feature/copytrade/CopyTradeScreen.kt"),
            File("../feature/connections/src/main/kotlin/com/coinepro/feature/connections/ConnectionsScreen.kt"),
        )
        guarded.forEach { file ->
            assertTrue("${file.path} has moved — this test is looking at nothing", file.isFile)
            val text = file.readText()
            assertTrue(
                "${file.name} draws none of the broker strings — either it changed or the " +
                    "patterns in BROKER_STRING_FAMILIES no longer match anything",
                BROKER_STRING_FAMILIES.any { family -> Regex("R\\.string\\.$family").containsMatchIn(text) },
            )
        }
    }

    /**
     * And the keys are all on the one screen's own resources, so hiding the screen hides them.
     *
     * The brief says twenty-two `connections_mt5_*` keys; the tree carries **twenty**, all of them
     * in `feature/connections`. The number is stated rather than asserted at 22 — a gate that
     * fails when somebody writes a twenty-first string would be a gate arguing with the product —
     * but the *location* is asserted, because that is the claim that matters.
     */
    @Test
    fun `the MetaTrader strings live in the connections module only`() {
        val english = File("../feature/connections/src/main/res/values/strings.xml").readText()
        val keys = Regex("""name="(connections_mt5_[a-z0-9_]+)"""").findAll(english).count()
        assertTrue("the MetaTrader strings have left feature/connections", keys > 0)
        println("connections_mt5_* keys in feature/connections: $keys")
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

        /**
         * The string families the brief names, as regular-expression stems.
         *
         * `connections_mt5_` is the MetaTrader login; `copy_` the copy-trading screen's account,
         * balance and broker. Written as stems rather than as a list of forty keys, so a new one
         * is covered the day it is added rather than the day somebody remembers this file.
         */
        val BROKER_STRING_FAMILIES = listOf("connections_mt5_[a-z0-9_]+", "copy_account_[a-z0-9_]+", "copy_balance", "copy_broker")
    }
}
