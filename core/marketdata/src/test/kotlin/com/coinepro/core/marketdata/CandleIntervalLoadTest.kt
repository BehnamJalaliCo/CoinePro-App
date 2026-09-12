package com.coinepro.core.marketdata

import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole interval path through a gateway: what it asks the server for, and what it hands back.
 *
 * The fake here answers with a straight run of one-minute or one-hour bars rather than with a
 * recorded fixture, because what is being asserted is the *request* — that an interval no backend
 * serves is never put on the wire, and that the multiplied limit is the one that goes with it. The
 * arithmetic of the fold itself is [CandleFoldTest]'s job.
 */
class CandleIntervalLoadTest {

    private val tehran = ZoneId.of("Asia/Tehran")

    private class RecordingGateway(
        private val step: Long,
        private val count: Int,
        private val hasMore: Boolean = false,
        private val firstBarAt: Long = 0L,
    ) : CandleGateway {
        var askedTimeframe: Timeframe? = null
        var askedLimit: Int = 0
        var askedBefore: Long? = null

        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage {
            askedTimeframe = timeframe
            askedLimit = limit
            askedBefore = before
            val bars = (0 until count).map { index ->
                val t = firstBarAt + index * step
                OhlcBar(t = t, o = 1.0 + index, h = 2.0 + index, l = index.toDouble(), c = 1.5 + index, v = 1.0)
            }
            return CandlePage(
                symbol = symbol,
                timeframe = timeframe,
                candles = bars,
                oldest = bars.firstOrNull()?.t,
                hasMore = hasMore,
                limitMax = 1_000,
            )
        }
    }

    @Test
    fun `an interval no backend serves is never the one put on the wire`() = runTest {
        val gateway = RecordingGateway(step = 3_600, count = 8)

        gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.H2), limit = 4, zone = tehran)

        assertEquals(Timeframe.H1, gateway.askedTimeframe)
        assertNotEquals(Timeframe.H2, gateway.askedTimeframe)
        assertEquals(8, gateway.askedLimit)
    }

    @Test
    fun `a custom interval is fetched as minutes and folded, not sent as a minute count`() = runTest {
        val gateway = RecordingGateway(step = 300, count = 82)

        val page = gateway.load("XAUUSD", ChartInterval.Custom(CustomInterval(205)), limit = 2, zone = tehran)

        assertEquals(Timeframe.M5, gateway.askedTimeframe)
        assertEquals(82, gateway.askedLimit)
        assertTrue(page.candles.isNotEmpty())
        // Every folded bar opens on a 205-minute boundary measured from Tehran midnight.
        val interval = ChartInterval.Custom(CustomInterval(205))
        for (candle in page.candles) {
            assertEquals(candle.t, interval.bucketStart(candle.t, tehran))
        }
    }

    @Test
    fun `a native interval is forwarded untouched, page metadata and all`() = runTest {
        val gateway = RecordingGateway(step = 3_600, count = 5, hasMore = true)

        val page = gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.H1), limit = 5, zone = tehran)

        assertEquals(Timeframe.H1, gateway.askedTimeframe)
        assertEquals(5, gateway.askedLimit)
        assertEquals(5, page.candles.size)
        assertTrue(page.hasMore)
    }

    @Test
    fun `a preset page still names the interval it drew, not the feed it came from`() = runTest {
        val gateway = RecordingGateway(step = 3_600, count = 8)

        val page = gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.H2), limit = 4, zone = tehran)

        assertEquals(Timeframe.H2, page.timeframe)
        assertEquals(listOf(0L, 7_200L, 14_400L, 21_600L), page.candles.map { it.t })
    }

    @Test
    fun `a leading bucket the page only half covers is dropped when older bars exist`() = runTest {
        // The page starts at 01:00, which is the middle of the 00:00 two-hour bucket, and the feed
        // says it has more. That bucket's real open is in bars nobody asked for, so drawing it
        // would present an hour-late price as an open.
        val gateway = RecordingGateway(step = 3_600, count = 5, hasMore = true, firstBarAt = 3_600)

        val page = gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.H2), limit = 4, zone = tehran)

        assertEquals(listOf(7_200L, 14_400L), page.candles.map { it.t })
        assertEquals(7_200L, page.oldest ?: 0L)
    }

    @Test
    fun `a half covered leading bucket is kept when the feed has nothing older`() = runTest {
        // Same page, but the feed says this is everything it has. The short bucket is then the
        // market's own beginning and dropping it would delete a real bar.
        val gateway = RecordingGateway(step = 3_600, count = 5, hasMore = false, firstBarAt = 3_600)

        val page = gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.H2), limit = 4, zone = tehran)

        assertEquals(listOf(0L, 7_200L, 14_400L), page.candles.map { it.t })
    }

    @Test
    fun `paging back passes the folded series' own oldest open time straight through`() = runTest {
        val gateway = RecordingGateway(step = 3_600, count = 4)

        gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.H2), limit = 2, before = 7_200L, zone = tehran)

        assertEquals(7_200L, gateway.askedBefore)
    }

    /**
     * A venue that answers for some of its own advertised bar lengths and not others.
     *
     * Which is what the owner's recording caught: M5, M15 and M30 are all native and all three came
     * back as «چارت بارگیری نشد» while H1 and M1 loaded in the same minute.
     */
    private class PartialGateway(
        private val refuse: Set<Timeframe>,
        private val step: Map<Timeframe, Long> = emptyMap(),
    ) : CandleGateway {
        val asked = mutableListOf<Timeframe>()

        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
            asked += timeframe
            if (timeframe in refuse) throw RuntimeException("HTTP 502 from the candle feed")
            val seconds = step[timeframe] ?: timeframe.seconds
            val bars = (0 until limit).map { index ->
                val t = index * seconds
                OhlcBar(t = t, o = 1.0 + index, h = 2.0 + index, l = index.toDouble(), c = 1.5 + index, v = 1.0)
            }
            return CandlePage(
                symbol = symbol,
                timeframe = timeframe,
                candles = bars,
                oldest = bars.firstOrNull()?.t,
                hasMore = false,
                limitMax = 1_000,
            )
        }
    }

    @Test
    fun `a native bar length the venue refuses is folded from the next feed down`() = runTest {
        // M15 is native and is asked for first. The venue refuses it, and fifteen minutes is three
        // M5 bars — so the reader gets a chart rather than a retry button.
        val gateway = PartialGateway(refuse = setOf(Timeframe.M15))

        val page = gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.M15), limit = 4, zone = tehran)

        assertEquals(listOf(Timeframe.M15, Timeframe.M5), gateway.asked)
        assertEquals(Timeframe.M15, page.timeframe)
        assertTrue(page.candles.isNotEmpty())
        // And every bar it hands back opens on a fifteen-minute boundary, which is the only thing
        // that makes it an M15 chart rather than three M5 bars under an M15 label.
        val interval = ChartInterval.Preset(Timeframe.M15)
        for (candle in page.candles) {
            assertEquals(candle.t, interval.bucketStart(candle.t, tehran))
        }
    }

    @Test
    fun `the walk goes coarsest first and stops at the first feed that answers`() = runTest {
        // M30 out of M15 is two bars folded; out of M1 it is thirty. Both are the same picture and
        // the cheap one is the right one, so M1 is never reached.
        val gateway = PartialGateway(refuse = setOf(Timeframe.M30))

        gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.M30), limit = 3, zone = tehran)

        assertEquals(listOf(Timeframe.M30, Timeframe.M15), gateway.asked)
    }

    @Test
    fun `a venue that refuses every feed reports the failure rather than swallowing it`() = runTest {
        val gateway = PartialGateway(refuse = Timeframe.entries.toSet())

        val thrown = runCatching {
            gateway.load("BTCUSDT", ChartInterval.Preset(Timeframe.M15), limit = 3, zone = tehran)
        }.exceptionOrNull()

        assertTrue("the reader is told, not left on a spinner", thrown is RuntimeException)
        // The first refusal, not the last: it is the one about the feed the reader actually chose.
        assertTrue(thrown!!.message!!.contains("502"))
    }
}
