package com.coinepro.feature.chart

import com.coinepro.core.chart.ComparisonBasis
import com.coinepro.core.chart.MAX_COMPARISONS
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparing a second instrument: which requests are refused, and what a failed one costs.
 *
 * The refusal rules are asserted through [refuseComparison] as well as through the controller,
 * because the order they are checked in is itself a decision — see the function's own note.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartComparisonTest {

    private class FakeGateway(
        /** Symbols this venue refuses to serve, so a failed comparison can be exercised. */
        private val missing: Set<String> = emptySet(),
    ) : CandleGateway {
        val calls = mutableListOf<Pair<String, Timeframe>>()

        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage {
            calls += symbol to timeframe
            if (symbol in missing) throw IllegalStateException("unsupported_symbol")
            return CandlePage(symbol, timeframe, bars(timeframe))
        }

        private fun bars(timeframe: Timeframe) = (0 until 40).map { index ->
            OhlcBar(
                t = 1_700_000_000L + index * timeframe.seconds,
                o = 100.0,
                h = 101.0,
                l = 99.0,
                c = 100.0 + index,
                v = 3.0,
            )
        }
    }

    private fun controller(scope: TestScope, gateway: CandleGateway = FakeGateway()) =
        ChartController(symbol = "BTCUSDT", gateway = gateway, scope = scope)

    @Test
    fun `a symbol that is already on the chart is refused as such, not silently ignored`() {
        assertEquals(
            ComparisonRefusal.ALREADY_COMPARED,
            refuseComparison(base = "BTCUSDT", existing = listOf("ETHUSDT"), symbol = "ethusdt"),
        )
    }

    @Test
    fun `the chart's own symbol is refused, whatever case it is typed in`() {
        assertEquals(
            ComparisonRefusal.SAME_SYMBOL,
            refuseComparison(base = "BTCUSDT", existing = emptyList(), symbol = " btcusdt "),
        )
    }

    @Test
    fun `the fifth comparison is refused, and four is the cap the palette can carry`() {
        val four = listOf("ETHUSDT", "SOLUSDT", "XAUUSD", "EURUSD")
        assertEquals(4, MAX_COMPARISONS)
        assertEquals(
            ComparisonRefusal.LIMIT_REACHED,
            refuseComparison(base = "BTCUSDT", existing = four, symbol = "ADAUSDT"),
        )
    }

    @Test
    fun `at the cap, tapping the chart's own symbol is answered with the true reason`() {
        // Order of the checks, asserted rather than assumed: telling somebody to delete a
        // comparison first would send them off to fix a problem they do not have.
        val four = listOf("ETHUSDT", "SOLUSDT", "XAUUSD", "EURUSD")
        assertEquals(
            ComparisonRefusal.SAME_SYMBOL,
            refuseComparison(base = "BTCUSDT", existing = four, symbol = "BTCUSDT"),
        )
    }

    @Test
    fun `a blank symbol is refused as blank rather than as a duplicate of nothing`() {
        assertEquals(
            ComparisonRefusal.BLANK,
            refuseComparison(base = "BTCUSDT", existing = emptyList(), symbol = "   "),
        )
    }

    @Test
    fun `a symbol that is none of those three is accepted`() {
        assertNull(refuseComparison(base = "BTCUSDT", existing = listOf("ETHUSDT"), symbol = "XAUUSD"))
    }

    @Test
    fun `a loaded comparison is aligned to the base bar count, exactly`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        assertNull(controller.addComparison("ETHUSDT"))
        advanceUntilIdle()

        val comparison = controller.state.value.comparisons.single()
        assertEquals("ETHUSDT", comparison.symbol)
        // One value per base bar. A comparison that changed the chart's own geometry would move
        // every drawing anchored to a bar index.
        assertEquals(controller.state.value.series.size, comparison.size)
    }

    @Test
    fun `a comparison that will not load is dropped, and the chart it was added to is untouched`() = runTest {
        val gateway = FakeGateway(missing = setOf("XAUUSD"))
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)), gateway)
        controller.start()
        advanceUntilIdle()
        val barsBefore = controller.state.value.series.size

        assertNull(controller.addComparison("XAUUSD"))
        advanceUntilIdle()

        assertTrue(controller.state.value.comparisons.isEmpty())
        assertEquals(barsBefore, controller.state.value.series.size)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `removing a comparison takes it off and re-colours the ones that are left`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        controller.addComparison("ETHUSDT")
        advanceUntilIdle()
        controller.addComparison("SOLUSDT")
        advanceUntilIdle()
        val secondColour = controller.state.value.comparisons[1].colour

        controller.removeComparison("ethusdt")
        advanceUntilIdle()

        val left = controller.state.value.comparisons.single()
        assertEquals("SOLUSDT", left.symbol)
        // It moved into the first slot, so it takes the first slot's colour: the palette keeps
        // lines apart by position, and it can only do that if it is reassigned.
        assertTrue(left.colour != secondColour)
    }

    @Test
    fun `changing the interval fetches every comparison again on the new grid`() = runTest {
        val gateway = FakeGateway()
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)), gateway)
        controller.start()
        advanceUntilIdle()
        controller.addComparison("ETHUSDT")
        advanceUntilIdle()

        controller.setTimeframe(Timeframe.D1)
        advanceUntilIdle()

        // Re-aligning the hourly closes across daily bars would draw a line that is wrong
        // everywhere and looks plausible, so the comparison is refetched instead.
        assertTrue(gateway.calls.contains("ETHUSDT" to Timeframe.D1))
        assertEquals("ETHUSDT", controller.state.value.comparisons.single().symbol)
    }

    @Test
    fun `the basis starts at percent and changes without touching the loaded series`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        controller.addComparison("ETHUSDT")
        advanceUntilIdle()
        val before = controller.state.value.comparisons

        assertEquals(ComparisonBasis.PERCENT, controller.state.value.comparisonBasis)
        controller.setComparisonBasis(ComparisonBasis.RATIO)

        assertEquals(ComparisonBasis.RATIO, controller.state.value.comparisonBasis)
        assertEquals(before, controller.state.value.comparisons)
    }
}
