package com.coinepro.feature.chart

import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Two venues on one chart, and the rule for when the caption names both.
 *
 * The forex chart draws MetaTrader 5's bars with Finnhub's last price on top of them. Until 5.0.2
 * the caption read «منبع قیمت: MetaTrader 5» — the *price* source — and named only the gateway.
 * `CandleGateway.sourceName`'s own KDoc says why that matters: the label exists so a reader who
 * suspects «کندل‌سازی» can hold this chart against the venue's own. One who did that against
 * MetaTrader 5's prices was checking a feed this screen was not showing them.
 */
class ChartQuoteVenueTest {

    private class Bars(override val sourceName: String) : CandleGateway {
        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage = CandlePage(symbol, timeframe, emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.controller(barsFrom: String) = ChartController(
        "XAUUSD",
        Bars(barsFrom),
        TestScope(StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `a price from a different venue is named beside the bars'`() = runTest {
        val controller = controller(barsFrom = "MetaTrader 5")
        controller.noteQuoteVenue("XAUUSD", "Finnhub")
        assertEquals("Finnhub", controller.state.value.quoteSourceName)
    }

    @Test
    fun `a price from the same venue as the bars is not named twice`() = runTest {
        // Crypto: LBank's candles under LBank's price. Two identical names is a line that costs
        // glass and settles nothing, on the screen this app has least room on.
        val controller = controller(barsFrom = "LBank")
        controller.noteQuoteVenue("XAUUSD", "LBank")
        assertEquals("", controller.state.value.quoteSourceName)
    }

    @Test
    fun `a feed that names no venue leaves the caption alone`() = runTest {
        // `QuoteSource.UNKNOWN` maps to an empty name on purpose: a price whose origin the app
        // does not recognise is shown silently rather than labelled with a guess.
        val controller = controller(barsFrom = "MetaTrader 5")
        controller.noteQuoteVenue("XAUUSD", "")
        assertEquals("", controller.state.value.quoteSourceName)
    }

    @Test
    fun `a tick for another market cannot rename this one's price`() = runTest {
        val controller = controller(barsFrom = "MetaTrader 5")
        controller.noteQuoteVenue("BTCUSDT", "LBank")
        assertEquals("", controller.state.value.quoteSourceName)
    }
}
