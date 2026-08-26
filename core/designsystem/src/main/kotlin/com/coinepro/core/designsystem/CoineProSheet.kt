package com.coinepro.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = CoineProSpacing.Gutter,
        ),
    ) {
        if (allLabel != null) {
            item(key = "__all") {
                Chip(label = allLabel, count = null, selected = selectedId == null) { onSelect(null) }
            }
        }
        items(options, key = { it.id }) { option ->
            Chip(
                label = option.label,
                count = option.count,
                selected = option.id == selectedId,
            ) { onSelect(option.id) }
        }
    }
}

/** One chip: an id, what it says, and optionally how many things are behind it. */
data class CoineProChip(val id: String, val label: String, val count: Int? = null)

@Composable
private fun Chip(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(if (selected) CoineProColors.Accent else CoineProColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        if (count != null) {
            Text(
                // A prose count, so Persian digits — unlike a price, which stays Latin. The chip
                // said «9» beside a subtitle that said «۵۲» until this line existed.
                text = persianDigits(count),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    CoineProColors.OnAccent.copy(alpha = 0.7f)
                } else {
                    CoineProColors.TextMuted
                },
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
            Icon(
                painter = painterResource(R.drawable.icon_x),
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable { onValueChange("") },
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

/**
 * A count, in Persian digits.
 *
 * For prose only — «۵۲ ابزار», «نقطهٔ ۲ از ۵». A price, a percentage or any other market figure stays
 * in Latin digits, because a trader reads those against a chart and a broker statement that use
 * Latin digits, and because the two sets are not interchangeable at a glance in a column of numbers.
 */
fun persianDigits(value: Int): String =
    value.toString().map { character ->
        if (character in '0'..'9') '۰' + (character - '0') else character
    }.joinToString("")
