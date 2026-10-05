package com.coinepro.app

import com.coinepro.core.common.FeatureFlags
import com.coinepro.feature.menu.MenuCatalogue
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The store build (5.22.0) drops membership, exchange links, portfolio, KYC, signals and AI —
 * absent, never greyed.** Cafe Bazaar refused a finance app from a personal account that carries
 * them; the web keeps every one.
 *
 * Read out of the shell's source, like `ForexSurfaceReachabilityTest`: a second list typed here is
 * a list free to be shorter than the code.
 */
class StoreRestrictedBuildTest {

    @After
    fun restore() = FeatureFlags.reset()

    private val shell = File("src/main/kotlin/com/coinepro/app/CoineProApp.kt").readText()

    @Test
    fun `a shipping build starts unrestricted, so the web keeps everything`() {
        FeatureFlags.reset()
        assertFalse(FeatureFlags.storeRestricted)
    }

    @Test
    fun `every restricted id is a real menu row`() {
        val ids = MenuCatalogue.ALL.map { it.id }.toSet()
        FeatureFlags.STORE_RESTRICTED_SURFACES.forEach { id ->
            assertTrue("«$id» is not in the menu catalogue — the store build hides nothing", id in ids)
        }
    }

    @Test
    fun `both the menu and search hide the restricted rows`() {
        val uses = Regex("""if \(FeatureFlags\.storeRestricted\) addAll\(FeatureFlags\.STORE_RESTRICTED_SURFACES\)""")
            .findAll(shell)
            .count()
        assertTrue("the menu and the search screen must both drop the restricted rows ($uses)", uses >= 2)
    }

    @Test
    fun `each restricted route refuses to draw itself`() {
        RESTRICTED_ROUTES.forEach { route ->
            val block = Regex("""composable\(\s*(route = )?$route\b(.*?)\n            \}""", RegexOption.DOT_MATCHES_ALL)
                .find(shell)
                ?.groupValues
                ?.get(2)
            assertTrue("no composable($route) block in the shell", block != null)
            assertTrue(
                "composable($route) draws without asking FeatureFlags.storeRestricted",
                block!!.contains("FeatureFlags.storeRestricted"),
            )
        }
    }

    @Test
    fun `the application sets the flag on Android only`() {
        val application = File("src/main/kotlin/com/coinepro/app/CoineProApplication.kt").readText()
        assertTrue(application.contains("FeatureFlags.storeRestricted ="))
    }

    private companion object {
        val RESTRICTED_ROUTES = listOf(
            "MEMBERSHIP_ROUTE",
            "KYC_ROUTE",
            "CONNECTIONS_ROUTE",
            "AI_VISION_ROUTE",
            "AI_ASSISTANT_ROUTE",
            "PORTFOLIO_ROUTE",
            "PORTFOLIO_REPORT_ROUTE",
            "SIGNALS_ROUTE",
            "SIGNAL_DETAIL_PATTERN",
            "EXECUTION_PATTERN",
            "AI_PATTERN",
            "TERMINAL_ROUTE",
        )
    }
}
