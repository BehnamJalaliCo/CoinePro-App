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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.backtest.BacktestFormat
import com.coinepro.core.backtest.ReplayLedger
import com.coinepro.core.backtest.ReplayPosition
import com.coinepro.core.backtest.ReplaySession
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.ReplaySpeed
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProToggleChip
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.marketdata.CHART_TIME_ZONE
import java.time.LocalDate

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

        ReplayLedgerPanel(state)
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
 * A paper trade could already be taken during a replay, because the setup card reads whatever price
 * the chart is showing and during replay that is the replay bar. Everything after the entry was
 * missing — no position, no running result, no ledger — so a reader could open a trade in a
 * rehearsal and had nowhere to see what happened to it.
 *
 * ### Kept out of the paper-trading book, deliberately
 *
 * A replay session is a rehearsal over history the reader can scrub, step backwards through and
 * start again. The paper book is a record of decisions taken without knowing what came next. Mixing
 * a rehearsal into a record is how a journal stops being trusted, so nothing here is written
 * anywhere: the session lives for as long as replay is on, and leaving replay discards it. That is
 * the honest shape of a rehearsal — worth reading, not worth keeping — and it is why the result is
 * shown here, while the session is still running, rather than in a summary afterwards.
 *
 * Collapsed until asked for. The transport above it is what a reader came to the bar for, and a
 * trading panel permanently open under the chart would push the chart itself off a phone.
 */
@Composable
private fun ReplayLedgerPanel(state: ReplayState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var sizeText by rememberSaveable { mutableStateOf("1") }
    // A new snapshot is a new session. Keyed on the first bar and the length, which together change
    // on every fresh entry into replay and on nothing else.
    var session by remember(state.bars.firstOrNull()?.t, state.bars.size) {
        mutableStateOf(ReplaySession())
    }

    // Marked rather than accumulated: stepping backwards un-reveals the bars that set an envelope,
    // and a run-up that survived the step back would be a number the reader cannot see on the
    // chart any more. See `ReplayLedger.mark`.
    val marked = remember(session, state.cursor, state.bars) {
        ReplayLedger.mark(session, state.bars, state.cursor)
    }
    val price = state.bars.getOrNull(state.cursor)?.c
    val size = sizeText.foldDigitsToLatin().trim().toDoubleOrNull() ?: 0.0
    val tradable = price != null && size > 0

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (expanded) "بستن دفتر تمرین" else "معاملهٔ تمرینی",
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
                        label = "حجم",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    ActionChip("خرید", CoineProColors.Buy, tradable) {
                        session = ReplayLedger.open(marked, state.bars, state.cursor, true, size)
                    }
                    ActionChip("فروش", CoineProColors.Sell, tradable) {
                        session = ReplayLedger.open(marked, state.bars, state.cursor, false, size)
                    }
                }

                if (price != null) {
                    LedgerRow("قیمت این کندل", BacktestFormat.money(price))
                }

                marked.open.forEach { position ->
                    OpenPositionRow(position, price) {
                        session = ReplayLedger.close(marked, state.bars, state.cursor, position.id)
                    }
                }

                if (marked.closed.isNotEmpty()) {
                    val summary = ReplayLedger.summary(marked, state.bars, state.cursor)
                    LedgerRow(
                        "نتیجهٔ بسته‌شده",
                        BacktestFormat.money(summary.netProfit, signed = true),
                        resultInk(summary.netProfit),
                    )
                    LedgerRow("تعداد معاملهٔ بسته", BacktestFormat.count(summary.totalTrades))
                    LedgerRow("درصد برد", BacktestFormat.percent(summary.percentProfitable, 1))
                    // A rehearsal of three winning trades has an infinite profit factor. A dash,
                    // never a number — see `BacktestFormat`.
                    LedgerRow("ضریب سود", BacktestFormat.ratio(summary.profitFactor))
                }

                if (marked.open.isNotEmpty()) {
                    Text(
                        text = "پایان جلسه و بستن همه",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.OnAccent,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CoineProShapes.small)
                            .background(CoineProColors.AccentFill)
                            .clickable {
                                session = ReplayLedger.closeAll(marked, state.bars, state.cursor)
                            }
                            .padding(horizontal = CoineProSpacing.OneHalf, vertical = 6.dp),
                    )
                }

                Text(
                    text = "این دفتر فقط برای همین جلسهٔ بازپخش است و در دفتر معاملهٔ آزمایشی ثبت نمی‌شود. با خروج از بازپخش پاک می‌شود. کارمزد هر طرف پنج صدم درصد حساب می‌شود، همان که بک‌تست حساب می‌کند.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * One open rehearsal position, marked against the replay bar.
 *
 * Run-up is on the row beside the profit, because on an open position the two together are the
 * whole decision: a position up ten that was up ninety is a position whose reader is already late,
 * and the profit alone does not say so.
 */
@Composable
private fun OpenPositionRow(position: ReplayPosition, price: Double?, onClose: () -> Unit) {
    val profit = price?.let(position::profit) ?: 0.0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (position.isLong) "خرید" else "فروش",
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
        // The amount and the percentage in one cell, stacked. Two columns would not fit beside the
        // run-up on a phone, and dropping the percentage would leave a reader comparing a rehearsal
        // on a two-hundred-dollar instrument against one on a forty-thousand-dollar instrument by
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
            text = BacktestFormat.money(position.runUp),
            style = CoineProTextStyles.RowFigure,
            color = CoineProColors.Buy,
            textAlign = TextAlign.Right,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "بستن",
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
