package com.coinepro.app.brief

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.MorningBrief
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.guest.GuestCandle
import com.coinepro.core.guest.GuestCandles
import com.coinepro.core.guest.GuestCommunity
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.guest.GuestPrices
import com.coinepro.core.guest.GuestQuote
import com.coinepro.core.guest.GuestTrackRecord
import com.coinepro.core.guest.MembershipTerms
import com.coinepro.core.notifications.NotificationCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The brief's one pass against real stores.
 *
 * Everything about *what is said* is proved in `RasadBriefTest` and everything about *when* in
 * `MorningBriefScheduleTest`. What is left for this file is the four refusals — off, empty, already
 * sent, unreachable — because each of them is a day with no brief or a day with two, and neither is
 * visible until tomorrow.
 */
class MorningBriefEngineTest {

    private class FakeGateway(
        val quotes: Map<String, Double?> = emptyMap(),
        val reachable: Boolean = true,
        val bars: List<GuestCandle> = emptyList(),
    ) : GuestGateway {
        val priced = mutableListOf<List<String>>()
        val charted = mutableListOf<String>()

        override suspend fun prices(symbols: List<String>): AppResult<GuestPrices> {
            priced += symbols
            if (!reachable) return AppResult.Failure(ErrorKind.NETWORK)
            return AppResult.Success(
                GuestPrices(
                    stale = false,
                    ageMillis = null,
                    quotes = symbols.map { symbol ->
                        GuestQuote(
                            symbol = symbol,
                            price = 1.0,
                            changePercent24h = quotes[symbol],
                            high24h = null,
                            low24h = null,
                            volume24h = null,
                        )
                    },
                ),
            )
        }

        override suspend fun news(limit: Int): AppResult<List<GuestHeadline>> =
            AppResult.Success(emptyList())

        override suspend fun community(): AppResult<GuestCommunity> = AppResult.Failure(ErrorKind.UNKNOWN)

        override suspend fun membership(): AppResult<MembershipTerms> = AppResult.Failure(ErrorKind.UNKNOWN)

        override suspend fun trackRecord(limit: Int): AppResult<GuestTrackRecord> =
            AppResult.Failure(ErrorKind.UNKNOWN)

        override suspend fun candles(
            symbol: String,
            timeframe: String,
            limit: Int,
        ): AppResult<GuestCandles> {
            charted += symbol
            return AppResult.Success(GuestCandles(symbol = symbol, timeframe = timeframe, candles = bars))
        }
    }

    private class FakeDeliverer : MorningBriefDeliverer {
        var delivered: MorningBrief? = null
        var sparkline: CandleSeries? = null
        var count = 0

        override suspend fun deliver(brief: MorningBrief, sparkline: CandleSeries?) {
            delivered = brief
            this.sparkline = sparkline
            count++
        }
    }

    private class FakeDelivery : BriefDeliveryStore {
        var at: Long? = null
        var day: Long? = null

        override suspend fun lastDeliveredAt(): Long? = at
        override suspend fun lastDeliveredDay(): Long? = day
        override suspend fun record(atEpochMillis: Long, localDay: Long) {
            at = atEpochMillis
            day = localDay
        }
    }

    private suspend fun engine(
        gateway: GuestGateway,
        deliverer: MorningBriefDeliverer,
        delivery: BriefDeliveryStore = FakeDelivery(),
        symbols: List<String> = listOf("BTCUSDT", "XAUUSD"),
        on: Boolean = true,
        english: Boolean = false,
    ): MorningBriefEngine {
        val preferences = FakeBriefPreferences()
        val settings = NotificationSettingsStore(preferences)
        settings.setCategory(NotificationCategory.MORNING_BRIEF, on)
        val watchlists = WatchlistStore(FakeBriefPreferences())
        symbols.forEach { watchlists.add(com.coinepro.core.datastore.Watchlist.DEFAULT_LIST_ID, it) }
        return MorningBriefEngine(
            watchlists = watchlists,
            gateway = gateway,
            settings = settings,
            languageOf = { if (english) AppLanguage.ENGLISH else AppLanguage.PERSIAN },
            delivery = delivery,
            deliverer = deliverer,
        )
    }

    @Test
    fun `switched off, nothing is fetched at all`() = runTest {
        val gateway = FakeGateway(quotes = mapOf("BTCUSDT" to 3.0))
        val deliverer = FakeDeliverer()
        val result = engine(gateway, deliverer, on = false).run(NOW, TODAY)

        assertEquals(BriefPassResult.Idle, result)
        // The guard is before the fetch on purpose: a reader who turned the brief off should not
        // be paying for one every morning.
        assertTrue(gateway.priced.isEmpty())
        assertEquals(0, deliverer.count)
    }

    @Test
    fun `an empty watchlist is idle rather than a brief about nothing`() = runTest {
        val gateway = FakeGateway()
        val deliverer = FakeDeliverer()
        val result = engine(gateway, deliverer, symbols = emptyList()).run(NOW, TODAY)

        assertEquals(BriefPassResult.Idle, result)
        assertTrue(gateway.priced.isEmpty())
    }

    @Test
    fun `an unreachable route is a retry, and nothing is written`() = runTest {
        val delivery = FakeDelivery()
        val deliverer = FakeDeliverer()
        val result = engine(FakeGateway(reachable = false), deliverer, delivery).run(NOW, TODAY)

        assertEquals(BriefPassResult.Unavailable, result)
        assertEquals(0, deliverer.count)
        // The day is unspent, so the retry can still deliver it.
        assertNull(delivery.day)
    }

    @Test
    fun `a brief already delivered today is not delivered again`() = runTest {
        val delivery = FakeDelivery().apply { at = 1L; day = TODAY }
        val gateway = FakeGateway(quotes = mapOf("BTCUSDT" to 3.0))
        val deliverer = FakeDeliverer()
        val result = engine(gateway, deliverer, delivery).run(NOW, TODAY)

        assertEquals(BriefPassResult.Idle, result)
        assertEquals(0, deliverer.count)
        assertTrue(gateway.priced.isEmpty())
    }

    @Test
    fun `yesterday's brief does not suppress today's`() = runTest {
        val delivery = FakeDelivery().apply { at = 1L; day = TODAY - 1 }
        val gateway = FakeGateway(quotes = mapOf("BTCUSDT" to 3.0, "XAUUSD" to -1.0))
        val deliverer = FakeDeliverer()
        val result = engine(gateway, deliverer, delivery).run(NOW, TODAY)

        assertTrue(result is BriefPassResult.Delivered)
        assertEquals(1, deliverer.count)
        assertEquals(TODAY, delivery.day)
    }

    @Test
    fun `the mover's candles are the only ones fetched`() = runTest {
        val gateway = FakeGateway(
            quotes = mapOf("BTCUSDT" to 1.0, "XAUUSD" to -8.0),
            bars = bars(60),
        )
        val deliverer = FakeDeliverer()
        engine(gateway, deliverer).run(NOW, TODAY)

        assertEquals(listOf("XAUUSD"), gateway.charted)
        assertEquals("XAUUSD", deliverer.delivered?.mover)
    }

    @Test
    fun `a forming bar is left out of the series the coach reads`() = runTest {
        val gateway = FakeGateway(
            quotes = mapOf("BTCUSDT" to 5.0),
            bars = bars(60) + bars(1, from = 60, closed = false),
        )
        val deliverer = FakeDeliverer()
        engine(gateway, deliverer).run(NOW, TODAY)

        assertEquals(60, deliverer.sparkline?.size)
    }

    @Test
    fun `a list nobody could measure is idle rather than a retry`() = runTest {
        // The route answered and said nothing about any of them. Retrying would get the same
        // answer, so this is not a failure — it is a morning with nothing to report.
        val delivery = FakeDelivery()
        val deliverer = FakeDeliverer()
        engine(FakeGateway(quotes = emptyMap()), deliverer, delivery).run(NOW, TODAY).let { result ->
            assertEquals(BriefPassResult.Idle, result)
        }
        assertEquals(0, deliverer.count)
        assertNull(delivery.day)
    }

    @Test
    fun `English is chosen at delivery, not at construction`() = runTest {
        val gateway = FakeGateway(quotes = mapOf("BTCUSDT" to 4.0))
        val deliverer = FakeDeliverer()
        engine(gateway, deliverer, english = true).run(NOW, TODAY)

        assertEquals("1 up", deliverer.delivered?.headline)
    }

    private fun bars(count: Int, from: Int = 0, closed: Boolean = true): List<GuestCandle> =
        (from until from + count).map { index ->
            val base = 100.0 + index
            GuestCandle(
                timeSeconds = 1_700_000_000L + index * 3_600L,
                open = base,
                high = base + 1.0,
                low = base - 1.0,
                close = base + 0.5,
                volume = 10.0,
                closed = closed,
            )
        }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val TODAY = 20_000L
    }
}

private class FakeBriefPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(data.value)
        data.value = next
        return next
    }
}
