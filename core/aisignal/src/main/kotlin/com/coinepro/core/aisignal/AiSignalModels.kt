package com.coinepro.core.aisignal

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.SignalDirection

/**
 * The bar lengths the AI endpoint will answer for.
 *
 * The app's own `Timeframe` enum runs M1…MN1, and the AI screen used to offer five of those. The
 * gap between the two lists is not arbitrary and is worth stating rather than silently shipping:
 *
 * * These eight are what **both** backends documented and serve natively.
 * * The seven the chart also offers — M2, M3, M10, M45, H2, H3, MN1 — exist because readers coming
 *   from TradingView expect them, and the chart builds them by folding a finer bar the server does
 *   send. There is no folding path here: the AI request is a bar length the *server* resolves before
 *   it fetches a series and prompts a model, so forwarding one of the seven gets an error back, or
 *   worse, a silently wrong series. Offering them would be the same class of mistake as offering a
 *   symbol the server refuses.
 *
 * Where a server states its own list — CoinePro-FX sends one alongside the quota — that list wins
 * over this one. See [AiSignalQuota.timeframes].
 *
 * [label] is Persian prose read aloud ("پانزده دقیقه"), so it carries Persian digits. [wireValue] is
 * identity and never appears on screen.
 */
enum class AiSignalTimeframe(val wireValue: String, val label: String) {
    M1("M1", "۱ دقیقه"),
    M5("M5", "۵ دقیقه"),
    M15("M15", "۱۵ دقیقه"),
    M30("M30", "۳۰ دقیقه"),
    H1("H1", "۱ ساعت"),
    H4("H4", "۴ ساعت"),
    D1("D1", "۱ روز"),
    W1("W1", "۱ هفته"),
    ;

    /**
     * How **this** server spells it, which is not the same question as what this enum is called.
     *
     * The two backends disagree, and the app was answering with one of the two everywhere:
     *
     *  * CoinePro-FX's `ai_signal.py` upper-cases the field and matches `M5 M15 H1 H4 D1`.
     *  * TradeYar's `mobile/ai.py` lower-cases it and matches `5m 15m 30m 1h 4h 1d`.
     *
     * Sending `H1` to TradeYar is what the owner photographed: `422`, `TYR-017 Validation Field
     * Invalid`, and the server's own sentence listing the six it does take. It is not a server
     * fault and not a spelling either side should change — a contract each has published — so the
     * request is written in the dialect of wherever it is being posted, at the one boundary that
     * knows which that is. See [AiSignalRequest.toWire].
     */
    fun wireValueFor(platform: MarketPlatform): String = when (platform) {
        MarketPlatform.COINEPRO_FX -> wireValue
        MarketPlatform.TRADEYAR -> tradingViewValue.lowercase()
    }

    companion object {
        /**
         * The lengths a platform's AI endpoint accepts, in this enum's own order.
         *
         * Read off the two servers rather than guessed, and narrower than [entries] on both: M1 is
         * on neither list — a model asked to plan a trade off one-minute candles was never going to
         * be answered — and W1 is on neither either. Offering a length that is refused on submit is
         * the same fault as offering a symbol that is: the reader finds out after they have filled
         * in the form.
         *
         * A server that states its own list still wins over this one; this is what the picker shows
         * before one has. See [AiSignalQuota.timeframes].
         */
        fun accepted(platform: MarketPlatform): List<AiSignalTimeframe> = when (platform) {
            // `_ALLOWED_TF = ["M5", "M15", "H1", "H4", "D1"]` in `src/api/routes/ai_signal.py`.
            MarketPlatform.COINEPRO_FX -> listOf(M5, M15, H1, H4, D1)
            // `TIMEFRAMES = ("5m", "15m", "30m", "1h", "4h", "1d")` in `app/api/mobile/ai.py`.
            MarketPlatform.TRADEYAR -> listOf(M5, M15, M30, H1, H4, D1)
        }

        /** The wire spelling, however a server chose to case or punctuate it. Null when unknown. */
        fun ofWire(raw: String?): AiSignalTimeframe? {
            val normalized = raw?.trim()?.uppercase()?.replace("-", "")?.replace("_", "")
                ?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.wireValue == normalized }
                // TradingView spellings, which is what a server team writing a list by hand reaches
                // for: `15m`, `1h`, `1d`. Recognising them costs nothing and the alternative is a
                // server-stated list this app reads as empty and then silently ignores.
                ?: entries.firstOrNull { it.tradingViewValue == normalized }
        }
    }

    private val tradingViewValue: String
        get() = when (this) {
            M1 -> "1M"
            M5 -> "5M"
            M15 -> "15M"
            M30 -> "30M"
            H1 -> "1H"
            H4 -> "4H"
            D1 -> "1D"
            W1 -> "1W"
        }
}

/**
 * The older three-level risk field.
 *
 * Kept because the app still carries it through [AiSignalRequest], and **not** sent on the wire any
 * more — see [AiSignalCreateJobDto]. `risk_appetite` is the control both contracts actually name.
 */
enum class AiSignalRisk(val wireValue: String, val label: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
}

enum class AiSignalJobStatus(val wireValue: String) {
    QUEUED("queued"),
    RUNNING("running"),
    DONE("done"),
    FAILED("failed"),
    EXPIRED("expired"),
}

object AiSignalProductScope {

    /** TradeYar — the crypto names to fall back on before the catalogue lands. */
    val cryptoSymbols: List<String> = listOf(
        "BTCUSDT",
        "ETHUSDT",
        "SOLUSDT",
        "BNBUSDT",
        "XRPUSDT",
        "ADAUSDT",
        "DOGEUSDT",
        "TRXUSDT",
    )

    /** CoinePro-FX — the majors and the metals, to fall back on before the catalogue lands. */
    val forexSymbols: List<String> = listOf(
        "XAUUSD",
        "XAGUSD",
        "EURUSD",
        "GBPUSD",
        "USDJPY",
        "USDCHF",
        "AUDUSD",
        "USDCAD",
    )

    /**
     * The **fallback** menu, not the menu.
     *
     * This used to be the whole offer: eight coins and two metals, hard-coded, on a platform that
     * quotes 441 crypto markets and the entire MT5 forex universe. It is now what the picker shows
     * before the catalogue has loaded and after a catalogue load has failed — a first screenful, not
     * a ceiling. See `AiSymbolUniverse`.
     *
     * Still per-platform: offering a reader a symbol their platform does not serve produces a
     * request the backend they are signed in to cannot answer, and the failure looks like a broken
     * model rather than a wrong market.
     */
    fun symbolsFor(platform: MarketPlatform): List<String> = when (platform) {
        MarketPlatform.TRADEYAR -> cryptoSymbols
        MarketPlatform.COINEPRO_FX -> forexSymbols
    }

    /**
     * A symbol cleaned to the spelling that goes on the wire, or null when it is not a ticker.
     *
     * ### What this used to do, and why that was a bug
     *
     * It accepted `XAUUSD`, `XAGUSD` and anything ending in `USDT`, and returned null for everything
     * else. `NetworkAiSignalGateway.createJob` then refused any other symbol before the request left
     * the phone, and `AiGeneratedSignalDto.toDomain` discarded any result that came back named
     * anything else. So an FX reader could ask about gold and silver and nothing else — not
     * `EURUSD`, not `US30`, not `USOIL` — on a platform whose entire product is forex, and a crypto
     * reader could not ask about a `BTCUSD` perpetual. The client was enforcing a product scope
     * three years narrower than the servers'.
     *
     * The server owns that scope and refuses what it does not serve, with a reason. This function's
     * job is only to reject what is not a symbol at all, so that a stray space or an empty box never
     * becomes a request. Anything that reads as a ticker is passed through and the server decides.
     */
    fun normalizeSymbol(raw: String): String? {
        val normalized = raw.trim().uppercase()
            .replace("/", "")
            .replace("-", "")
            .replace(" ", "")
            .replace(".", "")
        if (normalized.length !in TICKER_LENGTH) return null
        // A ticker is letters and digits, and must start with a letter: `1000PEPEUSDT` is real, but
        // so is a reader pasting a price into the box, and the leading letter tells them apart.
        if (!normalized.first().isLetter()) return null
        if (!normalized.all { it.isLetterOrDigit() }) return null
        return normalized
    }

    /** Three characters is `OIL`; sixteen clears the longest LBank pair by a wide margin. */
    private val TICKER_LENGTH = 3..16
}

/**
 * Everything the server will take into account when shaping a setup.
 *
 * Only [symbol] and [timeframe] are required. The rest narrow the result and are sent only when
 * the user actually set them, so an untouched control stays absent from the request rather than
 * being sent as a guess the model would then honour.
 */
data class AiSignalRequest(
    val symbol: String,
    val timeframe: AiSignalTimeframe,
    /**
     * Carried, never sent.
     *
     * Neither contract lists a `risk` field, and an always-present field a strict validator has not
     * been told about is exactly a 422 — which is what «ساخت ستاپ» produced on every press. The
     * control a reader sets is [riskAppetite]; this stays so that call sites written against the
     * older model still compile and so the screen has something to derive when it needs the older
     * three-level vocabulary.
     */
    val risk: AiSignalRisk = AiSignalRisk.MEDIUM,
    val tradeStyle: AiTradeStyle? = null,
    val riskAppetite: AiRiskAppetite? = null,
    val directionBias: AiDirectionBias? = null,
    val minRiskReward: Double? = null,
    val lot: Double? = null,
    val riskPercent: Double? = null,
    val balance: Double? = null,
)

/**
 * The daily allowance, and — where a server says so — what it will accept spending it on.
 *
 * [symbols] and [timeframes] are not decoration. CoinePro-FX returns them alongside the quota, which
 * makes them the only statement either backend makes about its own scope; a picker that offers
 * something outside them is a 422 waiting to happen. Empty means the server said nothing, not that
 * it accepts nothing.
 */
data class AiSignalQuota(
    val remaining: Int,
    val limit: Int,
    /** ISO-8601, as sent. Parsed for display rather than reformatted here. */
    val resetAt: String?,
    val symbols: List<String> = emptyList(),
    val timeframes: List<AiSignalTimeframe> = emptyList(),
    /**
     * Bar lengths the server named that this build cannot resolve.
     *
     * Kept rather than dropped so the screen can say so. A server that starts serving `M45` and an
     * app that quietly ignores it is a feature nobody can see has arrived.
     */
    val unknownTimeframes: List<String> = emptyList(),
) {
    val exhausted: Boolean get() = remaining <= 0
}

data class AiSignalEntryZone(
    val low: Double,
    val high: Double,
)

data class AiSignalTarget(
    val level: Int,
    val price: Double,
)

data class AiGeneratedSignal(
    /**
     * The stored signal this advice corresponds to, when there is one.
     *
     * Null on both servers today, and for the same stated reason: neither writes the model's
     * output into its signals table, because it is advice rather than a published call. So there
     * is nothing to open, and the screen must not offer to.
     */
    val signalId: Long? = null,
    val symbol: String,
    val direction: SignalDirection,
    val timeframe: String,
    val entry: Double,
    val entryZone: AiSignalEntryZone?,
    val stopLoss: Double,
    val targets: List<AiSignalTarget>,
    val confidence: Int,
    val riskRewardTp1: Double?,
    val rationale: String?,
    val validatedAt: String?,
    /** Suggested position size, when the request supplied a balance or risk percentage. */
    val lot: Double? = null,
    val strategy: String? = null,
    /** Server-raised caveats about the setup. Shown verbatim; never summarised away. */
    val warnings: List<String> = emptyList(),
    /** The indicator readings behind the setup, so the screen can show its reasoning. */
    val snapshot: AiTechnicalSnapshot? = null,
    /** The recent series the model reasoned over, oldest first. */
    val recentCandles: List<AiCandle> = emptyList(),
)

data class AiSignalJob(
    val id: String,
    val status: AiSignalJobStatus,
    val request: AiSignalRequest,
    val result: AiGeneratedSignal?,
    val errorCode: String?,
    val errorMessage: String?,
    val quota: AiSignalQuota?,
    val createdAt: String?,
    val expiresAt: String?,
) {
    val isPending: Boolean get() = status == AiSignalJobStatus.QUEUED || status == AiSignalJobStatus.RUNNING
    val canOpenValidatedSignal: Boolean get() = status == AiSignalJobStatus.DONE && result != null
}

data class AiSignalState(
    val submitting: Boolean = false,
    val refreshingQuota: Boolean = false,
    val job: AiSignalJob? = null,
    val quota: AiSignalQuota? = null,
    val entitlementRequired: Boolean = false,
    val quotaExhausted: Boolean = false,
    /** What the picker may offer, once the catalogue and the server's own list have been read. */
    val universe: AiSymbolUniverse = AiSymbolUniverse.EMPTY,
    /**
     * Owned copy, in the reader's language, plus the server's own words when it wrote any.
     *
     * This was a `String?` that the controller wrote authored **English** sentences into, and the
     * screen rendered verbatim to an audience whose default language is Persian — «ساخت ستاپ»
     * answered every press with "AI Signal request was rejected by server validation". [AiSignalError]
     * carries a reason the screen has Persian copy for, and the server's own `error_message` where
     * there was one, so a refusal reads as an explanation rather than as an exception.
     */
    val error: AiSignalError? = null,
)
