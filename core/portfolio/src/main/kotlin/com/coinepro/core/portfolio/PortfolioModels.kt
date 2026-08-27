package com.coinepro.core.portfolio

import kotlin.math.abs

/**
 * One closed trade, in the single shape both backends' histories map onto.
 *
 * The two are genuinely different ledgers and the fields say so rather than papering over it.
 * CoinePro-FX reports a broker's books: gross profit, commission and swap as three separate signed
 * numbers, plus the account balance after the trade. TradeYar reports an exchange's: one profit
 * figure that LBank itself computed, one fee, and no running balance anywhere — its balance table
 * is an upsert with one row per user, so there is no history to read.
 *
 * Everything nullable is nullable because one of them really can leave it out. In particular
 * [entry] and [openedAt] are null on a TradeYar trade whose opening leg fell before the requested
 * window: the server does not know, and said so rather than guessing.
 */
data class ClosedTrade(
    val id: String,
    val symbol: String,
    val direction: TradeDirection,
    val volume: Double?,
    val entry: Double?,
    val exit: Double?,
    /** Unix seconds. Null when the opening leg is outside the window the server could see. */
    val openedAt: Long?,
    val closedAt: Long,
    /**
     * Profit before costs. CoinePro-FX only — LBank reports one figure and it is already net of
     * nothing in particular, so inventing a gross for it would be inventing a number.
     */
    val grossProfit: Double? = null,
    val commission: Double? = null,
    val swap: Double? = null,
    /**
     * What the trade actually made or lost.
     *
     * The one field every screen reads, and the one both servers really send. On CoinePro-FX it is
     * `net_profit`, which their team confirmed is `gross + commission + swap` with both costs
     * stored negative. On TradeYar it is `closeProfit` straight from the exchange, which is why
     * it can be trusted against a statement: it *is* the statement's number.
     */
    val netProfit: Double?,
    val pips: Double? = null,
    /**
     * The broker's word for why the position closed — `sl`, `manual`, and whatever else appears.
     *
     * Deliberately a string. CoinePro-FX's team said the list is not closed and its source is the
     * broker's report, so an enum here would turn a new value into a crash or a silent "unknown".
     *
     * Never read this as the result. Their own live sample has an `sl` close with **+19.56** net
     * profit, because the stop had trailed above entry. Win and loss come from [netProfit]'s sign.
     */
    val closeReason: String? = null,
    /** MT5's real account balance after this trade. CoinePro-FX only. */
    val balanceAfter: Double? = null,
    /** LBank marked the close as forced. TradeYar only. */
    val liquidated: Boolean = false,
    val currency: String? = null,
) {
    /** Costs as one number, or null when neither is reported. */
    val costs: Double?
        get() = when {
            commission == null && swap == null -> null
            else -> (commission ?: 0.0) + (swap ?: 0.0)
        }

    val isWin: Boolean get() = (netProfit ?: 0.0) > 0.0
    val isLoss: Boolean get() = (netProfit ?: 0.0) < 0.0

    /** How long it was open, in seconds, when both ends are known. */
    val durationSeconds: Long?
        get() = openedAt?.let { closedAt - it }
}

enum class TradeDirection { BUY, SELL;

    companion object {
        /** Tolerant, because the two servers spell it `buy`/`sell` and `long`/`short`. */
        fun of(wire: String?): TradeDirection? = when (wire?.trim()?.lowercase()) {
            "buy", "long" -> BUY
            "sell", "short" -> SELL
            else -> null
        }
    }
}

/**
 * One point on the curve.
 *
 * [equity] means whichever of the two curves this is — see [PortfolioStats.equityIsBalance]. The
 * difference matters: a cumulative-profit curve starts at zero and a balance curve starts at
 * whatever was in the account, and reading one as the other makes a 5% drawdown look like a 90%
 * one.
 */
data class EquityPoint(val time: Long, val equity: Double)

/**
 * Everything the portfolio screen shows, computed here rather than read from a server.
 *
 * Both backends will happily compute some of this. They are not asked to, for one reason: they do
 * not agree. CoinePro-FX's `/stats` counts a zero-profit trade as neither a win nor a loss, and
 * its `max_drawdown_rel_pct` divides by the peak of a profit-from-zero curve — which their own
 * team flagged as producing values like 312%. Doing the arithmetic once, here, means the two
 * platforms show the same statistic under the same word, and means the app never has to print a
 * number it cannot explain.
 */
data class PortfolioStats(
    val trades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val net: Double = 0.0,
    val gross: Double? = null,
    val costs: Double? = null,
    val best: Double? = null,
    val worst: Double? = null,
    /** The winners added up, and the losers added up as a positive magnitude. */
    val grossWin: Double = 0.0,
    val grossLoss: Double = 0.0,
    val equity: List<EquityPoint> = emptyList(),
    /**
     * Whether [equity] is real account balance or cumulative profit from zero.
     *
     * True only when every trade in the window carried a balance, which is CoinePro-FX with a live
     * MT5 account. A curve stitched from some balances and some running totals would be a line
     * with a step in it that means nothing.
     */
    val equityIsBalance: Boolean = false,
) {
    /** Wins over decided trades. A break-even trade is in neither column and in neither total. */
    val winRate: Double?
        get() = (wins + losses).takeIf { it > 0 }?.let { wins.toDouble() / it * 100.0 }

    /**
     * Gross win over gross loss.
     *
     * Null with no losses rather than infinity: "∞" printed beside a win rate is read as a bug,
     * and a run of four winners is not evidence of an infinite edge.
     */
    val profitFactor: Double? get() = grossLoss.takeIf { it > 0.0 }?.let { grossWin / it }

    /** Average net per trade. */
    val expectancy: Double? get() = trades.takeIf { it > 0 }?.let { net / it }

    /**
     * The deepest fall from a peak, in currency.
     *
     * Absolute only. The percentage needs a denominator, and the only honest one is account
     * balance — which exists on one platform. Where it does, [maxDrawdownPercent] gives it; where
     * it does not, it is null rather than divided by a cumulative profit that can be near zero.
     */
    val maxDrawdown: Double get() = drawdown.first

    val maxDrawdownPercent: Double? get() = if (equityIsBalance) drawdown.second else null

    private val drawdown: Pair<Double, Double?> by lazy {
        if (equity.isEmpty()) return@lazy 0.0 to null
        var peak = equity.first().equity
        var deepest = 0.0
        var deepestRelative: Double? = null
        for (point in equity) {
            if (point.equity > peak) peak = point.equity
            val fall = peak - point.equity
            if (fall > deepest) {
                deepest = fall
                deepestRelative = if (abs(peak) > 0.0) fall / abs(peak) * 100.0 else null
            }
        }
        deepest to deepestRelative
    }
}

/** One symbol's share of the result, for the attribution list. */
data class SymbolPerformance(
    val symbol: String,
    val trades: Int,
    val wins: Int,
    val net: Double,
) {
    val winRate: Double? get() = trades.takeIf { it > 0 }?.let { wins.toDouble() / it * 100.0 }
}

/** One month's total, in the reader's own time zone and the reader's own calendar. */
data class MonthlyPerformance(
    /** Solar Hijri, e.g. 1405. */
    val year: Int,
    /**
     * 1–12, Solar Hijri — Farvardin is 1.
     *
     * Not Gregorian. A Jalali month spans two Gregorian ones, so a Gregorian bucket under a Persian
     * month name would attribute three weeks of trades to the wrong month. See `PortfolioMath.byMonth`.
     */
    val month: Int,
    val trades: Int,
    val net: Double,
)
