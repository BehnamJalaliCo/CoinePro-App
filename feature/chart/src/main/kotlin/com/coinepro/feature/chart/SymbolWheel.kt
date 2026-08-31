package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.symbols.SymbolArtwork

/**
 * The instrument on either side of the one on screen, in the reader's own list.
 *
 * ### Why a ring and not a list with ends
 *
 * A wheel that stops at both ends is a control that is dead on the first and last entry of the
 * watchlist, and the reader cannot tell a dead control from a broken one. Wrapping costs nothing
 * and means the two neighbours are always there — which is also the whole reason to draw them: a
 * step that is always available is a step a thumb learns.
 *
 * ### Two symbols is one neighbour, not the same one twice
 *
 * With exactly two entries the previous and the next are the same instrument, and drawing it on
 * both sides would be a control that looks like two moves and is one. It is drawn once, ahead.
 *
 * ### Artwork is the filter, here as everywhere
 *
 * `SymbolArtwork.covers` runs before anything else. A neighbour with no logo would be a blank
 * square or a lettered disc in the chart's own command band, which is the one defect the house
 * rules name outright.
 *
 * Pure Kotlin so the ring arithmetic — the part that is actually easy to get wrong — is a unit test
 * rather than something noticed on the ninth symbol of somebody's watchlist.
 */
internal data class SymbolNeighbours(
    val previous: String?,
    val next: String?,
    /** Where the current symbol sits in the covered list, one-based, or zero when it is not in it. */
    val position: Int,
    /** How many symbols the ring holds. */
    val total: Int,
) {
    /** Whether there is anything to draw at all. */
    val isEmpty: Boolean get() = previous == null && next == null
}

/**
 * The ring around [current], drawn from [symbols].
 *
 * A symbol the reader is looking at but has not starred is not forced into the ring: the wheel then
 * offers the first and last of the list, so one tap still steps *into* their watchlist rather than
 * doing nothing. [SymbolNeighbours.position] is zero in that case, and the caller draws no counter
 * — «۰ از ۹» is a lie and «۱ از ۹» is a different one.
 */
internal fun symbolNeighbours(symbols: List<String>, current: String): SymbolNeighbours {
    val ring = symbols.filter(SymbolArtwork::covers).distinctBy(String::uppercase)
    if (ring.isEmpty()) return SymbolNeighbours(null, null, 0, 0)
    val index = ring.indexOfFirst { it.equals(current, ignoreCase = true) }
    if (index < 0) {
        // Not in the list. One neighbour when the list holds one, both ends of it otherwise.
        return SymbolNeighbours(
            previous = ring.last().takeIf { ring.size > 1 },
            next = ring.first(),
            position = 0,
            total = ring.size,
        )
    }
    if (ring.size == 1) return SymbolNeighbours(null, null, 1, 1)
    val next = ring[(index + 1) % ring.size]
    val previous = ring[(index - 1 + ring.size) % ring.size]
    return SymbolNeighbours(
        previous = previous.takeIf { ring.size > 2 },
        next = next,
        position = index + 1,
        total = ring.size,
    )
}

/**
 * The symbol [steps] places along the ring from [current].
 *
 * The arithmetic behind the flick, and it is separate from the composable for the reason
 * [symbolNeighbours] is: a ring index that wraps the wrong way is not something anybody notices
 * until the ninth symbol of somebody else's watchlist, and it is two lines to test.
 *
 * Positive is *forward* — down the reader's list, which is the direction a finger dragging **up**
 * moves a scroll through. Null when there is nothing to step to: an empty list, or one with a
 * single entry, where every step lands back where it started and a control that appears to move and
 * does not is worse than one that does not move.
 *
 * A symbol the reader is looking at but has not starred is not in the ring at all. Stepping from
 * there enters the list at its first entry going forward and its last going back, which is the same
 * answer [symbolNeighbours] gives and for the same reason: one flick should take somebody *into*
 * their watchlist rather than do nothing.
 */
internal fun symbolStep(symbols: List<String>, current: String, steps: Int): String? {
    val ring = symbols.filter(SymbolArtwork::covers).distinctBy(String::uppercase)
    if (ring.isEmpty() || steps == 0) return null
    val index = ring.indexOfFirst { it.equals(current, ignoreCase = true) }
    if (index < 0) return if (steps > 0) ring.first() else ring.last()
    if (ring.size == 1) return null
    val moved = ((index + steps) % ring.size + ring.size) % ring.size
    return ring[moved].takeIf { moved != index }
}

/**
 * Switching instrument from the chart's own command band.
 *
 * ### What was wrong before
 *
 * There was a switcher in this module and it never reached a phone. It was drawn only on a build
 * with no per-symbol controller holder — and the shipping app has one — so in production the strip
 * was unreachable code. The other route, the watchlist split, is a *pane*: it costs a third of the
 * screen, it exists only for a reader who has starred something, and on a short phone it collapses
 * to a ticker row pinned below everything. None of that is the control the owner was pointing at,
 * which is in the bar attached to the chart, beside the bar lengths.
 *
 * ### Why the neighbours are drawn rather than a list
 *
 * The move being made cheap here is *the next one along*, and that is a single tap with no reading:
 * the reader sees the instrument they would land on before they commit to it. A row of eight logos
 * is a second market list under a chart, which is what the watchlist strip already is and what this
 * band has no room for. Three cells and the current one in the middle also says something a list
 * cannot: where in their own list they are.
 *
 * ### Direction
 *
 * The row follows the page, so on a Persian screen «قبلی» sits on the right and «بعدی» on the left,
 * which is the direction a Persian reader steps backwards in. The tickers inside are Latin runs and
 * are isolated, or a symbol ending in a digit reorders the cell it sits in.
 */
@Composable
internal fun SymbolWheelBar(
    symbols: List<String>,
    current: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = remember(symbols, current) { symbolNeighbours(symbols, current) }
    if (ring.isEmpty) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = WHEEL_HEIGHT)
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            ring.previous?.let { symbol ->
                NeighbourCell(
                    symbol = symbol,
                    icon = DesignR.drawable.icon_caret_right,
                    description = stringResource(R.string.chart_wheel_previous, symbol),
                    onSelect = onSelect,
                )
            }
        }
        CurrentCell(symbol = current, position = ring.position, total = ring.total)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            ring.next?.let { symbol ->
                NeighbourCell(
                    symbol = symbol,
                    icon = DesignR.drawable.icon_caret_left,
                    description = stringResource(R.string.chart_wheel_next, symbol),
                    onSelect = onSelect,
                    leadingGlyph = false,
                )
            }
        }
    }
}

/**
 * One of the two faint neighbours.
 *
 * Muted ink rather than a lower alpha on the primary: alpha on text is what makes a label look like
 * a disabled control, and this one is very much pressable. The logo keeps its own colours — a
 * dimmed brand mark reads as a failed image load.
 *
 * The caret points the way the tap moves the reader through the list, and it sits on the outside of
 * the cell so both carets point away from the middle.
 */
@Composable
private fun NeighbourCell(
    symbol: String,
    @DrawableRes icon: Int,
    description: String,
    onSelect: (String) -> Unit,
    leadingGlyph: Boolean = true,
) {
    val haptics = rememberCoineProHaptics()
    val caret: @Composable () -> Unit = {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = CoineProColors.TextDisabled,
            modifier = Modifier.size(WHEEL_CARET),
        )
    }
    Row(
        modifier = Modifier
            .clip(CoineProShapes.small)
            .clickable {
                haptics.select()
                onSelect(symbol)
            }
            .heightIn(min = WHEEL_TOUCH)
            .padding(horizontal = CoineProSpacing.Half)
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingGlyph) caret()
        CoineProAssetLogo(symbol = symbol, size = WHEEL_NEIGHBOUR_LOGO)
        LtrDirection {
            Text(
                text = BidiText.isolateLtr(symbol),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!leadingGlyph) caret()
    }
}

/**
 * The instrument the chart is actually drawing.
 *
 * Tinted rather than filled: a solid accent block here would be the second gold object on a screen
 * this design system allows one of, and it would also read as a button. The counter behind it is a
 * prose count and so is written in Persian digits, unlike every figure on the chart above it.
 */
@Composable
private fun CurrentCell(symbol: String, position: Int, total: Int) {
    Row(
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(CoineProTint.fill(CoineProColors.pageAccentInk))
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoineProAssetLogo(symbol = symbol, size = WHEEL_CURRENT_LOGO)
        LtrDirection {
            Text(
                text = BidiText.isolateLtr(symbol),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.pageAccentInk,
                maxLines = 1,
            )
        }
        // Zero means the chart is on something the reader has not starred, and a counter would then
        // be a claim about a position in a list this symbol is not in.
        if (position > 0 && total > 1) {
            Text(
                text = stringResource(
                    R.string.chart_wheel_position,
                    position.toPersianDigits(),
                    total.toPersianDigits(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextDisabled,
                maxLines = 1,
            )
        }
    }
}

/** Tall enough to hold a 20dp logo and its padding without the band growing a third tier of air. */
private val WHEEL_HEIGHT = 36.dp

/** The neighbour's whole target. Forty-eight would push the band past what a plot can spare. */
private val WHEEL_TOUCH = 32.dp

private val WHEEL_NEIGHBOUR_LOGO = 16.dp
private val WHEEL_CURRENT_LOGO = 20.dp
private val WHEEL_CARET = 12.dp


/**
 * The reader's watchlist as a scroll, in the chart's own tool row — item 7.
 *
 * ### The control the owner circled, and why the one already here is not it
 *
 * «هنوز نمادها به‌صورت اسکرول اضافه نشده به آپ». The screenshot is TradingView's mobile chart
 * toolbar: one row under the plot holding, from the leading edge, a narrow column with the previous
 * ticker faint above, the current one solid in the middle and the next faint below — a wheel you
 * flick **vertically** — and then the bar length, the pencil, the studies and the overflow.
 *
 * [SymbolWheelBar] is a different control that happens to share the arithmetic. It lays the same
 * three symbols out *horizontally* across a whole tier of the command band: it costs a full-width
 * row, it is stepped by tapping a caret rather than by dragging, and it is a page-level strip
 * rather than a cell in a toolbar. It is the right shape in the fullscreen mode, where the bottom
 * strip is full width and there is nothing beside it; it is not what the owner pointed at. So both
 * exist, each where it belongs, and they share [symbolNeighbours] so they can never disagree about
 * what the reader's ring is.
 *
 * ### Why a drag and not a scrolling list
 *
 * A `LazyColumn` with a snapping fling would be the literal reading of "scroll", and it brings a
 * loop with it: the settle emits a symbol, the symbol changes the current entry, the current entry
 * re-centres the list, the re-centre settles. Every version of that has a frame in it where the
 * chart is loading an instrument the reader did not stop on. A drag that steps once per row of
 * travel is the same gesture from the reader's side — flick and the tickers move — with no state
 * to keep in step: the wheel has no scroll position of its own, only the symbol the chart is on.
 *
 * Dragging **up** moves forward through the list, which is what dragging up does to any scroll:
 * the content rises and the entry below becomes the one in the middle.
 *
 * ### What it never does
 *
 * Navigate. `onSelect` is `ChartScreen.switchSymbol`, which swaps the *controller* for the symbol
 * and leaves the screen standing — the drawings, the bar length, the armed tool, the scroll
 * position and the reader's place in the page all survive. That is the entire value of putting the
 * switcher here rather than sending somebody back to a market list, and it is why this takes a
 * callback rather than a route.
 */
@Composable
internal fun SymbolScrollWheel(
    symbols: List<String>,
    current: String,
    /** The feed's own move per symbol, for the middle row. Absent is ordinary; see [WatchlistQuote]. */
    quotes: Map<String, WatchlistQuote>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = remember(symbols, current) { symbolNeighbours(symbols, current) }
    if (ring.isEmpty) return
    val haptics = rememberCoineProHaptics()
    // One row of travel is one step. Taken from the row height rather than from a number of its
    // own, so the tickers move at the speed the finger does — a threshold larger than the row
    // would make the wheel lag the drag, and a smaller one would step twice in a flick nobody
    // meant as two.
    val stepPixels = with(LocalDensity.current) { WHEEL_SCROLL_ROW.toPx() }
    // Read in composition, not inside the semantics lambda: `stringResource` is a composable and
    // the semantics block is not one.
    val wheelLabel = stringResource(R.string.chart_wheel_scroll, current, ring.total.toPersianDigits())
    // Held across the frames of one drag, exactly as the chart's own pan holds its residue: throwing
    // the remainder away every frame turns a slow drag into a control that does nothing at all.
    var travel by remember(current) { mutableStateOf(0f) }
    val drag = rememberDraggableState { delta ->
        travel += delta
        // One step per frame at most, and the residue dropped when one is taken. `current` is the
        // symbol the *chart* is on and it comes back through recomposition, so a second step in the
        // same frame would be measured from a symbol the reader has already left — which on a hard
        // flick is how a wheel skips two instruments and loads a chart nobody asked for.
        if (travel <= -stepPixels || travel >= stepPixels) {
            val forward = travel <= -stepPixels
            travel = 0f
            symbolStep(symbols, current, if (forward) 1 else -1)?.let { landed ->
                haptics.select()
                onSelect(landed)
            }
        }
    }
    Column(
        modifier = modifier
            .width(WHEEL_SCROLL_WIDTH)
            .height(WHEEL_SCROLL_ROW * 3)
            .clip(CoineProShapes.small)
            .draggable(state = drag, orientation = Orientation.Vertical)
            .semantics { contentDescription = wheelLabel },
        verticalArrangement = Arrangement.Center,
    ) {
        WheelSide(symbol = ring.previous, onSelect = onSelect)
        WheelCurrent(symbol = current, move = quotes[current.uppercase()]?.changePercent)
        WheelSide(symbol = ring.next, onSelect = onSelect)
    }
}

/**
 * A neighbour, faint, above or below the middle.
 *
 * Still pressable: a reader who can see the ticker they want should not have to drag to it, and the
 * two ways of reaching it cost nothing beside each other. Muted ink rather than an alpha on the
 * primary — alpha on text is what makes a live control look disabled — and clipped to one line, so
 * a long ticker shortens rather than pushing the wheel wider than the cell it sits in.
 *
 * An absent neighbour still takes its row. A wheel whose middle jumps up when the list is short is
 * a control that moves while you are reading it.
 */
@Composable
private fun WheelSide(symbol: String?, onSelect: (String) -> Unit) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WHEEL_SCROLL_ROW)
            .then(
                if (symbol == null) {
                    Modifier
                } else {
                    Modifier.clickable {
                        haptics.select()
                        onSelect(symbol)
                    }
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (symbol == null) return@Box
        LtrDirection {
            Text(
                text = BidiText.isolateLtr(symbol),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The instrument the chart is drawing, and what it has done.
 *
 * The move is beside the ticker rather than under it, because the wheel is three rows and a fourth
 * would make it taller than the tool row it sits in. Latin digits and the buy/sell inks, as every
 * market figure in this app is; a feed that has not answered leaves the figure out rather than
 * printing a zero, which is a claim that the market has not moved.
 */
@Composable
private fun WheelCurrent(symbol: String, move: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(WHEEL_SCROLL_ROW),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LtrDirection {
            Text(
                text = BidiText.isolateLtr(symbol),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.pageAccentInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        move?.let { percent ->
            LtrDirection {
                Text(
                    text = MarketNumberFormatter.signedPercent(percent),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (percent >= 0) CoineProColors.Buy else CoineProColors.Sell,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One row of the scroll: nineteen points.
 *
 * Three of them is fifty-seven, which is what the tool row can hold beside a bar-length key without
 * the band growing a tier. It is under the forty-eight-point touch minimum on its own, and it is
 * allowed to be: the target is the whole wheel, which is dragged, and the two faint rows are a
 * shortcut on top of that rather than the only way to reach a neighbour.
 */
private val WHEEL_SCROLL_ROW = 19.dp

/** Wide enough for a six-letter ticker and a signed percentage beside it, at the label sizes. */
private val WHEEL_SCROLL_WIDTH = 104.dp
