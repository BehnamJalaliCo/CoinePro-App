package com.coinepro.feature.signaldetail

import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview loader behind the signal screen.
 *
 * Everything here is about *not* getting in the way. This chart is decoration on a screen whose
 * numbers are already right, so the interesting cases are the quiet ones: a symbol the platform
 * does not carry, a network that dropped, and the same signal being looked at twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignalChartControllerTest {

    private fun bars(count: Int, step: Long = 3_600L): List<OhlcBar> =
        (0 until count).map { index ->
            val base = 100.0 + index
            OhlcBar(
                t = 1_700_000_000L + index * step,
                o = base,
                h = base + 1,
                l = base - 1,
                c = base + 0.5,
                v = 10.0,
                closed = true,
            )
        }

    private class FakeGateway(
        private val answer: (String, Timeframe, Int, Long?) -> CandlePage,
    ) : CandleGateway {
        var calls = 0
            private set
        var lastTimeframe: Timeframe? = null
            private set
        var lastLimit: Int = 0
            private set

        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage {
            calls++
            lastTimeframe = timeframe
            lastLimit = limit
            return answer(symbol, timeframe, limit, before)
        }
    }

    private fun page(symbol: String, timeframe: Timeframe, candles: List<OhlcBar>) = CandlePage(
        symbol = symbol,
        timeframe = timeframe,
        candles = candles,
        oldest = candles.firstOrNull()?.t,
        hasMore = false,
        limitMax = 1_000,
    )

    @Test
    fun `a signal's own timeframe is what gets loaded`() = runTest {
        val gateway = FakeGateway { symbol, timeframe, _, _ -> page(symbol, timeframe, bars(50)) }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("XAUUSD", "H4")

        assertEquals(Timeframe.H4, gateway.lastTimeframe)
        assertEquals(50, controller.state.value.series.size)
        assertFalse(controller.state.value.loading)
        assertFalse(controller.state.value.failed)
    }

    @Test
    fun `TradeYar's reversed spelling is understood`() = runTest {
        val gateway = FakeGateway { symbol, timeframe, _, _ -> page(symbol, timeframe, bars(10)) }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("BTCUSDT", "15m")

        assertEquals(Timeframe.M15, gateway.lastTimeframe)
    }

    @Test
    fun `an unknown timeframe falls back rather than showing nothing`() = runTest {
        // A signal carrying a timeframe this app does not offer — or none at all — is still a
        // signal on a symbol with bars. Refusing to draw them would hide real data over a label.
        val gateway = FakeGateway { symbol, timeframe, _, _ -> page(symbol, timeframe, bars(10)) }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("EURUSD", "H12")
        assertEquals(Timeframe.H1, gateway.lastTimeframe)

        controller.clear()
        controller.load("EURUSD", null)
        assertEquals(Timeframe.H1, gateway.lastTimeframe)
    }

    @Test
    fun `a failure leaves an empty series and says so, without throwing`() = runTest {
        val gateway = FakeGateway { _, _, _, _ -> throw IllegalStateException("TYR-021 unsupported_symbol") }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("NOSUCH", "H1")

        assertTrue(controller.state.value.series.isEmpty)
        assertTrue(controller.state.value.failed)
        assertFalse(controller.state.value.loading)
    }

    @Test
    fun `a failed load is attempted again on the next visit`() = runTest {
        // The opposite of the success case below, and the reason the memo is cleared on failure: a
        // reader who backs out of a signal and opens it again has asked for another try.
        var fail = true
        val gateway = FakeGateway { symbol, timeframe, _, _ ->
            if (fail) throw IllegalStateException("network") else page(symbol, timeframe, bars(5))
        }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("XAUUSD", "H1")
        assertTrue(controller.state.value.failed)

        fail = false
        controller.load("XAUUSD", "H1")

        assertEquals(2, gateway.calls)
        assertEquals(5, controller.state.value.series.size)
    }

    @Test
    fun `the same symbol and timeframe is not fetched twice`() = runTest {
        // The screen's LaunchedEffect re-runs on recomposition keys that include the controller
        // itself, so this guard is what stops a request per configuration change.
        val gateway = FakeGateway { symbol, timeframe, _, _ -> page(symbol, timeframe, bars(20)) }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("XAUUSD", "H1")
        controller.load("XAUUSD", "H1")
        controller.load("XAUUSD", "H1")

        assertEquals(1, gateway.calls)
    }

    @Test
    fun `a different signal reloads`() = runTest {
        val gateway = FakeGateway { symbol, timeframe, _, _ -> page(symbol, timeframe, bars(20)) }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("XAUUSD", "H1")
        controller.load("XAGUSD", "H1")
        controller.load("XAGUSD", "D1")

        assertEquals(3, gateway.calls)
    }

    @Test
    fun `an empty page is a failure rather than an empty chart`() = runTest {
        // A server that answers 200 with no bars is the same outcome for a reader as one that did
        // not answer: there is nothing to draw. Reported the same way, so the card stays away
        // rather than rendering an axis over blank space.
        val gateway = FakeGateway { symbol, timeframe, _, _ -> page(symbol, timeframe, emptyList()) }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("XAUUSD", "H1")

        assertTrue(controller.state.value.failed)
        assertTrue(controller.state.value.series.isEmpty)
    }

    @Test
    fun `clear forgets what was loaded so the next visit fetches`() = runTest {
        val gateway = FakeGateway { symbol, timeframe, _, _ -> page(symbol, timeframe, bars(20)) }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("XAUUSD", "H1")
        controller.clear()
        assertTrue(controller.state.value.series.isEmpty)

        controller.load("XAUUSD", "H1")
        assertEquals(2, gateway.calls)
    }

    @Test
    fun `the preview asks for fewer bars than the chart screen does`() = runTest {
        val gateway = FakeGateway { symbol, timeframe, _, _ -> page(symbol, timeframe, bars(20)) }
        val controller = SignalChartController(gateway, TestScope(UnconfinedTestDispatcher(testScheduler)))

        controller.load("XAUUSD", "H1")

        assertTrue(
            "asked for ${gateway.lastLimit}, which is not less than the gateway default",
            gateway.lastLimit < CandleGateway.DEFAULT_LIMIT,
        )
    }
}
