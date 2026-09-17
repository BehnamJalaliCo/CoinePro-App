package com.coinepro.app

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.navigation.AppDestination
import com.coinepro.feature.menu.MenuCatalogue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Doctrine D4 — **one layer**: nothing is more than one layer away from the chart.
 *
 * ### What "one layer" is measured as
 *
 * Two navigations. From the chart, a reader reaches the menu — which is on every screen — and from
 * the menu they reach the surface. That is the app's actual shape, and it is the reason the bottom
 * bar could shrink: the menu is the layer, and it is one tap deep from everywhere.
 *
 * So the rule this file holds is: **every route the graph declares is either a destination of the
 * menu, of the bottom bar, or of a screen that is itself one of those** — and anything else has to
 * be named, with a reason, in [DEEPER_ON_PURPOSE].
 *
 * ### Why the routes are read out of the source
 *
 * Because the alternative is a second list of them, and a second list is one that can be shorter
 * than the graph without anything failing — which is exactly the hole D4 exists to close. The
 * `composable(route = …)` calls in `CoineProApp.kt` are the graph; this reads them, the same way
 * `ReferenceDocsTest` reads `Builtins.kt` rather than trusting a table beside it.
 *
 * ### What it cannot do
 *
 * It cannot prove a *button* exists on a screen. A route in the menu's table is reachable by
 * construction; a route reached only from a card somewhere is trusted to the exemption list, where
 * it has to carry a sentence. That is the same bargain `MenuRouteCoverageTest` makes, and the
 * failure it catches is the one that actually happens: a screen added to the graph and reachable
 * from nothing.
 */
class NavigationDepthTest {

    private val watchlist = listOf("BTCUSDT")

    /** Every `composable(route = …)` the shell declares, as the constant name it was given. */
    private fun declaredRoutes(): Set<String> {
        val source = File("src/main/kotlin/com/coinepro/app/CoineProApp.kt").readText()
        val direct = Regex("""composable\(\s*(?:route\s*=\s*)?([A-Z][A-Z0-9_]*)\s*[,)]""")
            .findAll(source).map { it.groupValues[1] }
        val inBlock = Regex("""composable\(\s*\n\s*route\s*=\s*([A-Z][A-Z0-9_]*)\s*,""")
            .findAll(source).map { it.groupValues[1] }
        return (direct + inBlock).toSet()
    }

    /** The value of a `private const val NAME = "…"` in the shell, by name. */
    private fun routeValues(): Map<String, String> {
        val source = File("src/main/kotlin/com/coinepro/app/CoineProApp.kt").readText()
        return Regex("""(?:private |internal )?const val ([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"""")
            .findAll(source)
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    /** Everything the menu can send a reader to, for the two platforms the app ships. */
    private fun menuDestinations(): Set<String> =
        MarketPlatform.entries.flatMap { platform ->
            MenuCatalogue.ALL.map { entry -> menuRoute(entry.id, platform, watchlist) }
        }.toSet()

    @Test
    fun `the graph is read, and it is not empty`() {
        // The guard on the guard: a regex that stopped matching would turn this whole file into a
        // test that passes because it is looking at nothing.
        val routes = declaredRoutes()
        assertTrue("no composable routes were found — the reader broke, not the graph", routes.size >= 30)
        val values = routeValues()
        assertTrue("no route constants were found", values.size >= 30)
    }

    /**
     * A route without its query.
     *
     * `AI_ROUTE` is `"ai"` and the graph registers `AI_PATTERN`, `"ai?symbol={symbol}"`. They are
     * one screen: the menu navigates to the first and Navigation matches it against the second.
     * Comparing the two as strings says the screen is unreachable, which is a fact about the
     * comparison and not about the app.
     */
    private fun withoutQuery(route: String): String = route.substringBefore('?')

    @Test
    fun `every declared route is one layer from the chart`() {
        val values = routeValues()
        val reachable = (menuDestinations() + bottomBarRoutes()).map(::withoutQuery).toSet()
        val orphans = declaredRoutes()
            .filterNot { it in DEEPER_ON_PURPOSE }
            .mapNotNull { name -> values[name]?.let { name to it } }
            .filterNot { (_, route) -> withoutQuery(route) in reachable }
            .map { (name, route) -> "$name ($route)" }
            .sorted()
        assertEquals(
            "routes the menu and the bar cannot reach — add the button, or name it in " +
                "DEEPER_ON_PURPOSE with the reason",
            emptyList<String>(),
            orphans,
        )
    }

    @Test
    fun `every exemption is a route that exists`() {
        // An exemption list is a place for a name to rot. A route deleted from the graph leaves its
        // excuse behind, and the next reader takes the list as a description of the app.
        val declared = declaredRoutes()
        val stale = DEEPER_ON_PURPOSE.filterNot { it in declared }.sorted()
        assertEquals("exemptions for routes that no longer exist", emptyList<String>(), stale)
    }

    @Test
    fun `every exemption carries a reason`() {
        // The reasons are in this file, beside the list. The test is that the list and the reasons
        // are the same length — an entry added without a line is an entry nobody had to justify.
        assertEquals(REASONS.keys, DEEPER_ON_PURPOSE)
        for ((name, reason) in REASONS) {
            assertTrue("$name's reason says nothing", reason.length >= 24)
        }
    }

    @Test
    fun `the menu reaches every surface the search screen offers`() {
        // D4's other half: the two catalogues name the same ids on purpose, so a surface that is
        // searchable and not in the menu is one a reader can find by typing and not by looking.
        val platform = MarketPlatform.entries.first()
        val menu = MenuCatalogue.ALL.map { menuRoute(it.id, platform, watchlist) }.toSet()
        val searchable = com.coinepro.feature.search.AppSurfaces.ALL
            .map { surfaceRoute(it.id, platform, watchlist) }
            .toSet()
        val unreachable = (searchable - menu).sorted()
        assertEquals("searchable surfaces the menu cannot reach", emptyList<String>(), unreachable)
    }

    /**
     * **U7 — the bar is the five the owner named, and nothing lost its way in** (run ΤΦΥ).
     *
     * The bar became دیده‌بان · چارت · رَصد · انجمن · منو, which cost Explore its seat. That is the
     * change worth a test of its own: a destination removed from a bar is the easiest way in this
     * app to make a screen unreachable, because the bar is the one place a route needs nothing to
     * link to it.
     *
     * So this pins the set *and* the consequence — Explore and its three rooms are still one layer
     * away — rather than only the set, which would pass on a build that had quietly orphaned them.
     */
    @Test
    fun `the bar is five destinations and Explore's rooms survived losing theirs`() {
        assertEquals(
            listOf("watchlist", "chart-tab", "home", "ideas", "menu"),
            AppDestination.entries.map { it.route },
        )
        val values = routeValues()
        val reachable = (menuDestinations() + bottomBarRoutes()).map(::withoutQuery).toSet()
        // Explore itself, and the three screens it used to be the only door to. The markets
        // surface draws all three above its tabs — see `ExploreDoors` — and this is the part of
        // that claim a unit test can hold: they are still in the menu's reach, so a reader who
        // never finds the row still has a way.
        val rooms = listOf("EXPLORE_ROUTE", "NEWS_ROUTE", "CALENDAR_ROUTE", "HEATMAP_ROUTE")
        val lost = rooms
            .mapNotNull { name -> values[name]?.let { name to it } }
            .filterNot { (_, route) -> withoutQuery(route) in reachable }
            .map { (name, route) -> "$name ($route)" }
        assertEquals("Explore's rooms are unreachable now that its tab is gone", emptyList<String>(), lost)
    }

    /** The bottom bar's own destinations: zero steps, by definition. */
    private fun bottomBarRoutes(): Set<String> = AppDestination.entries.map { it.route }.toSet()

    private companion object {
        /**
         * Routes that are more than one layer away, each for a reason.
         *
         * The list is short on purpose. It is not a place to put a screen that nobody got round to
         * linking; every line here says why the route is *correctly* deeper — because it needs an
         * argument that only its parent has, or because it is the second half of a flow.
         */
        val REASONS: Map<String, String> = mapOf(
            "SIGNAL_DETAIL_PATTERN" to "One signal, which only the signal list can name.",
            "EXECUTION_PATTERN" to "The second half of a signal: it needs the signal it is executing.",
            "LESSON_PATTERN" to "One lesson, which only the academy's own list can name.",
            "COMMUNITY_THREAD_PATTERN" to "One thread, named by the community list.",
            "CHART_PATTERN" to "The chart itself — the thing everything else is measured from.",
            "STUDIO_PATTERN" to "Opens on the chart's own symbol, so it is reached from the chart.",
            "PANES_PATTERN" to "Two charts, on the symbol the first one had.",
            "DOM_PATTERN" to "The depth ladder, on the symbol in front of the reader.",
            "SCRIPT_PATTERN" to "The studio on a symbol; the menu reaches it as «backtest».",
            "MARKET_SEARCH_ROUTE" to "The search field itself, which is in the bar on every screen.",
            "PORTFOLIO_REPORT_ROUTE" to "The report on a portfolio: the second half of that screen.",
            "TERMS_ROUTE" to
                "Reached from the legal screen and from sign-up, where a reader is already being " +
                "asked to agree to it. A menu row for the terms would be the app inviting somebody " +
                "to go and read them, which nobody does and which would push a real surface down.",
            "PRIVACY_ROUTE" to "The other legal document, reached the same way and for the same reason.",
            "ADMIN_ROUTE" to
                "Diagnostics, which is not in the store build's menu at all — see the admin-panel " +
                "exclusion. A reader cannot reach it because it is not for them.",
        )

        val DEEPER_ON_PURPOSE: Set<String> = REASONS.keys
    }
}
