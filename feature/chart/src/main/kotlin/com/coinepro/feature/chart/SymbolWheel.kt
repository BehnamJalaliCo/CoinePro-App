package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProMotionSpecs
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.symbols.SymbolArtwork
import kotlin.math.abs

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
 * ### Where this one is drawn now
 *
 * In the fullscreen mode's bottom strip, which is full width and has nothing beside it. The chart
 * page's own command band carries [SymbolScrollWheel] instead — the vertical scroll the owner asked
 * for, which fits in a toolbar cell where this does not. The two share [symbolNeighbours], so they
 * can never disagree about what the reader's ring is.
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
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether a finger is on the wheel, and how far it has travelled since the last step.
     *
     * The chart page draws [SymbolWheelOverlay] over the plot for as long as the first is true — the
     * big picker TradingView's phone shows while the toolbar's ticker is being dragged — and slides
     * it by the second, so the card and the cell move as one control rather than as two things that
     * happen to agree about a symbol. The wheel does not draw the overlay itself because the overlay
     * belongs over the *chart*, which is not this cell's parent.
     */
    onDragging: (Boolean) -> Unit = {},
    onTravel: (Float) -> Unit = {},
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

    // **The wheel moves with the finger, and it did not before.**
    //
    // «باید خیلی انیمیشن روانی داشته باشه.» The residue used to be a number nothing drew: the
    // tickers stood still through seventeen points of drag and then jumped a whole row on the
    // eighteenth. That is not a wheel, it is a button being pressed by a swipe — and it is why the
    // control felt stiff however smooth each individual step was.
    //
    // So the residue is now the cell's own translation. A `graphicsLayer` rather than an offset:
    // it moves at draw time, so a drag does not relayout five rows of text a frame.
    //
    // Read only inside `graphicsLayer` and inside the drag callback — never in composition. A float
    // that changes every frame and is read where the tree is built is a recomposition of this whole
    // page per frame of a drag, which is the opposite of what it was added for.
    // **Not keyed on the symbol.** The remainder has to survive the step that consumed it, or the
    // carry below is thrown away by the very recomposition it exists to smooth over.
    val travel = remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // Publishing it is one assignment into the page's own float state, and the picker reads that
    // the same deferred way. See `SymbolWheelOverlay`.
    fun move(to: Float) {
        travel.floatValue = to
        onTravel(to)
    }
    // What is left over when the finger lifts is walked back to zero rather than dropped. Dropping
    // it is a jump of up to a row at the one moment the reader is looking straight at the control.
    LaunchedEffect(dragging) {
        if (dragging) return@LaunchedEffect
        val from = travel.floatValue
        if (from == 0f) return@LaunchedEffect
        // A spring, not a curve: the wheel is a thing that moves, and it settles the way it was flicked.
        animate(initialValue = from, targetValue = 0f, animationSpec = CoineProMotionSpecs.fastSpatial()) { value, _ ->
            move(value)
        }
    }
    val drag = rememberDraggableState { delta ->
        val moved = travel.floatValue + delta
        // One step per frame at most. `current` is the symbol the *chart* is on and it comes back
        // through recomposition, so a second step in the same frame would be measured from a symbol
        // the reader has already left — which on a hard flick is how a wheel skips two instruments
        // and loads a chart nobody asked for.
        if (moved <= -stepPixels || moved >= stepPixels) {
            val forward = moved <= -stepPixels
            val landed = symbolStep(symbols, current, if (forward) 1 else -1)
            if (landed == null) {
                // Nothing to step to — a ring of one. The wheel is held at the boundary rather
                // than allowed to run off it.
                move(moved.coerceIn(-stepPixels, stepPixels))
            } else {
                // **The remainder is carried, not zeroed.** Landing on the new symbol moves the
                // column one row on its own; subtracting exactly one row of travel is what makes
                // that shift invisible. Zeroing it here is a visible kick backwards at the exact
                // instant the wheel steps forward.
                move(moved + if (forward) stepPixels else -stepPixels)
                haptics.select()
                onSelect(landed)
            }
        } else {
            move(moved)
        }
    }
    // A turn the reader did not drag: the symbol changed under the wheel — a tap on a neighbour,
    // the picker over the plot, the watchlist strip, a search. The column starts one row off in
    // the direction the list moved and springs home, so the wheel *turns* to the new ticker
    // instead of the ticker being swapped in place. «اسکرول نمادها خیلی بدفرم و زشته، یکم خوشگل و
    // انیمیشنیش بکن.» The drag's own step is excluded: it already carries its remainder above.
    var settled by remember { mutableStateOf(current) }
    LaunchedEffect(current) {
        val from = settled
        settled = current
        if (dragging || from.equals(current, ignoreCase = true)) return@LaunchedEffect
        val forward = symbolStep(symbols, from, 1).equals(current, ignoreCase = true)
        val backward = symbolStep(symbols, from, -1).equals(current, ignoreCase = true)
        val start = when {
            forward -> stepPixels
            backward -> -stepPixels
            else -> 0f
        }
        if (start == 0f) return@LaunchedEffect
        animate(initialValue = start, targetValue = 0f, animationSpec = CoineProMotionSpecs.fastSpatial()) { value, _ ->
            move(value)
        }
    }
    // The cell, as a control rather than as a column of text cut by the bar's edge.
    //
    // A pill on the elevated surface with a hairline round it, the current ticker bold on its
    // centre line, the neighbours dimmed and faded out towards the pill's edges, and a pair of
    // carets on the trailing side. The carets are the whole of the discoverability: three tickers
    // stacked in the open read as a misprint, and a reader who does not know the cell turns will
    // never drag it. Five rows are still drawn where one and two halves are visible, because a
    // wheel that slides has to have something to slide *in*.
    Box(
        modifier = modifier
            .height(WHEEL_SCROLL_HEIGHT)
            .draggable(
                state = drag,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    dragging = true
                    onDragging(true)
                },
                onDragStopped = {
                    dragging = false
                    onDragging(false)
                },
            )
            .semantics { contentDescription = wheelLabel },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(WHEEL_PILL_HEIGHT)
                .clip(WHEEL_PILL_SHAPE)
                .background(CoineProColors.SurfaceElevated)
                .border(1.dp, CoineProColors.BorderSubtle, WHEEL_PILL_SHAPE)
                .padding(start = WHEEL_PILL_INSET, end = WHEEL_PILL_INSET / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(WHEEL_SCROLL_WIDTH)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                // The rows fade with distance from the centre — a neighbour at half ink, the row
                // beyond it fainter still — which is the wheel's curvature written as alpha. Per
                // row rather than as a gradient over the cell, because the gradient gate is
                // right: a wash on a control is decoration, and a row's own ink is not.
                Column(modifier = Modifier.graphicsLayer { translationY = travel.floatValue }) {
                    WheelSide(symbol = symbolStep(symbols, current, -2), onSelect = onSelect, far = true)
                    WheelSide(symbol = ring.previous, onSelect = onSelect, far = false)
                    WheelCurrent(symbol = current)
                    WheelSide(symbol = ring.next, onSelect = onSelect, far = false)
                    WheelSide(symbol = symbolStep(symbols, current, 2), onSelect = onSelect, far = true)
                }
            }
            Column(
                modifier = Modifier.padding(start = CoineProSpacing.Half),
                verticalArrangement = Arrangement.spacedBy(WHEEL_CARET_GAP),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Dimmed on the end the ring cannot turn towards, which is how the control says
                // «this is the last one» without a toast.
                WheelCaret(icon = DesignR.drawable.icon_caret_up, enabled = ring.previous != null)
                WheelCaret(icon = DesignR.drawable.icon_caret_down, enabled = ring.next != null)
            }
        }
    }
}

/** One of the pill's two carets: a hint that the cell turns, in the muted ink. */
@Composable
private fun WheelCaret(@DrawableRes icon: Int, enabled: Boolean) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = if (enabled) CoineProColors.TextMuted else CoineProColors.TextDisabled,
        modifier = Modifier.size(WHEEL_SCROLL_CARET),
    )
}

/**
 * A neighbour, faint, above or below the middle.
 *
 * Still pressable: a reader who can see the ticker they want should not have to drag to it, and the
 * two ways of reaching it cost nothing beside each other. The same weight as the current one in the
 * disabled ink and a touch smaller — the wheel's far rows are further from the eye — and cut rather
 * than ellipsised, because the pill's fade is doing the cutting anyway.
 *
 * An absent neighbour still takes its row. A wheel whose middle jumps up when the list is short is
 * a control that moves while you are reading it.
 */
@Composable
private fun WheelSide(symbol: String?, onSelect: (String) -> Unit, far: Boolean) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WHEEL_SCROLL_ROW)
            .alpha(if (far) WHEEL_FAR_ALPHA else WHEEL_NEAR_ALPHA)
            .graphicsLayer {
                scaleX = WHEEL_SIDE_SCALE
                scaleY = WHEEL_SIDE_SCALE
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
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
        WheelTicker(symbol = symbol, colour = CoineProColors.TextDisabled)
    }
}

/** The instrument the chart is drawing: bold, in the primary ink, on the pill's own centre line. */
@Composable
private fun WheelCurrent(symbol: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(WHEEL_SCROLL_ROW),
        contentAlignment = Alignment.CenterStart,
    ) {
        WheelTicker(symbol = symbol, colour = CoineProColors.TextPrimary)
    }
}

/** One ticker in the toolbar's wheel: 16 sp bold, Latin, one line, cut at the cell's edge. */
@Composable
private fun WheelTicker(symbol: String, colour: androidx.compose.ui.graphics.Color) {
    LtrDirection {
        Text(
            text = BidiText.isolateLtr(symbol),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colour,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * The picker over the plot while the toolbar's wheel is being dragged.
 *
 * ### What the owner circled
 *
 * TradingView's phone app, mid-drag: a translucent card in the middle of the chart, the current
 * ticker large and bold with its logo, four neighbours above and four below shrinking and fading
 * away from it, the whole thing gone the moment the finger lifts. It is the same ring the toolbar
 * cell draws three rows of — this is the rest of it, shown while the reader is actually choosing.
 *
 * Measured off the screenshot, 3×: a 197 pt card with 16 pt corners, the middle row at 32 pt with
 * a 32 pt logo, then 26, 20, 15 and 11 pt outward, the rows' ink fading with the size. The card is
 * white at about 92 % over the bars; no blur, which the house rules do not allow and which the
 * eye does not miss under a card this opaque.
 *
 * ### Why it is not the wheel
 *
 * The wheel is the control — it takes the drag and steps the symbol. This is a *picture* of where
 * the wheel is, and it is deliberately inert: it takes no touch, so a finger that wanders onto it
 * mid-drag keeps turning the wheel underneath. It reads [current] straight from the chart, so it
 * can never show a symbol the chart is not on.
 */
@Composable
internal fun SymbolWheelOverlay(
    symbols: List<String>,
    current: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    /**
     * How far the wheel has travelled since its last step. The card slides by the same amount.
     *
     * A lambda rather than a float, and read inside `graphicsLayer`: the value changes every frame
     * of a drag, and taking it as a parameter would recompose the chart page — the picker's own
     * parent — sixty times a second to move a card twelve points.
     */
    travelPx: () -> Float = { 0f },
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Two rows further out than the card shows, for the reason the toolbar cell draws five: a
        // card that slides needs a row waiting outside each edge, or the slide reveals the card's
        // own padding.
        val rows = remember(symbols, current) {
            (-OVERLAY_DRAWN..OVERLAY_DRAWN).map { step ->
                if (step == 0) current else symbolStep(symbols, current, step)
            }
        }
        LtrDirection {
            Column(
                modifier = Modifier
                    // **Wide enough for the ticker, rather than a number that happened to fit.**
                    //
                    // «کلمه T می‌افتد زیر چهارچوب.» The card was a fixed 200 points and the middle
                    // row is a 32 pt logo, a gap and a 32 sp bold ticker — which for `ADAUSDT` comes
                    // to a little over that, so the last letter was cut off by the card's own edge.
                    // A width that is measured off one screenshot is a width that is wrong for the
                    // next symbol; the card now takes the width of its widest row and keeps 200 as
                    // a floor so a short ticker does not shrink it into a chip.
                    .widthIn(min = OVERLAY_MIN_WIDTH)
                    .height(OVERLAY_HEIGHT)
                    .clip(CoineProShapes.large)
                    .background(CoineProColors.Stage.copy(alpha = OVERLAY_ALPHA))
                    .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.large)
                    .padding(vertical = CoineProSpacing.One, horizontal = CoineProSpacing.OneHalf),
                verticalArrangement = Arrangement.Center,
            ) {
                Column(modifier = Modifier.graphicsLayer { translationY = travelPx() }) {
                    rows.forEachIndexed { index, symbol ->
                        OverlayRow(symbol = symbol, distance = abs(index - OVERLAY_DRAWN))
                    }
                }
            }
        }
    }
}

/**
 * One row of the picker, sized by how far it is from the middle.
 *
 * An absent neighbour — a ring shorter than nine — still takes its row, for the reason the toolbar
 * cell keeps an empty row: the middle must not move.
 */
@Composable
private fun OverlayRow(symbol: String?, distance: Int) {
    val rung = OVERLAY_RUNGS[distance.coerceIn(0, OVERLAY_RUNGS.lastIndex)]
    Row(
        // No `fillMaxWidth`: the card is as wide as its widest row, and a row that filled the
        // width would make that measurement the whole screen.
        modifier = Modifier
            .height(rung.row)
            .alpha(rung.alpha),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol == null) return@Row
        CoineProAssetLogo(symbol = symbol, size = rung.logo)
        Text(
            text = BidiText.isolateLtr(symbol),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = rung.text),
            fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.SemiBold,
            color = if (distance == 0) CoineProColors.TextPrimary else CoineProColors.TextMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** One rung of the picker: the row's height, its logo, its type size and how faint it is. */
private class OverlayRung(val row: Dp, val logo: Dp, val text: TextUnit, val alpha: Float)

/**
 * Middle outward: the current row bold at 32 with its logo, the neighbours at sixty per cent —
 * the design brief's picker, five rows visible. The two rungs past those exist only for the
 * slide (see [OVERLAY_DRAWN]) and are drawn at the outer size, faint.
 */
private val OVERLAY_RUNGS = listOf(
    OverlayRung(row = 36.dp, logo = 32.dp, text = 32.sp, alpha = 1f),
    OverlayRung(row = 30.dp, logo = 26.dp, text = 26.sp, alpha = NEIGHBOUR_ALPHA),
    OverlayRung(row = 24.dp, logo = 20.dp, text = 20.sp, alpha = NEIGHBOUR_ALPHA),
    OverlayRung(row = 18.dp, logo = 14.dp, text = 15.sp, alpha = 0.25f),
)

/** The neighbours' ink, as the design brief measures the reference: sixty per cent. */
private const val NEIGHBOUR_ALPHA = 0.6f

/** How many neighbours the picker shows on each side of the current symbol: five rows in all. */
private const val OVERLAY_REACH = 2

/** How many it *draws*: two more, waiting outside the card's edges for the slide to reveal. */
private const val OVERLAY_DRAWN = OVERLAY_REACH + 1

/**
 * The card's height: the five rows it shows (36 + 2 × 30 + 2 × 24) plus its own padding.
 *
 * Fixed rather than wrapped, because the column inside it is two rows taller than that and slides.
 * A card that sized itself to what it contains would grow by the rows that are meant to be hidden.
 */
private val OVERLAY_HEIGHT = 160.dp

/**
 * The floor under the card's width.
 *
 * 197 pt on the phone app, which is what a five-letter ticker needs. The card takes the width of
 * its widest row above that — see [SymbolWheelOverlay].
 */
private val OVERLAY_MIN_WIDTH = 200.dp
private const val OVERLAY_ALPHA = 0.92f

/**
 * One row of the toolbar's wheel: eighteen points, the pitch measured between the phone app's
 * current ticker and the faint one under it. Three of them overflow the 44 pt bar on purpose.
 */
private val WHEEL_SCROLL_ROW = 18.dp

/** The touch target is the toolbar's own height; the pill inside it is shorter. */
private val WHEEL_SCROLL_HEIGHT = 44.dp

/** The pill: two rows of the wheel, so the neighbours show as halves fading into the surface. */
private val WHEEL_PILL_HEIGHT = 36.dp
private val WHEEL_PILL_SHAPE = RoundedCornerShape(10.dp)
private val WHEEL_PILL_INSET = 10.dp

/** The neighbours' ink, by distance from the centre row. */
private const val WHEEL_NEAR_ALPHA = 0.55f
private const val WHEEL_FAR_ALPHA = 0.25f

/** The far rows, a touch smaller: the wheel is round and its far rows are further from the eye. */
private const val WHEEL_SIDE_SCALE = 0.9f

/** The carets that say the cell turns. */
private val WHEEL_SCROLL_CARET = 10.dp
private val WHEEL_CARET_GAP = 1.dp

/** Eighty points, which is where the phone app cuts `IMXUSDT` to `IMXUSD` before the interval. */
private val WHEEL_SCROLL_WIDTH = 80.dp

