package com.coinepro.feature.portfolio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProPageHeading
import com.coinepro.core.designsystem.CoineProHeroFigure
import com.coinepro.core.designsystem.CoineProReading
import com.coinepro.core.designsystem.CoineProReadingRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.portfolio.ClosedTrade
import com.coinepro.core.portfolio.MonthlyPerformance
import com.coinepro.core.portfolio.PortfolioController
import com.coinepro.core.portfolio.PortfolioError
import com.coinepro.core.portfolio.PortfolioStats
import com.coinepro.core.portfolio.PortfolioUiState
import com.coinepro.core.portfolio.PortfolioWindow
import com.coinepro.core.portfolio.SymbolPerformance
import com.coinepro.core.portfolio.TradeDirection
import java.time.Instant
import java.time.ZoneId


/**
 * What the account has actually done.
 *
 * The screen is built around one honest limitation, stated rather than hidden: on the crypto side
 * there is no balance history anywhere — TradeYar's balance table holds one row per user, not a
 * series — so the curve there is realised profit from zero and says so under its own heading. On
 * the forex side every trade carries the broker's real balance, so the curve is the account, and
 * the drawdown percentage that follows from it is a figure worth printing.
 *
 * Nothing here is read from a server's statistics endpoint. Both have one and they do not agree
 * with each other; the arithmetic is in `PortfolioMath` so that one word means one thing.
 */
@Composable
fun PortfolioScreen(
    controller: PortfolioController,
    /** Opens the connections screen. Null hides the offer, on a platform that has no such screen. */
    onOpenConnections: (() -> Unit)? = null,
    /**
     * Opens the full report — the metric set, the attribution, the monthly matrix and the exports.
     *
     * Null simply omits the link rather than disabling it. A visible control that does nothing is
     * worse than an absent one, and a host that has not registered the report route has no screen
     * to send the reader to.
     */
    onOpenReport: (() -> Unit)? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
        WindowChips(state.window, controller::setWindow)
        CoineProTeachingStrip(TeachingSurface.PORTFOLIO)
        when {
            state.loading && state.trades.isEmpty() -> Centre { CoineProThinkingDots() }
            state.error != null && state.trades.isEmpty() ->
                Centre { Failure(state.error!!, controller::retry, onOpenConnections) }
            state.trades.isEmpty() -> Centre {
                Text(
                    text = stringResource(R.string.portfolio_empty),
                    color = CoineProColors.TextSecondary,
                )
            }
            else -> Content(state, controller::loadMore, onOpenReport, zone)
        }
    }
}

@Composable
private fun WindowChips(selected: PortfolioWindow, onSelect: (PortfolioWindow) -> Unit) {
    val options = listOf(
        CoineProChip(PortfolioWindow.WEEK.name, stringResource(R.string.portfolio_window_week)),
        CoineProChip(PortfolioWindow.MONTH.name, stringResource(R.string.portfolio_window_month)),
        CoineProChip(PortfolioWindow.ALL.name, stringResource(R.string.portfolio_window_all)),
    )
    CoineProChipRow(
        options = options,
        selectedId = selected.name,
        // Null means the reader tapped the selected chip again. There is no "no window" state to
        // fall back to, and refetching a cold LBank walk for a tap that changed nothing would be
        // seventeen seconds spent on a no-op.
        onSelect = { id -> id?.let { onSelect(PortfolioWindow.valueOf(it)) } },
        modifier = Modifier.padding(vertical = CoineProSpacing.OneHalf),
    )
}

@Composable
private fun Content(
    state: PortfolioUiState,
    onLoadMore: () -> Unit,
    onOpenReport: (() -> Unit)?,
    zone: ZoneId,
) {
    // The best rise and the deepest fall over exactly the trades this screen is showing — the same
    // window the summary above the list is computed over, so a row marked «در بیشترین رشد» is
    // marked against the figures the reader can see rather than against a history they cannot.
    val spans = remember(state.trades) { PortfolioMetrics.of(state.trades) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        // Before the numbers, not after them. A caveat under a total is a caveat most readers
        // never see, and both of these change what the total means.
        state.servedWindow?.let { window ->
            item { NarrowedWindowNote(window, zone) }
        }
        if (state.truncated) {
            item { Caveat(stringResource(R.string.portfolio_truncated)) }
        }
        item { Headline(state.stats) }
        item { CurveCard(state.stats) }
        item { StatsGrid(state.stats) }
        // Directly under the summary figures, because that is where a reader who wants more of
        // them looks. Below the trade list it would be a link nobody scrolls to.
        onOpenReport?.let { open ->
            item {
                CoineProSecondaryButton(
                    text = stringResource(R.string.portfolio_report_open),
                    onClick = open,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.byMonth.size > 1) {
            item { MonthsCard(state.byMonth, zone) }
        }
        if (state.bySymbol.isNotEmpty()) {
            item { SymbolsCard(state.bySymbol) }
        }
        item { CardLabel(stringResource(R.string.portfolio_recent)) }
        // Keyed, so paging in older trades does not re-compose every row already on screen.
        items(state.trades, key = { it.id }) { trade ->
            TradeRow(
                trade = trade,
                // Which stretch of the curve this trade fell in. Computed once for the whole list
                // above and passed down as two booleans, so a row costs nothing to decide.
                inRunUp = spans.runUp?.covers(trade.closedAt) == true,
                inDrawdown = spans.drawdown?.let { trade.closedAt in it.peakAt..it.troughAt } == true,
                zone = zone,
            )
        }
        if (state.hasMore) {
            item {
                CoineProSecondaryButton(
                    text = stringResource(R.string.portfolio_load_more),
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * The gold voice: this screen shows one thing, so the net result gets a heading and the size that
 * says it is the subject, with the three figures a reader checks beside it.
 *
 * Not a card any more. A card around the page's own headline made it read as one panel among the
 * panels below it, when it is what the rest of the page explains.
 */
@Composable
private fun Headline(stats: PortfolioStats) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CoineProPageHeading(
            title = stringResource(R.string.portfolio_net),
            eyebrow = stringResource(R.string.portfolio_eyebrow),
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        CoineProHeroFigure(
            figure = MarketNumberFormatter.money(stats.net, signed = true),
            captionColour = resultColour(stats.net),
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        CoineProReadingRow(
            readings = listOfNotNull(
                CoineProReading(
                    label = stringResource(R.string.portfolio_trades),
                    // Latin digits: this is a market figure, not a prose count.
                    value = BidiText.isolateLtr("${stats.trades}"),
                ),
                stats.winRate?.let {
                    CoineProReading(
                        label = stringResource(R.string.portfolio_win_rate),
                        value = BidiText.isolateLtr(MarketNumberFormatter.price(it, 1) + "%"),
                        tone = if (it >= 50.0) CoineProColors.Buy else CoineProColors.Sell,
                    )
                },
                stats.profitFactor?.let {
                    CoineProReading(
                        label = stringResource(R.string.portfolio_reading_factor),
                        value = BidiText.isolateLtr(MarketNumberFormatter.price(it, 2)),
                        tone = if (it >= 1.0) CoineProColors.Buy else CoineProColors.Sell,
                    )
                },
            ),
            modifier = Modifier.padding(horizontal = 0.dp),
        )
    }
}

@Composable
private fun CurveCard(stats: PortfolioStats) {
    if (stats.equity.size < 2) return
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(
            stringResource(
                if (stats.equityIsBalance) R.string.portfolio_curve_balance
                else R.string.portfolio_curve_profit,
            ),
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        EquityCurve(points = stats.equity, fromZero = !stats.equityIsBalance)
        if (!stats.equityIsBalance) {
            Spacer(Modifier.height(CoineProSpacing.One))
            Text(
                text = stringResource(R.string.portfolio_curve_profit_note),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun StatsGrid(stats: PortfolioStats) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        StatRow(stringResource(R.string.portfolio_profit_factor), stats.profitFactor?.let {
            MarketNumberFormatter.price(it, 2)
        })
        StatRow(stringResource(R.string.portfolio_expectancy), stats.expectancy?.let {
            MarketNumberFormatter.money(it, signed = true)
        }, tint = stats.expectancy)
        StatRow(
            label = stringResource(R.string.portfolio_max_drawdown),
            // The percentage only where there is a balance to divide by. On a profit-from-zero
            // curve the denominator can be near zero, which is how CoinePro-FX's own report ends
            // up printing 312%.
            value = stats.maxDrawdown.takeIf { it > 0.0 }?.let { fall ->
                val money = MarketNumberFormatter.money(-fall, signed = true)
                stats.maxDrawdownPercent?.let { percent ->
                    // One isolate around the whole thing, not two side by side. Two adjacent
                    // left-to-right runs in a right-to-left paragraph are laid out right to left
                    // *as runs*, so the bracket lands before the amount and the parentheses mirror
                    // — "‎-$4,475.13 (9.6%)" renders as "9.6%)( -$4,475.13".
                    BidiText.isolateLtr(
                        BidiText.strip(money) + " (" +
                            BidiText.strip(MarketNumberFormatter.price(percent, 1)) + "%)",
                    )
                } ?: money
            },
            tint = -1.0,
        )
        StatRow(stringResource(R.string.portfolio_best), stats.best?.let {
            MarketNumberFormatter.money(it, signed = true)
        }, tint = stats.best)
        StatRow(stringResource(R.string.portfolio_worst), stats.worst?.let {
            MarketNumberFormatter.money(it, signed = true)
        }, tint = stats.worst)
        stats.costs?.let {
            StatRow(stringResource(R.string.portfolio_costs), MarketNumberFormatter.money(it, signed = true))
        }
    }
}

@Composable
private fun MonthsCard(months: List<MonthlyPerformance>, zone: ZoneId) {
    // The month is already Solar Hijri — PortfolioMath buckets by it — so the label is simply its
    // name. No conversion here, and deliberately none: a label computed from a Gregorian month
    // would disagree with the bucket it sits under.
    val labels = months.map { month -> JalaliDate(month.year, month.month, 1).monthName }
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.portfolio_by_month))
        Spacer(Modifier.height(CoineProSpacing.One))
        MonthlyBars(months, labels)
    }
}

@Composable
private fun SymbolsCard(rows: List<SymbolPerformance>) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.portfolio_by_symbol))
        Spacer(Modifier.height(CoineProSpacing.One))
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                HorizontalDivider(color = CoineProColors.Border, thickness = 1.dp)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = BidiText.isolateLtr(row.symbol),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = BidiText.isolateLtr("${row.trades}") + " " +
                            stringResource(R.string.portfolio_trades) +
                            (row.winRate?.let {
                                " · " + BidiText.isolateLtr(MarketNumberFormatter.price(it, 0) + "%")
                            } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Text(
                    text = MarketNumberFormatter.money(row.net, signed = true),
                    style = CoineProTextStyles.RowFigure,
                    color = resultColour(row.net),
                )
            }
        }
    }
}

/**
 * One closed trade, and where it sat in the account's own story.
 *
 * ### Why a trade is marked rather than given a run-up of its own
 *
 * `core/chart`'s backtest engine computes a run-up **per trade**, from the bar extremes while the
 * position was open — the best that trade ever looked. Neither broker sends that: a `ClosedTrade`
 * carries an entry, an exit and a result, and nothing about what happened in between. Printing a
 * per-trade run-up here would mean inventing one from two prices, which would be a confident number
 * that is simply not true.
 *
 * What is true, and is what a reader actually wants from a list, is *where this trade sits*: inside
 * the stretch where the account made its largest continuous gain, or inside the one where it gave
 * the most back. That is arithmetic on the equity curve, which this app does have, and it turns a
 * list of results into a list a reader can find the bad fortnight in.
 */
@Composable
private fun TradeRow(trade: ClosedTrade, inRunUp: Boolean, inDrawdown: Boolean, zone: ZoneId) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                    Text(
                        text = BidiText.isolateLtr(trade.symbol),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(
                            if (trade.direction == TradeDirection.BUY) {
                                R.string.portfolio_direction_buy
                            } else {
                                R.string.portfolio_direction_sell
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (trade.direction == TradeDirection.BUY) {
                            CoineProColors.Buy
                        } else {
                            CoineProColors.Sell
                        },
                        fontWeight = FontWeight.Normal,
                    )
                    if (trade.liquidated) {
                        Text(
                            text = stringResource(R.string.portfolio_liquidated),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.Warning,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
                Text(
                    text = PersianDateTime.moment(Instant.ofEpochSecond(trade.closedAt), zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
                // At most one of the two. Where the spans overlap — which they can, at the turn —
                // the fall is the one shown, because it is the one a reader is looking for.
                val span = when {
                    inDrawdown -> R.string.portfolio_trade_in_drawdown to CoineProColors.Sell
                    inRunUp -> R.string.portfolio_trade_in_runup to CoineProColors.Buy
                    else -> null
                }
                span?.let { (label, tint) ->
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = trade.netProfit?.let { MarketNumberFormatter.money(it, signed = true) }
                        ?: stringResource(R.string.portfolio_value_missing),
                    style = CoineProTextStyles.RowFigure,
                    color = trade.netProfit?.let { resultColour(it) } ?: CoineProColors.TextMuted,
                )
                // The entry is genuinely unknown on some crypto trades — the opening leg fell
                // before the window. An em dash says so; a zero would be a price.
                Text(
                    text = BidiText.isolateLtr(
                        (trade.entry?.let { MarketNumberFormatter.price(it, decimalsFor(it)) }
                            ?: stringResource(R.string.portfolio_value_missing)) + " → " +
                            (trade.exit?.let { MarketNumberFormatter.price(it, decimalsFor(it)) }
                                ?: stringResource(R.string.portfolio_value_missing)),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun NarrowedWindowNote(window: ClosedRange<Long>, zone: ZoneId) {
    val from = PersianDateTime.numericDay(Instant.ofEpochSecond(window.start), zone)
    val to = PersianDateTime.numericDay(Instant.ofEpochSecond(window.endInclusive), zone)
    Caveat(stringResource(R.string.portfolio_window_narrowed, from, to))
}

@Composable
private fun Caveat(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.Warning,
    )
}

@Composable
private fun Failure(
    error: PortfolioError,
    onRetry: () -> Unit,
    onOpenConnections: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Text(
            text = stringResource(
                when (error) {
                    PortfolioError.NETWORK -> R.string.portfolio_error_network
                    PortfolioError.NOT_CONNECTED -> R.string.portfolio_error_not_connected
                },
            ),
            color = CoineProColors.TextSecondary,
        )
        when {
            // Retrying a missing API key would fail identically every time. The useful button is
            // the one that fixes the cause.
            error == PortfolioError.NOT_CONNECTED && onOpenConnections != null ->
                CoineProPrimaryButton(
                    text = stringResource(R.string.portfolio_open_connections),
                    onClick = onOpenConnections,
                )
            error == PortfolioError.NETWORK ->
                CoineProPrimaryButton(
                    text = stringResource(R.string.portfolio_retry),
                    onClick = onRetry,
                )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String?, tint: Double? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        Text(
            text = value ?: stringResource(R.string.portfolio_value_missing),
            style = CoineProTextStyles.RowFigure,
            color = when {
                value == null -> CoineProColors.TextMuted
                tint == null -> CoineProColors.TextPrimary
                else -> resultColour(tint)
            },
        )
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextSecondary,
    )
}

@Composable
private fun Centre(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun resultColour(value: Double): Color = when {
    value > 0 -> CoineProColors.Buy
    value < 0 -> CoineProColors.Sell
    else -> CoineProColors.TextPrimary
}

/**
 * Decimals for a price, from its size.
 *
 * The same rule the chart uses, and for the same reason: neither backend sends precision, so both
 * a gold price near 2,400 and a token price near 0.000018 arrive as plain doubles and two decimals
 * would render the second as `0.00`.
 */
private fun decimalsFor(price: Double): Int = when {
    price >= 1_000 -> 2
    price >= 1 -> 4
    price >= 0.01 -> 5
    else -> 8
}
