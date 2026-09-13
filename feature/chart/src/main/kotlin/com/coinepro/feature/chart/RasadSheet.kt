package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.AlertSuggestion
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.RasadCoach
import com.coinepro.core.chart.SetupScore
import com.coinepro.core.chart.SignalRead
import com.coinepro.core.chart.TradeFacts
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.onPageAccent
import com.coinepro.core.designsystem.pageAccent
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * **رصد**, on the chart page (run Ω4).
 *
 * ### One line on the page, three sentences behind it
 *
 * The brief wants the coach's three sentences on the chart from the first frame, and this page's
 * whole product is vertical space — three permanent lines of prose under the plot is the explanatory
 * text run Ω2 spent a version removing. So the *first* sentence is on the page, once, in a row the
 * height of a chip, and it opens the other two.
 *
 * That is not a compromise between the two lines: the first sentence is deliberately the trend one,
 * which is the sentence a reader wants when they have three seconds. The levels and the studies are
 * what they open when they have thirty.
 */
@Composable
internal fun RasadLine(
    series: CandleSeries,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val english = inEnglish()
    val first = RasadCoach.readChart(series, english = english).firstOrNull() ?: return
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half)
            .clip(CoineProShapes.small)
            .background(CoineProColors.Surface)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.small)
            .clickable {
                haptics.select()
                onOpen()
            }
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half)
            .semantics { contentDescription = RASAD_TAG },
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RasadMark()
        Text(
            text = first,
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The whole coach: what it sees, and the two things it can do about it.
 *
 * ### Why the actions are here and not three more sentences
 *
 * The brief names three jobs — read this chart, suggest an alert, review my last paper trade — and
 * the first is prose while the other two end in something happening. So the sentences are read and
 * the actions are pressed, which is also what stops this sheet becoming an essay with a button at
 * the bottom.
 *
 * An action with nothing behind it is not drawn. A suggestion needs a level clear of the current
 * price and a review needs a finished trade; where either is missing the sheet is simply shorter,
 * because a greyed «مرور معامله» on a reader's first day is an advertisement for something they have
 * not done yet.
 */
@Composable
internal fun RasadSheetBody(
    series: CandleSeries,
    reads: List<SignalRead>,
    setup: SetupScore,
    /** The reader's last finished paper trade, where there is one. */
    lastTrade: TradeFacts?,
    /** Arms the suggested alert. Null where this build has no composer to open. */
    onCreateAlert: ((AlertSuggestion) -> Unit)?,
) {
    val english = inEnglish()
    val sentences = RasadCoach.readChart(series, reads, setup, english)
    val suggestion = RasadCoach.suggestAlert(series, english)
    val review = lastTrade?.let { RasadCoach.reviewTrade(it, english) }.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter)
            .padding(bottom = CoineProSpacing.Two)
            .semantics { contentDescription = RASAD_SHEET_TAG },
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        if (sentences.isEmpty()) {
            // Under thirty bars the coach says nothing rather than guessing, and the sheet says so
            // rather than drawing three empty rows. See `RasadCoach.MINIMUM_BARS`.
            Text(
                text = stringResource(R.string.rasad_too_short),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextMuted,
            )
            return@Column
        }
        for (sentence in sentences) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                // A gold rule down the side of each sentence rather than a bullet: three bullets
                // read as a list of instructions, and these are three halves of one reading.
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = 20.dp)
                        .background(CoineProColors.pageAccent),
                )
                Text(
                    text = sentence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (suggestion != null && onCreateAlert != null) {
            HorizontalDivider(color = CoineProColors.BorderSubtle)
            Text(
                text = suggestion.why,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
            RasadAction(
                label = stringResource(R.string.rasad_suggest_alert),
                onClick = { onCreateAlert(suggestion) },
            )
        }

        if (review.isNotEmpty()) {
            HorizontalDivider(color = CoineProColors.BorderSubtle)
            Text(
                text = stringResource(R.string.rasad_review_heading),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            for (sentence in review) {
                Text(
                    text = sentence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextPrimary,
                )
            }
        }
    }
}

/** The coach's own mark: «رصد» in the accent, small, so the line is attributable at a glance. */
@Composable
private fun RasadMark() {
    Box(
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(CoineProTint.fill(CoineProColors.Gold, CoineProColors.SurfaceElevated))
            .padding(horizontal = CoineProSpacing.Half, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = BidiText.isolateLtr(stringResource(R.string.rasad_name)),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.pageAccentInk,
        )
    }
}

/** The sheet's one filled button. Gold, because it is the only thing on the sheet to press. */
@Composable
private fun RasadAction(label: String, onClick: () -> Unit) {
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(CoineProColors.pageAccent)
            .clickable {
                haptics.commit()
                onClick()
            }
            .padding(vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_bell),
            contentDescription = null,
            tint = CoineProColors.onPageAccent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.onPageAccent,
            modifier = Modifier.padding(start = CoineProSpacing.Half),
        )
    }
}

/** What a screenshot test looks for. */
private const val RASAD_TAG = "rasad-line"
private const val RASAD_SHEET_TAG = "rasad-sheet"
