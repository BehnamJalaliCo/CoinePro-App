package com.coinepro.feature.free

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint

/**
 * The page that answers «چرا این اپ؟» for somebody who found it on Google Play and knows nobody
 * behind it.
 *
 * ### Why this screen exists at all
 *
 * A survey of what the competing products put behind a paywall found that most of it is either
 * already built here, or is on-chain data nobody without an aggregator can have. Which means the
 * largest piece of marketing work available to this app is not building anything — it is *saying*
 * what is already true. That is the whole of this screen.
 *
 * ### The rule it is written under
 *
 * Every «رایگان» row was checked against the source before it was written, and the counts it
 * quotes are read from the app's own catalogues at runtime rather than typed — see [FreeFacts].
 *
 * And the «نداریم» rows stay. They are not an oversight and they are not modesty: a reader who has
 * used TradingView or Glassnode will test this page against the one thing they know, and if the
 * page has overstated that one thing, every other row on it is worthless. Five honest absences are
 * what make the eight present ones believable.
 */
@Composable
fun FreeScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                Text(
                    text = stringResource(R.string.free_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CoineProColors.TextPrimary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.free_lede),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            // The tally before the table, and it counts the absences out loud. A reader who is
            // told up front that five of thirteen rows say "no" reads the other eight differently.
            Text(
                text = stringResource(
                    R.string.free_tally,
                    FreeComparison.claims.size.toPersianDigits(),
                    FreeComparison.tally(Verdict.FREE).toPersianDigits(),
                    FreeComparison.tally(Verdict.PARTIAL).toPersianDigits(),
                    FreeComparison.tally(Verdict.ABSENT).toPersianDigits(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        items(FreeComparison.claims) { claim -> ClaimCard(claim) }

        item {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.free_honesty_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.free_honesty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                Text(
                    text = stringResource(R.string.free_footer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextPrimary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
                // The date the rivals' prices were read. A comparison table with no date is one
                // nobody can check, and this one is making claims about other people's businesses.
                Text(
                    text = stringResource(R.string.free_prices_checked, FreeFacts.PRICES_CHECKED),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ClaimCard(claim: FreeClaim) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerdictPill(claim.verdict)
            Text(
                // A company name, so it is isolated: a Latin proper noun in a right-to-left row
                // reorders around any punctuation beside it without this.
                text = BidiText.isolateLtr(claim.rival),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(claim.paywalled),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
        claim.price?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        Text(
            text = claim.answerText(),
            style = MaterialTheme.typography.bodyMedium,
            color = claim.verdict.ink(),
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
    }
}

/**
 * The answer sentence, with this app's own counts substituted where it has a placeholder.
 *
 * Four of the rows quote a figure and every one of them reads it from [FreeFacts] rather than from
 * a literal, so adding an indicator or lowering a cap changes the sentence on this page too. The
 * digits are Persian because these are counts in prose, not market figures.
 */
@Composable
private fun FreeClaim.answerText(): String = when (paywalled) {
    R.string.free_tv_indicators -> stringResource(
        answer,
        FreeFacts.indicators.toPersianDigits(),
        FreeFacts.drawingTools.toPersianDigits(),
        FreeFacts.chartTypes.toPersianDigits(),
    )

    R.string.free_tv_alerts -> stringResource(answer, FreeFacts.alerts.toPersianDigits())

    R.string.free_tv_watchlists -> stringResource(
        answer,
        FreeFacts.watchlists.toPersianDigits(),
        FreeFacts.layouts.toPersianDigits(),
    )

    else -> stringResource(answer)
}

/** Green where it is free, the ordinary muted ink where it is not. Never red: an absence we chose
 *  is not a failure, and colouring it like one would make the honest rows look like defects. */
@Composable
private fun Verdict.ink(): Color = when (this) {
    Verdict.FREE -> CoineProColors.Buy
    Verdict.PARTIAL -> CoineProColors.TextPrimary
    Verdict.ABSENT -> CoineProColors.TextMuted
}

@Composable
private fun VerdictPill(verdict: Verdict) {
    val tone = verdict.ink()
    Text(
        text = stringResource(
            when (verdict) {
                Verdict.FREE -> R.string.free_verdict_free
                Verdict.PARTIAL -> R.string.free_verdict_partial
                Verdict.ABSENT -> R.string.free_verdict_absent
            },
        ),
        style = MaterialTheme.typography.labelSmall,
        color = tone,
        modifier = Modifier
            .background(CoineProTint.fill(tone, CoineProColors.Surface), CoineProShapes.small)
            .border(1.dp, CoineProTint.edge(tone), CoineProShapes.small)
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
    )
}
