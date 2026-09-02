package com.coinepro.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bottom bar's identity, pinned in the module that owns it.
 *
 * The cross-phase gate already reads this file as text; this reads it as code, and the two catch
 * different things — a route renamed to a duplicate compiles and passes a regex over the source,
 * and would send two tabs to the same destination.
 */
class AppDestinationTest {

    @Test
    fun `every route is unique`() {
        val routes = AppDestination.entries.map { it.route }
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun `no route collides with the chart's own symbol route`() {
        // The chart tab is deliberately "chart-tab": "chart" belongs to the chart *of a symbol* and
        // has since the route existed. Giving the two the same name would break every saved back
        // stack that holds one.
        assertTrue(AppDestination.entries.none { it.route == "chart" })
    }

    @Test
    fun `routes are lower-case and free of spaces and slashes`() {
        // They are path segments in a deep link. A capital letter or a space is a route that works
        // in the app and fails from a browser.
        for (destination in AppDestination.entries) {
            assertEquals(destination.route.lowercase(), destination.route)
            assertTrue(destination.route.none { it == ' ' || it == '/' })
        }
    }

    @Test
    fun `the bar holds six destinations`() {
        // Six, with the community board. It was five as a layout constant — Persian labels at a
        // fixed type size — and the board went into the menu instead, where the owner reported it
        // as absent. The bar's label style is `labelSmall`, which fits six at the narrowest width
        // this app supports; `screenshot` fixtures cover it.
        assertEquals(6, AppDestination.entries.size)
    }

    @Test
    fun `home is first`() {
        assertEquals(AppDestination.HOME, AppDestination.entries.first())
    }
}
