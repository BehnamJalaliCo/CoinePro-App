package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.backtest.BacktestFormat
import com.coinepro.core.backtest.ReplayLedger
import com.coinepro.core.backtest.ReplayPosition
import com.coinepro.core.backtest.ReplayReport
import com.coinepro.core.backtest.ReplayReports
import com.coinepro.core.backtest.ReplaySession
import com.coinepro.core.backtest.replaySetup
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.ReplaySpeed
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProToggleChip
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.marketdata.CHART_TIME_ZONE
import java.time.LocalDate
import java.util.Locale

/**
 * The replay transport, under the chart.
 *
 * The whole point of replay is that the reader cannot see what happened next, so this bar is
 * deliberately the only thing on screen that says replay is on — and it says it loudly, in the
 * brand accent, because a reader who forgets they are in replay is reading a live chart that is
 * hours stale. Leaving is one tap and it is never hidden behind a menu.
 *
 * The speed ladder is nine fixed steps rather than a slider: a slider invites hunting for a speed
 * instead of watching the chart. The scrub *is* a slider, because "somewhere around here" is
 * exactly what a reader means when they drag it.
 */
@Composable
internal fun ReplayBar(
    state: ReplayState,
    onToggle: () -> Unit,
    onStep: () -> Unit,
    onStepBack: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeed: (ReplaySpeed) -> Unit,
    /** Reveals the rest of the snapshot without leaving replay. See `Replay.jumpToLive`. */
    onJumpToLive: () -> Unit,
    /** Moves the cursor to a bar the reader named. See `Replay.goTo`. */
    onGoTo: (Int) -> Unit,
    onExit: () -> Unit,
    /**
     * The open rehearsal position, as the chart should draw it, or null when there is none.
     *
     * Hoisted out as a callback rather than held by the screen, because the session itself must not
     * be: a rehearsal dies with the replay bar, and a chart screen holding it would be one refactor
     * away from persisting it. What the screen needs is the *drawing*, which is a value with no
     * lifetime of its own — it is handed up when it changes and cleared when this bar leaves.
     *
     * Defaulted, so a host that has not been wired for it draws nothing extra and still compiles.
     */
    onSetupOverlay: (SignalOverlay?) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoineProColors.SurfaceElevated)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "بازپخش",
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Accent,
            )
            Text(
                // Which bar of how many. The count is a prose figure, so Persian numerals, and it
                // is isolated as one run so the slash does not migrate across the pair in RTL.
                text = BidiText.isolateLtr(
                    "${(state.cursor + 1).toPersianDigits()} / ${state.bars.size.toPersianDigits()}",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            TransportButton(CoineProIcons.StepBack, "یک میله عقب", onStepBack)
            TransportButton(
                icon = if (state.playing) DesignR.drawable.icon_pause else DesignR.drawable.tv_play,
                label = if (state.playing) "توقف" else "پخش",
                onClick = onToggle,
                tint = CoineProColors.Accent,
            )
            TransportButton(CoineProIcons.StepForward, "یک میله جلو", onStep)
            // To the end of the snapshot, still inside replay. Distinct from the exit beside it,
            // and the reason both are here: a reader finishing a practice run wants to see how it
            // turned out before they throw the run away.
            TransportButton(DesignR.drawable.tv_maximize2, "تا آخرین کندل", onJumpToLive)
            TransportButton(DesignR.drawable.icon_x, "خروج از بازپخش", onExit)
        }

        Slider(
            value = state.progress,
            onValueChange = onSeek,
            colors = SliderDefaults.colors(
                thumbColor = CoineProColors.AccentFill,
                activeTrackColor = CoineProColors.AccentFill,
                inactiveTrackColor = CoineProColors.Border,
            ),
            modifier = Modifier.fillMaxWidth().height(20.dp),
        )

        // Nine steps, and the row scrolls rather than shrinking the chips: a speed control whose
        // targets are smaller than a fingertip is a speed control that gets the wrong speed.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            ReplaySpeed.entries.forEach { step ->
                // Matched on the resolved step rather than on the raw multiplier, so a speed
                // restored from a saved run always lights exactly one chip. See
                // `ReplayState.speedStep`.
                val selected = state.speedStep == step
                CoineProToggleChip(
                    // The multiplier is a market-adjacent figure and stays Latin, like every other
                    // number a trader compares against another app.
                    label = BidiText.isolateLtr(
                        if (step.multiplier < 1) "${step.multiplier}×" else "${step.multiplier.toInt()}×",
                    ),
                    selected = selected,
                    onClick = { onSpeed(step) },
                    // Compact, because this is the chart's chrome. Nine of these sit in a scrolling
                    // strip and at the full size they would be a headline over the plot.
                    compact = true,
                )
            }
        }

        GoToDateField(bars = state.bars, onGoTo = onGoTo)

        ReplayLedgerPanel(state, onSetupOverlay)
    }
}

/**
 * Going to a date, which is the one thing the scrub cannot do.
 *
 * Dragging a slider across two thousand bars to reach a particular Tuesday is a hunt: the handle
 * moves eight bars a pixel and the reader overshoots repeatedly. A date is what they actually have
 * in mind — the day of the announcement, the day the level broke — so it is what the control takes.
 *
 * Lifted out of the replay bar so an ordinary chart can carry it too. It is the same need on both:
 * a reader looking at a live chart who wants to see what happened in Mordad is doing exactly the
 * same hunt with a pan gesture. What it needs from the caller is a way to bring a bar index into
 * view, which is why [onGoTo] is an index rather than a scroll — the caller owns the viewport, and
 * on a live chart that is not this composable. See the report's WIRING NEEDED for what `ChartScreen`
 * has to pass.
 */
@Composable
internal fun GoToDateField(
    bars: List<Candle>,
    onGoTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typedDate by rememberSaveable { mutableStateOf("") }
    val target = remember(typedDate, bars) { indexOfTypedDate(typedDate, bars) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoineProTextField(
            value = typedDate,
            onValueChange = { typedDate = it },
            label = "رفتن به تاریخ ۱۴۰۳/۰۵/۱۲",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "برو",
            style = MaterialTheme.typography.labelSmall,
            color = if (target == null) CoineProColors.TextDisabled else CoineProColors.OnAccent,
            modifier = Modifier
                // A button, so a thumb has to reach it: six points of padding around labelSmall
                // draws about twenty-six.
                .minimumInteractiveComponentSize()
                .clip(CoineProShapes.small)
                .background(if (target == null) Color.Transparent else CoineProColors.AccentFill)
                .clickable(enabled = target != null) {
                    target?.let {
                        onGoTo(it)
                        typedDate = ""
                    }
                }
                .padding(horizontal = CoineProSpacing.OneHalf, vertical = 6.dp),
        )
    }
}

/**
 * The rehearsal ledger: trades taken during this replay session, and how they went.
 *
 * ### What this is, and what it used to be
 *
 * Replay used to step the bars and stop there. A reader could scrub back to March, watch a level
 * break and tell themselves they would have taken it — which is the one claim a chart can never
 * contradict. Everything after the entry was missing: no position, no stop, no target, no result.
 *
 * This is the other half. Open a long or a short at the bar you are looking at, name the price that
 * says you are wrong and the price that says you are done, step forward, and find out which of the
 * two the market reached first. What comes out at the end is «گزارش این جلسه» — the same five-tab
 * document the strategy tab produces, about the reader's own hand, on the same starting equity and
 * at the same fees, so the two can honestly be read side by side.
 *
 * ### Kept out of the paper-trading book, deliberately
 *
 * A replay session is a rehearsal over history the reader can scrub, step backwards through and
 * start again. The paper book is a record of decisions taken without knowing what came next. Mixing
 * a rehearsal into a record is how a journal stops being trusted, so nothing here is written
 * anywhere: the session lives for as long as replay is on, and leaving replay discards it. That is
 * the honest shape of a rehearsal — worth reading, not worth keeping — and it is why the report is
 * offered here, while the session is still running, rather than as a summary afterwards.
 *
 * Collapsed until asked for. The transport above it is what a reader came to the bar for, and a
 * trading panel permanently open under the chart would push the chart itself off a phone.
 */
@Composable
private fun ReplayLedgerPanel(state: ReplayState, onSetupOverlay: (SignalOverlay?) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    var sizeText by rememberSaveable { mutableStateOf("1") }
    var stopText by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }
    // A new snapshot is a new session. Keyed on the first bar and the length, which together change
    // on every fresh entry into replay and on nothing else.
    var session by remember(state.bars.firstOrNull()?.t, state.bars.size) {
        mutableStateOf(ReplaySession())
    }

    // ── Stepping the bars is what resolves a stop ────────────────────────────────────────────────
    //
    // Committed to the session rather than derived beside it, and that is the whole difference
    // between a ledger and a fiction. A derived stop-out would be recomputed from the raw session
    // every time the cursor moved, so dragging the scrub back three bars would un-hit it — and a
    // reader who can un-hit their stops by scrubbing has a report of the best version of every
    // trade they took. See `ReplayLedger.advance`: a bar already walked stays walked.
    //
    // Assigning an equal session is a no-op for recomposition — `mutableStateOf` compares
    // structurally — so this effect settles on the step where nothing triggered.
    LaunchedEffect(state.cursor, state.bars) {
        session = ReplayLedger.advance(session, state.bars, state.cursor)
    }

    // Marked rather than accumulated: stepping backwards un-reveals the bars that set an envelope,
    // and a run-up that survived the step back would be a number the reader cannot see on the
    // chart any more. See `ReplayLedger.mark`.
    val marked = remember(session, state.cursor, state.bars) {
        ReplayLedger.mark(session, state.bars, state.cursor)
    }
    val price = state.bars.getOrNull(state.cursor)?.c

    // ── The open position, drawn on the chart like any other ────────────────────────────────────
    //
    // Converted to the overlay the chart already knows how to draw — green above the entry, red
    // below, and nothing at all left of the candle it was opened on — rather than given a renderer
    // of its own. See `replaySetup` for why a second renderer would eventually disagree with this
    // one about which side of a short's entry the red goes on.
    //
    // The newest open position wins, because the chart carries one setup and the newest is the one
    // the reader just took. The rest are on the rows below, where they are labelled.
    val entryLabel = stringResource(R.string.replay_setup_entry)
    val stopLabel = stringResource(R.string.replay_setup_stop)
    val targetLabel = stringResource(R.string.replay_setup_target)
    val overlay = remember(marked.open.lastOrNull(), entryLabel, stopLabel, targetLabel) {
        marked.open.lastOrNull()?.let { replaySetup(it, entryLabel, stopLabel, targetLabel) }
    }
    LaunchedEffect(overlay) { onSetupOverlay(overlay) }
    // Leaving replay takes the drawing with it. A rehearsal's levels left on a live chart would be
    // two prices nobody placed, drawn exactly like two prices somebody did.
    DisposableEffect(Unit) { onDispose { onSetupOverlay(null) } }

    val size = sizeText.foldDigitsToLatin().trim().toDoubleOrNull() ?: 0.0
    val stop = stopText.foldDigitsToLatin().trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val target = targetText.foldDigitsToLatin().trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    // A level on the wrong side of the market refuses the order rather than being dropped from it —
    // see `ReplayLedger.open`. The chip is dimmed for the same reason the ledger refuses: a reader
    // who typed a stop and got a position without one is trading unprotected while believing they
    // are not.
    val canBuy = price != null && size > 0 && placeable(true, stop, target, price)
    val canSell = price != null && size > 0 && placeable(false, stop, target, price)
    val typedButUnusable = price != null && size > 0 && !canBuy && !canSell &&
        (stopText.isNotBlank() || targetText.isNotBlank())

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (expanded) R.string.replay_ledger_hide else R.string.replay_ledger_show,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (expanded) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CoineProShapes.small)
                    .background(if (expanded) CoineProColors.AccentFill else Color.Transparent)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
            )
            if (!marked.isEmpty) {
                val running = ReplayLedger.realised(marked) +
                    ReplayLedger.unrealised(marked, state.bars, state.cursor)
                Text(
                    text = BacktestFormat.money(running, signed = true),
                    style = CoineProTextStyles.RowFigure,
                    color = resultInk(running),
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // The report outlives the panel being collapsed: a reader who has ended their session wants
        // the answer, not the order ticket that produced it.
        if (reportOpen) {
            ReplaySessionReport(
                report = remember(marked, state.cursor, state.bars) {
                    ReplayReports.build(marked, state.bars, state.cursor)
                },
                onDismiss = { reportOpen = false },
            )
        }

        if (!expanded) return@Column

        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoineProTextField(
                        value = sizeText,
                        onValueChange = { sizeText = it },
                        label = stringResource(R.string.replay_size),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    // The size a strategy run commits, in one tap. Without it a reader types «1»,
                    // trades a thousandth of a stake on a forty-thousand-dollar instrument, and
                    // reads a net profit that cannot be compared with anything on the backtest
                    // sheet. See `ReplayLedger.stakeSize`.
                    ActionChip(
                        label = stringResource(R.string.replay_size_stake),
                        colour = CoineProColors.AccentFill,
                        enabled = price != null,
                    ) {
                        price?.let { sizeText = stakeText(it) }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoineProTextField(
                        value = stopText,
                        onValueChange = { stopText = it },
                        label = stringResource(R.string.replay_stop),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    CoineProTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        label = stringResource(R.string.replay_target),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActionChip(stringResource(R.string.replay_buy), CoineProColors.Buy, canBuy) {
                        session = ReplayLedger.open(
                            session = marked,
                            bars = state.bars,
                            cursor = state.cursor,
                            isLong = true,
                            size = size,
                            stopLoss = stop,
                            takeProfit = target,
                        )
                    }
                    ActionChip(stringResource(R.string.replay_sell), CoineProColors.Sell, canSell) {
                        session = ReplayLedger.open(
                            session = marked,
                            bars = state.bars,
                            cursor = state.cursor,
                            isLong = false,
                            size = size,
                            stopLoss = stop,
                            takeProfit = target,
                        )
                    }
                    if (price != null) {
                        Text(
                            text = BacktestFormat.money(price),
                            style = CoineProTextStyles.RowFigure,
                            color = CoineProColors.TextSecondary,
                            textAlign = TextAlign.Right,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (typedButUnusable) {
                    Text(
                        text = stringResource(R.string.replay_levels_wrong_side),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Warning,
                        fontWeight = FontWeight.Normal,
                    )
                }

                marked.open.forEach { position ->
                    OpenPositionRow(
                        position = position,
                        price = price,
                        onBreakEven = {
                            // The one in-flight edit worth a tap of its own: risk removed, trade
                            // left running. Refused by the ledger when the market is already back
                            // through the entry, which is exactly when it would fill instantly.
                            session = ReplayLedger.protect(
                                session = marked,
                                bars = state.bars,
                                cursor = state.cursor,
                                id = position.id,
                                stopLoss = position.entryPrice,
                                takeProfit = position.takeProfit,
                            )
                        },
                        onClose = {
                            session = ReplayLedger.close(marked, state.bars, state.cursor, position.id)
                        },
                    )
                }

                if (marked.closed.isNotEmpty()) {
                    val summary = ReplayLedger.summary(marked, state.bars, state.cursor)
                    LedgerRow(
                        stringResource(R.string.replay_realised),
                        BacktestFormat.money(summary.netProfit, signed = true),
                        resultInk(summary.netProfit),
                    )
                    LedgerRow(
                        stringResource(R.string.replay_closed_count),
                        BacktestFormat.count(summary.totalTrades),
                    )
                    // Not a win rate. Four and two is what happened; «۶۷٪» is a measurement, and a
                    // rehearsal has never taken enough trades to have made one. The full argument
                    // is in `BacktestFormat.ratioIfSampled`, and the report says it in words.
                    LedgerRow(
                        stringResource(R.string.replay_wins),
                        stringResource(
                            R.string.replay_wins_of,
                            summary.winningTrades.toPersianDigits(),
                            summary.totalTrades.toPersianDigits(),
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (marked.open.isNotEmpty()) {
                        // Ending a session closes everything at the cursor. A session that finishes
                        // on an open position finishes on a hope, and the report would be taken
                        // from a number that had not happened yet.
                        ActionChip(
                            label = stringResource(R.string.replay_end_session),
                            colour = CoineProColors.AccentFill,
                            enabled = true,
                        ) {
                            session = ReplayLedger.closeAll(marked, state.bars, state.cursor)
                            reportOpen = true
                        }
                    }
                    ActionChip(
                        label = stringResource(R.string.replay_report_open),
                        colour = CoineProColors.AccentFill,
                        enabled = !marked.isEmpty,
                    ) { reportOpen = true }
                }

                Text(
                    text = stringResource(R.string.replay_ledger_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * The full-stake size as text a field can hold and this file can parse back.
 *
 * Deliberately not `BacktestFormat.money`, which is the obvious thing to reach for and is wrong
 * here twice over: it groups thousands with commas and wraps the run in bidirectional isolates.
 * Both are right for a figure being *read* and fatal for one being typed — `toDoubleOrNull` on
 * "1,234.5678" is null, so the chip would fill the field with something the buy button then
 * refuses, with nothing on screen to say why.
 *
 * Six decimals, trimmed. A stake on an instrument quoted in the tens of thousands is a fraction of
 * a unit, and rounding it to two would commit a different amount from the one the report is scaled
 * against.
 */
private fun stakeText(price: Double): String {
    val stake = ReplayLedger.stakeSize(price)
    if (!stake.isFinite() || stake <= 0) return ""
    return String.format(Locale.US, "%.6f", stake).trimEnd('0').trimEnd('.')
}

/**
 * Whether both typed levels can be placed on this side at this price.
 *
 * Blank is legitimate — a reader may take a position without a plan, and the ledger's job is to
 * show them what that cost rather than to refuse it. Text that is not a number is not blank: it is
 * a level the reader meant, and letting the chip fire would open a position without it.
 */
private fun placeable(isLong: Boolean, stop: Double?, target: Double?, price: Double): Boolean {
    val stopOk = stop == null || ReplayLedger.stopIsPlaceable(isLong, stop, price)
    val targetOk = target == null || ReplayLedger.targetIsPlaceable(isLong, target, price)
    return stopOk && targetOk
}

/**
 * The session's own backtest, in the sheet the strategy report uses.
 *
 * The same chrome and the same five tabs, because it is the same kind of answer about a different
 * trader — and a reader who has learned to read one of them has learned to read the other. The
 * export is deliberately absent: a rehearsal is not a document, and a CSV of one would be the first
 * step towards a rehearsal being filed as a record.
 */
// The sheet's own `SheetState` default is the experimental API here, not anything this file uses.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaySessionReport(report: ReplayReport, onDismiss: () -> Unit) {
    CoineProSheet(
        title = stringResource(R.string.replay_report_title),
        subtitle = stringResource(R.string.replay_report_subtitle),
        onDismiss = onDismiss,
    ) {
        ReplayReportBody(
            report = report,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(bottom = CoineProSpacing.Gutter),
        )
    }
}

/**
 * One open rehearsal position, marked against the replay bar.
 *
 * Two lines rather than one. The first is what the position is doing — side, entry, profit — and
 * the second is the plan it is running under: the stop, the target and how far it went in the
 * reader's favour before now. Squeezing all seven figures onto one row on a phone leaves each of
 * them four characters wide, which is how a stop ends up unreadable at exactly the moment it
 * matters.
 *
 * Run-up is beside the profit because on an open position the two together are the whole decision:
 * a position up ten that was up ninety is a position whose reader is already late, and the profit
 * alone does not say so.
 */
@Composable
private fun OpenPositionRow(
    position: ReplayPosition,
    price: Double?,
    onBreakEven: () -> Unit,
    onClose: () -> Unit,
) {
    val profit = price?.let(position::profit) ?: 0.0
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (position.isLong) R.string.replay_buy else R.string.replay_sell,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (position.isLong) CoineProColors.Buy else CoineProColors.Sell,
                modifier = Modifier.width(40.dp),
            )
            Text(
                text = BacktestFormat.money(position.entryPrice),
                style = CoineProTextStyles.RowFigure,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            // The amount and the percentage in one cell, stacked. Two columns would not fit on a
            // phone, and dropping the percentage would leave a reader comparing a rehearsal on a
            // two-hundred-dollar instrument against one on a forty-thousand-dollar instrument by
            // the absolute number, which says nothing about which decision was better.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = BacktestFormat.money(profit, signed = true),
                    style = CoineProTextStyles.RowFigure,
                    color = resultInk(profit),
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = BacktestFormat.signedPercent(price?.let(position::profitPercent) ?: 0.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = resultInk(profit),
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.replay_position_close),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.OnAccent,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CoineProShapes.small)
                    .background(CoineProColors.AccentFill)
                    .clickable(onClick = onClose)
                    .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A dash where there is no stop, and it is the most important dash on the row: a
            // position with no stop has unbounded risk, and a zero or a blank there would read as
            // a level that exists.
            LevelCell(
                label = stringResource(R.string.replay_stop),
                value = position.stopLoss?.let(BacktestFormat::money) ?: BacktestFormat.ABSENT,
                colour = CoineProColors.Sell,
            )
            LevelCell(
                label = stringResource(R.string.replay_target),
                value = position.takeProfit?.let(BacktestFormat::money) ?: BacktestFormat.ABSENT,
                colour = CoineProColors.Buy,
            )
            LevelCell(
                label = stringResource(R.string.replay_run_up),
                value = BacktestFormat.money(position.runUp),
                colour = CoineProColors.Buy,
            )
            Text(
                text = stringResource(R.string.replay_break_even),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CoineProShapes.small)
                    .background(CoineProColors.SurfaceElevated)
                    .clickable(onClick = onBreakEven)
                    .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
            )
        }
    }
}

/** A small labelled figure on the position's second line. */
@Composable
private fun LevelCell(label: String, value: String, colour: Color) {
    Column(modifier = Modifier.width(76.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
        Text(
            text = value,
            style = CoineProTextStyles.RowFigure,
            color = colour,
            maxLines = 1,
        )
    }
}

@Composable
private fun LedgerRow(label: String, value: String, colour: Color = CoineProColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
        Text(value, style = CoineProTextStyles.RowFigure, color = colour, textAlign = TextAlign.Right)
    }
}

/**
 * A buy or sell chip, dimmed rather than removed when it cannot fire.
 *
 * Dimmed because a control that vanishes while a field is incomplete leaves the reader looking for
 * what they did wrong; one that is visibly present and dim says the same thing without the search.
 */
@Composable
private fun ActionChip(label: String, colour: Color, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (enabled) CoineProColors.OnAccent else CoineProColors.TextDisabled,
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(if (enabled) colour else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = 6.dp),
    )
}

/** Green above zero, red below, ordinary ink at a scratch. */
@Composable
private fun resultInk(value: Double): Color = when {
    !value.isFinite() -> CoineProColors.TextMuted
    value > 0 -> CoineProColors.Buy
    value < 0 -> CoineProColors.Sell
    else -> CoineProColors.TextPrimary
}

/**
 * The bar nearest a date the reader typed, or null when what they typed is not one yet.
 *
 * ### Jalali, because that is the calendar the reader has in mind
 *
 * «۱۴۰۳/۰۵/۱۲» is the date an Iranian trader remembers an announcement by, and a field that
 * silently wanted 2024-08-02 instead would be a field nobody could use. Persian digits are folded
 * first for the same reason the custom-interval field folds them: an Iranian keyboard produces
 * them by default, and refusing them while accepting Latin ones looks broken.
 *
 * ### Nearest, and never past the end
 *
 * A date the snapshot does not cover resolves to its closest bar rather than to nothing. Somebody
 * who types a weekend, a holiday or a market outage means "around there", and answering with a
 * disabled button would leave them guessing which nearby day the feed actually has. The controller
 * clamps as well, so a date before the first bar lands on the first bar.
 *
 * Null means the text is not yet a date — the normal state while somebody is still typing, and not
 * an error worth saying anything about.
 *
 * Internal rather than private so it can be asserted directly: the off-by-one that matters here is
 * a calendar conversion, and a calendar conversion is far easier to be certain about as a test than
 * as a field somebody types into.
 */
internal fun indexOfTypedDate(typed: String, bars: List<Candle>): Int? {
    if (bars.isEmpty()) return null
    val parts = typed.trim().foldDigitsToLatin().split('/', '-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    val gregorian: LocalDate = runCatching { JalaliDate(year, month, day).toGregorian() }
        .getOrNull() ?: return null
    // Midnight in Tehran, because that is the boundary every daily bar in this app is cut on.
    val wanted = gregorian.atStartOfDay(CHART_TIME_ZONE).toEpochSecond()
    var best = 0
    var distance = Long.MAX_VALUE
    bars.forEachIndexed { index, candle ->
        val gap = kotlin.math.abs(candle.t - wanted)
        if (gap < distance) {
            distance = gap
            best = index
        }
    }
    return best
}

@Composable
private fun TransportButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = CoineProColors.TextSecondary,
) {
    Box(
        modifier = Modifier
            .clip(CoineProShapes.small)
            .clickable(onClick = onClick)
            .padding(CoineProSpacing.Half),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}
