package com.coinepro.core.signals

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One reader, two very different signal objects.
 *
 * TradeYar built its object to the shape the app parses. CoinePro-FX publishes an older, flatter
 * one — `entry_price`, `sl`, `tp1`/`tp2`/`tp3`, `signal_score` — and omits several fields
 * altogether. Read under the wrong assumption neither of them fails: they produce a signal with no
 * entry, no stop and no targets, which renders as a setup with nothing in it rather than as a
 * parsing problem.
 */
class ForexSignalShapeTest {
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private val now = 1_772_000_000_000L

    private fun parse(json: String, platform: MarketPlatform) =
        gson.fromJson(json, SignalDto::class.java).toDomain(now, platform)

    @Test
    fun `CoinePro-FX's flat object maps onto the shape the screens read`() {
        // Copied from src/api/routes/public.py::_pub_signal.
        val signal = parse(
            """
            {
              "id": 4821,
              "symbol": "XAUUSD",
              "direction": "BUY",
              "signal_type": "auto",
              "entry_price": 2400.0,
              "sl": 2380.0,
              "tp1": 2440.0,
              "tp2": 2470.0,
              "tp3": null,
              "signal_score": 72,
              "timeframe": "H1",
              "status": "active",
              "close_reason": null,
              "pnl_pips": null,
              "hit_target": null,
              "created_at": "2026-08-25T09:00:00+00:00",
              "closed_at": null,
              "current_price": 2412.85
            }
            """.trimIndent(),
            MarketPlatform.COINEPRO_FX,
        )

        requireNotNull(signal)
        assertEquals(4821L, signal.id)
        assertEquals(2400.0, requireNotNull(signal.entry), 0.0001)
        assertEquals("`sl` is what it calls the stop", 2380.0, requireNotNull(signal.stopLoss), 0.0001)
        assertEquals("`signal_score` is what it calls confidence", 72, signal.confidence)
        assertEquals(SignalDirection.BUY, signal.direction)

        // The three level fields become targets, and an absent third is absent rather than zero.
        assertEquals(2, signal.targets.size)
        assertEquals(listOf(1, 2), signal.targets.map { it.level })
        assertEquals(2440.0, requireNotNull(signal.targets.first().price), 0.0001)
    }

    @Test
    fun `a market the server never named comes from the platform that answered`() {
        // CoinePro-FX serves exactly one market and says so nowhere. It is not a guess: a signal
        // cannot arrive from a server that does not trade it.
        val forex = parse(
            """{"id": 1, "symbol": "XAUUSD", "direction": "SELL", "entry_price": 2400.0, "tp1": 2350.0}""",
            MarketPlatform.COINEPRO_FX,
        )
        assertEquals(MarketType.FOREX, requireNotNull(forex).market)

        val crypto = parse(
            """{"id": 2, "market": "crypto", "symbol": "BTCUSDT", "direction": "BUY", "entry": 90000.0,
                "targets": [{"level": 1, "price": 95000.0}]}""",
            MarketPlatform.TRADEYAR,
        )
        assertEquals(MarketType.CRYPTO, requireNotNull(crypto).market)
    }

    @Test
    fun `a signal from the wrong platform is refused rather than shown`() {
        // The mixed watchlist is the bug the platform split exists to prevent, and a signal is the
        // one place a wrong market costs money.
        assertNull(
            parse(
                """{"id": 3, "market": "crypto", "symbol": "BTCUSDT", "direction": "BUY", "entry": 1.0, "tp1": 2.0}""",
                MarketPlatform.COINEPRO_FX,
            ),
        )
    }

    @Test
    fun `a bare price is carried as a quote of unknown age, not as a live one`() {
        val signal = parse(
            """{"id": 4, "symbol": "XAUUSD", "direction": "BUY", "entry_price": 2400.0,
                "tp1": 2440.0, "current_price": 2412.85}""",
            MarketPlatform.COINEPRO_FX,
        )

        val quote = requireNotNull(requireNotNull(signal).currentQuote)
        assertEquals(2412.85, quote.price, 0.0001)
        assertTrue(
            "Without a timestamp there is nothing to judge freshness by, and fresh is the claim " +
                "that would cost someone money",
            quote.isStale,
        )
    }

    @Test
    fun `TradeYar's own shape is read unchanged`() {
        val signal = parse(
            """
            {
              "id": 77, "market": "crypto", "symbol": "BTCUSDT", "direction": "BUY",
              "status": "active", "timeframe": "H4", "strategy": "breakout", "confidence": 64,
              "entry": 90000.0, "stop_loss": 88000.0,
              "targets": [{"level": 1, "price": 95000.0}, {"level": 2, "price": 98000.0}],
              "risk_reward_tp1": 2.5
            }
            """.trimIndent(),
            MarketPlatform.TRADEYAR,
        )

        requireNotNull(signal)
        assertEquals(90000.0, requireNotNull(signal.entry), 0.0001)
        assertEquals(88000.0, requireNotNull(signal.stopLoss), 0.0001)
        assertEquals(2, signal.targets.size)
        assertEquals(2.5, requireNotNull(signal.riskRewardTp1), 0.0001)
        assertEquals("breakout", signal.strategy)
    }

    @Test
    fun `the two envelopes both yield a total`() {
        // CoinePro-FX calls it `count`; TradeYar reports none at all.
        val forex = gson.fromJson("""{"items": [], "count": 12}""", SignalListResponseDto::class.java)
        assertEquals(12, forex.total)

        val crypto = gson.fromJson(
            """{"items": [], "next_cursor": null, "membership_required": true,
                "membership_message": "اشتراک لازم است"}""",
            SignalListResponseDto::class.java,
        )
        assertEquals(0, crypto.total)
        assertTrue(crypto.membershipRequired)
        assertEquals("اشتراک لازم است", crypto.membershipMessage)
    }
}
