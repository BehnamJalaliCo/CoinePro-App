package com.coinepro.feature.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.NumberStyle
import com.coinepro.core.common.MyWeek
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.WeekSummary
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProRowDivider
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.proseDigits
import java.time.Instant
import java.time.ZoneId

/**
 * **هفته‌ی من** — what the reader actually did, at the top of the journal.
 *
 * ### Why here and not a screen of its own
 *
 * Because the journal is where somebody already goes to review, and a weekly review that lived
 * behind its own tab would be a second place to go for the same act. The card is the first thing on
 * the screen and the entries are underneath it, which is the order a review happens in.
 *
 * ### The one figure it will not print
 *
 * A win rate under [MyWeek.MINIMUM_TRADES] closed trades. `WeekSummary.winPercent` is null there
 * and the card shows «۲ از ۴» instead — the count, which is true — rather than «۵۰٪», which is
 * three coin flips wearing a confident font. The whole of that rule is in `MyWeek`; this file only
 * has to not undo it, which it does by having no fallback.
 *
 * ### And the week it will not pretend to
 *
 * An untouched week is a sentence, not four zeros. A screen of zeros reads as a broken feature, and
 * «you did not trade this week» is the more useful thing to say to somebody building a habit.
 */
@Composable
internal fun MyWeekCard(
    week: WeekSummary,
    zone: ZoneId,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    CoineProCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringResource(R.string.journal_week_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                // The week's own first day, named. A review that did not say which week it was
                // about would be the same card every Saturday.
                text = stringResource(
                    R.string.journal_week_since,
                    PersianDateTime.day(Instant.ofEpochMilli(week.fromEpochMillis), zone),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )

            if (week.empty) {
                Text(
                    text = stringResource(R.string.journal_week_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CoineProSpacing.Half),
                )
                return@CoineProCard
            }

            CoineProRowDivider()

            WeekRow(
                label = stringResource(R.string.journal_week_trades),
                // The count is prose, so Persian digits.
                value = week.trades.proseDigits(),
            )
            if (week.trades > 0) {
                val rate = week.winPercent
                WeekRow(
                    label = stringResource(R.string.journal_week_wins),
                    value = if (rate != null) {
                        // A percentage a reader compares against a broker's statement, so Latin
                        // digits — `MarketNumberFormatter` pins the locale for exactly this.
                        stringResource(
                            R.string.journal_week_win_rate,
                            BidiText.isolateLtr(NumberStyle.percent(rate, 0)),
                        )
                    } else {
                        // Below the floor. The count, which is the true claim, and no percentage
                        // anywhere on the row — not greyed, not caveated, not there.
                        stringResource(
                            R.string.journal_week_win_count,
                            week.won.proseDigits(),
                            week.trades.proseDigits(),
                        )
                    },
                    colour = if (rate == null) CoineProColors.TextPrimary else if (rate >= 50.0) {
                        CoineProColors.Buy
                    } else {
                        CoineProColors.Sell
                    },
                )
                if (rate == null) {
                    Text(
                        text = stringResource(
                            R.string.journal_week_win_floor,
                            MyWeek.MINIMUM_TRADES.proseDigits(),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            WeekRow(
                label = stringResource(R.string.journal_week_alerts),
                value = stringResource(
                    R.string.journal_week_alerts_value,
                    week.alertsFired.proseDigits(),
                    week.alertsArmed.proseDigits(),
                ),
            )
            if (week.practiceSessions > 0) {
                WeekRow(
                    label = stringResource(R.string.journal_week_practice),
                    value = week.practiceSessions.proseDigits(),
                )
            }
            week.moverSymbol?.let { symbol ->
                val move = week.moverPercent ?: 0.0
                WeekRow(
                    label = stringResource(R.string.journal_week_mover),
                    // The ticker and its figure are both market values and both stay Latin.
                    value = BidiText.isolateLtr(
                        symbol + " " + MarketNumberFormatter.signedPercent(move),
                    ),
                    colour = if (move >= 0) CoineProColors.Buy else CoineProColors.Sell,
                )
            }

            onShare?.let { share ->
                CoineProSecondaryButton(
                    text = stringResource(R.string.journal_week_share),
                    onClick = share,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CoineProSpacing.Half),
                )
            }
        }
    }
}

/** One line of the week: what it is on the left, what it was on the right. */
@Composable
private fun WeekRow(label: String, value: String, colour: Color = CoineProColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = colour,
        )
    }
}
