package com.coinepro.feature.papertrade

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.papertrade.PaperCloseReason
import com.coinepro.core.papertrade.PaperOrderType
import com.coinepro.core.papertrade.PaperReject
import com.coinepro.core.papertrade.PaperSide
import java.time.Instant
import java.time.ZoneId

/**
 * How this screen prints a number.
 *
 * Two rules from the house style are enforced here rather than at forty call sites. Market figures
 * are Latin-digit, because a trader compares them against MetaTrader and LBank and a Persian-digit
 * price cannot be compared against either; prose counts are Persian, because «۳ معامله» is a
 * sentence and «3 معامله» is a sentence with a foreign word in it.
 *
 * The minus sign is U+2212 and not the hyphen `MarketNumberFormatter` emits. A hyphen beside a
 * Persian label reads as a dash between two words — the reason the rest of this app uses the real
 * minus, and the substitution is done here so no screen has to remember.
 */
object PaperFormat {

    /** A dash, never a zero. A number that is not known is not a number that is nothing. */
    const val ABSENT = "—"

    fun price(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { minus(MarketNumberFormatter.priceAuto(it)) } ?: ABSENT

    fun money(value: Double?, signed: Boolean = false): String =
        value?.takeIf { it.isFinite() }
            ?.let { minus(MarketNumberFormatter.money(it, signed = signed)) }
            ?: ABSENT

    fun percent(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { minus(MarketNumberFormatter.signedPercent(it)) } ?: ABSENT

    /** A plain ratio — a profit factor, a payoff. Two decimals, no sign, no currency. */
    fun ratio(value: Double?): String =
        value?.takeIf { it.isFinite() }?.let { MarketNumberFormatter.price(it, 2) } ?: ABSENT

    fun size(value: Double): String = MarketNumberFormatter.price(value, decimalsFor(value))

    /** A count in prose. Persian digits, because it is being read as a word. */
    fun count(value: Int): String = value.toPersianDigits()

    fun moment(epochMillis: Long, zone: ZoneId): String = when {
        epochMillis <= 0L -> ABSENT
        else -> PersianDateTime.moment(Instant.ofEpochMilli(epochMillis), zone)
    }

    /** A ticker, isolated so it survives inside right-to-left copy. */
    fun ticker(symbol: String): String = BidiText.isolateLtr(symbol)

    @Composable
    fun sideLabel(side: PaperSide): String =
        stringResource(if (side == PaperSide.BUY) R.string.paper_buy else R.string.paper_sell)

    @Composable
    fun typeLabel(type: PaperOrderType): String = stringResource(
        when (type) {
            PaperOrderType.MARKET -> R.string.paper_type_market
            PaperOrderType.LIMIT -> R.string.paper_type_limit
            PaperOrderType.STOP -> R.string.paper_type_stop
            PaperOrderType.STOP_LIMIT -> R.string.paper_type_stop_limit
        },
    )

    @Composable
    fun reasonLabel(reason: PaperCloseReason): String = stringResource(
        when (reason) {
            PaperCloseReason.MANUAL -> R.string.paper_reason_manual
            PaperCloseReason.STOP_LOSS -> R.string.paper_reason_sl
            PaperCloseReason.TAKE_PROFIT -> R.string.paper_reason_tp
            PaperCloseReason.LIQUIDATION -> R.string.paper_reason_liq
            PaperCloseReason.REVERSE -> R.string.paper_reason_rev
        },
    )

    @Composable
    fun rejectLabel(reason: PaperReject): String = stringResource(
        when (reason) {
            PaperReject.MARGIN -> R.string.paper_reject_margin
            PaperReject.NO_PRICE -> R.string.paper_reject_price
            PaperReject.INVALID -> R.string.paper_reject_invalid
            PaperReject.NOTHING_TO_REDUCE -> R.string.paper_reject_reduce
        },
    )

    /**
     * The colour a result takes.
     *
     * Zero is muted rather than green. A scratch is not a win, and it is the same rule the record's
     * arithmetic keeps — see `PortfolioMath`, where a break-even trade is in neither column.
     */
    @Composable
    fun tone(value: Double?): Color = when {
        value == null || !value.isFinite() || value == 0.0 -> CoineProColors.TextMuted
        value > 0.0 -> CoineProColors.Buy
        else -> CoineProColors.Sell
    }

    @Composable
    fun sideTone(side: PaperSide): Color =
        if (side == PaperSide.BUY) CoineProColors.Buy else CoineProColors.Sell

    private fun minus(formatted: String): String = formatted.replace('-', '−')

    /**
     * Decimals for a quantity.
     *
     * A size is not a price and does not want a price's rule: 0.05 lots and 0.00042 BTC are both
     * ordinary, and both read as zero at two decimals.
     */
    private fun decimalsFor(value: Double): Int = when {
        kotlin.math.abs(value) >= 100.0 -> 2
        kotlin.math.abs(value) >= 1.0 -> 3
        else -> 5
    }
}
