package com.coinepro.core.designsystem

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import kotlin.math.roundToInt

/**
 * The app's bottom sheet.
 *
 * One chrome for every sheet in the product, because the alternative is what the chart pickers had
 * before this: three surfaces that each invented their own header, their own padding and their own
 * way of being dismissed. A reader learns a sheet once.
 *
 * The grab handle is drawn here rather than taken from Material's default, which is a thin grey bar
 * that all but disappears on this near-black stage. Four density-independent pixels of the strong
 * border colour is the smallest thing that still reads as "drag me".
 */
/**
 * How many sheets and sheet-dialogs are open right now. The coach-mark host reads it so a mark
 * never floats over a sheet (DIALOGS-28); nothing else should need it.
 */
object CoineProSheetPresence {
    var open by mutableIntStateOf(0)
        internal set
}

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
     *
     * As a dialog the same request is read one step lighter — see [dialogScrimAlpha].
     */
    scrimAlpha: Float = SHEET_SCRIM_ALPHA,
    /**
     * The widest the sheet gets when it opens as a dialog. [SHEET_DIALOG_MAX_WIDTH] suits a list of
     * options; a picker with a category column beside its list (indicators, symbol search) asks for
     * more, the way TradingView's own 840 px indicators dialog does.
     */
    dialogMaxWidth: Dp = SHEET_DIALOG_MAX_WIDTH,
    /** See [CoineProSheetBody]'s. Null keeps the body's own layout. */
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    DisposableEffect(Unit) {
        CoineProSheetPresence.open++
        onDispose { CoineProSheetPresence.open-- }
    }
    // On an expanded window a bottom sheet is a strip across a twelve-inch glass — the wrong
    // shape and, at full width, a wall of controls the reader has to walk. The same body opens as
    // a dialog capped at [dialogMaxWidth] instead: the reader's eye, not the glass, decides how
    // wide a list of options is. Nothing about the content changes; the phone keeps its sheet.
    if (coineProWindowClass().showsTwoPanes) {
        SheetDialog(onDismiss = onDismiss, scrimAlpha = dialogScrimAlpha(scrimAlpha)) {
            Surface(
                modifier = modifier
                    .widthIn(max = dialogMaxWidth)
                    // Wraps its content up to nine tenths of the window, rather than always being
                    // nine tenths: a one-row settings panel in a 778 px box was mostly box.
                    .sheetDialogHeight()
                    .padding(CoineProSpacing.Two),
                shape = CoineProShapes.large,
                color = CoineProColors.Surface,
                border = BorderStroke(1.dp, CoineProColors.Border),
                // With the page barely dimmed, the shadow is what lifts the dialog off the chart.
                shadowElevation = SHEET_DIALOG_SHADOW,
            ) {
                // Deliberately **not** scrolling here (run Σ, S8). A container that scrolls measures
                // its child with an unbounded height, and a body that scrolls itself — the paste
                // panel, the alert editor, the screener's filters, half a dozen others — then throws
                // rather than drawing: «Vertically scrollable component was measured with an
                // infinity maximum height». The height cap above is a *bounded* maximum, so a body
                // that scrolls itself still has a floor to scroll against.
                Column {
                    SheetBody(
                        title = title,
                        subtitle = subtitle,
                        onClose = onDismiss,
                        form = SheetForm.Dialog,
                        contentPadding = contentPadding,
                        content = content,
                    )
                }
            }
        }
        return
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Sixteen, not Material's twenty-eight: the reference's phone sheets, and a full-height
        // sheet over a chart should not leave a rounded sliver of candles at each corner.
        shape = CoineProSheetDefaults.Shape,
        containerColor = CoineProColors.Surface,
        // Forty per cent, not Material's thirty-two: the chart stays legible behind a sheet, and
        // the reference app's sheets are measured at this depth.
        scrimColor = Color.Black.copy(alpha = scrimAlpha),
        dragHandle = null,
        modifier = modifier,
    ) {
        SheetBody(
            title = title,
            subtitle = subtitle,
            onClose = onDismiss,
            form = SheetForm.Sheet,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

/** The widest a sheet-as-dialog gets on a tablet: the plan's number, and about sixty characters of Persian. */
val SHEET_DIALOG_MAX_WIDTH = 560.dp
private const val SHEET_DIALOG_MAX_HEIGHT_FRACTION = 0.9f
private val SHEET_DIALOG_SHADOW = 12.dp

/**
 * The page's dimming behind a sheet opened as a dialog.
 *
 * One step lighter than the phone's: TradingView dims nothing behind its desktop dialogs, and a
 * dialog does not cover the page from an edge the way a sheet does, so it needs less help to read
 * as in front. The default forty per cent becomes twenty; a live-preview sheet's twenty becomes
 * none at all, so a drawing's style is edited over the drawing itself.
 */
internal fun dialogScrimAlpha(sheetAlpha: Float): Float =
    (sheetAlpha - SHEET_PREVIEW_SCRIM_ALPHA).coerceIn(0f, 1f)

/**
 * Wrap the content's height, up to [SHEET_DIALOG_MAX_HEIGHT_FRACTION] of the window.
 *
 * Not `heightIn(max = …)` with a number: the cap is a fraction of whatever window the dialog is in,
 * and the window is only known at measure time. The child is always handed a bounded maximum, which
 * is what keeps a body that scrolls itself from being measured against infinity.
 */
private fun Modifier.sheetDialogHeight(): Modifier = layout { measurable, constraints ->
    val cap = if (constraints.hasBoundedHeight) {
        (constraints.maxHeight * SHEET_DIALOG_MAX_HEIGHT_FRACTION).roundToInt()
    } else {
        constraints.maxHeight
    }
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = cap))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** The shapes and paddings every sheet shares, for a body that wants to match them. */
object CoineProSheetDefaults {
    /**
     * The inset a sheet body's content takes: the title's own gutter on both sides and a step of
     * room under the last row. Pass it as `contentPadding` to a sheet whose body does not pad
     * itself; a list that pads its own rows keeps null and stays full-bleed.
     */
    val ContentPadding: PaddingValues = PaddingValues(
        start = CoineProSpacing.Gutter,
        end = CoineProSpacing.Gutter,
        bottom = CoineProSpacing.Two,
    )

    /** A phone sheet's top corners — see `CoineProShapes.extraLarge`. */
    val Shape: Shape = RoundedCornerShape(
        topStart = CoineProTokens.Radius.sheet,
        topEnd = CoineProTokens.Radius.sheet,
    )
}

/** Which chrome a sheet body draws: a phone sheet's, a desktop dialog's, or neither's handle. */
private enum class SheetForm { Sheet, Dialog, Inline }

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
    /**
     * Whether the grab handle is drawn. Only a sheet that can be dragged should promise it can: a
     * panel laid inline in a page, or a body shown in a dialog, passes false.
     */
    showHandle: Boolean = true,
    /**
     * An inset around [content], opt-in. Null (the default) hands the body the sheet's full width,
     * as it always has — a list that pads its own rows wants that. A body of loose controls passes
     * [CoineProSheetDefaults.ContentPadding], which is what keeps its first letter off the glass's
     * edge.
     */
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SheetBody(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        onClose = onClose,
        form = if (showHandle) SheetForm.Sheet else SheetForm.Inline,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
private fun SheetBody(
    title: String,
    form: SheetForm,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    contentPadding: PaddingValues? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dialog = form == SheetForm.Dialog
    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Surface)) {
        if (form == SheetForm.Sheet) SheetHandle()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CoineProSpacing.Gutter,
                    end = if (dialog) CoineProSpacing.OneHalf else CoineProSpacing.Gutter,
                    // A dialog has no handle above its title, so the title takes TradingView's
                    // twenty points of top room itself: a 60 px header instead of 88.
                    top = when (form) {
                        SheetForm.Dialog -> SHEET_DIALOG_TOP
                        SheetForm.Inline -> CoineProSpacing.OneHalf
                        SheetForm.Sheet -> 0.dp
                    },
                    bottom = CoineProSpacing.OneHalf,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    // TradingView's sheet title is its largest text — 24 px bold on a phone, 20 px
                    // in a desktop dialog. It was `titleMedium` here, one step above the rows
                    // under it, and the sheet read as a list with a caption rather than as a page
                    // with a name.
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
                if (dialog) DialogClose(close) else SheetClose(close)
            }
        }
        if (contentPadding == null) {
            content()
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding), content = content)
        }
    }
}

/** A phone sheet's close: a filled disc, which a thumb finds without aiming. */
@Composable
private fun SheetClose(close: () -> Unit) {
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

/**
 * A dialog's close: a bare cross whose plate appears only under the pointer — TradingView's. A
 * filled disc on a desktop dialog is the heaviest thing in its header and says nothing a cross
 * does not.
 */
@Composable
private fun DialogClose(close: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val plate by animateColorAsState(
        targetValue = if (hovered) CoineProColors.SurfaceHover else Color.Transparent,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "dialogClosePlate",
    )
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(SHEET_CLOSE)
            .clip(CoineProShapes.small)
            .background(plate)
            .hoverable(interaction)
            .clickable(interaction, null, onClick = close),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.icon_x),
            contentDescription = stringResource(R.string.sheet_close),
            tint = if (hovered) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Thirty-two, the design brief's measure of the reference's disc; the tap target stays 48. */
private val SHEET_CLOSE = 32.dp

private const val SHEET_HANDLE_ALPHA = 0.6f

/** A dialog title's top room: TradingView's 20 px inset. */
private val SHEET_DIALOG_TOP = 20.dp

@Composable
private fun SheetHandle() {
    // Eight above and below, not twelve: the handle is a hint, and at twelve it spent 28 dp of every
    // phone sheet before the title. A mid-grey rather than the border colour, which on the dark
    // sheet measured 1.3:1 and was not there at all; this is about 2.2:1 in both themes.
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(CoineProColors.TextDisabled.copy(alpha = SHEET_HANDLE_ALPHA)),
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
    val state = rememberLazyListState()
    CoineProLazyRow(
        modifier = modifier.fillMaxWidth().scrollEdgeFade(state),
        state = state,
        verticalAlignment = Alignment.CenterVertically,
        // Eight at both sizes: pills with an edge of their own need air between them, or a row of
        // them reads as one segmented bar.
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
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

/**
 * Fade whichever end of a chip row still has chips beyond it (DIALOGS-14).
 *
 * A row that ran out of the sheet ended in a chip cut in half against the edge, which reads as a
 * mistake rather than as «there is more». Twenty-four points of fade at the end that scrolls says
 * the second thing, and costs nothing when the whole row fits.
 */
private fun Modifier.scrollEdgeFade(state: LazyListState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = CHIP_ROW_FADE.toPx().coerceAtMost(size.width / 2f)
        val rtl = layoutDirection == LayoutDirection.Rtl
        // «Forward» is the reading end: the left edge in Persian.
        val fadeLeft = if (rtl) state.canScrollForward else state.canScrollBackward
        val fadeRight = if (rtl) state.canScrollBackward else state.canScrollForward
        // A mask on the row's own pixels, stepped rather than a gradient brush: the design system
        // keeps gradients to the brand mark and the chart (check-motion-policy.sh), and eight
        // bands over twenty-four points are indistinguishable from a ramp at this size.
        val band = fade / CHIP_ROW_FADE_STEPS
        for (step in 0 until CHIP_ROW_FADE_STEPS) {
            // Band 0 is at the very edge and keeps the least of the row.
            val keep = Color.Black.copy(alpha = (step + 0.5f) / CHIP_ROW_FADE_STEPS)
            if (fadeLeft) {
                drawRect(keep, topLeft = Offset(step * band, 0f), size = Size(band, size.height), blendMode = BlendMode.DstIn)
            }
            if (fadeRight) {
                drawRect(
                    keep,
                    topLeft = Offset(size.width - (step + 1) * band, 0f),
                    size = Size(band, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }

private val CHIP_ROW_FADE = 24.dp
private const val CHIP_ROW_FADE_STEPS = 8

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
 *
 * Since the pastel pass the solid fill is gone too: a selected chip is a wash of the accent with
 * the accent's ink and hairline ([chipLook]), 32 dp compact and 36 dp regular
 * ([CoineProChipDefaults]), so no chip competes with the screen's one filled action.
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
     * A hue for a chip that means something other than "selected" — a side, an outcome. Drawn as
     * the chip family draws the accent: a soft wash of it, its edge, and its own hue as the label
     * (pulled toward black in the light theme) — see [chipLook].
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
    val look = chipLook(selected = selected, neutral = neutral, fill = fill)
    // Animated rather than swapped. A chip row is a set of exclusive states, and a tint that
    // crosses over its neighbour's in 160ms is what tells the reader the selection *moved* instead
    // of two unrelated chips independently changing colour.
    val plate by animateColorAsState(look.plate, CoineProMotionSpecs.standard(), label = "chipFill")
    val ink by animateColorAsState(look.ink, CoineProMotionSpecs.standard(), label = "chipInk")
    val edge by animateColorAsState(look.edge, CoineProMotionSpecs.standard(), label = "chipEdge")
    Row(
        modifier = modifier
            // A chip is a control and a control is reachable with a thumb. Five screens had
            // hand-rolled their own at four points of vertical padding, which draws about
            // twenty-three — half a target — and this is the row a reader taps most in the app.
            .minimumInteractiveComponentSize()
            .pressScale(interaction, CoineProPress.CHIP)
            .heightIn(min = if (compact) CoineProChipDefaults.CompactHeight else CoineProChipDefaults.Height)
            .clip(CoineProPillShape)
            .background(plate)
            .border(1.dp, edge, CoineProPillShape)
            .clickable(interaction, null) {
                if (!selected) haptics.select()
                onClick()
            }
            .padding(horizontal = if (compact) CoineProSpacing.OneHalf else CoineProSpacing.Two),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            color = ink,
            // SemiBold, not Bold: on a pastel tint the ink already says «chosen», and Bold in
            // IRANYekanX set the chosen chip a size larger than its neighbours.
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        if (count != null) {
            Text(
                // A prose count, so Persian digits — unlike a price, which stays Latin. The chip
                // said «9» beside a subtitle that said «۵۲» until this line existed.
                text = count.proseDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) ink.copy(alpha = 0.7f) else CoineProColors.TextMuted,
            )
        }
    }
}

/**
 * The sizes every chip, pill and toggle in the app shares.
 *
 * TradingView's filter pills measure 34 px; its dialog type pills 28. Thirty-two for a row that is
 * chrome and thirty-six for one that is content sit either side of that and keep one rhythm.
 */
object CoineProChipDefaults {
    val CompactHeight: Dp = 32.dp
    val Height: Dp = 36.dp
}

/** What a chip, a segment or a tab looks like in one state: its plate, its label and its edge. */
@Immutable
internal data class ChipLook(val plate: Color, val ink: Color, val edge: Color)

/**
 * The one pastel family every selectable pill in the app draws from (run «پاستیلی»).
 *
 * ### Selected is a tint, not a fill
 *
 * A selected chip used to be a solid slab of the page accent with near-black letters — the loudest
 * object in any row it sat in, and indistinguishable at a glance from the primary button two
 * inches away. It is now a soft wash of the accent (sixteen per cent), lettered in the accent's own
 * *ink* and closed with a hairline of it: the choice reads as chosen, and the only solid gold on a
 * screen is still the one action worth pressing. The ink is the accent's ink tone, not its fill
 * tone, because in the light theme the fill gold measures 2.1:1 on white — the wash keeps the ink at
 * 4.6:1 there and 6:1 on the dark card.
 *
 * ### Unselected is a quiet plate
 *
 * One rung up from the ground with the faintest hairline, so a row of options is a row of shapes
 * without any of them asking for the eye.
 *
 * ### Neutral
 *
 * A terminal filter selects with a raised neutral and a visible edge instead of the accent — see
 * [CoineProToggleChip]'s `neutral` — the same shape, the same edge, no colour.
 */
@Composable
@ReadOnlyComposable
internal fun chipLook(selected: Boolean, neutral: Boolean = false, fill: Color? = null): ChipLook {
    val palette = LocalCoineProPalette.current
    return when {
        !selected -> ChipLook(
            plate = palette.surfaceElevated,
            ink = palette.textSecondary,
            edge = palette.borderSubtle,
        )
        neutral -> ChipLook(
            plate = palette.surfaceRaised,
            ink = palette.textPrimary,
            edge = palette.borderStrong,
        )
        else -> {
            val tone = fill ?: CoineProColors.pageAccent
            // A caller's own fill (buy, sell, a brand gold) is a fill tone; as ink on white it is
            // pulled toward black the way the palette's own ink gold is.
            val ink = when {
                fill == null -> CoineProColors.pageAccentInk
                palette.isDark -> fill
                else -> lerp(fill, Color.Black, CHIP_LIGHT_INK_SHIFT)
            }
            ChipLook(
                plate = tone.copy(alpha = CHIP_TINT_ALPHA),
                ink = ink,
                edge = tone.copy(alpha = CHIP_EDGE_ALPHA),
            )
        }
    }
}

private const val CHIP_TINT_ALPHA = 0.16f
private const val CHIP_EDGE_ALPHA = 0.45f
private const val CHIP_LIGHT_INK_SHIFT = 0.35f

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
    //
    // Its desktop dialogs are the other way round (DIALOGS-20): a transparent 40 px field with a
    // one-pixel edge that takes the accent while it has focus. A pointer finds an outline; a thumb
    // finds a plate.
    val fieldInteraction = remember { MutableInteractionSource() }
    val focused by fieldInteraction.collectIsFocusedAsState()
    val outlined = coineProWindowClass().showsTwoPanes
    val edge by animateColorAsState(
        targetValue = if (focused) CoineProColors.pageAccent else CoineProColors.Border,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "sheetSearchEdge",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SHEET_SEARCH_HEIGHT)
            .then(
                if (outlined) {
                    Modifier
                        .clip(CoineProShapes.small)
                        .border(1.dp, edge, CoineProShapes.small)
                } else {
                    Modifier
                        .clip(CoineProShapes.medium)
                        .background(CoineProColors.SurfaceElevated)
                },
            )
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
            interactionSource = fieldInteraction,
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

/** The sheet search field's height: 40, the reference's on both its phone sheets and its dialogs. */
private val SHEET_SEARCH_HEIGHT = 40.dp

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

/**
 * The widest a single column of content gets before it stops being readable (run Σ, S8).
 *
 * A list of cards that fills a 1973 dp panel puts a row's label at one edge and its figure at the
 * other, half a metre of glass apart, and turns a button into a pill the width of the device. The
 * dashboard screens cap at this and centre what is left; the chart, which *wants* every pixel, does
 * not. Seven hundred and twenty because it is about twice the sheet's cap and still one eyeful.
 *
 * On a phone it costs nothing: 411 dp is already narrower.
 */
val CONTENT_MAX_WIDTH = 720.dp

/**
 * The same cap on a window wide enough for **two** columns of content (run Σ-FIX 7).
 *
 * 720 is one readable column, and on a 1973 dp panel a single 720 dp column with two empty thirds
 * beside it is not restraint, it is a phone screen in the middle of a tablet — which is what the
 * owner's review of 4.85.0 said about Home, and they were right. A dashboard on that much glass
 * puts two columns side by side and caps the pair.
 *
 * 1160 because it is two 560 dp columns and a gutter: 560 is the sheet's own cap, which is the
 * width this app already decided a column of controls reads well at.
 */
val CONTENT_MAX_WIDTH_WIDE = 1160.dp
