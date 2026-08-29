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
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CoineProColors.Surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        CoineProSheetBody(title = title, subtitle = subtitle, content = content)
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
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Surface)) {
        SheetHandle()
        Column(
            modifier = Modifier.padding(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                bottom = CoineProSpacing.OneHalf,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
        content()
    }
}

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
                Chip(
                    label = allLabel,
                    count = null,
                    selected = selectedId == null,
                    compact = compact,
                ) { onSelect(null) }
            }
        }
        items(options, key = { it.id }) { option ->
            Chip(
                label = option.label,
                count = option.count,
                selected = option.id == selectedId,
                compact = compact,
            ) { onSelect(option.id) }
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
private fun Chip(
    label: String,
    count: Int?,
    selected: Boolean,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    // Animated rather than swapped. A chip row is a set of exclusive states, and a fill that
    // crosses over its neighbour's in 160ms is what tells the reader the selection *moved* instead
    // of two unrelated chips independently changing colour.
    val fill by animateColorAsState(
        targetValue = if (selected) CoineProColors.pageAccent else CoineProColors.SurfaceElevated,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "chipFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) CoineProColors.onPageAccent else CoineProColors.TextSecondary,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "chipInk",
    )
    Row(
        modifier = Modifier
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
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CoineProColors.SurfaceElevated)
            // The same hairline every other control gained. A filled field inside a sheet whose
            // own surface is one step below it is otherwise a slightly different grey, and a
            // reader has to guess that it is a field at all.
            .border(1.dp, CoineProColors.BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Icon(
            painter = painterResource(R.drawable.tv_search),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = CoineProColors.TextMuted,
        )
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = CoineProColors.TextPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(CoineProColors.Gold),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
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

