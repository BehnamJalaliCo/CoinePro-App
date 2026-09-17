package com.coinepro.feature.search

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.MarketPulse
import com.coinepro.core.symbols.SymbolCategory

/**
 * **The seven tabs the markets surface is read through** (run ΤΦΥ, U5).
 *
 * The screen used to carry two strips: a category tray — «همه», «کریپتو», «فارکس», «فلزات»,
 * «دیده‌بان» — and a lens row under it — «داغ», «بیشترین رشد», «بیشترین افت», «ارزش معاملات». Two
 * filled trays stacked over a list is a lot of chrome to say one thing, and a reader had to
 * understand that the two compose before either of them was useful.
 *
 * This is the reference's arrangement and the owner's list: one scrollable row of seven, underlined
 * where it is live. A tab is a **question**, and the fact that some of them are a category and some
 * of them are an ordering is an implementation detail the reader never has to learn.
 *
 * The sortable headings under the row have not gone anywhere, so «only the ones that are up» — the
 * thing the two one-sided lenses used to answer — is one tap on the change column from «برنده/بازنده».
 */
enum class MarketsPage(
    @StringRes val labelRes: Int,
    /** The family this tab narrows to, or null for every market. */
    val category: SymbolCategory? = null,
    /** How the tab orders what is left. */
    internal val lens: MarketLens = MarketLens.NONE,
    /** Whether the tab is the watchlist panel rather than a list of the catalogue. */
    val panel: Boolean = false,
) {
    TOP(R.string.markets_page_top),
    POPULAR(R.string.markets_page_popular, lens = MarketLens.HOT),
    WATCHLIST(R.string.markets_watchlist, panel = true),
    MOVERS(R.string.markets_page_movers, lens = MarketLens.MOVERS),
    VOLUME(R.string.markets_page_volume, lens = MarketLens.VOLUME),
    FOREX(R.string.markets_page_forex, category = SymbolCategory.FOREX),
    METALS(R.string.markets_page_metals, category = SymbolCategory.METAL),
    ;

    /** Whether this tab needs the day's figures — and so must be absent where there is no route. */
    val needsFigures: Boolean get() = lens != MarketLens.NONE
}

/**
 * Which of the seven this platform can actually fill.
 *
 * A tab that can only ever be empty is worse than no tab: it teaches the reader that the app is
 * broken rather than that the data is elsewhere. Two rules, and both were already the screen's:
 *
 * * a **category** tab is offered only where the catalogue holds that family;
 * * a tab that **orders by the day's figures** is offered only where there is a route to fill.
 *
 * «برتر» and «دیده‌بان» are always offered: the first is the list itself and the second is the
 * reader's own, which is allowed to be empty and has copy that says so.
 */
fun offeredPages(families: Set<SymbolCategory>, hasFigures: Boolean): List<MarketsPage> =
    MarketsPage.entries.filter { page ->
        when {
            page.category != null -> page.category in families
            page.needsFigures -> hasFigures
            else -> true
        }
    }

/**
 * The row itself: scrollable, with the live tab underlined.
 *
 * Underlined rather than filled, which is the difference between this and the tray it replaces. A
 * filled pill reads as a *filter applied*; an underline reads as *where you are*, and with seven of
 * them across a phone the second is the only one that fits.
 */
@Composable
fun MarketsTabRow(
    pages: List<MarketsPage>,
    selected: MarketsPage,
    onSelect: (MarketsPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        items(pages, key = { it.name }) { page ->
            val live = page == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .clickable {
                        haptics.select()
                        onSelect(page)
                    }
                    .padding(horizontal = CoineProSpacing.Half, vertical = CoineProSpacing.One),
            ) {
                Text(
                    text = stringResource(page.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                    color = if (live) CoineProColors.TextPrimary else CoineProColors.TextMuted,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .padding(top = CoineProSpacing.Half)
                        .height(UNDERLINE)
                        .width(if (live) UNDERLINE_WIDTH else 0.dp)
                        .clip(CoineProPillShape)
                        .background(CoineProColors.Accent),
                )
            }
        }
    }
}

/**
 * **The four figures pinned above the list** (run ΤΦΥ, U3).
 *
 * Total capitalisation, the day's turnover, bitcoin's share and the fear-and-greed reading. Three of
 * the four are «—» today and that is the honest state rather than a loading one — see [MarketPulse],
 * which explains what each would need, and `docs/runs/RUN_TFY/BLOCKED.md`, which names it.
 *
 * A dash is a **fact**, so it is tappable like every other cell: [onOpen] is what says why. A cell
 * that is merely blank teaches nothing.
 */
@Composable
fun MarketPulseRow(
    pulse: MarketPulse,
    onOpen: (MarketPulseCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        PulseCell(
            cell = MarketPulseCell.MARKET_CAP,
            value = pulse.marketCap?.let(::compactFigure),
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
        PulseCell(
            cell = MarketPulseCell.TURNOVER,
            value = pulse.turnover24h?.let(::compactFigure),
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
        PulseCell(
            cell = MarketPulseCell.DOMINANCE,
            value = pulse.bitcoinDominance?.let { "${it.toInt()}%" },
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
        PulseCell(
            cell = MarketPulseCell.FEAR_GREED,
            value = pulse.fearGreed?.toString(),
            onOpen = onOpen,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Which figure a reader tapped, so the sheet can say the right thing about it. */
enum class MarketPulseCell(@StringRes val labelRes: Int, @StringRes internal val whyRes: Int) {
    MARKET_CAP(R.string.markets_pulse_cap, R.string.markets_pulse_cap_why),
    TURNOVER(R.string.markets_pulse_volume, R.string.markets_pulse_venue_only),
    DOMINANCE(R.string.markets_pulse_dominance, R.string.markets_pulse_dominance_why),
    FEAR_GREED(R.string.markets_pulse_mood, R.string.markets_pulse_mood_why),
}

/**
 * **What one pulse cell means, and why it reads «—»** (run ΤΦΥ, U3).
 *
 * The row's whole argument is that a dash is a *fact* rather than a loading state, and a fact is
 * only worth drawing if the reader can find out what it is. So every cell opens, whether it carries
 * a figure or not: the one that does explains what the figure is measured over — this venue's own
 * book, not the world's — and the three that do not explain what they would need.
 *
 * The mood cell is the one with something extra to say. This app cannot serve the published
 * fear-and-greed index and will not compute a number of its own and put that name on it, but it
 * *can* count how much of the board is up today, so that is printed under its own name below the
 * explanation. Two different numbers, named separately, is the whole of the honesty here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketPulseSheet(
    cell: MarketPulseCell,
    pulse: MarketPulse,
    onDismiss: () -> Unit,
) {
    CoineProSheet(title = stringResource(cell.labelRes), onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            if (valueOf(cell, pulse) == null) {
                Text(
                    text = stringResource(R.string.markets_pulse_unknown_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextPrimary,
                )
            }
            Text(
                text = stringResource(cell.whyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
                fontWeight = FontWeight.Normal,
            )
            // What this app *can* measure of the mood, under its own name. See the sheet's note.
            if (cell == MarketPulseCell.FEAR_GREED) {
                pulse.breadth?.let { share ->
                    Text(
                        text = stringResource(R.string.markets_pulse_breadth, share.toString()),
                        style = MaterialTheme.typography.labelMedium.numeric(),
                        color = CoineProColors.TextPrimary,
                    )
                }
            }
        }
    }
}

/** The figure a cell is drawing, or null where there is none. One place, so the sheet agrees with the row. */
private fun valueOf(cell: MarketPulseCell, pulse: MarketPulse): Double? = when (cell) {
    MarketPulseCell.MARKET_CAP -> pulse.marketCap
    MarketPulseCell.TURNOVER -> pulse.turnover24h
    MarketPulseCell.DOMINANCE -> pulse.bitcoinDominance
    MarketPulseCell.FEAR_GREED -> pulse.fearGreed?.toDouble()
}

@Composable
private fun PulseCell(
    cell: MarketPulseCell,
    value: String?,
    onOpen: (MarketPulseCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    Column(
        modifier = modifier
            .clip(CoineProShapes.small)
            .clickable {
                haptics.select()
                onOpen(cell)
            }
            .padding(vertical = CoineProSpacing.Half),
    ) {
        Text(
            text = stringResource(cell.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // A dash, never a zero. Zero is a claim that the market is worth nothing.
            text = value ?: EM_DASH,
            style = MaterialTheme.typography.labelMedium.numeric(),
            color = if (value == null) CoineProColors.TextDisabled else CoineProColors.TextPrimary,
            maxLines = 1,
        )
    }
}

/**
 * **The headlines, scrolling sideways under the pulse** (run ΤΦΥ, U4).
 *
 * One row, horizontally scrollable, with a «مهم» chip on the stories that carry one. Tapping opens
 * the article.
 *
 * It takes plain values rather than the news module's own model, and that is a module-boundary
 * decision rather than laziness: the markets surface has no business depending on `feature:news`,
 * and a ticker that did would drag a reading page, an image loader and a body parser into a screen
 * that wants four fields. The caller — which already has both — hands over the four.
 *
 * Absent rather than empty where there are no headlines: a row with nothing in it is a strip of
 * padding the list pays for on every scroll.
 */
@Composable
fun MarketNewsTicker(
    headlines: List<MarketHeadline>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (headlines.isEmpty()) return
    val haptics = rememberCoineProHaptics()
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(headlines, key = { it.id }) { story ->
            Row(
                modifier = Modifier
                    .clip(CoineProPillShape)
                    .background(CoineProColors.SurfaceElevated)
                    .clickable {
                        haptics.select()
                        onOpen(story.id)
                    }
                    .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                if (story.important) {
                    Box(
                        modifier = Modifier
                            .clip(CoineProPillShape)
                            .background(CoineProColors.AccentFill)
                            .padding(horizontal = CoineProSpacing.Half),
                    ) {
                        Text(
                            text = stringResource(R.string.markets_news_important),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.OnAccent,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = story.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(HEADLINE_WIDTH),
                )
            }
        }
    }
}

/** One headline, as the ticker needs it. See [MarketNewsTicker] for why it is not a `NewsStory`. */
data class MarketHeadline(
    val id: String,
    val title: String,
    /** Whether the story carries the «مهم» chip — a high-impact item on the feed's own reading. */
    val important: Boolean = false,
)

/**
 * A big figure, short enough for a quarter of a phone's width.
 *
 * Latin digits and a Latin suffix, because this is a market figure and the house rule is that market
 * figures are Latin. `K`/`M`/`B`/`T` rather than a translated word for the same reason every ticker
 * on the screen is Latin: a reader scanning four cells is matching shapes, not reading prose.
 */
private fun compactFigure(value: Double): String {
    val magnitude = kotlin.math.abs(value)
    val (scaled, suffix) = when {
        magnitude >= 1_000_000_000_000.0 -> value / 1_000_000_000_000.0 to "T"
        magnitude >= 1_000_000_000.0 -> value / 1_000_000_000.0 to "B"
        magnitude >= 1_000_000.0 -> value / 1_000_000.0 to "M"
        magnitude >= 1_000.0 -> value / 1_000.0 to "K"
        else -> value to ""
    }
    // One decimal under a hundred, none above it: «$1.4B» and «$340M» are the shapes a reader
    // expects, and «$340.2M» is two characters of noise in a cell this narrow.
    val decimals = if (kotlin.math.abs(scaled) < 100.0 && suffix.isNotEmpty()) 1 else 0
    return "$" + java.lang.String.format(java.util.Locale.US, "%,.${decimals}f", scaled) + suffix
}

/** What a cell with no figure says. Not a zero, which would be a claim. */
private const val EM_DASH = "—"

private val UNDERLINE = 2.dp
private val UNDERLINE_WIDTH = 20.dp

/** Long enough for a headline to be worth reading, short enough that four fit on a phone. */
private val HEADLINE_WIDTH = 168.dp
