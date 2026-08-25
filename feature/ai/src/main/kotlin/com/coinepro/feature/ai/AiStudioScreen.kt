package com.coinepro.feature.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.aisignal.AiDirectionBias
import com.coinepro.core.aisignal.AiGeneratedSignal
import com.coinepro.core.aisignal.AiRiskAppetite
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aisignal.AiSignalProductScope
import com.coinepro.core.aisignal.AiSignalRequest
import com.coinepro.core.aisignal.AiSignalRisk
import com.coinepro.core.aisignal.AiSignalTimeframe
import com.coinepro.core.aisignal.AiTechnicalSnapshot
import com.coinepro.core.aisignal.AiTradeStyle
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProStreamingBar
import com.coinepro.core.designsystem.CoineProStreamingText
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.common.BidiText
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.SignalDirection

/**
 * The AI section: ask for a setup, watch it being produced, then read the reasoning behind it.
 *
 * Everything the server takes into account is exposed, and everything it returns is shown —
 * including the indicator readings and the candles the model reasoned over. A verdict with no
 * visible basis is a request for trust; a verdict next to its evidence can be judged.
 *
 * The reveal is staged on the client. The server reports queued or running and never a percentage
 * or a token stream, so the copy says the request is being analysed and the progress bar is
 * deliberately indeterminate. Nothing here claims to be live model output, because it is not.
 */
@Composable
fun AiStudioScreen(
    controller: AiSignalController,
    onOpenSignal: (Long) -> Unit,
    onOpenChartAnalysis: () -> Unit,
    onOpenAssistant: () -> Unit = {},
    platform: MarketPlatform = MarketPlatform.TRADEYAR,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()

    val symbols = AiSignalProductScope.symbolsFor(platform)
    var symbol by rememberSaveable(platform) { mutableStateOf(symbols.first()) }
    var timeframe by rememberSaveable { mutableStateOf(AiSignalTimeframe.H1) }
    var tradeStyle by rememberSaveable { mutableStateOf<AiTradeStyle?>(null) }
    var riskAppetite by rememberSaveable { mutableStateOf<AiRiskAppetite?>(null) }
    var directionBias by rememberSaveable { mutableStateOf<AiDirectionBias?>(null) }

    LaunchedEffect(controller) { controller.refreshQuota() }

    val job = state.job
    val working = state.submitting || job?.isPending == true
    val result = job?.result

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = CoineProSpacing.Gutter,
            vertical = CoineProSpacing.Gutter,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        item { AiHeader(quotaText = quotaText(state.quota?.remaining, state.quota?.limit)) }

        item {
            AiPanel(title = stringResource(R.string.ai_setup_title)) {
                AiChoiceRow(
                    label = stringResource(R.string.ai_symbol),
                    options = symbols.map { it to it },
                    selected = symbol,
                    onSelect = { symbol = it ?: symbol },
                )
                AiChoiceRow(
                    label = stringResource(R.string.ai_timeframe),
                    options = AiSignalTimeframe.entries.map { it to it.label },
                    selected = timeframe,
                    onSelect = { timeframe = it ?: timeframe },
                )
                AiChoiceRow(
                    label = stringResource(R.string.ai_trade_style),
                    options = AiTradeStyle.entries.map { it to tradeStyleLabel(it) },
                    selected = tradeStyle,
                    onSelect = { tradeStyle = it },
                )
                AiChoiceRow(
                    label = stringResource(R.string.ai_risk_appetite),
                    options = AiRiskAppetite.entries.map { it to riskAppetiteLabel(it) },
                    selected = riskAppetite,
                    onSelect = { riskAppetite = it },
                )
                AiChoiceRow(
                    label = stringResource(R.string.ai_direction_bias),
                    options = AiDirectionBias.entries.map { it to directionBiasLabel(it) },
                    selected = directionBias,
                    onSelect = { directionBias = it },
                )
                Text(
                    stringResource(R.string.ai_unset_means_server_decides),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }

        item {
            val ready = !working && !state.entitlementRequired && !state.quotaExhausted
            CoineProPrimaryButton(
                text = if (working) {
                    stringResource(R.string.ai_analysing)
                } else {
                    stringResource(R.string.ai_generate)
                },
                onClick = {
                    if (!ready) return@CoineProPrimaryButton
                    controller.submit(
                        AiSignalRequest(
                            symbol = symbol,
                            timeframe = timeframe,
                            risk = riskAppetite.toLegacyRisk(),
                            tradeStyle = tradeStyle,
                            riskAppetite = riskAppetite,
                            directionBias = directionBias,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().alpha(if (ready) 1f else 0.5f),
            )
        }

        if (working) {
            item { AiWorkingPanel(symbol = symbol, timeframe = timeframe.label) }
        }

        state.error?.let { message ->
            item { AiNotice(message, CoineProColors.Warning) }
        }

        result?.let { signal ->
            item { AiResultPanel(signal = signal, onOpenSignal = onOpenSignal) }
            signal.snapshot?.let { snapshot ->
                item { AiEvidencePanel(snapshot) }
            }
        }

        item {
            AiPanel(title = stringResource(R.string.ai_chart_analysis_title)) {
                Text(
                    stringResource(R.string.ai_chart_analysis_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.ai_chart_analysis_open),
                    onClick = onOpenChartAnalysis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            AiPanel(title = stringResource(R.string.ai_assistant_title)) {
                Text(
                    stringResource(R.string.ai_assistant_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.ai_assistant_open),
                    onClick = onOpenAssistant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AiHeader(quotaText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.ai_eyebrow),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.Gold,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.ai_headline), style = MaterialTheme.typography.headlineSmall)
        Text(quotaText, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
    }
}

/**
 * Shown while the job is queued or running. The bar is indeterminate on purpose — the server
 * reports a state, never a percentage, and a determinate bar would be inventing progress.
 */
@Composable
private fun AiWorkingPanel(symbol: String, timeframe: String) {
    AiPanel(title = null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoineProThinkingDots()
            Text(
                text = BidiText.isolateLtr("$symbol · $timeframe"),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
        }
        Text(
            stringResource(R.string.ai_working_body),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        CoineProStreamingBar(Modifier.fillMaxWidth())
    }
}

@Composable
private fun AiResultPanel(signal: AiGeneratedSignal, onOpenSignal: (Long) -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + expandVertically(tween(260)),
    ) {
        AiPanel(title = null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                text = BidiText.isolateLtr(signal.symbol),
                    style = MaterialTheme.typography.titleMedium,
                    color = CoineProColors.Gold,
                )
                Text(
                    directionLabel(signal.direction),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (signal.direction == SignalDirection.BUY) {
                        CoineProColors.Buy
                    } else {
                        CoineProColors.Sell
                    },
                )
            }

            AiCandleChart(
                candles = signal.recentCandles,
                entry = signal.entry,
                stopLoss = signal.stopLoss,
                targets = signal.targets.map { it.price },
            )

            AiLevelRow(stringResource(R.string.ai_entry), signal.entry, CoineProColors.GoldBright)
            AiLevelRow(stringResource(R.string.ai_stop), signal.stopLoss, CoineProColors.Sell)
            signal.targets.forEach { target ->
                AiLevelRow("TP${target.level}", target.price, CoineProColors.Buy)
            }
            signal.lot?.let {
                AiLevelRow(stringResource(R.string.ai_suggested_lot), it, CoineProColors.TextSecondary, decimals = 2)
            }

            signal.rationale?.let {
                CoineProStreamingText(
                    text = it,
                    // The result arrives whole, so this reveals once on first paint and never
                    // re-performs. Replaying it on every recomposition would dramatise finished work.
                    streaming = true,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }

            // Server caveats are shown verbatim and never folded into the rationale.
            signal.warnings.forEach { warning ->
                AiNotice(warning, CoineProColors.Warning)
            }

            CoineProSecondaryButton(
                text = stringResource(R.string.ai_open_signal),
                onClick = { onOpenSignal(signal.signalId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The indicator readings the model was given, so the setup can be checked rather than trusted. */
@Composable
private fun AiEvidencePanel(snapshot: AiTechnicalSnapshot) {
    AiPanel(title = stringResource(R.string.ai_evidence_title)) {
        AiReadingRow("RSI 14", snapshot.rsi14, decimals = 1)
        AiReadingRow("ATR 14", snapshot.atr14, decimals = 2)
        AiReadingRow("MACD", snapshot.macd, decimals = 4)
        AiReadingRow("EMA 20", snapshot.ema20)
        AiReadingRow("EMA 50", snapshot.ema50)
        AiReadingRow("EMA 200", snapshot.ema200)
        Text(
            stringResource(R.string.ai_evidence_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun AiReadingRow(label: String, value: Double?, decimals: Int = 2) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(BidiText.isolateLtr(label), style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        // A reading the server could not compute is shown as missing, never as zero.
        if (value == null) {
            Text(
                stringResource(R.string.ai_reading_missing),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        } else {
            Text(
                BidiText.isolateLtr(MarketNumberFormatter.price(value, decimals)),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun AiLevelRow(
    label: String,
    value: Double,
    colour: androidx.compose.ui.graphics.Color,
    decimals: Int = 2,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        Text(
            BidiText.isolateLtr(MarketNumberFormatter.price(value, decimals)),
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
        )
    }
}

@Composable
private fun AiPanel(title: String?, content: @Composable () -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
            title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
            }
            content()
        }
    }
}

@Composable
private fun AiNotice(message: String, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
    }
}

/**
 * The request still carries the older three-level risk field. Appetite is the control the user
 * now sees, so it drives both rather than leaving a second hidden knob out of step with it.
 */
private fun AiRiskAppetite?.toLegacyRisk(): AiSignalRisk = when (this) {
    AiRiskAppetite.CONSERVATIVE -> AiSignalRisk.LOW
    AiRiskAppetite.AGGRESSIVE -> AiSignalRisk.HIGH
    AiRiskAppetite.BALANCED, null -> AiSignalRisk.MEDIUM
}

@Composable
private fun quotaText(remaining: Int?, limit: Int?): String = when {
    remaining == null || limit == null -> stringResource(R.string.ai_quota_unknown)
    else -> stringResource(R.string.ai_quota, remaining, limit)
}

@Composable
private fun tradeStyleLabel(style: AiTradeStyle): String = stringResource(
    when (style) {
        AiTradeStyle.SCALP -> R.string.ai_style_scalp
        AiTradeStyle.INTRADAY -> R.string.ai_style_intraday
        AiTradeStyle.SWING -> R.string.ai_style_swing
    },
)

@Composable
private fun riskAppetiteLabel(appetite: AiRiskAppetite): String = stringResource(
    when (appetite) {
        AiRiskAppetite.CONSERVATIVE -> R.string.ai_appetite_conservative
        AiRiskAppetite.BALANCED -> R.string.ai_appetite_balanced
        AiRiskAppetite.AGGRESSIVE -> R.string.ai_appetite_aggressive
    },
)

@Composable
private fun directionBiasLabel(bias: AiDirectionBias): String = stringResource(
    when (bias) {
        AiDirectionBias.AUTO -> R.string.ai_bias_auto
        AiDirectionBias.LONG -> R.string.ai_bias_long
        AiDirectionBias.SHORT -> R.string.ai_bias_short
    },
)

@Composable
private fun directionLabel(direction: SignalDirection): String = stringResource(
    when (direction) {
        SignalDirection.BUY -> R.string.ai_buy
        SignalDirection.SELL -> R.string.ai_sell
        SignalDirection.NEUTRAL -> R.string.ai_neutral
    },
)
