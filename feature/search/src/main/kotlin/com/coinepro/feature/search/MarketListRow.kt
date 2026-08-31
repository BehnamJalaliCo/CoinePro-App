package com.coinepro.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.datastore.WatchlistColumn
import com.coinepro.core.datastore.WatchlistColumnUnit
import com.coinepro.core.datastore.WatchlistFlag
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.SharedKeys
import com.coinepro.core.designsystem.sharedElement
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPercentPill
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.coineProPriceFlash
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.MarketSearchRow
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * One instrument, in the single shape both lists in this module use.
 *
 * There is exactly one of these on purpose. The markets tab and the watchlist show the same
 * instruments to the same reader one tap apart, and the moment they are two row implementations
 * they start to drift — a different logo size here, a different vertical rhythm there — and the
 * app reads as two apps stitched together. That already happened once in this repository, which is
 * why `CoineProMarketRow` exists in the design system; this is the same argument one level down,
 * for the denser terminal row that only these two screens use.
 *
 * What differs between the two lists is the **trailing block** and nothing else: the markets tab
 * puts a sparkline and a price there, the watchlist puts whichever columns the reader chose. So
 * that is a slot, and everything before it — the flag rail, the drag handle, the star, the logo,
 * the ticker and its Persian name — is fixed here and cannot diverge.
 *
 * The flag rail is the first child of the row, which puts it at the **leading** edge: in the
 * Persian layout direction this app runs in, that is the right-hand side. It is deliberately not
 * `Alignment.Start` on an absolute axis and deliberately not a hardcoded right edge — a row that
 * pinned the flag to the physical left would put it under the price column, where nothing else in
 * the app lives.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MarketListRow(
    row: MarketSearchRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The row menu. Null on a list with nothing to offer beyond opening the chart. */
    onLongClick: (() -> Unit)? = null,
    /** The colour on this symbol in this list, or null for one the reader has not flagged. */
    flag: WatchlistFlag? = null,
    /**
     * Whether the row carries the flag rail at all.
     *
     * True on the watchlist while the reader keeps the flag column, false on the markets tab. It
     * governs the rail's *presence*, not its colour: an unflagged row still draws a transparent
     * one, because a list where three of forty rows are flagged and the other thirty-seven are
     * indented three points less is a column of tickers that does not line up, which is the thing
     * this row exists to prevent.
     */
    flagRail: Boolean = false,
    /** Null where the list offers no starring, so no grey star appears that cannot be pressed. */
    starred: Boolean? = null,
    onToggleStar: (() -> Unit)? = null,
    /** The reorder grip, drawn only while the list is in the reader's own order. */
    handle: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Every row the same height whether or not the feed has quoted it yet. Without this a
            // list of forty markets where six are still waiting breathes as the prices land, and
            // the reader's thumb lands on the row below the one they aimed at. The watchlist's
            // drag arithmetic also counts on it — see `WatchlistPanel`.
            .defaultMinSize(minHeight = MarketRowHeight)
            // The tint a trader reads: which rows are moving, found before any figure is read.
            .coineProPriceFlash(row.quote?.price)
            .combinedClickable(
                onClick = {
                    haptics.select()
                    onClick()
                },
                onLongClick = onLongClick?.let { menu ->
                    {
                        haptics.commit()
                        menu()
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (flagRail) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .clip(CoineProPillShape)
                    // Transparent rather than absent when unflagged: the rail holds the column
                    // open so every ticker below it starts at the same place.
                    .background(flag?.let { Color(it.argb.toULong() shl 32) } ?: Color.Transparent),
            )
        }
        handle?.invoke()
        if (starred != null && onToggleStar != null) {
            val starHaptics = rememberCoineProHaptics()
            Icon(
                painter = painterResource(
                    if (starred) DesignR.drawable.icon_filled_star else DesignR.drawable.icon_star,
                ),
                contentDescription = stringResource(
                    if (starred) R.string.watchlist_unstar else R.string.watchlist_star,
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CoineProShapes.small)
                    .clickable {
                        starHaptics.commit()
                        onToggleStar()
                    }
                    .size(18.dp),
                tint = if (starred) CoineProColors.Accent else CoineProColors.TextDisabled,
            )
        }
        // The disc travels to the chart's header when this row is tapped, so what opens is
        // visibly the market the reader touched rather than a new page about it. A no-op
        // everywhere the row is not drawn inside a navigation destination — a sheet, a preview,
        // a render test. See `CoineProSharedElement`.
        CoineProAssetLogo(
            symbol = row.meta.symbol,
            size = 30.dp,
            modifier = Modifier.sharedElement(SharedKeys.logo(row.meta.symbol)),
        )
        // Wider than it was. Eighty-four points fitted the ticker and cut every Persian name
        // under it; the sparkline beside it was floating in a weighted box with room to spare.
        Column(modifier = Modifier.width(SymbolColumn)) {
            Text(
                text = row.meta.symbol,
                style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                color = CoineProColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedElement(SharedKeys.ticker(row.meta.symbol)),
            )
            Text(
                text = row.meta.listDescription,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/**
 * The figures a watchlist row can put in a column.
 *
 * Nullable throughout, and every null renders as an em dash rather than as a zero. A volume shown
 * as `0` is a claim that nothing traded; a dash is the truth, which is that this build's feed did
 * not say.
 */
internal data class WatchlistFigures(
    val price: Double? = null,
    val change: Double? = null,
    val changePercent: Double? = null,
    val dayHigh: Double? = null,
    val dayLow: Double? = null,
    val volume: Double? = null,
    val quoteVolume: Double? = null,
)

/**
 * What this build can actually put in each column, for one row.
 *
 * The catalogue feed quotes a price and a twenty-four-hour move and nothing else — there is no
 * high, no low and no volume in `MarketQuote`. Rather than leave four of the eight columns empty,
 * two of them are derived from data already on screen:
 *
 * * The **change in price** is recovered from the price and the percentage, which is exact
 *   arithmetic rather than an estimate: a price `p` that moved `c` percent opened at
 *   `p / (1 + c/100)`.
 * * The **day high and low** are the extremes of the same twenty-four-hour series the sparkline in
 *   the next column draws. They are sampled hourly closes, so they sit inside the true session
 *   extremes rather than on them — and that is the honest trade: a figure taken from a *different*
 *   series than the shape beside it would disagree with the picture the reader is looking at,
 *   which is worse than a figure that is slightly conservative and matches.
 *
 * Volume has no source in this build at all and stays null. The column exists so that the day a
 * feed carries it, one field changes here and nothing else does — and the column picker says so,
 * rather than letting a reader choose a column that will only ever show a dash.
 */
internal fun figuresFor(row: MarketSearchRow, line: List<Double>): WatchlistFigures {
    val price = row.quote?.price
    val percent = row.quote?.changePercent
    val open = if (price != null && percent != null && percent != -100.0) {
        price / (1.0 + percent / 100.0)
    } else {
        null
    }
    return WatchlistFigures(
        price = price,
        change = if (price != null && open != null) price - open else null,
        changePercent = percent,
        dayHigh = line.maxOrNull(),
        dayLow = line.minOrNull(),
    )
}

/**
 * One column's figure, right-aligned in a fixed width.
 *
 * Fixed rather than measured, for the same reason the price column in `CoineProMarketRow` is: a
 * column that resizes with its contents is a column that does not align, and a watchlist is read
 * by comparing one row against the next.
 *
 * [WatchlistColumn.unit] rather than the column's identity decides the formatting, which is why
 * the unit is carried on the enum at all — a percent is the only one that ends in a sign, and
 * `MarketNumberFormatter.signedPercent` is where that sign comes from.
 */
@Composable
internal fun WatchlistFigureCell(
    column: WatchlistColumn,
    figures: WatchlistFigures,
    modifier: Modifier = Modifier,
) {
    val value = when (column) {
        WatchlistColumn.LAST_PRICE -> figures.price
        WatchlistColumn.CHANGE -> figures.change
        WatchlistColumn.CHANGE_PERCENT -> figures.changePercent
        WatchlistColumn.DAY_HIGH -> figures.dayHigh
        WatchlistColumn.DAY_LOW -> figures.dayLow
        WatchlistColumn.VOLUME -> figures.volume
        WatchlistColumn.QUOTE_VOLUME -> figures.quoteVolume
        WatchlistColumn.FLAG -> null
    }
    // The move gets the pill rather than plain text, because that is how it is drawn everywhere
    // else in the app and a percentage that looks different here would read as a different number.
    if (column == WatchlistColumn.CHANGE_PERCENT && value != null) {
        Box(modifier = modifier.width(widthOf(column)), contentAlignment = Alignment.CenterEnd) {
            CoineProPercentPill(percent = value, background = CoineProColors.Stage)
        }
        return
    }
    Text(
        text = value?.let { formatFigure(column.unit, it) } ?: EmDash,
        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
        color = when {
            value == null -> CoineProColors.TextDisabled
            column.unit == WatchlistColumnUnit.SIGNED_PRICE && value > 0 -> CoineProColors.Buy
            column.unit == WatchlistColumnUnit.SIGNED_PRICE && value < 0 -> CoineProColors.Sell
            else -> CoineProColors.TextPrimary
        },
        // Right, never End. The device locale is Persian and the figures are Latin; an End
        // alignment would flip a column of prices to the far side of its own box.
        textAlign = TextAlign.Right,
        maxLines = 1,
        modifier = modifier.width(widthOf(column)),
    )
}

/**
 * A column's heading, and the control that sorts by it.
 *
 * One tap sorts by the column largest-first, a second flips it, a third puts the list back into
 * the reader's own order. Three states rather than two, because a watchlist's own order is the
 * point of a watchlist and a sort with no way off it would be a trap.
 */
@Composable
internal fun WatchlistColumnHeading(
    column: WatchlistColumn,
    sorted: Boolean,
    descending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = column.persianLabel + when {
            !sorted -> ""
            descending -> " ↓"
            else -> " ↑"
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (sorted) CoineProColors.TextSecondary else CoineProColors.TextDisabled,
        textAlign = TextAlign.Right,
        maxLines = 1,
        modifier = modifier
            .width(widthOf(column))
            .clickable(onClick = onClick),
    )
}

/**
 * How wide each column is drawn.
 *
 * The price column matches `CoineProMarketRow`'s ninety-two points so that the watchlist and every
 * other market list in the app put the decimal point in the same place. The rest are sized to the
 * longest figure each can hold — a signed change never needs the grouping a price does, a
 * percentage is the pill's own minimum width — rather than chosen to look even.
 */
internal fun widthOf(column: WatchlistColumn): Dp = when (column) {
    WatchlistColumn.FLAG -> 0.dp
    WatchlistColumn.LAST_PRICE -> 92.dp
    WatchlistColumn.CHANGE -> 78.dp
    WatchlistColumn.CHANGE_PERCENT -> 64.dp
    WatchlistColumn.DAY_HIGH, WatchlistColumn.DAY_LOW -> 82.dp
    WatchlistColumn.VOLUME, WatchlistColumn.QUOTE_VOLUME -> 76.dp
}

/**
 * The figure, in the form its unit calls for.
 *
 * Every branch goes through [MarketNumberFormatter] or through [compactAmount], and both pin
 * [Locale.US]. The device locale is Persian: a `String.format` left to follow it emits Persian
 * digits into a price column, which has already caused one bug in this repository.
 */
private fun formatFigure(unit: WatchlistColumnUnit, value: Double): String = when (unit) {
    WatchlistColumnUnit.PRICE -> MarketNumberFormatter.priceAuto(value)
    // The sign is the whole point of this column, so it is written even for a rise, where
    // `priceAuto` would give a bare number indistinguishable from a price.
    WatchlistColumnUnit.SIGNED_PRICE ->
        (if (value > 0) "+" else if (value < 0) "−" else "") + MarketNumberFormatter.priceAuto(abs(value))
    WatchlistColumnUnit.PERCENT -> MarketNumberFormatter.signedPercent(value)
    WatchlistColumnUnit.BASE_AMOUNT, WatchlistColumnUnit.QUOTE_AMOUNT -> compactAmount(value)
    WatchlistColumnUnit.NONE -> EmDash
}

/**
 * A traded amount, short enough for a column seventy-six points wide.
 *
 * Volumes run to ten figures and no watchlist column can hold `1,284,930,447.20`. Compacting to
 * `1.28B` loses precision the reader was never going to use at a glance — the question a volume
 * column answers is "is this one busy", not "exactly how busy" — and the full figure is on the
 * instrument's own screen one tap away.
 *
 * The suffixes are Latin `K`/`M`/`B` rather than Persian words, for the same reason the digits are
 * Latin: this is a market figure, and a reader comparing it against LBank or TradingView should
 * see the same characters rather than have to translate.
 */
internal fun compactAmount(value: Double): String {
    val magnitude = abs(value)
    val (scaled, suffix) = when {
        magnitude >= 1_000_000_000.0 -> value / 1_000_000_000.0 to "B"
        magnitude >= 1_000_000.0 -> value / 1_000_000.0 to "M"
        magnitude >= 1_000.0 -> value / 1_000.0 to "K"
        else -> value to ""
    }
    // Two decimals under a thousand, two above: `918.44M` and `12.70K` both read at a glance, and
    // a varying decimal count down a column is the jitter this app spends a fixed width avoiding.
    val formatted = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(scaled)
    return formatted + suffix
}

/** The width of the ticker-and-name block, shared by both lists so their logos line up. */
internal val SymbolColumn = 96.dp

/**
 * The height every row in this module settles at.
 *
 * Not a minimum that rows happen to exceed. Nothing inside a watchlist row is taller than the
 * reorder grip's forty points, and forty plus the row's nine points of padding at each end is
 * exactly this — so the minimum is what every row actually measures, and the watchlist's drag
 * arithmetic can convert a finger's travel into a number of positions by dividing by it.
 *
 * The markets tab's rows are taller, because the star takes Material's forty-eight point tap
 * target. That is fine there and would not be here: it is the reason the grip sets its own height
 * rather than asking for the same minimum.
 *
 * At a very large system font scale the two-line ticker column can outgrow forty points and the
 * row with it, and a drag then lags the finger slightly. Correcting for it would mean measuring
 * each item and holding a map of bounds that is stale for exactly the frame the next step is
 * computed in, which trades a small inaccuracy for an occasional wrong one.
 */
internal val MarketRowHeight = 58.dp

/** What a cell with no figure says. Not a zero, which would be a claim. */
private const val EmDash = "—"
