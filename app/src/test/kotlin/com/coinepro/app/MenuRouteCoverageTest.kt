package com.coinepro.app

import com.coinepro.core.model.MarketPlatform
import com.coinepro.feature.menu.MenuCatalogue
import com.coinepro.feature.search.AppSurfaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every row in the menu goes somewhere, and «somewhere» is not the profile by accident.
 *
 * ### The failure this exists for
 *
 * `surfaceRoute` maps thirty ids to routes and ends in `else -> PROFILE_ROUTE`. That fallback is
 * the right shape — a `when` over strings has to end somewhere and a crash would be worse — and it
 * is also silent: an entry added to `MenuCatalogue` without a case here does not fail to build, it
 * ships, and the row opens the profile. A reader taps «اسکرینر» and lands on their own account
 * page with nothing to explain it, which reads as the app being broken rather than as one line
 * missing from a table.
 *
 * That risk went up the moment the bottom bar shrank: Home, the AI studio and the signal list all
 * became menu rows in the same change, and the menu is now the only way to two of them.
 *
 * ### What is asserted
 *
 * That the fallback is reachable — an id nobody has heard of does land on the profile — and that
 * **no real id reaches it** except the one that means the profile. It cannot assert that each
 * route exists in the graph without composing the whole `NavHost`; what it can do is catch the
 * class of mistake that has an actual failure mode, which is the missing case.
 *
 * The search catalogue is checked with the same rule, because both name the same ids on purpose
 * and `menuRoute` delegates to `surfaceRoute` precisely so the two cannot disagree.
 */
class MenuRouteCoverageTest {

    private val watchlist = listOf("BTCUSDT")

    private fun menu(id: String, platform: MarketPlatform) = menuRoute(id, platform, watchlist)

    @Test
    fun `the fallback is reachable, which is what makes the rest of this test mean something`() {
        for (platform in MarketPlatform.entries) {
            assertEquals(PROFILE_ROUTE, menu("no-such-surface", platform))
            assertEquals(PROFILE_ROUTE, menu("", platform))
        }
    }

    @Test
    fun `every menu row resolves to a route of its own`() {
        for (platform in MarketPlatform.entries) {
            for (entry in MenuCatalogue.ALL) {
                val route = menu(entry.id, platform)
                if (entry.id == "profile") {
                    assertEquals(PROFILE_ROUTE, route)
                    continue
                }
                assertTrue(
                    "menu row '${entry.id}' falls through to the profile on $platform — " +
                        "add its case to surfaceRoute or menuRoute",
                    route != PROFILE_ROUTE,
                )
                assertTrue("menu row '${entry.id}' resolves to a blank route", route.isNotBlank())
            }
        }
    }

    @Test
    fun `every search surface resolves to a route of its own`() {
        for (platform in MarketPlatform.entries) {
            for (surface in AppSurfaces.ALL) {
                val route = surfaceRoute(surface.id, platform, watchlist)
                if (surface.id == "profile") continue
                assertTrue(
                    "search surface '${surface.id}' falls through to the profile on $platform",
                    route != PROFILE_ROUTE,
                )
            }
        }
    }

    @Test
    fun `the three that left the bottom bar are still reachable from the menu`() {
        // The point of the navigation change, stated as a test. Removing a destination from the
        // bar removes it from the bar; a reader who used it daily must still be two taps from it.
        val ids = MenuCatalogue.ALL.map { it.id }
        for (gone in listOf("home", "ai", "signals", "community")) {
            assertTrue("$gone left the bar and has no menu row", gone in ids)
        }
    }
}
