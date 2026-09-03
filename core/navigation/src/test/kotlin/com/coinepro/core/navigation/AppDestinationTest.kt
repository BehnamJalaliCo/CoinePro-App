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
    fun `the bar holds exactly five destinations`() {
        // Five, and the number is the point rather than a layout constraint. It held six, and
        // every one of them was a real screen — which is how a bottom bar becomes a catalogue of
        // the modules the app contains instead of the jobs somebody opens it to do. Six fitted at
        // `labelSmall`; that it fitted was never the argument for it.
        assertEquals(5, AppDestination.entries.size)
    }

    @Test
    fun `the watchlist is first`() {
        // Where a reader lands when they have no other question, and until this release a
        // sub-screen two taps down behind a menu glyph.
        assertEquals(AppDestination.WATCHLIST, AppDestination.entries.first())
    }

    @Test
    fun `the roots that left the bar did not take their routes with them`() {
        // Removing a destination from the bar removes it from the bar and nothing else: `home`,
        // `signals`, `ai` and `community` are still screens in the graph, still resolve from a
        // saved back stack written by an older build, and are still reachable from the menu or
        // the Ideas tab. This pins the *other* half of that — none of the four may quietly come
        // back as a tab without somebody deciding to.
        val routes = AppDestination.entries.map { it.route }
        for (gone in listOf("home", "signals", "ai", "community")) {
            assertTrue("$gone is a route, not a tab", gone !in routes)
        }
    }
}
