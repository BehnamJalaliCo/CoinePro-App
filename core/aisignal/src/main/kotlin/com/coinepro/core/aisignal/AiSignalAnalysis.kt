package com.coinepro.core.aisignal

/**
 * The technical picture the model was given before it produced a setup.
 *
 * The server computes this and returns it alongside every result. It was previously discarded on
 * the client, which left the AI screen showing a verdict with nothing behind it. Surfacing it is
 * what lets the screen justify the setup instead of asking for trust.
 *
 * Every field is optional because the server omits what it could not compute for a short series —
 * a missing indicator must read as missing, never as zero.
 */
data class AiTechnicalSnapshot(
    val ema20: Double? = null,
    val ema50: Double? = null,
    val ema200: Double? = null,
    val rsi14: Double? = null,
    val atr14: Double? = null,
    val macd: Double? = null,
    val bollingerUpper: Double? = null,
    val bollingerLower: Double? = null,
    val swingHigh20: Double? = null,
    val swingLow20: Double? = null,
    val changePercent20: Double? = null,
    val priceNow: Double? = null,
) {
    val hasAny: Boolean
        get() = listOf(ema20, ema50, ema200, rsi14, atr14, macd, priceNow).any { it != null }

    /** Where price sits between the 20-bar swing low and high, or null if the range is unusable. */
    val swingPosition: Double?
        get() {
            val low = swingLow20 ?: return null
            val high = swingHigh20 ?: return null
            val price = priceNow ?: return null
            val span = high - low
            if (span <= 0.0) return null
            return ((price - low) / span).coerceIn(0.0, 1.0)
        }
}

/** One candle of the recent series the model reasoned over. */
data class AiCandle(
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    /**
     * Bar open time in unix seconds, or null when the server did not say.
     *
     * Nullable because only one of the two does. TradeYar sends `t` in milliseconds; CoinePro-FX's
     * evidence block is twelve bars of open/high/low/close with no time on them at all. Rather than
     * invent a spacing for the second case, the chart drops its time axis — see
     * `ChartDecoration.showTimeAxis`. A candle is still a candle without a date on it.
     */
    val time: Long? = null,
)

/**
 * How the user wants the setup shaped. The server accepts all of these; the client previously sent
 * only symbol, timeframe and risk, so the remaining controls had no way to reach the model.
 */
enum class AiTradeStyle(val wireValue: String) {
    SCALP("scalp"),
    INTRADAY("intraday"),
    SWING("swing"),
}

enum class AiRiskAppetite(val wireValue: String) {
    CONSERVATIVE("conservative"),
    BALANCED("balanced"),
    AGGRESSIVE("aggressive"),
}

enum class AiDirectionBias(val wireValue: String) {
    AUTO("auto"),
    LONG("long"),
    SHORT("short"),
}
