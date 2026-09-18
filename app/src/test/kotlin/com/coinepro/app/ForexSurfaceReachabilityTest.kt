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
 * than dimmed — the introducing brokers, the venue link, and every row that leads to one of them.
 * A dimmed control is an advertisement for something the reader cannot have, and on a screen about
 * money it reads as a fault.
 *
 * **Copy trading is not on the list any more** (run Ψ). It was the largest thing the flag hid, and
 * it is now something stronger than hidden: the screen, the route, the controller and the two
 * modules behind it are deleted. A test asserting the flag hides it would be asserting the flag's
 * reach over something nothing could reach anyway, so what is left is the one row the flag still
 * governs and the one route that still refuses itself.
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
     * So the address checks for itself, and this reads the check out of the shell rather than
     * trusting that somebody remembered it.
     */
    @Test
    fun `the broker route refuses to draw itself with the flag off`() {
        val source = File("src/main/kotlin/com/coinepro/app/CoineProApp.kt").readText()
        GUARDED_ROUTES.forEach { route ->
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
     * Run Ξ named three families of string — the MetaTrader keys, the copy-trading account block,
     * and the broker's own name — and asked that the flag take all of them.
     *
     * **All three are now gone from the tree** (run Ψ), which is the strongest form the answer can
     * take: the MetaTrader card went with copy trading, and its twenty `connections_mt5_*` strings
     * went with the card. So what this asserts is that none of them is drawn anywhere — and the
     * companion assertion is the important half, because a walk that finds nothing because it was
     * pointed at nothing is a test that passes by looking at the wrong place.
     */
    @Test
    fun `no copy-trading or MetaTrader string is drawn anywhere`() {
        val roots = listOf(File("../app/src/main"), File("../core"), File("../feature"))
        roots.forEach { root ->
            assertTrue("${root.path} is not on disk — this test is looking at nothing", root.isDirectory)
        }
        val sources = roots
            .asSequence()
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .filterNot { "/test/" in it.path.replace('\\', '/') }
            .toList()
        assertTrue("no Kotlin sources were walked at all", sources.size > 100)

        val offenders = sources
            .filter { file ->
                val text = file.readText()
                BROKER_STRING_FAMILIES.any { family ->
                    Regex("R\\.string\\.$family").containsMatchIn(text)
                }
            }
            .map { it.path }
        assertTrue(
            "a copy-trading or MetaTrader string is still drawn: $offenders",
            offenders.isEmpty(),
        )
        println("walked ${sources.size} sources; broker strings drawn in ${offenders.size}")
    }

    /**
     * And the resources went with the screen, in both languages.
     *
     * Run Ξ counted twenty `connections_mt5_*` keys in `feature/connections`. Run Ψ removed the
     * MetaTrader card, so all twenty are gone — and gone from `values-fa/` as well, which is the
     * half that rots quietly: a Persian string with no English twin passes a parity check by being
     * absent from the comparison rather than by being right.
     */
    @Test
    fun `no MetaTrader string survives in either language`() {
        listOf("values", "values-fa").forEach { locale ->
            val file = File("../feature/connections/src/main/res/$locale/strings.xml")
            assertTrue("${file.path} is missing", file.isFile)
            val keys = Regex("""name="(connections_mt5_[a-z0-9_]+)"""").findAll(file.readText()).count()
            assertEquals("MetaTrader strings survive in $locale", 0, keys)
        }
    }

    private companion object {
        /**
         * The rows that must not survive the flag.
         *
         * `connections` is the venue link and the broker list. It is the only one left: run Ψ
         * deleted copy trading from the product, so `copy-trade` is not a row this flag hides — it
         * is a row that does not exist, along with its screen, its route and its two modules. A
         * test that still asserted the flag hid it would be asserting the flag's reach over
         * something nothing could reach anyway.
         */
        val FORBIDDEN_IDS = listOf("connections")

        /**
         * The string families the brief names, as regular-expression stems.
         *
         * `connections_mt5_` is the MetaTrader login; `copy_` the copy-trading screen's account,
         * balance and broker. Written as stems rather than as a list of forty keys, so a new one
         * is covered the day it is added rather than the day somebody remembers this file.
         */
        val BROKER_STRING_FAMILIES = listOf("connections_mt5_[a-z0-9_]+", "copy_account_[a-z0-9_]+", "copy_balance", "copy_broker")

        /** The route this test still guards. `COPY_TRADE_ROUTE` went with the feature (run Ψ). */
        val GUARDED_ROUTES = listOf("CONNECTIONS_ROUTE")
    }
}
