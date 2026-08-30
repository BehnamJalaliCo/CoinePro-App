package com.coinepro.core.aisignal

import com.coinepro.core.model.MarketPlatform
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the AI endpoints actually see, and what they actually send.
 *
 * ### Why this file exists
 *
 * Every press of «ساخت ستاپ» came back `422` and the screen showed the reader an English exception
 * sentence. The cause was two JSON key names, and **not one test in this module could have caught
 * it**: they all went from domain object to domain object, so the one surface that was wrong — the
 * bytes on the wire — was the one surface nothing looked at. A key name is a contract with another
 * team; a contract nothing asserts is a contract that drifts the next time somebody renames a
 * Kotlin property.
 *
 * So these tests serialise through **the same Gson `NetworkFactory` builds** — the naming policy
 * matters, and it is exactly what made `riskPct` look right in the source and arrive as `risk_pct`
 * on the wire — and assert the literal keys against the two server contracts:
 *
 * * `docs/SERVER_PROMPT_TRADEYAR.md` part 11
 * * `docs/SERVER_PROMPT_COINEPROFX.md` and `docs/PRODUCT_DIRECTION.md` for the FX side
 *
 * Nothing here pins a count. What is pinned is behaviour a server depends on: which names go out,
 * which never do, and which names coming back are understood.
 */
class AiSignalWireTest {

    /** The one `core:network` configures. A test with a different Gson tests a different app. */
    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create()

    private fun body(
        request: AiSignalRequest,
        platform: MarketPlatform = MarketPlatform.COINEPRO_FX,
    ): JsonObject =
        JsonParser.parseString(gson.toJson(request.toWire(request.symbol, platform))).asJsonObject

    private val fullRequest = AiSignalRequest(
        symbol = "XAUUSD",
        timeframe = AiSignalTimeframe.H1,
        risk = AiSignalRisk.HIGH,
        tradeStyle = AiTradeStyle.SWING,
        riskAppetite = AiRiskAppetite.AGGRESSIVE,
        directionBias = AiDirectionBias.LONG,
        minRiskReward = 2.5,
        lot = 0.25,
        riskPercent = 1.5,
        balance = 5_000.0,
    )

    @Test
    fun `every request key is the name both server contracts document`() {
        val json = body(fullRequest)

        assertEquals("XAUUSD", json["symbol"].asString)
        assertEquals("H1", json["timeframe"].asString)
        assertEquals("swing", json["trade_style"].asString)
        assertEquals("aggressive", json["risk_appetite"].asString)
        assertEquals("long", json["direction_bias"].asString)
        assertEquals(2.5, json["min_rr"].asDouble, 0.0)
        assertEquals(0.25, json["lot"].asDouble, 0.0)
        assertEquals(1.5, json["risk_percent"].asDouble, 0.0)
        assertEquals(5_000.0, json["balance"].asDouble, 0.0)
    }

    @Test
    fun `the request carries nothing the contracts do not list`() {
        // The 422. `risk` was sent on every request and appears in neither contract; on a strict
        // validator an unknown field is not ignored, it is a refusal. Asserting the whole key set
        // rather than just `risk`'s absence is what stops the next stray field.
        val documented = setOf(
            "symbol",
            "timeframe",
            "trade_style",
            "risk_appetite",
            "direction_bias",
            "min_rr",
            "lot",
            "risk_percent",
            "balance",
        )

        assertEquals(documented, body(fullRequest, MarketPlatform.COINEPRO_FX).keySet())
    }

    @Test
    fun `lot belongs to CoinePro-FX and never reaches TradeYar`() {
        // The `TYR-017` the owner reported. `lot` is in CoinePro-FX's contract and in neither line
        // of TradeYar's part 11, and it went to both — on the strength of a comment claiming
        // TradeYar would ignore it. `risk` had already shown that this validator refuses unknown
        // fields rather than ignoring them, and `TYR-017` is what refusing one is called.
        val crypto = body(
            fullRequest.copy(symbol = "BTCUSDT"),
            MarketPlatform.TRADEYAR,
        )

        assertFalse(crypto.has("lot"))
        assertEquals(
            setOf(
                "symbol",
                "timeframe",
                "trade_style",
                "risk_appetite",
                "direction_bias",
                "min_rr",
                "risk_percent",
                "balance",
            ),
            crypto.keySet(),
        )
        // The rest of the sizing controls do belong to both, so dropping one must not have dropped
        // the pair beside it — that would swap a refusal for a setup sized against nothing.
        assertEquals(1.5, crypto["risk_percent"].asDouble, 0.0)
        assertEquals(5_000.0, crypto["balance"].asDouble, 0.0)
    }

    @Test
    fun `risk_pct was the other half of the 422 and is never sent under that name`() {
        val json = body(fullRequest)

        assertFalse(json.has("risk_pct"))
        assertFalse(json.has("riskPct"))
        assertFalse(json.has("risk"))
    }

    @Test
    fun `an untouched control is absent rather than sent as a guess`() {
        // Both contracts say an absent optional means "you decide". A field sent as null, or as a
        // zero, is the app answering a question the reader deliberately left alone.
        val json = body(AiSignalRequest(symbol = "BTCUSDT", timeframe = AiSignalTimeframe.M15))

        assertEquals(setOf("symbol", "timeframe"), json.keySet())
    }

    @Test
    fun `a zero lot or balance is an untouched control, not a position of nothing`() {
        val json = body(fullRequest.copy(lot = 0.0, balance = 0.0, riskPercent = 0.0, minRiskReward = 0.0))

        assertFalse(json.has("lot"))
        assertFalse(json.has("balance"))
        assertFalse(json.has("risk_percent"))
        assertFalse(json.has("min_rr"))
    }

    @Test
    fun `a non-finite figure never reaches the wire`() {
        val json = body(fullRequest.copy(minRiskReward = Double.NaN, balance = Double.POSITIVE_INFINITY))

        assertFalse(json.has("min_rr"))
        assertFalse(json.has("balance"))
    }

    @Test
    fun `the quota is read under either backend's spelling`() {
        // TradeYar sends remaining; CoinePro-FX sends used and states its own accepted lists.
        val tradeyar = gson.fromJson(
            """{"used": 3, "limit": 20, "remaining": 17, "reset_at": "2026-08-30T00:00:00Z"}""",
            AiSignalQuotaDto::class.java,
        ).toDomain()

        assertEquals(17, requireNotNull(tradeyar).remaining)
        assertEquals(20, tradeyar.limit)
        assertEquals("2026-08-30T00:00:00Z", tradeyar.resetAt)

        val fx = gson.fromJson(
            """{"used": 8, "limit": 20, "reset_at": "2026-08-30T00:00:00Z",
                "symbols": ["xau/usd", "EURUSD"], "timeframes": ["M15", "H1", "M45"]}""",
            AiSignalQuotaDto::class.java,
        ).toDomain()

        assertEquals(12, requireNotNull(fx).remaining)
        assertEquals(listOf("XAUUSD", "EURUSD"), fx.symbols)
        assertEquals(listOf(AiSignalTimeframe.M15, AiSignalTimeframe.H1), fx.timeframes)
        // Kept rather than dropped, so the screen can say the server offers a length this build has
        // no wire value for instead of silently pretending it was never offered.
        assertEquals(listOf("M45"), fx.unknownTimeframes)
    }

    @Test
    fun `a ceiling written as quota rather than limit is still a ceiling`() {
        val parsed = gson.fromJson("""{"quota": 5, "remaining": 2}""", AiSignalQuotaDto::class.java)

        assertEquals(5, requireNotNull(parsed.toDomain()).limit)
    }

    @Test
    fun `TradeYar's nested snapshot and CoinePro-FX's flat one both reach the screen`() {
        val nested = gson.fromJson(
            """{"symbol": "BTCUSDT", "direction": "BUY", "entry": 100.0, "sl": 95.0, "tp1": 110.0,
                "confidence": 0.7, "rr": 2.0,
                "snapshot": {"rsi_14": 61.2, "atr_14": 3.4, "macd": 0.12,
                             "ema_20": 99.0, "ema_50": 97.0, "ema_200": 90.0},
                "candles": [{"t": 1756400000000, "o": 99.0, "h": 101.0, "l": 98.0, "c": 100.0},
                            {"t": 1756403600000, "o": 100.0, "h": 102.0, "l": 99.0, "c": 101.0}]}""",
            AiGeneratedSignalDto::class.java,
        ).toDomain(AiSignalRequest("BTCUSDT", AiSignalTimeframe.H1))

        val snapshot = requireNotNull(requireNotNull(nested).snapshot)
        assertEquals(61.2, requireNotNull(snapshot.rsi14), 0.001)
        assertEquals(90.0, requireNotNull(snapshot.ema200), 0.001)
        // `candles`, not `recent_candles` — the evidence chart drew nothing under TradeYar's name.
        assertEquals(2, nested.recentCandles.size)

        val flat = gson.fromJson(
            """{"symbol": "XAUUSD", "direction": "SELL", "entry": 2500.0, "stop_loss": 2520.0,
                "tp1": 2460.0, "confidence": 74, "rsi14": 38.0, "ema20": 2498.0,
                "bb_upper": 2530.0, "bb_lower": 2470.0,
                "swing_high_20": 2540.0, "swing_low_20": 2450.0, "price_now": 2501.0,
                "recent_candles": [{"o": 1.0, "h": 2.0, "l": 0.5, "c": 1.5}]}""",
            AiGeneratedSignalDto::class.java,
        ).toDomain(AiSignalRequest("XAUUSD", AiSignalTimeframe.H1))

        val flatSnapshot = requireNotNull(requireNotNull(flat).snapshot)
        assertEquals(38.0, requireNotNull(flatSnapshot.rsi14), 0.001)
        assertEquals(2530.0, requireNotNull(flatSnapshot.bollingerUpper), 0.001)
        assertEquals(2450.0, requireNotNull(flatSnapshot.swingLow20), 0.001)
        assertEquals(2501.0, requireNotNull(flatSnapshot.priceNow), 0.001)
    }

    @Test
    fun `an indicator the server could not compute stays missing rather than becoming zero`() {
        val parsed = gson.fromJson(
            """{"symbol": "BTCUSDT", "direction": "BUY", "entry": 100.0, "sl": 95.0, "tp1": 110.0,
                "confidence": 55, "snapshot": {"rsi_14": 61.0, "atr_14": null}}""",
            AiGeneratedSignalDto::class.java,
        ).toDomain(AiSignalRequest("BTCUSDT", AiSignalTimeframe.H1))

        val snapshot = requireNotNull(requireNotNull(parsed).snapshot)
        assertNull(snapshot.atr14)
        assertNull(snapshot.macd)
    }

    @Test
    fun `the job envelope is read under either backend's key for the job id`() {
        val request = AiSignalRequest("BTCUSDT", AiSignalTimeframe.H1)

        val byJobId = gson.fromJson(
            """{"job_id": "j-1", "status": "queued", "quota": {"limit": 20, "remaining": 19}}""",
            AiSignalJobDto::class.java,
        ).toDomain(request, fallbackId = null)
        assertEquals("j-1", requireNotNull(byJobId).id)
        assertEquals(19, requireNotNull(byJobId.quota).remaining)

        // The poll response names no job. Requiring one back would fail every poll on both servers.
        val polled = gson.fromJson(
            """{"status": "failed", "error_code": "AI-014", "error_message": "سرویس مدل در دسترس نیست."}""",
            AiSignalJobDto::class.java,
        ).toDomain(request, fallbackId = "j-1")
        assertEquals("j-1", requireNotNull(polled).id)
        assertEquals("AI-014", polled.errorCode)
        assertEquals("سرویس مدل در دسترس نیست.", polled.errorMessage)
    }

    @Test
    fun `a warning list and a warning string both survive`() {
        val request = AiSignalRequest("BTCUSDT", AiSignalTimeframe.H1)
        val levels = """"symbol": "BTCUSDT", "direction": "BUY", "entry": 100.0, "sl": 95.0,
                        "tp1": 110.0, "confidence": 60"""

        val asList = gson.fromJson(
            """{$levels, "warnings": ["نوسان بالا", "نقدشوندگی کم"]}""",
            AiGeneratedSignalDto::class.java,
        ).toDomain(request)
        assertEquals(listOf("نوسان بالا", "نقدشوندگی کم"), requireNotNull(asList).warnings)

        val asString = gson.fromJson(
            """{$levels, "warnings": "نوسان بالا؛ نقدشوندگی کم"}""",
            AiGeneratedSignalDto::class.java,
        ).toDomain(request)
        assertEquals(listOf("نوسان بالا", "نقدشوندگی کم"), requireNotNull(asString).warnings)
    }

    @Test
    fun `a forex major is a request the client forwards rather than one it refuses`() {
        // The second half of the same bug. `normalizeSymbol` accepted the two metals and anything
        // ending in USDT, so on a forex platform the client refused EURUSD before the request left
        // the phone — on a product whose entire subject is forex.
        assertEquals("EURUSD", AiSignalProductScope.normalizeSymbol("eur/usd"))
        assertEquals("US30", AiSignalProductScope.normalizeSymbol("us-30"))
        assertEquals("BTCUSDT", AiSignalProductScope.normalizeSymbol(" btc-usdt "))

        assertNull(AiSignalProductScope.normalizeSymbol(""))
        assertNull(AiSignalProductScope.normalizeSymbol("   "))
        assertNull(AiSignalProductScope.normalizeSymbol("2500"))
        assertTrue(AiSignalProductScope.normalizeSymbol("a") == null)
    }
}
