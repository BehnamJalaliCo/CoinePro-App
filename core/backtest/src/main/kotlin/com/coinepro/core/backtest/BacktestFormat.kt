package com.coinepro.core.backtest

import com.coinepro.core.common.BidiText
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.common.MarketNumberFormatter
import java.time.Instant
import java.time.ZoneId

/**
 * Rendering a metric, including the cases where there is no metric to render.
 *
 * ### The dash, and why it is the whole point of this file
 *
 * A profit factor is gross profit over gross loss. A run that never lost divides by zero, and the
 * engine answers `Double.POSITIVE_INFINITY` — correctly, because that is what the arithmetic says.
 * Every way of printing that number is a lie. `∞` reads as a perfect strategy. A very large number
 * reads as a spectacular one. A blank cell hides a result the reader should see, and a zero says
 * the opposite of what happened. The one honest rendering is a dash: *there is no ratio here*, go
 * and read the trade count beside it, which is the number that actually settles it — because a run
 * with no losing trade is nearly always a run with three trades.
 *
 * The same applies to every metric that can divide by zero: win/loss ratio with no losers, Sortino
 * with no negative bar, average loss on a run that never lost. Each is routed through the functions
 * below rather than formatted at the call site, so no screen can print an infinity by forgetting.
 *
 * ### Latin digits, `Locale.US`, and no exceptions
 *
 * Every function here delegates to [MarketNumberFormatter], which pins the locale. The device
 * locale in this app is Persian, and an unqualified `String.format` renders `12.5` as «۱۲٫۵» —
 * silently, in a column the reader is comparing against Binance. Dates are the deliberate
 * exception: a date is prose, not a market figure, and [dateRange] renders it in the Jalali
 * calendar with Persian digits because that is the calendar the reader has in mind.
 */
object BacktestFormat {

    /**
     * What is printed where a metric does not exist.
     *
     * An em dash, not a hyphen and not "N/A": it is a typographic mark for an absent value that
     * reads as absent at a glance, and it cannot be mistaken for a minus sign in a column of
     * signed figures the way a hyphen can.
     */
    const val ABSENT = "—"

    /** Whether a metric is a number at all. Infinity and `NaN` are both "no metric". */
    fun exists(value: Double): Boolean = value.isFinite()

    /** A bare ratio — profit factor, win/loss, Sharpe, Sortino — or [ABSENT]. */
    fun ratio(value: Double, decimals: Int = 2): String =
        if (!exists(value)) ABSENT else MarketNumberFormatter.price(value, decimals)

    /** A currency amount, signed where a sign carries meaning, or [ABSENT]. */
    fun money(value: Double, signed: Boolean = false, decimals: Int = 2): String =
        if (!exists(value)) {
            ABSENT
        } else {
            MarketNumberFormatter.money(value, currencySymbol = "", decimals = decimals, signed = signed)
        }

    /** A signed percentage, `+12.34%`, or [ABSENT]. */
    fun signedPercent(value: Double): String =
        if (!exists(value)) ABSENT else MarketNumberFormatter.signedPercent(value)

    /**
     * An unsigned percentage — a win rate, a drawdown depth — or [ABSENT].
     *
     * Unsigned because the sign would be noise: a drawdown is stated as a magnitude and labelled as
     * a fall, and printing it as a negative invites a reader to subtract it twice.
     */
    fun percent(value: Double, decimals: Int = 2): String =
        if (!exists(value)) ABSENT else BidiText.isolateLtr(rawPercent(value, decimals))

    /** A whole count — trades, bars, wins — in Latin digits with thousands separators. */
    fun count(value: Int): String = MarketNumberFormatter.price(value.toDouble(), 0)

    /** A fractional bar count, one decimal. Averages of bars held are rarely whole. */
    fun bars(value: Double): String =
        if (!exists(value)) ABSENT else MarketNumberFormatter.price(value, 1)

    /**
     * The percentage text without the bidirectional isolate, for a file rather than a screen.
     *
     * A CSV cell must contain the digits and nothing else: the isolate characters [percent] adds
     * are invisible in a terminal and are two extra codepoints inside a spreadsheet cell, which is
     * enough to make the column text rather than numbers.
     */
    fun rawPercent(value: Double, decimals: Int = 2): String =
        MarketNumberFormatter.price(value, decimals).let(BidiText::strip) + "%"

    /**
     * The dates a run covered, in Jalali, as one phrase.
     *
     * Persian digits, deliberately, against the rule that governs everything else in this file: a
     * date is not a figure a reader compares against another terminal, it is the day they remember
     * an announcement by. «۱۲ مرداد ۱۴۰۳ تا ۲۹ شهریور ۱۴۰۳» is what they would say out loud.
     *
     * Zero timestamps — an empty series — produce an empty string rather than the Jalali epoch,
     * which would be a real-looking date in the year 1348.
     */
    fun dateRange(fromEpochSeconds: Long, toEpochSeconds: Long, zone: ZoneId): String {
        if (fromEpochSeconds <= 0 || toEpochSeconds <= 0) return ""
        val from = JalaliDate.fromInstant(Instant.ofEpochSecond(fromEpochSeconds), zone)
        val to = JalaliDate.fromInstant(Instant.ofEpochSecond(toEpochSeconds), zone)
        return "${from.format()} تا ${to.format()}"
    }
}
