package com.coinepro.feature.news

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProFontFamily
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment

/**
 * The type this feature reads in, and the one place in the app where reading leading is correct.
 *
 * `CoineProType` makes an explicit argument against slack leading and it is right — for the app it
 * was written for. Its words: Material's reading ratios are "right for a paragraph somebody sits
 * down with and wrong for a row in a market list", and every screen it had was the second kind. So
 * `bodyLarge` runs at 16/23, a ratio of 1.44, which is a *dense* ratio: correct for a position row
 * and too tight for prose.
 *
 * This is the first screen in the app of the first kind. Somebody opening a story is reading, not
 * scanning, and the measure they are reading is a full-width Persian paragraph — where the marks
 * that separate ب from ت from ث sit above and below the letter body, and tight leading is exactly
 * what makes two lines of Persian collide. So the styles here take `CoineProType`'s own size
 * decisions unchanged and give the reading roles the leading that file withheld from list rows: a
 * ratio near 1.75, which is where Persian body text is set everywhere it is set well.
 *
 * They are defined here rather than added to `CoineProType` because they are this screen's, and a
 * reading scale offered app-wide would end up under a market list within a release.
 */
internal object NewsTextStyles {

    private fun reading(
        fontSize: Int,
        lineHeight: Int,
        weight: FontWeight,
        letterSpacing: Double = 0.0,
    ): TextStyle = TextStyle(
        fontFamily = CoineProFontFamily,
        fontWeight = weight,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
        // Trimmed at neither end, unlike the dense styles. A paragraph's first line wants its full
        // leading above it here: this text has a picture or a rule over it, not another row.
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    /**
     * The headline on the reading page, and the largest thing on it.
     *
     * Twenty-six rather than `headlineMedium`'s twenty-five, at a leading of thirty-six rather than
     * thirty-two. Headlines are the one place a tighter ratio is right — a headline is read as a
     * shape, not a paragraph — but 1.28 was tight enough that a three-line Persian headline had its
     * descenders touching the line below.
     */
    val Headline: TextStyle = reading(26, 36, FontWeight.Bold, -0.2)

    /** A headline inside a card in the list. One step under the page's own. */
    val CardHeadline: TextStyle = reading(18, 26, FontWeight.Bold, -0.1)

    /**
     * The opening paragraph — what the feed calls the summary, which on this page is the article.
     *
     * A point above the body and set in the secondary ink rather than in bold. A lede that is bold
     * competes with the headline directly above it; one that is merely larger and quieter reads as
     * the entrance to the text, which is the job.
     */
    val Lede: TextStyle = reading(18, 32, FontWeight.Normal)

    /** Ordinary reading text. */
    val Body: TextStyle = reading(16, 28, FontWeight.Normal)
}

/**
 * A short coloured label — an impact, a direction, a staleness.
 *
 * The colour is carried at 12% in the fill and 32% in the edge, which is the tint recipe
 * `CoineProCard` uses for the same reason: a pill that is *filled* with a meaning colour competes
 * with the gold action, and a pill with no edge at all disappears on the card behind it.
 */
@Composable
internal fun MetaPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PILL_RADIUS),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.32f)),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private val PILL_RADIUS = 999.dp

@Composable
internal fun ImpactPill(impact: MarketImpact) {
    MetaPill(stringResource(impact.labelRes()), impact.tint())
}

@Composable
internal fun SentimentPill(sentiment: NewsSentiment) {
    MetaPill(stringResource(sentiment.labelRes()), sentiment.tint())
}

/**
 * Who published this and when, on one line.
 *
 * The two halves are what a reader checks before deciding whether to read the rest, and they were
 * split across the top and the bottom of the old card — the time above the headline, the source
 * below the summary — so answering "is this a source I trust, and is it from today" meant reading
 * the whole card. Together they answer it in a glance.
 *
 * The date comes from [PersianDateTime.moment], so the day is Jalali and in Persian digits and the
 * clock is Latin and isolated. That split is the app's rule and it is right here for the reason the
 * rule gives: the day is prose and the clock is a figure a reader checks against their platform.
 */
@Composable
internal fun NewsByline(item: MarketNewsItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.source,
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Right,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = MIDDLE_DOT,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = PersianDateTime.moment(item.publishedAt),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            maxLines = 1,
        )
    }
}

/** The separator this app already uses between a day and a clock, reused between a source and one. */
private const val MIDDLE_DOT = "·"

@StringRes
internal fun MarketRelevance.labelRes(): Int = when (this) {
    MarketRelevance.GOLD -> R.string.news_relevance_gold
    MarketRelevance.SILVER -> R.string.news_relevance_silver
    MarketRelevance.CRYPTO -> R.string.news_relevance_crypto
}

@StringRes
internal fun MarketImpact.labelRes(): Int = when (this) {
    MarketImpact.HIGH -> R.string.news_impact_high
    MarketImpact.MEDIUM -> R.string.news_impact_medium
    MarketImpact.LOW -> R.string.news_impact_low
    MarketImpact.UNKNOWN -> R.string.news_impact_unknown
}

@StringRes
internal fun NewsSentiment.labelRes(): Int = when (this) {
    NewsSentiment.BULLISH -> R.string.news_sentiment_bullish
    NewsSentiment.BEARISH -> R.string.news_sentiment_bearish
    NewsSentiment.NEUTRAL -> R.string.news_sentiment_neutral
    NewsSentiment.UNKNOWN -> R.string.news_sentiment_unknown
}

@Composable
internal fun MarketImpact.tint(): Color = when (this) {
    MarketImpact.HIGH -> CoineProColors.Sell
    MarketImpact.MEDIUM -> CoineProColors.Warning
    MarketImpact.LOW -> CoineProColors.Buy
    MarketImpact.UNKNOWN -> CoineProColors.TextMuted
}

@Composable
internal fun NewsSentiment.tint(): Color = when (this) {
    NewsSentiment.BULLISH -> CoineProColors.Buy
    NewsSentiment.BEARISH -> CoineProColors.Sell
    NewsSentiment.NEUTRAL -> CoineProColors.Silver
    NewsSentiment.UNKNOWN -> CoineProColors.TextMuted
}

/**
 * One icon action, drawn at 40 and touchable at 48.
 *
 * `CoineProHeaderAction` is the app's version of this and is very nearly right, but it fixes the
 * tint at the primary ink — and both controls this feature needs, the save on an article and the
 * saved filter on the list, have to be able to say in colour that they are on. Everything else here
 * is deliberately identical to it, down to the radius and the hairline, so the two never read as
 * different kinds of control.
 */
@Composable
internal fun NewsIconAction(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CoineProColors.TextPrimary,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CoineProShapes.small)
            .background(CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.small)
            .clickable(interaction, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}
