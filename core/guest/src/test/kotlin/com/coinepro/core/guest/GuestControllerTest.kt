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
        val controller = GuestController(gateway, this)

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
        val controller = GuestController(gateway, this)

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
    fun `the shelf keeps its order while the feed reorders underneath it`() = runTest {
        val gateway = FakeGuestGateway(
            prices = AppResult.Success(
                GuestPrices(
                    listOf(
                        quote("SMALLUSDT", 1.0, volume = 10.0),
                        quote("BIGUSDT", 2.0, volume = 900.0),
                        quote("MIDUSDT", 3.0, volume = 400.0),
                    ),
                    stale = false, ageMillis = 10,
                ),
            ),
        )
        val controller = GuestController(gateway, this, visibleCount = 3)

        controller.refreshPrices(all = true)
        val first = (controller.prices.value as GuestPricesState.Ready).prices.quotes.map { it.symbol }

        // Busiest first on the first read, and the total is carried so the screen can say how many
        // markets there really are.
        assertEquals(listOf("BIGUSDT", "MIDUSDT", "SMALLUSDT"), first)

        // Now the feed answers in a different order. The shelf must not move.
        gateway.prices = AppResult.Success(
            GuestPrices(
                listOf(
                    quote("MIDUSDT", 3.5, volume = 4000.0),
                    quote("SMALLUSDT", 1.5, volume = 20.0),
                    quote("BIGUSDT", 2.5, volume = 10.0),
                ),
                stale = false, ageMillis = 10,
            ),
        )
        controller.refreshPrices()

        val second = (controller.prices.value as GuestPricesState.Ready).prices.quotes.map { it.symbol }
        assertEquals(first, second)
    }

    @Test
    fun `start polls once, not once per call`() = runTest {
        val gateway = FakeGuestGateway()
        val controller = GuestController(gateway, this, pollMillis = 10_000)

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

private fun quote(symbol: String, price: Double, volume: Double? = null) =
    GuestQuote(symbol, price, changePercent24h = null, high24h = null, low24h = null, volume24h = volume)

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

    override suspend fun trackRecord(limit: Int) =
        AppResult.Success(GuestTrackRecord(emptyList(), available = false))
}
