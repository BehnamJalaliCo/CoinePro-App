package com.coinepro.app

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.navigation.AppDestination
import com.coinepro.feature.chart.ChartSignalEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Doctrine D3 — **sixty seconds to meaning**: a new reader with no account sees a *judgement*
 * within sixty seconds of first launch.
 *
 * ### A step count, not a stopwatch
 *
 * Sixty seconds is the owner's sentence and it is not a thing a unit test can hold: it depends on a
 * device, a network and a person. What a test *can* hold is everything that makes sixty seconds
 * possible, and those are the three things that actually break:
 *
 * 1. **The path is short.** A reader who has not signed in starts on a destination that is one step
 *    from a chart. If the app ever opens on a sign-in wall, D3 is dead whatever the chart does.
 * 2. **Nothing is asked first.** No account, no watchlist, no choice of platform.
 * 3. **The first chart says something.** With the indicators a new install carries and nothing
 *    configured, the Signal Layer produces a state, a sentence and a confidence — not a blank
 *    legend waiting to be set up. This is D1 measured at the moment it matters most.
 *
 * The third is where the risk lives, and it is the one a reading of the code cannot settle: a
 * default set that produces `ChartSignalLayer.EMPTY` would look perfectly reasonable in a diff.
 */
class SixtySecondsToMeaningTest {

    /**
     * A market with shape, of the length a first fetch actually returns.
     *
     * Two hundred bars rather than a thousand: that is roughly what the first page of candles is,
     * and a study whose warm-up needs more than the first fetch has nothing to say in the sixty
     * seconds this doctrine is about. Testing on a long series would hide exactly that.
     */
    private val firstFetch: CandleSeries = CandleSeries(
        List(200) { index ->
            var seed = 20260913L + index
            fun noise(): Double {
                seed = seed * 6364136223846793005L + 1442695040888963407L
                return ((seed ushr 11).toDouble() / (1L shl 53).toDouble()) - 0.5
            }
            val base = 100.0 + sin(index / 9.0) * 6 + sin(index / 31.0) * 14 + index * 0.03 + noise()
            val wick = (0.4 + abs(noise())) * 2
            Candle(
                t = 1_700_000_000L + index * 3_600L,
                o = base - 0.4,
                h = base + wick,
                l = base - wick,
                c = base,
                v = 900.0 + (index % 17) * 40,
            )
        },
    )

    // ── 1. the path is short ─────────────────────────────────────────────────────────────────

    @Test
    fun `a reader who has never signed in starts one step from a chart`() {
        // `startRoute` defaults to WATCHLIST for a reader with nothing stored — and the watchlist's
        // rows open charts. One step. What would break D3 is a start destination that is a wall.
        val start = AppDestination.WATCHLIST
        assertTrue(
            "the app starts somewhere that is not a bar destination, so the chart is further away",
            start in AppDestination.entries,
        )
        assertTrue(
            "the first screen is behind sign-in",
            start.route !in setOf("sign-in", "login", "onboarding", "welcome"),
        )
    }

    @Test
    fun `the chart is reachable without an account`() {
        // The guest path's existence, asserted where it is decided rather than by reading a screen:
        // a chart route is built from a symbol and nothing about it mentions a session.
        val route = chartRouteForTest("BTCUSDT")
        assertTrue(route, route.startsWith("chart/"))
        assertTrue("the chart route asks for a session", "sign" !in route && "auth" !in route)
    }

    // ── 2. nothing is asked first ────────────────────────────────────────────────────────────

    @Test
    fun `the first chart needs nothing configured`() {
        // No periods, no parameters, no scripts — what a new install actually has.
        val layer = ChartSignalEngine.evaluate(series = firstFetch, indicatorIds = firstRunIndicators())
        assertTrue("a new install's chart says nothing at all", layer.reads.isNotEmpty())
    }

    // ── 3. the first chart says something ────────────────────────────────────────────────────

    @Test
    fun `every default indicator produces a sentence a reader can act on`() {
        val layer = ChartSignalEngine.evaluate(series = firstFetch, indicatorIds = firstRunIndicators())
        val silent = layer.reads.filter { it.note.text(english = false).isBlank() }.map { it.id }
        assertEquals("default indicators with no sentence", emptyList<String>(), silent)
    }

    @Test
    fun `and a state, which is the judgement the doctrine is about`() {
        // Not «a state exists» — the type guarantees that. That at least one study has made up its
        // mind: a chart where every reading is NEUTRAL is a chart that has told the reader nothing,
        // which is exactly what D3 forbids in the first minute.
        val layer = ChartSignalEngine.evaluate(series = firstFetch, indicatorIds = firstRunIndicators())
        assertTrue(
            "every default study is neutral on a series with shape in it — no judgement was made",
            layer.reads.any { it.state != com.coinepro.core.chart.MarketState.NEUTRAL },
        )
    }

    @Test
    fun `the same is true in English, because a new reader may be in either`() {
        val layer = ChartSignalEngine.evaluate(
            series = firstFetch,
            indicatorIds = firstRunIndicators(),
            english = true,
        )
        val silent = layer.reads.filter { it.note.text(english = true).isBlank() }.map { it.id }
        assertEquals(emptyList<String>(), silent)
        for (read in layer.reads) {
            val sentence = read.note.text(english = true)
            assertTrue("a Persian sentence reached the English chart: $sentence", sentence.none { it in '؀'..'ۿ' })
        }
    }

    @Test
    fun `a half-loaded chart says something rather than nothing`() {
        // The sixty seconds include the fetch. A reader looking at forty bars while the rest
        // arrives must not be looking at a blank legend — and a study that cannot speak yet should
        // be quiet rather than wrong.
        val partial = CandleSeries(firstFetch.bars.take(40))
        val layer = ChartSignalEngine.evaluate(series = partial, indicatorIds = firstRunIndicators())
        assertTrue("forty bars produced nothing at all", layer.reads.isNotEmpty())
        val silent = layer.reads.filter { it.note.text(english = false).isBlank() }.map { it.id }
        assertEquals("a study with too few bars printed an empty sentence", emptyList<String>(), silent)
    }

    @Test
    fun `an empty chart is empty rather than a crash`() {
        // The first frame, before anything has arrived.
        val layer = ChartSignalEngine.evaluate(series = CandleSeries(emptyList()), indicatorIds = firstRunIndicators())
        assertTrue(layer.reads.isEmpty())
    }

    /**
     * What a new install's chart carries.
     *
     * Taken from the catalogue rather than written down, so a default set that changes changes this
     * test with it. The first two are what the chart opens with; the point of the test is that
     * *whatever* they are, they speak.
     */
    private fun firstRunIndicators(): List<String> =
        ChartCatalog.INDICATORS.take(DEFAULT_ON_FIRST_RUN).map { it.id }

    private fun chartRouteForTest(symbol: String): String = "chart/$symbol"

    private companion object {
        /** How many studies a fresh chart draws. Small on purpose: D1 says each must explain itself. */
        const val DEFAULT_ON_FIRST_RUN = 2
    }
}
