package com.coinepro.core.guest

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.marketdata.PriceTick
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guest's live price, which did not exist.
 *
 * «قیمت‌ها باید لحظه‌ای باشند» and «تایم‌فریم ۱۰ ثانیه تا ۵۰ ثانیه کار نمی‌کند» are one fault seen
 * from two screens: a guest was handed a catalogue read once and no feed at all, so prices stood
 * still and a sub-minute chart — which is built from ticks and nothing else — never drew a bar.
 *
 * What is pinned here is the three things the rest of the app depends on: that nothing is polled
 * when nothing is on screen, that a repeated snapshot is not a tick, and that a chart's symbol is
 * carried whatever the lists have asked for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuestPriceFeedTest {

    private fun feedOf(gateway: FeedGateway, scope: kotlinx.coroutines.CoroutineScope) =
        GuestPriceFeed(gateway, scope, nowMillis = gateway::clock)

    @Test
    fun `nothing is polled until a screen asks for something`() = runTest {
        val gateway = FeedGateway()
        feedOf(gateway, this)

        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(0, gateway.calls)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a list's symbols are polled and reach the map`() = runTest {
        val gateway = FeedGateway(price = 0.2111)
        val feed = feedOf(gateway, this)

        feed.subscribe(listOf("adausdt"))
        runCurrent()

        assertEquals(0.2111, feed.quotes.value.getValue("ADAUSDT").price, 0.0)
        assertTrue(gateway.calls >= 1)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a repeated snapshot is not a new quote`() = runTest {
        // The route answers from a cache, so most polls carry the price the last one did. Stamping
        // each with the clock would turn a flat market into a stream of ticks that never traded —
        // and on a ten-second chart that is a row of identical candles.
        val gateway = FeedGateway(price = 0.2111)
        val feed = feedOf(gateway, this)

        feed.subscribe(listOf("ADAUSDT"))
        runCurrent()
        val first = feed.quotes.value.getValue("ADAUSDT")

        gateway.clockMillis += 5_000
        advanceTimeBy(4_000)
        runCurrent()

        assertSame(first, feed.quotes.value.getValue("ADAUSDT"))
        assertTrue("the poll did run", gateway.calls > 1)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a moved price is a tick, with the instant it moved`() = runTest {
        val gateway = FeedGateway(price = 0.2111)
        val feed = feedOf(gateway, this)
        val ticks = mutableListOf<PriceTick>()
        val collector = launch {
            feed.chartTicks().ticks("ADAUSDT").toList(ticks)
        }
        runCurrent()

        gateway.price = 0.2127
        gateway.clockMillis += 1_000
        advanceTimeBy(1_500)
        runCurrent()

        assertEquals(listOf(0.2111, 0.2127), ticks.map { it.price })
        // Unix seconds, because that is what every bucket boundary in the chart is measured in.
        assertEquals(gateway.clockMillis / 1_000L, ticks.last().epochSeconds)
        collector.cancel()
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a chart's symbol is carried even when no list asked for it`() = runTest {
        val gateway = FeedGateway(price = 1.0)
        val feed = feedOf(gateway, this)
        feed.subscribe(listOf("BTCUSDT"))
        runCurrent()

        val collector = launch { feed.chartTicks().ticks("ADAUSDT").collect { } }
        runCurrent()

        assertTrue("ADAUSDT" in gateway.lastAsked)
        assertTrue("BTCUSDT" in gateway.lastAsked)

        // And released when the chart goes: a socket that carries an extra symbol costs nothing, a
        // poll that keeps asking for a chart nobody is looking at is a request a second, forever.
        collector.cancel()
        runCurrent()
        gateway.lastAsked = emptyList()
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(listOf("BTCUSDT"), gateway.lastAsked)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `an empty subscription stops the poll`() = runTest {
        val gateway = FeedGateway()
        val feed = feedOf(gateway, this)
        feed.subscribe(listOf("ADAUSDT"))
        runCurrent()
        val before = gateway.calls

        feed.subscribe(emptyList())
        advanceTimeBy(20_000)
        runCurrent()

        assertEquals(before, gateway.calls)
        coroutineContext.cancelChildren()
    }

    @Test
    fun `a failed poll leaves the price already on screen alone`() = runTest {
        val gateway = FeedGateway(price = 0.2111)
        val feed = feedOf(gateway, this)
        feed.subscribe(listOf("ADAUSDT"))
        runCurrent()

        gateway.fail = true
        advanceTimeBy(4_000)
        runCurrent()

        assertEquals(0.2111, feed.quotes.value.getValue("ADAUSDT").price, 0.0)
        assertNull(feed.quotes.value["ETHUSDT"])
        coroutineContext.cancelChildren()
    }
}

private class FeedGateway(
    var price: Double = 1.0,
    var fail: Boolean = false,
) : GuestGateway {
    var calls = 0
    var lastAsked: List<String> = emptyList()
    var clockMillis: Long = 1_700_000_000_000

    fun clock(): Long = clockMillis

    override suspend fun prices(symbols: List<String>): AppResult<GuestPrices> {
        calls++
        lastAsked = symbols
        if (fail) return offline()
        return AppResult.Success(
            GuestPrices(
                quotes = symbols.map { symbol ->
                    GuestQuote(
                        symbol = symbol,
                        price = price,
                        changePercent24h = null,
                        high24h = null,
                        low24h = null,
                        volume24h = null,
                    )
                },
                stale = false,
                ageMillis = 0,
            ),
        )
    }

    override suspend fun news(limit: Int): AppResult<List<GuestHeadline>> =
        AppResult.Success(emptyList())

    override suspend fun candles(
        symbol: String,
        timeframe: String,
        limit: Int,
    ): AppResult<GuestCandles> = offline()

    override suspend fun trackRecord(limit: Int): AppResult<GuestTrackRecord> = offline()

    override suspend fun community(): AppResult<GuestCommunity> = offline()

    override suspend fun membership(): AppResult<MembershipTerms> = offline()

    /** Everything this fake does not serve. The feed under test asks for none of it. */
    private fun <T> offline(): AppResult<T> = AppResult.Failure(ErrorKind.NETWORK)
}
