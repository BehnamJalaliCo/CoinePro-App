package com.coinepro.app.pulse

import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier

/** One price, when it was seen. */
data class PulseSample(val atMillis: Long, val price: Double)

/** A move worth a notification: [percent] signed, from [fromPrice] to [toPrice] over [windowMillis]. */
data class PulseMove(
    val symbol: String,
    val percent: Double,
    val fromPrice: Double,
    val toPrice: Double,
    val windowMillis: Long,
)

/**
 * The market notifications' rules (5.27.0), apart from anything Android, so every number in them
 * is pinned by a test.
 *
 * The owner's «متعادل»: bitcoin at ±3 %, a starred market at ±5 %, gold at ±1.5 %, a major pair at
 * ±0.7 % — each measured inside a four-hour window, so a move that took a week is not news this
 * morning. At most six of these a day, none between 23:00 and 08:00, and one market is not
 * announced twice inside three hours.
 */
object MarketPulse {

    /** How far back a move is measured from. */
    const val WINDOW_MILLIS: Long = 4L * 60 * 60 * 1000

    /** One market, one notification, per this long. */
    const val COOLDOWN_MILLIS: Long = 3L * 60 * 60 * 1000

    /** The day's ceiling, across moves, the summary and the calendar together. */
    const val DAILY_CAP: Int = 6

    /** Quiet from 23:00 to 08:00, local. */
    const val QUIET_FROM_MINUTE: Int = 23 * 60
    const val QUIET_TO_MINUTE: Int = 8 * 60

    /** The summary goes out at the first run after this, local. */
    const val DIGEST_MINUTE: Int = 9 * 60

    /** A calendar release is announced when it is at most this far off. */
    const val CALENDAR_LEAD_MILLIS: Long = 20L * 60 * 1000

    const val BITCOIN = "BTCUSDT"

    /** The markets a sharp-move notification watches whatever the reader starred. */
    val MAJORS: List<String> = listOf(BITCOIN, "XAUUSD", "EURUSD")

    /** The summary's four tiles. */
    val DIGEST_MAJORS: List<String> = listOf(BITCOIN, "ETHUSDT", "XAUUSD", "EURUSD")

    /** The move, in percent, that [symbol] has to make to be announced. */
    fun thresholdFor(symbol: String): Double {
        val upper = symbol.uppercase()
        if (upper == BITCOIN) return 3.0
        return when (SymbolClassifier.classify(upper).category) {
            SymbolCategory.METAL -> 1.5
            SymbolCategory.FOREX -> 0.7
            SymbolCategory.INDEX, SymbolCategory.ENERGY -> 1.5
            SymbolCategory.CRYPTO, SymbolCategory.OTHER -> 5.0
        }
    }

    /**
     * Adds [price] to [samples] and says whether the window now holds a move past [threshold].
     *
     * A move is measured from the window's low (a rise) or its high (a fall) to now. When one is
     * found the window starts again from now, so the same climb is not announced on every run.
     */
    fun observe(
        symbol: String,
        samples: List<PulseSample>,
        nowMillis: Long,
        price: Double,
        threshold: Double,
    ): Pair<List<PulseSample>, PulseMove?> {
        if (price <= 0.0 || price.isNaN()) return samples to null
        val kept = (samples.filter { nowMillis - it.atMillis in 0..WINDOW_MILLIS } + PulseSample(nowMillis, price))
        val low = kept.minBy { it.price }
        val high = kept.maxBy { it.price }
        val rise = (price - low.price) / low.price * 100.0
        val fall = (price - high.price) / high.price * 100.0
        val move = when {
            rise >= threshold && rise >= -fall -> PulseMove(symbol, rise, low.price, price, nowMillis - low.atMillis)
            -fall >= threshold -> PulseMove(symbol, fall, high.price, price, nowMillis - high.atMillis)
            else -> null
        }
        return if (move != null) listOf(PulseSample(nowMillis, price)) to move else kept.takeLast(MAX_SAMPLES) to null
    }

    /** Whether [minuteOfDay] falls in the night the owner asked to keep quiet. */
    fun quiet(minuteOfDay: Int): Boolean = minuteOfDay >= QUIET_FROM_MINUTE || minuteOfDay < QUIET_TO_MINUTE

    /** Whether the summary is due: past its hour, not yet sent today, and not in the quiet night. */
    fun digestDue(minuteOfDay: Int, today: Long, lastDigestDay: Long?): Boolean =
        minuteOfDay >= DIGEST_MINUTE && !quiet(minuteOfDay) && lastDigestDay != today

    /** Fifteen-minute runs over four hours, and a little more. */
    private const val MAX_SAMPLES = 24
}
