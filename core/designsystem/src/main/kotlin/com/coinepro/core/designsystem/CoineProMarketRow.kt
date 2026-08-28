package com.coinepro.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.MarketNumberFormatter

/**
 * The percentage move, as a filled pill.
 *
 * Coloured text was the previous answer and it is the weaker one. A number tinted green among other
 * numbers has to be found before it can be read; a filled block is found before it is read, which
 * is the actual job — a reader scanning a market list is looking for *which rows moved*, not for
 * any particular figure. It is why every exchange's list has this shape, and the reason is legible
 * rather than fashionable.
 *
 * The fill is [CoineProTint.fill] at the same 8% the rest of the system uses for a tinted surface,
 * not a flat alpha: alpha over an unknown background produces a different colour on the card, on
 * the stage, and in the light theme, and the pill sits on all three.
 */
@Composable
fun CoineProPercentPill(
    percent: Double,
    modifier: Modifier = Modifier,
    /** The surface behind the pill, which the tint is computed against. */
    background: Color = CoineProColors.Surface,
) {
    // Zero is neither a rise nor a fall and is deliberately drawn as neither: a green 0.00% claims
    // a direction the market did not have.
    val ink = when {
        percent > 0 -> CoineProColors.Buy
        percent < 0 -> CoineProColors.Sell
        else -> CoineProColors.TextMuted
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CoineProTint.fill(ink, background))
            // Wide enough that a row of pills forms a column rather than a ragged edge — the point
            // of the shape is comparison down the list, and comparison needs alignment.
            .defaultMinSize(minWidth = 68.dp)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = MarketNumberFormatter.signedPercent(percent),
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Where the current price sits between the session's low and high.
 *
 * Two numbers a market feed already carries and almost no app draws. A price is only meaningful
 * against its own range — 64,180 says nothing; 64,180 sitting at the top of a 62,800–64,900 day
 * says the thing a reader opened the app to find out — and the bar answers it in one glance
 * without spending a line of text or a request for candles.
 *
 * Drawn only when the feed supplied both ends and they differ. A degenerate range would render as
 * a marker pinned to one edge, which reads as a price at its extreme rather than as no range at
 * all, and inventing that impression is worse than leaving the row plain.
 */
@Composable
fun CoineProRangeBar(
    low: Double,
    high: Double,
    price: Double,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 44.dp,
    /** The move's own colour, so the bar and the pill beside it read as one object. */
    ink: Color = CoineProColors.TextSecondary,
) {
    if (high <= low) return
    val position = ((price - low) / (high - low)).coerceIn(0.0, 1.0)
    val marker = 4.dp
    Box(
        modifier = modifier
            .width(width)
            .height(marker)
            .clip(CoineProPillShape)
            .background(CoineProColors.Border),
    ) {
        // A marker, not a fill. The question is "where in today's range", and a filled bar answers
        // "how much of it", which is a different question and the wrong one.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                // Inset by half the marker at each end so the dot stays inside the track at both
                // extremes instead of hanging off the end of a day's high.
                .padding(start = (width - marker) * position.toFloat())
                .width(marker)
                .height(marker)
                .clip(CoineProPillShape)
                .background(ink),
        )
    }
}

/**
 * One instrument in a list.
 *
 * There were three of these — Home, search and the guest market each grew their own — and they had
 * already drifted: different logo sizes, different vertical rhythm, the percentage as plain text in
 * two of them and absent from the third. Three implementations of one row is three places for the
 * next change to be applied twice and forgotten once.
 *
 * The layout is the one every trading app converges on, and it converges for a reason: the eye
 * runs down the left edge for the instrument, down the right edge for the number, and the pill
 * gives it a third column it can scan without reading. Density is the point — a market list is
 * read by comparison, and comparison needs rows close enough to hold in one glance.
 */
@Composable
fun CoineProMarketRow(
    symbol: String,
    /** The instrument's own name, or the ticker again where there is no better one. */
    title: AnnotatedString,
    subtitle: AnnotatedString,
    /** Already formatted and isolated by the caller — it owns the decimals its feed deserves. */
    price: String?,
    modifier: Modifier = Modifier,
    changePercent: Double? = null,
    low24h: Double? = null,
    high24h: Double? = null,
    rawPrice: Double? = null,
    /** Shown in place of the pill. A stale feed says so; nothing stands in for a missing move. */
    trailingNote: String? = null,
    trailingNoteColor: Color = CoineProColors.TextMuted,
    background: Color = CoineProColors.Surface,
    /**
     * Screen edge to content, for a list that sits on the stage rather than inside a card.
     *
     * Zero by default because the common case is a card, which already carries its own. A list on
     * the bare stage has nothing between it and the edge of the phone, and the figures end up
     * touching the glass — which is what happened the first time this row was shared.
     */
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    /**
     * Whether this instrument is on the reader's watchlist, or null where the row has no star.
     *
     * Null rather than false, so a list that does not offer starring shows no star at all. A grey
     * star on every row of a screen where it cannot be pressed is an invitation that goes nowhere.
     */
    starred: Boolean? = null,
    onToggleStar: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Every row is the same height whether or not the feed sent a move for it, because a
            // list whose rows breathe according to how complete the data is reads as a list that
            // is still loading. The minimum is the tall case — logo, two lines of text, price and
            // pill — so a row with none of them holds the shape rather than collapsing into it.
            .defaultMinSize(minHeight = ROW_MIN_HEIGHT)
            // The tint the trader reads. It goes on the row rather than on the figure because the
            // question a market list answers is *which* instrument moved, and a flash confined to
            // eleven characters of number is one nobody catches in peripheral vision.
            .coineProPriceFlash(rawPrice)
            // The clear comes first and the click after it. `clearAndSetSemantics` wipes everything
            // declared before it on this node, so a clickable above this line would have its action
            // erased — the row would still work under a finger and be unreachable with TalkBack,
            // which is the worst version of the bug because it looks fine.
            .clearAndSetSemantics { contentDescription = symbol }
            .let { base ->
                onClick?.let { action ->
                    base
                        .pressScale(interaction, CoineProPress.ROW)
                        .clickable(interaction, null) {
                            haptics.select()
                            action()
                        }
                } ?: base
            }
            .padding(horizontal = horizontalPadding, vertical = CoineProSpacing.OneHalf),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (starred != null && onToggleStar != null) {
            // Leading, on the reading edge. The star is a state the reader scans down the list as
            // much as a control they press, and putting it beside the price would make it compete
            // with the number that has to be read first.
            Icon(
                painter = painterResource(
                    if (starred) R.drawable.icon_filled_star else R.drawable.icon_star,
                ),
                contentDescription = null,
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .clickable {
                        // Starring is a change the reader made to their own list, not navigation,
                        // so it gets the weight of a committed action rather than a selection.
                        haptics.commit()
                        onToggleStar()
                    }
                    .padding(4.dp)
                    .size(18.dp),
                tint = if (starred) CoineProColors.Accent else CoineProColors.TextDisabled,
            )
        }
        CoineProAssetLogo(symbol = symbol, size = 34.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = price ?: "—",
                style = CoineProTextStyles.RowFigure,
                color = if (price == null) CoineProColors.TextMuted else CoineProColors.TextPrimary,
                maxLines = 1,
            )
            // Two lines, never three. The range bar sits *beside* the pill rather than under it,
            // which buys the day's range for no extra height — the reason most lists do not carry
            // one is that a third line per row costs more than the range is worth.
            Row(
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (low24h != null && high24h != null && rawPrice != null && changePercent != null) {
                    CoineProRangeBar(
                        low = low24h,
                        high = high24h,
                        price = rawPrice,
                        ink = if (changePercent >= 0) CoineProColors.Buy else CoineProColors.Sell,
                    )
                }
                when {
                    trailingNote != null -> Text(
                        text = trailingNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = trailingNoteColor,
                        fontWeight = FontWeight.Normal,
                    )
                    changePercent != null ->
                        CoineProPercentPill(changePercent, background = background)
                    // Nothing at all rather than a dash. A missing move is missing; a dash in the
                    // column where the movers are read looks like a market that did not move.
                    else -> Unit
                }
            }
        }
    }
}

/**
 * The shortest a market row is allowed to be.
 *
 * Set by the tallest thing inside it — the 34dp logo plus this row's own vertical padding — rather
 * than chosen, so a row that happens to have no price and no pill still reserves the same space as
 * its neighbours.
 */
private val ROW_MIN_HEIGHT = 62.dp
