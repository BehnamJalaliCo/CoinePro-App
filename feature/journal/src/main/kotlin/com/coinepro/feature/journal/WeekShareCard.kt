package com.coinepro.feature.journal

import android.content.Context
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MyWeek
import com.coinepro.core.common.NumberStyle
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.WeekSummary
import com.coinepro.core.common.proseDigits
import com.coinepro.core.designsystem.ShareCardContent
import com.coinepro.core.designsystem.ShareCardTone
import java.time.Instant
import java.time.ZoneId

/**
 * «هفته‌ی من», as a square somebody can post.
 *
 * ### What goes on it, and the one thing that does not
 *
 * The headline is the week's win rate **where there is one**, and the count where there is not —
 * the same rule the card on screen keeps, for the stronger reason: a screenshot outlives the screen
 * it came from, and a «۵۰٪» from two trades will be read by people who never see the three under
 * it. `WeekSummary.winPercent` is already null there, so the only way to get this wrong would be to
 * invent a fallback, and there is none.
 *
 * ### No chart image
 *
 * `ShareCardContent.image` stays null. Every other card in this app has a picture of a market on it
 * because every other card is *about* a market; this one is about a person's week, and a chart
 * behind it would be decoration standing in for a fact.
 */
internal fun weekShareContent(
    context: Context,
    week: WeekSummary,
    title: String,
    zone: ZoneId,
): ShareCardContent {
    val rate = week.winPercent
    return ShareCardContent(
        title = title,
        subtitle = context.getString(
            R.string.journal_week_since,
            PersianDateTime.day(Instant.ofEpochMilli(week.fromEpochMillis), zone),
        ),
        headline = when {
            week.empty -> context.getString(R.string.journal_week_share_quiet)
            rate != null -> BidiText.isolateLtr(NumberStyle.percent(rate, 0))
            // Below the floor there is no percentage anywhere on this picture, and the count is
            // the whole of the claim. See this file's own note on why that matters more here.
            else -> context.getString(
                R.string.journal_week_win_count,
                week.won.proseDigits(),
                week.trades.proseDigits(),
            )
        },
        tone = when {
            week.empty || rate == null -> ShareCardTone.NEUTRAL
            rate >= 50.0 -> ShareCardTone.UP
            else -> ShareCardTone.DOWN
        },
        lines = buildList {
            if (week.empty) return@buildList
            add(
                context.getString(
                    R.string.journal_week_share_trades,
                    week.trades.proseDigits(),
                ),
            )
            if (rate == null && week.trades > 0) {
                add(
                    context.getString(
                        R.string.journal_week_win_floor,
                        MyWeek.MINIMUM_TRADES.proseDigits(),
                    ),
                )
            }
            if (week.alertsFired > 0) {
                add(
                    context.getString(
                        R.string.journal_week_share_alerts,
                        week.alertsFired.proseDigits(),
                    ),
                )
            }
            // Three lines is the cap `ShareCardContent` states — more than three is a page, and a
            // page does not survive a feed — so the mover is offered last and only where there is
            // still room for it.
            week.moverSymbol?.takeIf { size < 3 }?.let { symbol ->
                add(
                    context.getString(
                        R.string.journal_week_share_mover,
                        BidiText.isolateLtr(
                            symbol + " " + NumberStyle.percent(week.moverPercent ?: 0.0, 1),
                        ),
                    ),
                )
            }
        },
    )
}
