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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.aisignal.AiDirectionBias
import com.coinepro.core.aisignal.AiGeneratedSignal
import com.coinepro.core.aisignal.AiRiskAppetite
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aisignal.AiSignalError
import com.coinepro.core.aisignal.AiSignalQuota
import com.coinepro.core.aisignal.AiSignalRequest
import com.coinepro.core.aisignal.AiSignalRisk
import com.coinepro.core.aisignal.AiSignalTimeframe
import com.coinepro.core.aisignal.AiSymbolOrigin
import com.coinepro.core.aisignal.AiSymbolUniverse
import com.coinepro.core.aisignal.AiTechnicalSnapshot
import com.coinepro.core.aisignal.AiTradeStyle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProMotionSpecs
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProRangeBar
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProStreamingBar
import com.coinepro.core.designsystem.CoineProStreamingText
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.SignalDirection

/**
 * The AI section: ask for a setup, watch it being produced, then read the reasoning behind it.
 *
 * ### What this screen was, and what it is now
 *
 * It was a toy: nine hard-coded symbols, five timeframes, an undifferentiated wall of chip rows,
 * three of the server's nine inputs reachable, and a result panel that dropped most of what the
 * server returns. Pressing «ساخت ستاپ» failed every time with an English exception sentence.
 *
 * The request panel is now three groups because the reader is answering three different kinds of
 * question and they do not mix: **what is being asked** (symbol, timeframe), **how the setup should
 * be shaped** (style, appetite, bias, minimum R:R), and **how big the position is** (lot, risk
 * percent, balance). Each group says for itself what leaving it alone means, rather than one
 * sentence at the bottom trying to cover all of them at once.
 *
 * The result panel shows everything the servers send — the levels, the R:R, the confidence, the
 * strategy, the caveats verbatim, and the whole indicator snapshot. A setup a reader cannot check is
 * a setup they have to trust; the snapshot is what makes it checkable.
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
    /**
     * What this deployment reports it can do.
     *
     * Each defaults to available so a screen rendered without them — a preview, a screenshot —
     * still shows its full self. In the app they arrive from the server, and a feature it has
     * switched off is not drawn rather than drawn and failing.
     */
    chartVisionAvailable: Boolean = true,
    assistantAvailable: Boolean = true,
    aiSignalsAvailable: Boolean = true,
    platform: MarketPlatform = MarketPlatform.TRADEYAR,
    /**
     * The market to open on, or null for this platform's own first one.
     *
     * How the chart hands a chart over. The AI is contextual now rather than a tab of its own —
     * somebody asks about the market in front of them — and a studio that opened on whatever it
     * defaulted to would make them pick the symbol again, having just arrived from it.
     *
     * Honoured only where this platform actually quotes it. A symbol that arrived from a chart on
     * the other backend, or one the server has since stopped serving, is refused the same way a
     * stale saved choice is: the universe's own first market. See the effect below.
     */
    initialSymbol: String? = null,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    // The screen knows its platform for certain; the controller is told at construction. Until the
    // AI controllers are built with their platform, a fallback list resolved by the controller would
    // put USDT pairs in front of a forex reader — so where nothing better has loaded, the screen's
    // own platform decides. As soon as a server list or a catalogue lands, that wins over both.
    val universe = state.universe.takeIf { it.origin != AiSymbolOrigin.FALLBACK }
        ?: AiSymbolUniverse.fallback(platform)

    var symbol by rememberSaveable(platform, initialSymbol) {
        mutableStateOf(initialSymbol ?: universe.markets.firstOrNull()?.symbol.orEmpty())
    }
    // The last few markets asked about, newest first. In memory rather than in a datastore: this is
    // a shortcut, not a preference, and a shortcut that survives the process is already more than
    // the screen had. It is seeded from the universe so the row is never empty on a first visit.
    var recents by rememberSaveable(platform) { mutableStateOf(listOf<String>()) }
    var timeframe by rememberSaveable(platform) { mutableStateOf(AiSignalTimeframe.H1) }
    var tradeStyle by rememberSaveable { mutableStateOf<AiTradeStyle?>(null) }
    var riskAppetite by rememberSaveable { mutableStateOf<AiRiskAppetite?>(null) }
    var directionBias by rememberSaveable { mutableStateOf<AiDirectionBias?>(null) }
    var minRiskReward by rememberSaveable { mutableStateOf<Double?>(null) }
    var lot by rememberSaveable { mutableStateOf("") }
    var riskPercent by rememberSaveable { mutableStateOf("") }
    var balance by rememberSaveable { mutableStateOf("") }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    // Whether this platform sizes a position in lots at all. See the sizing panel below.
    val sizesInLots = platform == MarketPlatform.COINEPRO_FX

    LaunchedEffect(controller) { controller.refreshQuota() }

    // A symbol chosen before the real universe arrived, or one the server has since stopped
    // accepting, is corrected rather than left to be refused on submit. Silently sending a symbol
    // the picker no longer offers is precisely the failure this screen was rebuilt to stop.
    LaunchedEffect(universe) {
        if (universe.markets.isNotEmpty() && !universe.allows(symbol)) {
            symbol = universe.markets.first().symbol
        }
    }
    // Likewise for the bar length, once a server has said which ones it takes.
    //
    // Where none has, the fallback is **this platform's** published list rather than every value
    // the enum has. The enum runs M1…W1 and neither AI endpoint answers for either end of that:
    // offering them put a length in the picker that comes back `422 TYR-017` after the reader has
    // filled in the whole form, which is what the owner photographed.
    val offeredTimeframes = state.quota?.timeframes?.takeIf { it.isNotEmpty() }
        ?: AiSignalTimeframe.accepted(platform)
    LaunchedEffect(offeredTimeframes) {
        if (timeframe !in offeredTimeframes) timeframe = offeredTimeframes.first()
    }

    val job = state.job
    val working = state.submitting || job?.isPending == true
    val result = job?.result
    val quota = state.quota

    if (pickerOpen) {
        AiSymbolPickerSheet(
            universe = universe,
            selected = symbol,
            onSelect = {
                symbol = it
                recents = (listOf(it) + recents).distinct().take(RECENT_COUNT)
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }

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
        item { AiHeader(quota) }
        item { CoineProTeachingStrip(TeachingSurface.AI, gutter = false) }

        item {
            AiPanel(title = stringResource(R.string.ai_group_what)) {
                AiSymbolField(
                    symbol = symbol,
                    universe = universe,
                    recents = recents.ifEmpty { universe.markets.take(RECENT_COUNT).map { it.symbol } },
                    onSelect = { symbol = it },
                    onBrowse = { pickerOpen = true },
                )
                AiChoiceRow(
                    label = stringResource(R.string.ai_timeframe),
                    options = offeredTimeframes.map { it to it.label },
                    selected = timeframe,
                    onSelect = { timeframe = it ?: timeframe },
                )
                AiFootnote(
                    if (state.quota?.timeframes.orEmpty().isNotEmpty()) {
                        stringResource(R.string.ai_timeframe_server_note)
                    } else {
                        stringResource(R.string.ai_timeframe_scope_note)
                    },
                )
                // A length the server offers that this build has no wire value for. Saying so beats
                // silently shipping a shorter list, which reads as the app being complete.
                state.quota?.unknownTimeframes.orEmpty().takeIf { it.isNotEmpty() }?.let { extra ->
                    AiFootnote(
                        stringResource(
                            R.string.ai_timeframe_unknown,
                            // Each name isolated, not the joined run: the separator is a
                            // Persian comma and the names either side of it are Latin.
                            extra.joinToString("، ") { BidiText.isolateLtr(it) },
                        ),
                    )
                }
            }
        }

        item {
            AiPanel(title = stringResource(R.string.ai_group_how)) {
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
                AiChoiceRow(
                    label = stringResource(R.string.ai_min_rr),
                    // A ratio is a market figure, so Latin digits, isolated so `1.5` does not
                    // reorder inside the right-to-left row it sits in.
                    options = MIN_RR_OPTIONS.map { it to MarketNumberFormatter.price(it, 1) },
                    selected = minRiskReward,
                    onSelect = { minRiskReward = it },
                )
                AiFootnote(stringResource(R.string.ai_group_how_hint))
            }
        }

        item {
            AiPanel(title = stringResource(R.string.ai_group_size)) {
                // A lot is an MT5 position size, and only CoinePro-FX's contract has the field.
                // TradeYar refuses the request outright when it arrives — `422` and
                // `TYR-017 Validation Field Invalid`, which is what the AI section was answering —
                // so on crypto the box is not drawn at all. A control whose value the server will
                // not take is worse than a missing one: the reader fills it in, presses the button
                // and is refused for something they did on purpose.
                if (sizesInLots) {
                    AiNumberField(
                        label = stringResource(R.string.ai_lot),
                        value = lot,
                        onValueChange = { lot = it },
                    )
                }
                AiNumberField(
                    label = stringResource(R.string.ai_risk_percent),
                    value = riskPercent,
                    onValueChange = { riskPercent = it },
                )
                AiNumberField(
                    label = stringResource(R.string.ai_balance),
                    value = balance,
                    onValueChange = { balance = it },
                )
                AiFootnote(
                    stringResource(
                        if (sizesInLots) {
                            R.string.ai_group_size_hint
                        } else {
                            R.string.ai_group_size_hint_no_lot
                        },
                    ),
                )
            }
        }

        item {
            // Honestly disabled: an exhausted allowance, a missing entitlement, a deployment with
            // the model switched off and a request already in flight are four different reasons the
            // button cannot be pressed, and none of them is "press it and find out".
            val ready = !working && !state.entitlementRequired && !state.quotaExhausted &&
                aiSignalsAvailable && symbol.isNotBlank()
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
                            minRiskReward = minRiskReward,
                            lot = lot.asFigure(),
                            riskPercent = riskPercent.asFigure(),
                            balance = balance.asFigure(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().alpha(if (ready) 1f else 0.5f),
            )
            if (!aiSignalsAvailable) {
                AiFootnote(stringResource(R.string.ai_unavailable))
            }
        }

        if (working) {
            item { AiWorkingPanel(symbol = symbol, timeframe = timeframe.label) }
        }

        state.error?.let { error -> item { AiErrorNotice(error) } }

        result?.let { signal ->
            item {
                AiResultPanel(
                    signal = signal,
                    onOpenSignal = onOpenSignal,
                    onRetry = controller::retryCurrent,
                    onDismiss = controller::dismissJob,
                )
            }
            signal.snapshot?.let { snapshot ->
                item { AiEvidencePanel(snapshot) }
            }
        }

        // Both panels below front optional features. A deployment without a vision model, or
        // without a conversational assistant, has nothing behind them — so they are absent rather
        // than present and failing.
        if (chartVisionAvailable) item {
            AiPanel(title = stringResource(R.string.ai_chart_analysis_title)) {
                Text(
                    stringResource(R.string.ai_chart_analysis_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.ai_chart_analysis_open),
                    onClick = onOpenChartAnalysis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (assistantAvailable) item {
            AiPanel(title = stringResource(R.string.ai_assistant_title)) {
                Text(
                    stringResource(R.string.ai_assistant_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
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

/** How many markets sit under the reader's thumb before the picker is needed. */
private const val RECENT_COUNT = 5

/**
 * The ratios worth offering as a chip.
 *
 * A minimum reward-to-risk below one asks the model for a setup that loses money at its own target,
 * and above three it refuses almost everything. Anything outside that a reader could want is a
 * different question than this control is asking.
 */
private val MIN_RR_OPTIONS = listOf(1.5, 2.0, 2.5, 3.0)

@Composable
private fun AiHeader(quota: AiSignalQuota?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ai_eyebrow),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.Gold,
            fontWeight = FontWeight.Bold,
        )
        Text(stringResource(R.string.ai_headline), style = MaterialTheme.typography.headlineSmall)
        Text(
            // Persian digits: an allowance is a count read aloud, not a figure compared against
            // another terminal. Zero says so in its own words rather than as «۰ از ۲۰».
            text = when {
                quota == null -> stringResource(R.string.ai_quota_unknown)
                quota.exhausted -> stringResource(R.string.ai_quota_empty)
                else -> stringResource(
                    R.string.ai_quota,
                    quota.remaining.toPersianDigits(),
                    quota.limit.toPersianDigits(),
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (quota?.exhausted == true) CoineProColors.Warning else CoineProColors.TextMuted,
        )
        // "None left" is a dead end; "none left until tomorrow at three" is an answer. Only drawn
        // where the server actually said, because an invented refill time is worse than none.
        quota?.resetAt.asMoment()?.let {
            Text(
                stringResource(R.string.ai_quota_reset, it),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

/**
 * The symbol, its recents, and the door to everything else.
 *
 * The fast path is a short row of markets the reader has actually asked about; the whole universe
 * is one tap behind it. That ordering is the point — the nine chips this replaces were somebody
 * else's nine, permanently, with nothing on screen suggesting there were four hundred more.
 */
@Composable
private fun AiSymbolField(
    symbol: String,
    universe: AiSymbolUniverse,
    recents: List<String>,
    onSelect: (String) -> Unit,
    onBrowse: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        AiChoiceRow(
            label = stringResource(R.string.ai_symbol),
            // A market's identity, in Latin, isolated so it does not reorder in a Persian row.
            // Capped at what one line holds. Unbounded, the selected market plus its recents came
            // to five, and five eight-character tickers wrap to four and **one** — a single chip
            // alone on a second row, which reads as a layout fault rather than as a choice. The
            // whole universe is one tap below in any case, so the row's job is the fast path and
            // not completeness.
            options = (listOf(symbol).filter { it.isNotBlank() } + recents).distinct()
                .take(SYMBOL_CHIPS)
                .map { it to BidiText.isolateLtr(it) },
            selected = symbol,
            onSelect = { onSelect(it ?: symbol) },
        )
        CoineProSecondaryButton(
            text = stringResource(R.string.ai_symbol_change),
            onClick = onBrowse,
            modifier = Modifier.fillMaxWidth(),
        )
        // Which of the three lists is behind that button, and how big it is. A reader who cannot
        // find their market is entitled to know whether the app has not loaded the catalogue or the
        // server has said it will not answer for it.
        AiFootnote(universe.originLine())
    }
}

/**
 * One optional figure.
 *
 * Latin digits on the wire whatever the keyboard produced: a Persian keyboard types «۱٫۵», and
 * `toDouble` on that is an exception, which in a form is a field that silently does nothing.
 */
@Composable
private fun AiNumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    val invalid = value.isNotBlank() && value.asFigure() == null
    CoineProTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = invalid,
        supporting = if (invalid) {
            stringResource(R.string.ai_number_invalid)
        } else {
            stringResource(R.string.ai_number_optional)
        },
    )
}

/** A blank box is a control left alone, not a zero. Zero and negative are not figures either. */
private fun String.asFigure(): Double? = trim()
    .takeIf { it.isNotEmpty() }
    ?.foldDigitsToLatin()
    ?.replace('٫', '.')
    ?.replace(",", "")
    ?.toDoubleOrNull()
    ?.takeIf { it.isFinite() && it > 0.0 }

/**
 * Shown while the job is queued or running. The bar is indeterminate on purpose — the server
 * reports a state, never a percentage, and a determinate bar would be inventing progress.
 */
@Composable
private fun AiWorkingPanel(symbol: String, timeframe: String) {
    AiPanel(title = null) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoineProThinkingDots()
            Text(
                text = BidiText.isolateLtr(symbol) + " · " + timeframe,
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
        }
        Text(
            stringResource(R.string.ai_working_body),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Right,
        )
        CoineProStreamingBar(Modifier.fillMaxWidth())
    }
}

/** A refusal, in Persian, with the server's own machine code under it where there was one. */
@Composable
private fun AiErrorNotice(error: AiSignalError) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = CoineProColors.Warning.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, CoineProColors.Warning.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                error.sentence(),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
            )
            error.codeLine()?.let {
                Text(
                    stringResource(R.string.ai_error_code, BidiText.isolateLtr(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun AiResultPanel(
    signal: AiGeneratedSignal,
    onOpenSignal: (Long) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + expandVertically(CoineProMotionSpecs.defaultSpatialFor()),
    ) {
        AiPanel(title = null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = BidiText.isolateLtr(signal.symbol + " · " + signal.timeframe),
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
                isLong = signal.direction != SignalDirection.SELL,
            )

            AiLevelRow(stringResource(R.string.ai_entry), signal.entry, CoineProColors.GoldBright)
            // The zone was parsed and never drawn. Where a server sends one, entering at a single
            // price is not what it advised.
            signal.entryZone?.let { zone ->
                AiTextRow(
                    stringResource(R.string.ai_entry_zone),
                    MarketNumberFormatter.priceAuto(zone.low) + " – " +
                        MarketNumberFormatter.priceAuto(zone.high),
                    CoineProColors.GoldBright,
                )
            }
            AiLevelRow(stringResource(R.string.ai_stop), signal.stopLoss, CoineProColors.Sell)
            signal.targets.forEach { target ->
                AiLevelRow(BidiText.isolateLtr("TP" + target.level), target.price, CoineProColors.Buy)
            }
            signal.riskRewardTp1?.let {
                AiTextRow(
                    stringResource(R.string.ai_risk_reward),
                    MarketNumberFormatter.price(it, 2),
                    CoineProColors.TextPrimary,
                )
            }
            AiTextRow(
                stringResource(R.string.ai_confidence),
                // A percent sign needs isolating or it lands on the wrong end of the figure.
                BidiText.isolateLtr(signal.confidence.toString() + "%"),
                CoineProColors.TextPrimary,
            )
            signal.lot?.let {
                AiLevelRow(
                    stringResource(R.string.ai_suggested_lot),
                    it,
                    CoineProColors.TextSecondary,
                    decimals = 2,
                )
            }
            signal.strategy?.let {
                AiTextRow(stringResource(R.string.ai_strategy), it, CoineProColors.TextSecondary)
            }
            signal.validatedAt.asMoment()?.let {
                AiTextRow(stringResource(R.string.ai_generated_at), it, CoineProColors.TextMuted)
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

            // Only when there is a stored signal to open. Neither server writes the model's output
            // into its signals table — both call it advice rather than a published call — so the
            // button would otherwise open a detail screen for a signal that does not exist.
            signal.signalId?.let { signalId ->
                CoineProSecondaryButton(
                    text = stringResource(R.string.ai_open_signal),
                    onClick = { onOpenSignal(signalId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.ai_retry),
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.ai_dismiss),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The indicator readings the model was given, so the setup can be checked rather than trusted.
 *
 * Grouped rather than listed: trend, momentum and range are three different questions, and eleven
 * undifferentiated rows is a table nobody reads. The twenty-bar range gets a marker bar because
 * "where is price inside its recent range" is a spatial question and three numbers answer it worse
 * than one picture.
 */
@Composable
private fun AiEvidencePanel(snapshot: AiTechnicalSnapshot) {
    AiPanel(title = stringResource(R.string.ai_evidence_title)) {
        snapshot.priceNow?.let {
            AiReadingRow(stringResource(R.string.ai_evidence_price), it, decimals = 2)
        }

        AiGroupLabel(stringResource(R.string.ai_evidence_trend))
        AiReadingRow(BidiText.isolateLtr("EMA 20"), snapshot.ema20)
        AiReadingRow(BidiText.isolateLtr("EMA 50"), snapshot.ema50)
        AiReadingRow(BidiText.isolateLtr("EMA 200"), snapshot.ema200)

        AiGroupLabel(stringResource(R.string.ai_evidence_momentum))
        AiReadingRow(BidiText.isolateLtr("RSI 14"), snapshot.rsi14, decimals = 1)
        AiReadingRow(BidiText.isolateLtr("ATR 14"), snapshot.atr14, decimals = 2)
        AiReadingRow(BidiText.isolateLtr("MACD"), snapshot.macd, decimals = 4)
        AiReadingRow(stringResource(R.string.ai_evidence_bb_upper), snapshot.bollingerUpper)
        AiReadingRow(stringResource(R.string.ai_evidence_bb_lower), snapshot.bollingerLower)

        AiGroupLabel(stringResource(R.string.ai_evidence_range))
        AiReadingRow(stringResource(R.string.ai_evidence_swing_high), snapshot.swingHigh20)
        AiReadingRow(stringResource(R.string.ai_evidence_swing_low), snapshot.swingLow20)
        snapshot.changePercent20?.let {
            AiTextRow(
                stringResource(R.string.ai_evidence_change),
                MarketNumberFormatter.signedPercent(it),
                if (it >= 0.0) CoineProColors.Buy else CoineProColors.Sell,
            )
        }
        // Only when all three legs are present and the range is real; `swingPosition` returns null
        // otherwise rather than pinning a marker to an edge, which reads as a price at its extreme.
        if (snapshot.swingPosition != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.ai_evidence_swing_position),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
                CoineProRangeBar(
                    low = requireNotNull(snapshot.swingLow20),
                    high = requireNotNull(snapshot.swingHigh20),
                    price = requireNotNull(snapshot.priceNow),
                    ink = CoineProColors.Gold,
                )
            }
        }

        AiFootnote(stringResource(R.string.ai_evidence_footnote))
    }
}

@Composable
private fun AiGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = CoineProColors.TextMuted,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = CoineProSpacing.Half),
    )
}

@Composable
private fun AiFootnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
        textAlign = TextAlign.Right,
    )
}

@Composable
private fun AiReadingRow(label: String, value: Double?, decimals: Int = 2) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        // A reading the server could not compute is shown as missing, never as zero.
        if (value == null) {
            Text(
                stringResource(R.string.ai_reading_missing),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        } else {
            Text(
                MarketNumberFormatter.price(value, decimals),
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
    AiTextRow(label, MarketNumberFormatter.price(value, decimals), colour)
}

@Composable
private fun AiTextRow(label: String, value: String, colour: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colour)
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
            textAlign = TextAlign.Right,
        )
    }
}

/**
 * The request still carries the older three-level risk field. Appetite is the control the user
 * now sees, so it drives both rather than leaving a second hidden knob out of step with it.
 *
 * Nothing sends it any more — see `AiSignalCreateJobDto` — but the domain model still carries it and
 * a value derived from what the reader chose beats a constant nobody chose.
 */
private fun AiRiskAppetite?.toLegacyRisk(): AiSignalRisk = when (this) {
    AiRiskAppetite.CONSERVATIVE -> AiSignalRisk.LOW
    AiRiskAppetite.AGGRESSIVE -> AiSignalRisk.HIGH
    AiRiskAppetite.BALANCED, null -> AiSignalRisk.MEDIUM
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

/**
 * Symbol chips on one line.
 *
 * Four eight-character tickers is what a 360dp phone fits at this chip size; the fifth starts a row
 * of its own. Everything past them is behind «تغییر نماد», which is where the other four hundred
 * markets already live.
 */
private const val SYMBOL_CHIPS = 4
