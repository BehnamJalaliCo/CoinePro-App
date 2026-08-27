package com.coinepro.feature.signaldetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.highImpactWarningsFor
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.SignalController
import com.coinepro.core.signals.TradingSignal
import java.time.Instant
import java.time.ZoneId

private val CHART_HEIGHT = 220.dp



@Composable
fun SignalDetailScreen(
    controller: SignalController,
    marketIntelController: MarketIntelController,
    signalId: Long,
    /**
     * Sends this signal to the order screen, where the platform places orders per signal.
     *
     * Null where it does not. CoinePro-FX reaches a reader's account through copy trading instead,
     * and a button leading to an order screen that could only report the feature as absent was
     * worse than no button — it read as something broken rather than something elsewhere.
     */
    onExecute: ((Long) -> Unit)?,
    /** Opens copy trading, on the platform whose signals arrive that way. */
    onOpenCopyTrading: (() -> Unit)? = null,
    /**
     * The bars behind the setup. Null draws the screen exactly as it was before the chart existed,
     * which is what a platform with no candle route should get.
     */
    chartController: SignalChartController? = null,
) {
    LaunchedEffect(signalId) { controller.loadDetail(signalId) }
    LaunchedEffect(marketIntelController) { marketIntelController.refresh() }
    DisposableEffect(signalId) {
        onDispose {
            controller.clearDetail()
            chartController?.clear()
        }
    }
    val state by controller.detailState.collectAsStateWithLifecycle()
    val marketIntelState by marketIntelController.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Center { CoineProThinkingDots() }
        state.membershipRequired -> Center { Text(stringResource(R.string.detail_membership), color = CoineProColors.TextSecondary) }
        state.error != null -> Center {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    state.error?.resolve().orEmpty(),
                    color = CoineProColors.TextSecondary,
                )
                Spacer(Modifier.height(CoineProSpacing.One))
                CoineProPrimaryButton(
                    text = stringResource(R.string.detail_retry),
                    onClick = { controller.loadDetail(signalId) },
                )
            }
        }
        state.signal != null -> {
            val signal = state.signal!!
            val warnings = if (signal.status == "active") {
                marketIntelState.calendar.highImpactWarningsFor(signal.symbol, Instant.now())
            } else {
                emptyList()
            }
            LaunchedEffect(chartController, signal.symbol, signal.timeframe) {
                chartController?.load(signal.symbol, signal.timeframe)
            }
            val chartState = chartController?.state?.collectAsStateWithLifecycle()
            SignalContent(
                signal = signal,
                highImpactWarnings = warnings,
                chart = chartState?.value,
                onExecute = onExecute,
                onOpenCopyTrading = onOpenCopyTrading,
            )
        }
        else -> Center { Text(stringResource(R.string.detail_not_found), color = CoineProColors.TextSecondary) }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun SignalContent(
    signal: TradingSignal,
    highImpactWarnings: List<EconomicEvent>,
    chart: SignalChartState?,
    onExecute: ((Long) -> Unit)?,
    onOpenCopyTrading: (() -> Unit)?,
) {
    val directionColor = when (signal.direction) {
        SignalDirection.BUY -> CoineProColors.Buy
        SignalDirection.SELL -> CoineProColors.Sell
        SignalDirection.NEUTRAL -> CoineProColors.TextSecondary
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(CoineProColors.Stage)
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = BidiText.isolateLtr(signal.symbol),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    listOfNotNull(signal.timeframe, signal.strategy).joinToString(" · "),
                    color = CoineProColors.TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(signal.direction.labelRes()),
                    color = directionColor,
                    style = MaterialTheme.typography.titleMedium,
                )
                signal.confidence?.let {
                    // The percent sign belongs inside the isolate; outside it, bidi reordering
                    // renders "78%" as "%78".
                    Text(
                        text = stringResource(R.string.detail_confidence, BidiText.isolateLtr("$it%")),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = highImpactWarnings.isNotEmpty(),
            enter = fadeIn() + slideInVertically { -it / 5 },
            exit = fadeOut() + slideOutVertically { -it / 5 },
        ) {
            HighImpactWarningCard(highImpactWarnings)
        }

        signal.currentQuote?.let { quote ->
            InfoCard(
                title = stringResource(
                    if (quote.isStale) R.string.detail_last_price else R.string.detail_current_price,
                ),
            ) {
                FinancialText(formatPrice(signal.symbol, quote.price), MaterialTheme.typography.headlineSmall)
                if (quote.isStale) {
                    Text(
                        text = stringResource(R.string.detail_stale_note),
                        color = CoineProColors.Warning,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // Above the numbers rather than below them. The levels are the answer; the chart is what
        // makes the answer checkable, and a reader who scrolls past the prices has already decided.
        if (chart != null && !chart.series.isEmpty) {
            // In a card like everything else on this screen. Without one the chart runs to the
            // screen edges while every panel around it is inset, and the price axis ends up
            // hanging outside the column the rest of the page is aligned to.
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                CoineProChart(
                    series = chart.series,
                    modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT),
                    decoration = ChartDecoration(
                        // Null entry means no band. A signal without one is rare and is usually a
                        // neutral read; drawing a zone around a price nobody named would invent the
                        // most important number on the screen.
                        signal = signal.entry?.takeIf(Double::isFinite)?.let { entry ->
                            val drawn = signal.targets
                                .sortedBy { it.level }
                                .filter { it.price?.isFinite() == true }
                            SignalOverlay(
                                entry = entry,
                                stopLoss = signal.stopLoss?.takeIf(Double::isFinite),
                                takeProfits = drawn.map { it.price!! },
                                isLong = signal.direction != SignalDirection.SELL,
                                entryLabel = stringResource(R.string.detail_entry),
                                stopLabel = stringResource(R.string.detail_stop),
                                // Named by the server's own level number, not by position in the
                                // list: a signal whose second target has no price would otherwise
                                // draw its third one labelled "۲".
                                targetLabels = drawn.map { target ->
                                    stringResource(R.string.detail_target, target.level)
                                },
                            )
                        },
                        // The volume pane takes a third of a short card and answers a question this
                        // screen is not asking. The chart screen keeps it; the preview does not.
                        showVolume = false,
                    ),
                    // A picture, not an instrument. Panning it would be panning a card inside a
                    // scrolling column, and the gesture would fight the scroll on every drag.
                    interactive = false,
                )
            }
        }

        InfoCard(stringResource(R.string.detail_setup)) {
            LevelRow(stringResource(R.string.detail_entry), signal.entry, signal.symbol)
            signal.entryZone?.let { zone ->
                if (zone.low != null || zone.high != null) {
                    LevelRow(stringResource(R.string.detail_zone_low), zone.low, signal.symbol)
                    LevelRow(stringResource(R.string.detail_zone_high), zone.high, signal.symbol)
                }
            }
            LevelRow(stringResource(R.string.detail_stop), signal.stopLoss, signal.symbol, accent = CoineProColors.Sell)
            signal.targets.sortedBy { it.level }.forEach { target ->
                LevelRow(
                    label = stringResource(R.string.detail_target, target.level) +
                        if (target.hit == true) " · " + stringResource(R.string.detail_hit) else "",
                    value = target.price,
                    symbol = signal.symbol,
                    accent = CoineProColors.Buy,
                )
            }
            signal.riskRewardTp1?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.detail_rr), style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
                    FinancialText(BidiText.isolateLtr("1:" + BidiText.strip(MarketNumberFormatter.price(it, 2))))
                }
            }
        }

        signal.rationale?.takeIf { it.isNotBlank() }?.let { rationale ->
            InfoCard(stringResource(R.string.detail_why)) {
                Text(rationale, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
        }

        signal.scoreBreakdown?.let { scores ->
            if (scores.technical != null || scores.pattern != null || scores.ml != null) {
                InfoCard(stringResource(R.string.detail_evidence)) {
                    ScoreRow(stringResource(R.string.detail_score_technical), scores.technical)
                    ScoreRow(stringResource(R.string.detail_score_pattern), scores.pattern)
                    ScoreRow(stringResource(R.string.detail_score_ml), scores.ml)
                }
            }
        }

        signal.result?.let { result ->
            InfoCard(stringResource(R.string.detail_result)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.detail_pnl), style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
                    FinancialText(
                        value = result.pnlUsd?.let { MarketNumberFormatter.money(it, signed = true) }
                            ?: stringResource(R.string.detail_value_missing),
                        color = when {
                            result.pnlUsd == null -> CoineProColors.TextMuted
                            result.pnlUsd!! >= 0 -> CoineProColors.Buy
                            else -> CoineProColors.Sell
                        },
                    )
                }
                result.source?.let {
                    Text(
                        text = stringResource(R.string.detail_source, it),
                        color = CoineProColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        if (
            signal.status == "active" &&
            signal.direction in setOf(SignalDirection.BUY, SignalDirection.SELL)
        ) {
            if (onExecute != null) {
                CoineProPrimaryButton(
                    text = stringResource(R.string.detail_execute),
                    onClick = { onExecute(signal.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.detail_execute_note),
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (onOpenCopyTrading != null) {
                // Says how this signal reaches an account here, which is not by pressing anything
                // on this screen. Without it the absence of a button reads as an omission.
                Text(
                    text = stringResource(R.string.detail_copy_note),
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.detail_open_copy),
                    onClick = onOpenCopyTrading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(CoineProSpacing.Two))
    }
}

@Composable
private fun HighImpactWarningCard(events: List<EconomicEvent>) {
    // The one bordered surface in the product. A risk warning has to separate from the cards around
    // it, and the direction's usual answer — a gap — is exactly what a reader skims past.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(CoineProColors.Sell.copy(alpha = 0.08f), MaterialTheme.shapes.large)
            .border(1.dp, CoineProColors.Sell.copy(alpha = 0.55f), MaterialTheme.shapes.large)
            .padding(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
            Text(
                text = stringResource(R.string.detail_high_impact_title),
                color = CoineProColors.Sell,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.detail_high_impact_body),
                color = CoineProColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            events.take(3).forEach { event ->
                HorizontalDivider(color = CoineProColors.Border)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(event.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(
                        PersianDateTime.moment(event.scheduledAt),
                        color = CoineProColors.Warning,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One), content = content)
    }
}

@Composable
private fun LevelRow(label: String, value: Double?, symbol: String, accent: Color = CoineProColors.TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        FinancialText(
            // A missing level is an em dash, never a zero: one says "not set", the other is a price.
            value = value?.let { formatPrice(symbol, it) } ?: stringResource(R.string.detail_value_missing),
            color = if (value == null) CoineProColors.TextMuted else accent,
        )
    }
}

@Composable
private fun ScoreRow(label: String, value: Double?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        FinancialText(MarketNumberFormatter.price(value, 1))
    }
}

@Composable
private fun FinancialText(
    value: String,
    style: TextStyle = CoineProTextStyles.RowFigure,
    color: Color = CoineProColors.TextPrimary,
) {
    // Already isolated by the formatter; a layout-direction override here would fix the row too.
    Text(value, color = color, style = style)
}

@androidx.annotation.StringRes
private fun SignalDirection.labelRes(): Int = when (this) {
    SignalDirection.BUY -> R.string.detail_direction_buy
    SignalDirection.SELL -> R.string.detail_direction_sell
    SignalDirection.NEUTRAL -> R.string.detail_direction_neutral
}

private fun formatPrice(symbol: String, value: Double): String {
    val decimals = when {
        symbol == "XAUUSD" -> 2
        symbol == "XAGUSD" -> 3
        value >= 1_000 -> 2
        value >= 1 -> 4
        else -> 6
    }
    return MarketNumberFormatter.price(value, decimals)
}
