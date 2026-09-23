package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.coinepro.core.chart.Duel
import com.coinepro.core.chart.DuelCall
import com.coinepro.core.chart.DuelRecord
import com.coinepro.core.chart.DuelVerdict
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.NumberStyle
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.onPageAccent
import com.coinepro.core.designsystem.pageAccent
import com.coinepro.core.designsystem.proseDigits
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * The duel's own band: the question, the two answers, and afterwards what the market did — run Τ2, C3.
 *
 * ### Why it is one band and not a screen
 *
 * The same argument as [ArenaBar]'s, and here it is stronger: the whole of this feature is *look at
 * this chart and say which way*. Everything but the question is already on the screen — the plot,
 * the instrument, the bars — so what the band adds is the question and the two taps, and after the
 * call the one line the reader came for.
 *
 * ### Before the call there are two buttons; after it there are none
 *
 * Deliberate. The next twenty bars are on screen once the call is made, and a reader who could
 * answer again with the answer in front of them would be recording a prediction they did not make.
 * See [DuelSession] for the same rule stated from the state's side.
 */
@Composable
internal fun DuelBar(
    session: DuelSession,
    /** The reader's record so far, for the line under the verdict. */
    record: DuelRecord,
    onCall: (DuelCall) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = DUEL_BAR_TAG },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.duel_name),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.pageAccent,
            )
            val outcome = session.outcome
            if (outcome == null) {
                Text(
                    text = stringResource(R.string.duel_question, Duel.FORWARD_BARS.proseDigits()),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                // `Buy`/`Sell` and not `MarketUp`/`MarketDown`, which look almost the same and mean
                // something else: those two are a *readout* — a candle that closed higher — and
                // these are controls a reader presses. The pair that is measured for legible ink on
                // a fill is the execution pair; see `CoineProPalette.marketUp` for the split.
                DuelPill(
                    label = stringResource(R.string.duel_up),
                    fill = CoineProColors.Buy,
                    ink = CoineProColors.OnAccent,
                    onClick = {
                        haptics.commit()
                        onCall(DuelCall.UP)
                    },
                )
                DuelPill(
                    label = stringResource(R.string.duel_down),
                    fill = CoineProColors.Sell,
                    ink = CoineProColors.OnAccent,
                    onClick = {
                        haptics.commit()
                        onCall(DuelCall.DOWN)
                    },
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            when (outcome.verdict) {
                                DuelVerdict.RIGHT -> R.string.duel_right
                                DuelVerdict.WRONG -> R.string.duel_wrong
                                DuelVerdict.TOO_CLOSE -> R.string.duel_too_close
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = when (outcome.verdict) {
                            DuelVerdict.RIGHT -> CoineProColors.Buy
                            DuelVerdict.WRONG -> CoineProColors.Sell
                            // Neither colour, because it was neither. A green «نزدیک بود» would be
                            // the app scoring noise as a read, which is the one thing this feature
                            // is built to refuse — see `DuelVerdict.TOO_CLOSE`.
                            DuelVerdict.TOO_CLOSE -> CoineProColors.TextSecondary
                        },
                    )
                    Text(
                        // Latin digits and a sign: this is a market figure. The rule is in the
                        // working agreement — prose counts are Persian, figures are not.
                        text = stringResource(
                            R.string.duel_move,
                            BidiText.isolateLtr(
                                (if (outcome.movePercent > 0) "+" else "") +
                                    NumberStyle.percent(outcome.movePercent, MOVE_DECIMALS),
                            ),
                        ),
                        style = MaterialTheme.typography.bodySmall.numeric(),
                        color = CoineProColors.TextMuted,
                    )
                    DuelRecordLine(record = record, alreadyAnswered = session.alreadyAnswered)
                }
                DuelPill(
                    label = stringResource(R.string.duel_finish),
                    fill = CoineProColors.pageAccent,
                    ink = CoineProColors.onPageAccent,
                    onClick = {
                        haptics.commit()
                        onFinish()
                    },
                )
            }
        }
        HorizontalDivider(color = CoineProColors.BorderSubtle)
    }
}

/**
 * The record, or the reason there is not one yet.
 *
 * Below [Duel.MINIMUM_ROUNDS] judged rounds there is **no rate at all** — not a rate with a caveat
 * beside it. `DuelRecord.rightPercent` is null there and this has no branch that invents one, which
 * is the same shape `MyWeek` uses and for the same reason: a hit rate over three calls is three coin
 * flips, and printing it teaches a reader something about themselves that is not true.
 */
@Composable
private fun DuelRecordLine(record: DuelRecord, alreadyAnswered: Boolean) {
    if (alreadyAnswered) {
        // Said, not swallowed. A refusal the screen hid would look exactly like a counter that
        // stopped working — which is why `DuelStore.answer` returns a boolean at all.
        Text(
            text = stringResource(R.string.duel_already),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        return
    }
    val rate = record.rightPercent
    Text(
        text = if (rate == null) {
            stringResource(
                R.string.duel_record_building,
                record.judged.proseDigits(),
                Duel.MINIMUM_ROUNDS.proseDigits(),
            )
        } else {
            stringResource(
                R.string.duel_record,
                BidiText.isolateLtr(NumberStyle.percent(rate, 0)),
                record.judged.proseDigits(),
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
}

/** One tap-sized answer. The band's only control, and there are at most two of them. */
@Composable
private fun DuelPill(label: String, fill: Color, ink: Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = ink,
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
    )
}

/** Two places on a twenty-bar move: enough to tell 0.62 % from 0.58 %, and no more. */
private const val MOVE_DECIMALS = 2

/** What a screenshot test looks for. */
private const val DUEL_BAR_TAG = "duel-bar"
