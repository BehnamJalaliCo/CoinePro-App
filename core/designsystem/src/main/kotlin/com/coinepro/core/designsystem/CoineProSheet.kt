package com.coinepro.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.toPersianDigits

/**
 * The app's bottom sheet.
 *
 * One chrome for every sheet in the product, because the alternative is what the chart pickers had
 * before this: three surfaces that each invented their own header, their own padding and their own
 * way of being dismissed. A reader learns a sheet once.
 *
 * The grab handle is drawn here rather than taken from Material's default, which is a thin grey bar
 * that all but disappears on this near-black stage. Four density-independent pixels of the border
 * colour is the smallest thing that still reads as "drag me".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoineProSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    /**
     * How dark the page behind the sheet goes. Forty per cent by default; a sheet whose controls
     * change the picture behind it live — a drawing's style, an indicator's inputs — asks for
     * [SHEET_PREVIEW_SCRIM_ALPHA] so the reader can see what they are changing.
     */
    scrimAlpha: Float = SHEET_SCRIM_ALPHA,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CoineProColors.Surface,
        // Forty per cent, not Material's thirty-two: the chart stays legible behind a sheet, and
        // the reference app's sheets are measured at this depth.
        scrimColor = Color.Black.copy(alpha = scrimAlpha),
        dragHandle = null,
        modifier = modifier,
    ) {
        CoineProSheetBody(title = title, subtitle = subtitle, onClose = onDismiss, content = content)
    }
}

/**
 * The sheet's chrome without the sheet.
 *
 * Two callers, and both matter. A screen can embed the same panel inline — a tablet layout will —
 * and the screenshot tests can render it, which a [ModalBottomSheet] defeats: it draws into its own
 * window, so an off-device capture of the activity's decor view comes back empty. Splitting the
 * chrome out is what lets every sheet in this app be looked at before it ships.
 */
@Composable
fun CoineProSheetBody(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /**
     * The round close button at the title's far end — TradingView's sheets all carry one, a 32 dp
     * disc on the elevated rung with a cross in it, and a sheet that can only be dismissed by
     * dragging is a sheet a reader has to know something about. Null draws none (an inline panel).
     */
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Surface)) {
        SheetHandle()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CoineProSpacing.Gutter,
                    end = CoineProSpacing.Gutter,
                    bottom = CoineProSpacing.OneHalf,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    // TradingView's sheet title is its largest text — 24 px bold on a phone. It
                    // was `titleMedium` here, one step above the rows under it, and the sheet
                    // read as a list with a caption rather than as a page with a name.
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = CoineProColors.TextPrimary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
            onClose?.let { close ->
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(SHEET_CLOSE)
                        .clip(CircleShape)
                        .background(CoineProColors.SurfaceElevated)
                        .clickable(onClick = close),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.icon_x),
                        contentDescription = stringResource(R.string.sheet_close),
                        tint = CoineProColors.TextPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        content()
    }
}

/** Thirty-two, the design brief's measure of the reference's disc; the tap target stays 48. */
private val SHEET_CLOSE = 32.dp

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.OneHalf),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(CoineProColors.Border),
        )
    }
}

/**
 * A row of filter chips.
 *
 * Horizontal and scrolling rather than an accordion, which is the other obvious way to present
 * eleven groups of tools. An accordion hides ten of eleven group names behind a tap and makes
 * finding a tool a two-step search — open the right drawer, then look inside it. A chip row keeps
 * every group name visible, costs one tap, and never leaves the reader wondering what is collapsed.
 */
@Composable
fun CoineProChipRow(
    options: List<CoineProChip>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** The chip that clears the filter. Null omits it. */
    allLabel: String? = null,
    /**
     * A tighter chip, for a row that is chrome rather than content.
     *
     * The chart's timeframe strip is the case: eight chips at the sheet's size filled a third of
     * the screen above the plot and read as a headline rather than as a control. Same shape, less
     * of it.
     */
    compact: Boolean = false,
    /** Neutral selection instead of the page accent — see [CoineProToggleChip]. */
    neutral: Boolean = false,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            if (compact) CoineProSpacing.Half else CoineProSpacing.One,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (compact) CoineProSpacing.One else CoineProSpacing.Gutter,
        ),
    ) {
        if (allLabel != null) {
            item(key = "__all") {
                CoineProToggleChip(
                    label = allLabel,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                    compact = compact,
                    neutral = neutral,
                )
            }
        }
        items(options, key = { it.id }) { option ->
            CoineProToggleChip(
                label = option.label,
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                count = option.count,
                compact = compact,
                neutral = neutral,
            )
        }
    }
}

/** One chip: an id, what it says, and optionally how many things are behind it. */
data class CoineProChip(val id: String, val label: String, val count: Int? = null)

/**
 * One chip.
 *
 * ### Two things were wrong with it
 *
 * A selected chip filled with `CoineProColors.Accent` and lettered in `OnAccent`. Both of those are
 * theme-dependent and they move in the *same* direction: in the light theme the accent darkens to
 * `#8A6318` so it can be read as ink, and `OnAccent` is near-black in both themes — so the light
 * theme's selected chip was near-black text on dark brown, about 2.6:1, which is a chip whose label
 * cannot be read. The fill/ink split exists exactly to prevent this and the chip was on the wrong
 * side of it: a *fill* takes [CoineProColors.pageAccent], never the ink gold. Following the page
 * accent also means a filter on an analysis screen selects in blue rather than putting a second
 * gold object next to the screen's one gold action.
 *
 * And it did not move. A chip is the most-pressed control in this app — every timeframe, every
 * filter, every symbol — and it was the one with no press state, no haptic and no transition
 * between selected and not. That is most of what "nothing responds" means.
 */
@Composable
fun CoineProToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    compact: Boolean = false,
    /**
     * A fill for a chip that means something other than "selected" — a side, an outcome.
     *
     * Null takes the page accent, which is what a filter should do. The journal's buy/sell pair is
     * the case for passing one: green and red there are the *content* of the choice, not a
     * selection colour, and replacing them with the page accent would lose the only thing that
     * tells the two chips apart at a glance.
     *
     * It is a **fill**, so pass a fill colour. `CoineProColors.Accent` is the ink gold and is a
     * dark brown in the light theme; [CoineProColors.AccentFill] is its fill twin.
     */
    fill: Color? = null,
    /**
     * A selection marked by a raised neutral rather than by the page accent.
     *
     * For a **terminal filter**: which watchlist, which lens, which category. Those are views over
     * a list, not commercial actions, and on a page whose accent is the brand they were coming out
     * gold — so a screen of forty prices had a gold object on it that meant "this filter", and the
     * gold that means "this is the one thing here worth pressing" had to compete with it. The
     * raised neutral is what this app already uses for "one of these is in force" everywhere the
     * choice is a view: the chart's interval keys, the Ideas switch, the bottom bar's own plate.
     *
     * The accent stays the default, because most chip rows in this app are not filters.
     */
    neutral: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    // Animated rather than swapped. A chip row is a set of exclusive states, and a fill that
    // crosses over its neighbour's in 160ms is what tells the reader the selection *moved* instead
    // of two unrelated chips independently changing colour.
    val fill by animateColorAsState(
        targetValue = when {
            !selected -> CoineProColors.SurfaceElevated
            neutral -> CoineProColors.SurfaceRaised
            else -> fill ?: CoineProColors.pageAccent
        },
        animationSpec = CoineProMotionSpecs.standard(),
        label = "chipFill",
    )
    val ink by animateColorAsState(
        targetValue = when {
            !selected -> CoineProColors.TextSecondary
            // A raised neutral is a *surface*, so the label on it is the page's own primary ink.
            neutral -> CoineProColors.TextPrimary
            // Every fill this chip accepts is a mid-tone or darker in both themes — the page
            // accents, the brand gold, buy and sell — so the label that reads on all of them is
            // the one the gold already needs. White would fail on gold in either theme.
            fill != null -> CoineProColors.OnAccent
            else -> CoineProColors.onPageAccent
        },
        animationSpec = CoineProMotionSpecs.standard(),
        label = "chipInk",
    )
    Row(
        modifier = modifier
            // A chip is a control and a control is reachable with a thumb. Five screens had
            // hand-rolled their own at four points of vertical padding, which draws about
            // twenty-three — half a target — and this is the row a reader taps most in the app.
            .minimumInteractiveComponentSize()
            .pressScale(interaction, CoineProPress.CHIP)
            .clip(CoineProPillShape)
            .background(fill)
            // The hairline is only on the unselected chip: a filled chip already has an edge, and
            // an outline over a fill reads as a chip that is both selected and not.
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(1.dp, CoineProColors.BorderSubtle, CoineProPillShape)
                },
            )
            .clickable(interaction, null) {
                if (!selected) haptics.select()
                onClick()
            }
            .padding(
                horizontal = if (compact) CoineProSpacing.One else CoineProSpacing.OneHalf,
                vertical = if (compact) CoineProSpacing.Half else CoineProSpacing.One,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
            color = ink,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (count != null) {
            Text(
                // A prose count, so Persian digits — unlike a price, which stays Latin. The chip
                // said «9» beside a subtitle that said «۵۲» until this line existed.
                text = count.toPersianDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) ink.copy(alpha = 0.7f) else CoineProColors.TextMuted,
            )
        }
    }
}

/**
 * A compact search field for inside a sheet.
 *
 * Not [CoineProTextField]: that one is an outlined field with a floating label, sized for a form.
 * Inside a sheet the field is a filter rather than a question, and it needs to be short enough that
 * the list below it is still the thing the eye lands on.
 */
@Composable
fun CoineProSheetSearch(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /**
     * Whether the keyboard comes up with the sheet. The reference's indicator sheet does this —
     * eighty rows is a list nobody scrolls — and the tool sheet does not, because a reader
     * opening it is about to tap a tile. Off by default.
     */
    autoFocus: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
    // TradingView's phone sheets, measured: a 40 pt field on a grey plate with 10 pt corners and
    // no edge — the plate is the field. The hairline it used to carry read as a second, different
    // control beside the tiles under it.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SHEET_SEARCH_HEIGHT)
            .clip(CoineProShapes.medium)
            .background(CoineProColors.SurfaceElevated)
            .padding(horizontal = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Icon(
            painter = painterResource(R.drawable.tv_search),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = CoineProColors.TextMuted,
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = CoineProColors.TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(CoineProColors.Gold),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CoineProColors.TextMuted,
                    )
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            val clearInteraction = remember { MutableInteractionSource() }
            Icon(
                painter = painterResource(R.drawable.icon_x),
                contentDescription = stringResource(R.string.field_clear),
                modifier = Modifier
                    // Sixteen points of glyph was also sixteen points of *target*, which is a
                    // third of the minimum and sits inside a field a thumb is already near. It is
                    // drawn at sixteen and touchable at forty-eight, like every other small
                    // control in the app.
                    .minimumInteractiveComponentSize()
                    .pressScale(clearInteraction, CoineProPress.CONTROL)
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(clearInteraction, null) { onValueChange("") },
                tint = CoineProColors.TextMuted,
            )
        }
    }
}

/** The sheet search field's height: 44, the design brief's measure; 12 dp corners below. */
private val SHEET_SEARCH_HEIGHT = 44.dp

/** Shown where a filter matched nothing, in place of a blank sheet. */
@Composable
fun CoineProSheetEmpty(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(CoineProSpacing.Four),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextMuted,
        )
    }
}

/** Transparent, for a chip that is not selected. Named so the intent is not read as a mistake. */
internal val UnselectedChip: Color = Color.Transparent

/** How much of the chart a sheet hides. See `CoineProSheet`. */
internal const val SHEET_SCRIM_ALPHA = 0.4f

/** The scrim behind a sheet that previews its changes on the chart: twenty per cent. */
const val SHEET_PREVIEW_SCRIM_ALPHA = 0.2f
