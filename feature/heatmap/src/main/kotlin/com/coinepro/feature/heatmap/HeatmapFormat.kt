package com.coinepro.feature.heatmap

import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * How one figure is written on a tile or in the detail sheet.
 *
 * Every number here is Latin and dot-decimal, because every one of them is a market figure a reader
 * holds up against MetaTrader, LBank or TradingView. The prose around the map — how many markets,
 * how many of them are known — is the other half of that rule and stays in Persian digits; nothing
 * in this file formats prose.
 *
 * A minus sign is U+2212 rather than the hyphen `DecimalFormat` reaches for. At tile sizes the
 * hyphen is two pixels of grey that a reader does not register as a sign, and a market down four
 * percent printed as `4.00%` is the worst single failure this screen could have. Every negative
 * figure on the map goes through here for that reason.
 */
object HeatmapFormat {

    /** What an unknown figure shows. An em dash, which is what the market list already uses. */
    const val ABSENT: String = "—"

    /** The proper minus. Not the hyphen: see the object note. */
    private const val MINUS = '−'

    /**
     * A signed percentage, or [ABSENT].
     *
     * Isolated as one left-to-right run by [MarketNumberFormatter], and then the hyphen it produces
     * is exchanged for the real minus inside that run — which is safe precisely because the run is
     * isolated, so the substitution cannot alter how the surrounding Persian paragraph is ordered.
     */
    fun percent(value: Double?): String {
        val number = value?.takeIf(Double::isFinite) ?: return ABSENT
        return MarketNumberFormatter.signedPercent(number).replace('-', MINUS)
    }

    /** A price at the precision its own magnitude deserves, or [ABSENT]. */
    fun price(value: Double?): String {
        val number = value?.takeIf(Double::isFinite) ?: return ABSENT
        return MarketNumberFormatter.priceAuto(number).replace('-', MINUS)
    }

    /**
     * A traded quantity or value, abbreviated once it stops fitting.
     *
     * Turnover on a liquid pair runs to ten figures and there is no tile on a phone wide enough for
     * one. The suffixes are the ones every exchange and every terminal uses, so `1.24B` is read
     * rather than decoded — translating them into Persian words would make the figure longer and
     * less recognisable at once.
     */
    fun amount(value: Double?): String {
        val number = value?.takeIf { it.isFinite() } ?: return ABSENT
        val magnitude = abs(number)
        val (scaled, suffix) = when {
            magnitude >= 1_000_000_000_000.0 -> number / 1_000_000_000_000.0 to "T"
            magnitude >= 1_000_000_000.0 -> number / 1_000_000_000.0 to "B"
            magnitude >= 1_000_000.0 -> number / 1_000_000.0 to "M"
            magnitude >= 1_000.0 -> number / 1_000.0 to "K"
            else -> number to ""
        }
        val decimals = when {
            abs(scaled) >= 100.0 -> 0
            abs(scaled) >= 10.0 -> 1
            else -> 2
        }
        val pattern = if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        val formatted = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(scaled)
        // Isolated as one run including the suffix. Without the isolate a Persian paragraph
        // reorders `1.24B` to `B1.24`, which is the same fault `MarketNumberFormatter.money`
        // documents for a currency sign.
        return BidiText.isolateLtr((formatted + suffix).replace('-', MINUS))
    }

    /**
     * The figure a tile prints under its ticker, for the metric the map is coloured by.
     *
     * The unit follows the metric rather than the number: [HeatmapColour.RANGE] is a position on a
     * scale from the low to the high and is not a percentage of anything the reader would compare
     * against another screen, so it is written as a plain signed figure. Printing a percent sign on
     * it would invite exactly the comparison it does not support.
     */
    fun tileFigure(value: Double?, colour: HeatmapColour): String = when {
        value == null || !value.isFinite() -> ABSENT
        colour == HeatmapColour.RANGE -> BidiText.isolateLtr(
            DecimalFormat("0", DecimalFormatSymbols(Locale.US)).format(value).replace('-', MINUS),
        )
        else -> percent(value)
    }
}
