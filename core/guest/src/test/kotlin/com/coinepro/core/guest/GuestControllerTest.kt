package com.coinepro.core.guest

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuestControllerTest {

    @Test
    fun `a failed poll leaves the numbers already on screen alone`() = runTest {
        val gateway = FakeGuestGateway(
            prices = AppResult.Success(GuestPrices(listOf(quote("BTCUSDT", 64_000.0)), stale = false, ageMillis = 120)),
        )
        val controller = GuestController(gateway, this, symbols = listOf("BTCUSDT"))

        controller.refreshPrices()
        gateway.prices = AppResult.Failure(ErrorKind.NETWORK)
        controller.refreshPrices()

        // Still the price, not an error. A list that becomes a failure message because one poll in
        // ten timed out is a list nobody can watch.
        val state = controller.prices.value
        assertTrue(state is GuestPricesState.Ready)
        assertEquals(64_000.0, (state as GuestPricesState.Ready).prices.quotes.single().price, 0.0)
    }

    @Test
    fun `a first failure with nothing to keep does say so`() = runTest {
        val gateway = FakeGuestGateway(prices = AppResult.Failure(ErrorKind.NETWORK))
        val controller = GuestController(gateway, this, symbols = listOf("BTCUSDT"))

        controller.refreshPrices()

        assertTrue(controller.prices.value is GuestPricesState.Unavailable)
    }

    @Test
    fun `an English-only headline is dropped rather than shown to a Persian reader`() = runTest {
        val gateway = FakeGuestGateway(
            news = AppResult.Success(
                listOf(GuestHeadline("a", "بیت‌کوین بالاتر رفت", null, "TradeYar", null)),
            ),
        )
        val controller = GuestController(gateway, this)

        controller.refreshNews()
        runCurrent()

        val state = controller.news.value
        assertTrue(state is GuestNewsState.Ready)
        assertEquals(1, (state as GuestNewsState.Ready).headlines.size)
    }

    @Test
    fun `start polls once, not once per call`() = runTest {
        val gateway = FakeGuestGateway()
        val controller = GuestController(gateway, this, symbols = listOf("BTCUSDT"), pollMillis = 10_000)

        controller.start()
        controller.start()
        controller.start()
        runCurrent()
        controller.stop()

        // Three starts, one pass. A screen that starts a job per recomposition triples the request
        // rate for as long as it is open, and the only place that shows up is the server's bill.
        assertEquals(1, gateway.priceCalls)
    }
}

private fun quote(symbol: String, price: Double) =
    GuestQuote(symbol, price, changePercent24h = null, high24h = null, low24h = null, volume24h = null)

private class FakeGuestGateway(
    var prices: AppResult<GuestPrices> = AppResult.Success(GuestPrices(emptyList(), stale = true, ageMillis = null)),
    var news: AppResult<List<GuestHeadline>> = AppResult.Success(emptyList()),
) : GuestGateway {
    var priceCalls = 0

    override suspend fun prices(symbols: List<String>): AppResult<GuestPrices> {
        priceCalls++
        return prices
    }

    override suspend fun news(limit: Int): AppResult<List<GuestHeadline>> = news
}
