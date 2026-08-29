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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.ReplaySpeed
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
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
    var typedDate by rememberSaveable { mutableStateOf("") }
    val target = remember(typedDate, state.bars) { indexOfTypedDate(typedDate, state.bars) }

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
                thumbColor = CoineProColors.Accent,
                activeTrackColor = CoineProColors.Accent,
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
                Text(
                    // The multiplier is a market-adjacent figure and stays Latin, like every other
                    // number a trader compares against another app.
                    text = BidiText.isolateLtr(
                        if (step.multiplier < 1) "${step.multiplier}×" else "${step.multiplier.toInt()}×",
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                    modifier = Modifier
                        .clip(CoineProShapes.small)
                        .background(if (selected) CoineProColors.Accent else Color.Transparent)
                        .clickable { onSpeed(step) }
                        .padding(horizontal = CoineProSpacing.One, vertical = 2.dp),
                )
            }
        }

        // Going to a date, which is the one thing the scrub cannot do.
        //
        // Dragging a slider across two thousand bars to reach a particular Tuesday is a hunt: the
        // handle moves eight bars a pixel and the reader overshoots repeatedly. A date is what
        // they actually have in mind — the day of the announcement, the day the level broke — so
        // it is what the control takes.
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    .clip(CoineProShapes.small)
                    .background(if (target == null) Color.Transparent else CoineProColors.Accent)
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
 */
private fun indexOfTypedDate(typed: String, bars: List<Candle>): Int? {
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
