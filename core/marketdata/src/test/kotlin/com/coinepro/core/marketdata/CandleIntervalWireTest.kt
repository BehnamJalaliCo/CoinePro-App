package com.coinepro.core.marketdata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every timeframe, against both venues, on the wire — which is the bug this file was written for.
 *
 * The chart drew on one hour and failed on the others, and the cause was a single list: the
 * resolver picked a source feed from `SERVER_NATIVE_TIMEFRAMES` whatever venue it was talking to,
 * and called it "the eight both backends serve". Measured live against each route on five symbols,
 * CoinePro-FX answers `404 {"detail":"دادهٔ این نماد نیست."}` on `M1`, `M30` and `W1` for every one
 * of them. Because `M2` and `M3` resolve to `M1` and every custom multiple of thirty minutes
 * resolved to `M30`, the damage reached well past those three keys — and it arrived as a network
 * error over a retry that could not work.
 *
 * So this walks all fifteen presets on both venues rather than sampling: the failure was invisible
 * precisely because the one timeframe anybody tested first — `H1`, the chart's own default and both
 * routes' own default — is served everywhere.
 */
class CandleIntervalWireTest {

    /** A gateway that answers with a plausible page and remembers exactly what it was asked. */
    private class RecordingGateway(
        override val nativeTimeframes: List<Timeframe>,
        override val sourceLimitMax: Int = CandleGateway.SOURCE_LIMIT_MAX,
    ) : CandleGateway {
        override val sourceName: String = "test venue"
        var askedTimeframe: Timeframe? = null
        var askedLimit: Int = 0
        var calls: Int = 0

        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage {
            askedTimeframe = timeframe
            askedLimit = limit
            calls++
            val bars = (0 until 8).map { index ->
                val t = 1_600_000_000L + index * timeframe.seconds
                OhlcBar(t = t, o = 1.0, h = 2.0, l = 0.5, c = 1.5, v = 1.0)
            }
            return CandlePage(symbol = symbol, timeframe = timeframe, candles = bars)
        }
    }

    // ── the crypto venue serves all eight, so all fifteen presets draw ─────────────────

    @Test
    fun `every preset resolves to a source the crypto venue actually serves`() {
        for (frame in Timeframe.entries) {
            val source = requireNotNull(sourceTimeframeFor(ChartInterval.Preset(frame), SERVER_NATIVE_TIMEFRAMES)) {
                "${frame.wire} must resolve on the crypto venue"
            }
            assertTrue(
                "${frame.wire} resolved to ${source.wire}, which that venue does not serve",
                source in SERVER_NATIVE_TIMEFRAMES,
            )
        }
    }

    @Test
    fun `the wire value sent for every preset is one of the eight, never the reader's own`() = runTest {
        for (frame in Timeframe.entries) {
            val gateway = RecordingGateway(SERVER_NATIVE_TIMEFRAMES)
            gateway.load("BTCUSDT", ChartInterval.Preset(frame), limit = 10)
            val sent = requireNotNull(gateway.askedTimeframe) { "${frame.wire} made no request at all" }
            assertTrue(
                "${frame.wire} put ${sent.wire} on the wire, which the venue does not serve",
                sent in SERVER_NATIVE_TIMEFRAMES,
            )
            // The eight it does serve go out unchanged; the other seven must not.
            if (frame in SERVER_NATIVE_TIMEFRAMES) {
                assertEquals(frame.wire, frame, sent)
            } else {
                assertTrue("${frame.wire} was forwarded verbatim", sent != frame)
            }
        }
    }

    // ── the forex venue serves five, and the other ten are folded or refused ───────────

    @Test
    fun `the three the forex venue does not serve are folded from ones it does`() {
        val natives = ACADEMY_NATIVE_TIMEFRAMES
        // Half an hour is two fifteen-minute bars, and a week is seven days. Both nest exactly, so
        // both are drawable there — which is why the fix is a fold and not a greyed-out key.
        assertEquals(Timeframe.M15, sourceTimeframeFor(ChartInterval.Preset(Timeframe.M30), natives))
        assertEquals(Timeframe.D1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.W1), natives))
        assertEquals(Timeframe.D1, sourceTimeframeFor(ChartInterval.Preset(Timeframe.MN1), natives))
        // One minute is the one that genuinely cannot be built: nothing divides sixty seconds there.
        assertNull(sourceTimeframeFor(ChartInterval.Preset(Timeframe.M1), natives))
    }

    @Test
    fun `every preset on the forex venue either resolves to a feed it has or is refused outright`() {
        for (frame in Timeframe.entries) {
            val source = sourceTimeframeFor(ChartInterval.Preset(frame), ACADEMY_NATIVE_TIMEFRAMES)
            if (source != null) {
                assertTrue(
                    "${frame.wire} resolved to ${source.wire}, which that venue answers 404 for",
                    source in ACADEMY_NATIVE_TIMEFRAMES,
                )
                assertEquals(
                    "${frame.wire} does not divide by ${source.wire}",
                    0L,
                    if (frame == Timeframe.MN1) 0L else frame.seconds % source.seconds,
                )
            } else {
                // The three finer than the venue's finest bar, and nothing else.
                assertTrue(
                    "${frame.wire} was refused and should not have been",
                    frame in setOf(Timeframe.M1, Timeframe.M2, Timeframe.M3),
                )
            }
        }
    }

    @Test
    fun `an interval the forex venue cannot build is refused before a request goes out`() = runTest {
        val gateway = RecordingGateway(ACADEMY_NATIVE_TIMEFRAMES)

        val thrown = runCatching { gateway.load("XAUUSD", ChartInterval.Preset(Timeframe.M1), limit = 10) }
            .exceptionOrNull()

        val refusal = thrown as? CandleIntervalUnavailableException
            ?: throw AssertionError("a one-minute forex chart must be refused, not attempted: $thrown")
        // Nothing went out. That is the whole point: the request that used to go out came back 404.
        assertEquals(0, gateway.calls)
        assertTrue(
            "the token the chart's error mapping reads",
            refusal.message.orEmpty().contains("interval_unavailable"),
        )
        assertEquals(Timeframe.M5, refusal.finest)
    }

    @Test
    fun `a custom interval on the forex venue uses its five-minute feed rather than a half-hour one`() = runTest {
        val gateway = RecordingGateway(ACADEMY_NATIVE_TIMEFRAMES)

        // Sixty minutes is two half-hour bars on the crypto venue and four fifteen-minute ones here,
        // because the half-hour feed does not exist on this platform at all.
        gateway.load("XAUUSD", ChartInterval.Custom(CustomInterval(60)), limit = 4)
        assertEquals(Timeframe.M15, gateway.askedTimeframe)

        assertEquals(
            Timeframe.M5,
            sourceTimeframeFor(ChartInterval.Custom(CustomInterval(205)), ACADEMY_NATIVE_TIMEFRAMES),
        )
        // Seven minutes needs a one-minute feed, which is the one thing this venue has not got.
        assertNull(sourceTimeframeFor(ChartInterval.Custom(CustomInterval(7)), ACADEMY_NATIVE_TIMEFRAMES))
    }

    // ── the limit that goes with it ───────────────────────────────────────────────────

    @Test
    fun `a request is clamped to the venue's own ceiling and not to a shared constant`() = runTest {
        // The public crypto route refuses more than five hundred with a 422 rather than truncating,
        // so a fold that multiplies past it must be clamped at the venue that will answer it.
        val guestLike = RecordingGateway(SERVER_NATIVE_TIMEFRAMES, sourceLimitMax = 500)
        guestLike.load("BTCUSDT", ChartInterval.Preset(Timeframe.MN1), limit = 300)
        assertTrue("asked for ${guestLike.askedLimit}", guestLike.askedLimit <= 500)

        val forexLike = RecordingGateway(ACADEMY_NATIVE_TIMEFRAMES, sourceLimitMax = 3_000)
        forexLike.load("XAUUSD", ChartInterval.Preset(Timeframe.MN1), limit = 300)
        assertTrue("asked for ${forexLike.askedLimit}", forexLike.askedLimit in 1..3_000)
    }

    @Test
    fun `a plan for an interval the venue cannot build is marked unavailable rather than guessed`() {
        val plan = resolveCandleRequest(
            ChartInterval.Preset(Timeframe.M1),
            natives = ACADEMY_NATIVE_TIMEFRAMES,
        )
        assertFalse(plan.available)
        // It still names the finest feed there is, because that is the one a caller suggests
        // instead — and because a null source would put a branch at every call site that only
        // wanted to size or explain a request.
        assertEquals(Timeframe.M5, plan.source)

        assertTrue(resolveCandleRequest(ChartInterval.Preset(Timeframe.M1)).available)
    }
}
