package com.coinepro.feature.screener

import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.feature.screener.model.ScreenerUnit
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * How one screener cell is written.
 *
 * ### Every number here is Latin, and that is not a styling choice
 *
 * A screener column is read by comparing it against another terminal — MetaTrader, LBank,
 * TradingView — and a price in Persian digits cannot be compared without converting it in the head.
 * So [Locale.US] is pinned in every format below, exactly as [MarketNumberFormatter] pins it and for
 * the same reason. The device locale is Persian and would otherwise emit «۹۱٬۲۴۸٫۳۰» silently, in a
 * column the reader is holding up against a chart.
 *
 * The *prose* around the table — the result count, a lesson number, an item position — is the other
 * half of that rule and stays in Persian digits. Nothing in this file formats prose.
 *
 * ### The unit decides the shape, not the magnitude
 *
 * A cell cannot work out what it is holding. `2.41` is a price on gold, a percentage on a day's
 * move and a bare RSI reading, and the only thing that knows which is the column it came from. So
 * every entry point takes a [ScreenerUnit] and there is exactly one place that turns a unit into a
 * suffix.
 */
object ScreenerFormat {

    /** What an unresolved cell shows. An em dash, which is what the market list already uses. */
    const val ABSENT: String = "—"

    /**
     * One cell of the table.
     *
     * Null is [ABSENT] rather than a zero, for the reason [com.coinepro.feature.screener.model.ScreenerRow]
     * gives at length: a zero volume is a claim that the market did not trade, and a screener that
     * makes that claim about four hundred instruments it has not read yet is lying in a column
     * people act on.
     */
    fun cell(value: Double?, unit: ScreenerUnit): String {
        val number = value?.takeIf(Double::isFinite) ?: return ABSENT
        return when (unit) {
            ScreenerUnit.PRICE -> MarketNumberFormatter.priceAuto(number)
            ScreenerUnit.PERCENT -> MarketNumberFormatter.signedPercent(number)
            ScreenerUnit.VOLUME -> volume(number)
            ScreenerUnit.PLAIN -> MarketNumberFormatter.price(number, decimals = 2)
            ScreenerUnit.TEXT -> ABSENT
        }
    }

    /**
     * A traded quantity, abbreviated once it stops fitting.
     *
     * Turnover on a liquid pair runs to ten figures, and a column of them at full width is a column
     * nothing else fits beside. The suffixes are the ones every exchange and every terminal uses, so
     * `1.24B` is read rather than decoded — translating them into Persian words would make the
     * column longer and less recognisable at once.
     *
     * Two significant decimals inside each band, so `1.24M` and `12.4M` and `124M` all occupy about
     * the same width and the column stays scannable.
     */
    fun volume(value: Double): String {
        val magnitude = abs(value)
        val (scaled, suffix) = when {
            magnitude >= 1_000_000_000_000.0 -> value / 1_000_000_000_000.0 to "T"
            magnitude >= 1_000_000_000.0 -> value / 1_000_000_000.0 to "B"
            magnitude >= 1_000_000.0 -> value / 1_000_000.0 to "M"
            magnitude >= 1_000.0 -> value / 1_000.0 to "K"
            else -> value to ""
        }
        val decimals = when {
            suffix.isEmpty() -> if (abs(scaled) >= 100.0) 0 else 2
            abs(scaled) >= 100.0 -> 0
            abs(scaled) >= 10.0 -> 1
            else -> 2
        }
        val pattern = if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        val formatted = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(scaled)
        // Isolated as one left-to-right run including the suffix. Without the isolate a Persian
        // paragraph reorders `1.24B` to `B1.24`, which is the same bug `MarketNumberFormatter.money`
        // documents for a currency sign.
        return BidiText.isolateLtr(formatted + suffix)
    }

    /**
     * A number as the reader typed it back into a filter row.
     *
     * Plain and ungrouped: this is echoed inside a sentence describing a condition — «تغییر روزانه
     * بیشتر از 3» — where a thousands separator reads as punctuation in the sentence rather than as
     * part of the number. A whole number loses its trailing `.0` for the same reason.
     */
    fun threshold(value: Double): String {
        if (!value.isFinite()) return ABSENT
        val text = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            DecimalFormat("0.####", DecimalFormatSymbols(Locale.US)).format(value)
        }
        return BidiText.isolateLtr(text)
    }
}
