package com.coinepro.core.notifications

import com.coinepro.core.common.BidiText
import com.coinepro.core.common.PersianDateTime
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * The reader's own wording for an alert, with the facts filled in.
 *
 * ### Why an alert carries a message at all
 *
 * By the time an alert fires, the reason it was made has usually been forgotten. "BTCUSDT reached
 * 65,000" is a fact; "the level I said I would sell half at" is the reason, and only the reader can
 * write it. A notification that arrives during a move and does not say what to do about it costs a
 * second of thinking that the reader does not have.
 *
 * ### Four placeholders, and no expression language
 *
 * `{symbol}`, `{price}`, `{time}` and `{tf}`. Not a templating language, not arithmetic, not
 * conditionals — because every one of those turns a note to oneself into a thing that can be wrong,
 * and a notification that renders `{{price|round(2)}}` in front of somebody at the moment their
 * level is hit is worse than one with no message at all. An unknown placeholder is left exactly as
 * it was typed rather than blanked, so a typo looks like a typo instead of like a missing price.
 *
 * ### The digits are Latin, deliberately
 *
 * Persian is this app's default locale, and `String.format` with the default locale renders `۶۵۰۰۰`
 * for a price. That has already caused one bug in this repository. A market figure is Latin here as
 * it is everywhere else in the app, so every number below goes through [Locale.US] and the clock
 * goes through [PersianDateTime.clock], which is Latin for the same reason.
 *
 * ### And every Latin run is bidi-isolated
 *
 * The message around them is Persian and right-to-left, and an un-isolated Latin run inside it
 * reorders against its neighbours — a price and a percentage side by side can render with the signs
 * against the wrong numbers. [BidiText.isolateLtr] is what the rest of the app uses at the moment
 * of display, and rendering a notification body is that moment. The characters it adds are
 * zero-width formatting marks; [BidiText.strip] removes them where a test or a comparison wants the
 * bare text.
 */
object AlertMessageTemplate {

    /** The instrument the alert is on, exactly as the alert stores it. */
    const val SYMBOL = "{symbol}"

    /** The price that satisfied the condition, formatted for its own magnitude. */
    const val PRICE = "{price}"

    /** Clock time in the reader's own zone, `14:30`, Latin. */
    const val TIME = "{time}"

    /** The timeframe the alert was evaluated on, such as `1h`. */
    const val TIMEFRAME = "{tf}"

    /** Every placeholder, for a screen that wants to offer them as chips rather than as prose. */
    val PLACEHOLDERS: List<String> = listOf(SYMBOL, PRICE, TIME, TIMEFRAME)

    /**
     * How long a reader's message may be.
     *
     * A notification body is truncated by the system somewhere past this anyway, and the whole
     * point of the message is to be read at a glance during a move. Enforced by the screen; this
     * constant is here so that the screen and the store agree on one number.
     */
    const val MAX_LENGTH = 160

    /**
     * The message as the reader will see it.
     *
     * Pure and total. [at] is a parameter rather than a clock read so that a fired alert can be
     * re-rendered later — in the audit log, say — and produce exactly the text that was sent, and
     * [zone] is a parameter for the same reason a clock is: it makes the rendering testable and
     * keeps the ambient default out of the function's meaning.
     *
     * A null or blank message falls back to the symbol and the price and nothing else. Deliberately
     * not a Persian sentence: this string is also what a screen shows as a preview and what the
     * audit log stores, and a fallback carrying its own prose would put words in the reader's mouth
     * every time they cleared the field. The Persian wording of an empty alert belongs to the
     * notification the app builds around this, where it can be a string resource.
     */
    fun render(
        message: String?,
        symbol: String,
        price: Double,
        at: Long,
        timeframe: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val isolatedSymbol = BidiText.isolateLtr(symbol)
        val isolatedPrice = BidiText.isolateLtr(formatPrice(price))
        val template = message?.takeIf(String::isNotBlank)
            ?: return "$isolatedSymbol $isolatedPrice"
        return template
            .replace(SYMBOL, isolatedSymbol)
            .replace(PRICE, isolatedPrice)
            // Already isolated by PersianDateTime, which returns a clock for exactly this purpose.
            .replace(TIME, PersianDateTime.clock(Instant.ofEpochMilli(at), zone))
            .replace(TIMEFRAME, BidiText.isolateLtr(timeframe))
    }

    /**
     * A price with as many decimals as it is worth showing, grouped, in Latin digits.
     *
     * The bands exist because this app lists instruments four orders of magnitude apart. Two
     * decimals on a token priced at 0.000031 renders `0.00`, which reads as an outage; eight
     * decimals on Bitcoin renders a number nobody can scan. The thresholds are the same ones the
     * rest of the app uses for the same reason.
     */
    fun formatPrice(price: Double): String {
        if (price.isNaN() || price.isInfinite()) return "-"
        val magnitude = abs(price)
        val pattern = when {
            magnitude >= 1_000.0 -> "%,.2f"
            magnitude >= 1.0 -> "%.2f"
            magnitude >= 0.01 -> "%.4f"
            else -> "%.8f"
        }
        return String.format(Locale.US, pattern, price)
    }
}
