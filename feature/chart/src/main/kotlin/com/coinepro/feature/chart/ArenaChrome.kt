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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.Arena
import com.coinepro.core.chart.ArenaScore
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.onPageAccent
import com.coinepro.core.designsystem.pageAccent
import com.coinepro.core.designsystem.proseDigits
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * The Arena's own band: the clock, and the way out of it.
 *
 * One row above the command band, and deliberately the only chrome the mode adds. A reader in the
 * Arena is looking at a chart with five minutes on it, and every control they need is the one they
 * already know — the replay bar steps the market, the setup card places the trade. What this adds is
 * the two things the chart cannot say: how long is left, and that pressing this ends it.
 */
@Composable
internal fun ArenaBar(session: ArenaSession, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberCoineProHaptics()
    // Under a minute the clock turns to the direction colour a fall is drawn in. Not «danger» — the
    // market's own red, because this screen has exactly two colours with meanings and inventing a
    // third for a timer would be the app teaching a colour it does not use anywhere else.
    val urgent = session.remaining <= URGENT_SECONDS && session.running
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = ARENA_BAR_TAG },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.arena_name),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.pageAccent,
            )
            Text(
                // Latin digits: this is a figure, read at a glance, against a clock. The app's
                // Persian digits are for prose counts — see the rule in the working agreement.
                text = BidiText.isolateLtr(clockOf(session.remaining)),
                style = MaterialTheme.typography.titleSmall.numeric(),
                color = if (urgent) CoineProColors.MarketDown else CoineProColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(if (session.running) R.string.arena_finish else R.string.arena_start),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.onPageAccent,
                modifier = Modifier
                    .clip(CoineProPillShape)
                    .background(CoineProColors.pageAccent)
                    .clickable {
                        haptics.commit()
                        onFinish()
                    }
                    .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
            )
        }
        // The clock as a rule, so a glance at the band answers «how long» without reading a number.
        Box(
            modifier = Modifier
                .fillMaxWidth(session.progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(if (urgent) CoineProColors.MarketDown else CoineProColors.pageAccent),
        )
        HorizontalDivider(color = CoineProColors.BorderSubtle)
    }
}

/**
 * What five minutes came to, and the one thing to do with it.
 *
 * ### Why the two halves are printed apart
 *
 * Sixty points for how the trades were taken and forty for what they made, and the sheet says both
 * — because a single total lets a profitable, undisciplined session hide inside a good number, which
 * is the reading the whole Arena exists to make impossible. The line under them names the two facts
 * the discipline score is actually made of: how many trades carried a stop, and how many followed a
 * loss too closely.
 */
@Composable
internal fun ArenaResultBody(
    score: ArenaScore,
    symbol: String,
    /** How many days in a row, including this one. Zero draws no streak at all. */
    streak: Int,
    /** The reader's best total so far, or null on their first day. */
    best: Int?,
    onShare: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter)
            .padding(bottom = CoineProSpacing.Two)
            .semantics { contentDescription = ARENA_RESULT_TAG },
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        if (!score.played) {
            // No trades is not a zero. A zero is a score; this is an absence, and saying «۰ از ۱۰۰»
            // to somebody who watched five minutes and decided not to trade would be scoring the one
            // decision this app most wants people to be able to make.
            Text(
                text = stringResource(R.string.arena_no_trades),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            return@Column
        }
        Text(
            text = BidiText.isolateLtr("${score.total}"),
            style = MaterialTheme.typography.displaySmall.numeric(),
            color = CoineProColors.pageAccent,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Two)) {
            ScoreHalf(
                label = stringResource(R.string.arena_discipline),
                value = score.discipline,
                outOf = Arena.DISCIPLINE_POINTS,
            )
            ScoreHalf(
                label = stringResource(R.string.arena_profit),
                value = score.profit,
                outOf = Arena.PROFIT_POINTS,
            )
        }
        Text(
            text = stringResource(
                R.string.arena_working,
                score.withStop.proseDigits(),
                score.trades.proseDigits(),
                score.revenge.proseDigits(),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        if (streak > 1) {
            Text(
                text = stringResource(R.string.arena_streak, streak.proseDigits()),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
            )
        }
        best?.takeIf { it > 0 }?.let {
            Text(
                // «You, over time» — the league of one, until there is somebody to compare against.
                // See `ArenaStore` for why a leaderboard of one reader is worse than none.
                text = stringResource(R.string.arena_best, BidiText.isolateLtr("$it")),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        onShare?.let { share ->
            val haptics = rememberCoineProHaptics()
            Text(
                text = stringResource(R.string.arena_share),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.onPageAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CoineProPillShape)
                    .background(CoineProColors.pageAccent)
                    .clickable {
                        haptics.commit()
                        share()
                    }
                    .padding(vertical = CoineProSpacing.One),
            )
        }
        Text(
            text = BidiText.isolateLtr(symbol),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextDisabled,
        )
    }
}

/** One half of the score, with the number it is out of. */
@Composable
private fun ScoreHalf(label: String, value: Int, outOf: Int) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            // «۴۵/۶۰» is a figure and a denominator, which is the same shape the Signal Layer's own
            // «۶۱٪ از ۱۸» has: a number is never printed without what it is out of.
            text = BidiText.isolateLtr("$value/$outOf"),
            style = MaterialTheme.typography.titleMedium.numeric(),
            color = CoineProColors.TextPrimary,
        )
    }
}

/** `m:ss`, which is the only clock a five-minute timer needs. */
internal fun clockOf(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val remainder = safe % 60
    return "${safe / 60}:${if (remainder < 10) "0" else ""}$remainder"
}

/** When the clock changes colour. One minute: enough to close a position, not enough to open one. */
private const val URGENT_SECONDS = 60

/** What a screenshot test looks for. */
private const val ARENA_BAR_TAG = "arena-bar"
private const val ARENA_RESULT_TAG = "arena-result"
