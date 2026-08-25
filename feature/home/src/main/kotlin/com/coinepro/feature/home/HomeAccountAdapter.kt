package com.coinepro.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.coinepro.core.account.AccountPortfolio
import com.coinepro.core.account.BriefingState
import com.coinepro.core.account.PortfolioState
import com.coinepro.core.common.MarketNumberFormatter
import kotlin.math.abs

/**
 * Turns what the account controller knows into what the home screen draws.
 *
 * The screen's types carry finished strings, so this is where the server's numbers become text.
 * Doing it here rather than in the screen keeps one rule enforceable: every figure goes through
 * [MarketNumberFormatter], which isolates it as a left-to-right run. A number formatted inline in a
 * layout is the way that rule gets broken, and the symptom — `$12,480.35` rendering as
 * `12,480.35$` — only appears in Persian, which is the language most readers will see.
 */
@Composable
fun BriefingState.toHomeBriefing(nowEpochSeconds: Long): HomeBriefing = when (this) {
    BriefingState.Idle, BriefingState.Nothing -> HomeBriefing.Resting
    BriefingState.Loading -> HomeBriefing.Working
    is BriefingState.Unavailable -> HomeBriefing.Unavailable(reason)
    is BriefingState.Ready -> HomeBriefing.Ready(
        body = briefing.body,
        ageLabel = ageLabel(nowEpochSeconds - briefing.generatedAtEpochSeconds),
    )
}

/**
 * Null when there is nothing to show yet, which the screen renders as a dash.
 *
 * A portfolio whose total is null still returns null here on purpose: the server is saying it does
 * not know the balance, and the screen's dash says exactly that. Holdings without a total would
 * imply a balance the app would then have to leave blank in the one place a reader looks first.
 */
@Composable
fun PortfolioState.toHomePortfolio(): HomePortfolio? {
    val portfolio = (this as? PortfolioState.Ready)?.portfolio ?: return null
    val total = portfolio.total ?: return null
    // Resolved once, outside the loop: a resource lookup inside a lambda is the shape that stops
    // compiling the moment the lambda is no longer inlined into this composable's scope.
    val missing = stringResource(R.string.home_value_missing)

    return HomePortfolio(
        totalLabel = MarketNumberFormatter.money(total.amount, total.currency.symbol()),
        changeLabel = portfolio.changeLabel(),
        isUp = (portfolio.change?.amount ?: portfolio.change?.percent ?: 0.0) >= 0,
        holdings = portfolio.holdings.map { holding ->
            HomeHolding(
                symbol = holding.symbol,
                displayName = holding.displayName,
                quantityLabel = MarketNumberFormatter.quantity(
                    value = holding.quantity,
                    unit = holding.quantityUnit.orEmpty(),
                ),
                // Absent rather than zero: the position is real, its current worth is unknown, and
                // a 0.00 here would read as a position that has lost everything.
                valueLabel = holding.value
                    ?.let { MarketNumberFormatter.money(it, total.currency.symbol()) }
                    ?: missing,
                changeLabel = holding.changePercent
                    ?.let(MarketNumberFormatter::signedPercent)
                    ?: missing,
                isUp = (holding.changePercent ?: 0.0) >= 0,
            )
        },
    )
}

@Composable
@ReadOnlyComposable
private fun AccountPortfolio.changeLabel(): String {
    val change = change ?: return stringResource(R.string.home_value_missing)
    val symbol = total?.currency?.symbol() ?: "$"
    val amount = change.amount?.let { MarketNumberFormatter.money(it, symbol, signed = true) }
    val percent = change.percent?.let(MarketNumberFormatter::signedPercent)

    return when {
        amount != null && percent != null ->
            stringResource(R.string.home_change_amount_and_percent, amount, percent)
        amount != null -> amount
        percent != null -> percent
        else -> stringResource(R.string.home_value_missing)
    }
}

/**
 * How old the briefing's data is, in words.
 *
 * Rounded down and coarse on purpose. The figure is there to answer "can I still act on this",
 * which needs an honest order of magnitude rather than a precise second — and a precise second
 * would have to keep ticking, which is a continuous animation for no gain.
 */
@Composable
@ReadOnlyComposable
private fun ageLabel(ageSeconds: Long): String {
    val age = abs(ageSeconds)
    return when {
        age < 60 -> stringResource(R.string.home_age_moments)
        age < 3_600 -> stringResource(R.string.home_age_minutes, age / 60)
        age < 86_400 -> stringResource(R.string.home_age_hours, age / 3_600)
        else -> stringResource(R.string.home_age_days, age / 86_400)
    }
}

/**
 * The currency's symbol where one is well known, and the code itself otherwise.
 *
 * Falling back to the code rather than to `$` — labelling euros or rials with a dollar sign is a
 * wrong number, not a cosmetic slip.
 */
private fun String.symbol(): String = when (uppercase()) {
    "USD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    else -> "${uppercase()} "
}
