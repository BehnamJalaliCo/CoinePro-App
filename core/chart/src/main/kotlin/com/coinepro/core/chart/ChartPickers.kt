package com.coinepro.core.chart

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.R as DesignR

/**
 * The chart-type list.
 *
 * Every row carries a «؟». That is not decoration and it is not optional: this list offers Kagi and
 * Point & Figure beside candles, and a professional audience still contains people who have never
 * used them — the whole reason they are worth offering is that somebody can find out what they are
 * without leaving the app to search.
 *
 * [onHelp] receives the entry id. A screen that has no help catalogue loaded passes null and the
 * «؟» disappears rather than opening an empty sheet.
 */
@Composable
fun ChartTypePicker(
    selected: ChartType,
    onSelect: (ChartType) -> Unit,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(CoineProColors.Surface),
        contentPadding = PaddingValues(vertical = CoineProSpacing.One),
    ) {
        items(ChartCatalog.CHART_TYPES, key = { it.type }) { option ->
            PickerRow(
                label = option.label,
                subtitle = if (option.type.isTimeBased) null else TIME_FREE_NOTE,
                icon = option.icon,
                selected = option.type == selected,
                accent = null,
                onClick = { onSelect(option.type) },
                onHelp = onHelp?.let { { it(option.helpId) } },
            )
        }
    }
}

/**
 * The indicator list, grouped by where each one draws.
 *
 * The grouping is the useful distinction rather than an alphabet: a reader adding a third overlay
 * to the price is making a different decision from one opening a fourth pane below it, and the list
 * should say which they are about to do.
 */
@Composable
fun IndicatorPicker(
    active: Set<String>,
    onToggle: (IndicatorOption) -> Unit,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
) {
    val onPrice = ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.PRICE }
    val separate = ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.SEPARATE }
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(CoineProColors.Surface),
        contentPadding = PaddingValues(vertical = CoineProSpacing.One),
    ) {
        item { GroupHeader("روی قیمت") }
        items(onPrice, key = { it.id }) { option ->
            PickerRow(
                label = option.label,
                subtitle = null,
                icon = option.icon,
                selected = option.id in active,
                accent = Color(option.colour),
                onClick = { onToggle(option) },
                onHelp = onHelp?.let { { it(option.helpId) } },
            )
        }
        item { GroupHeader("در پنل جدا") }
        items(separate, key = { it.id }) { option ->
            PickerRow(
                label = option.label,
                subtitle = null,
                icon = option.icon,
                selected = option.id in active,
                accent = Color(option.colour),
                onClick = { onToggle(option) },
                onHelp = onHelp?.let { { it(option.helpId) } },
            )
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = Modifier.padding(
            horizontal = CoineProSpacing.Gutter,
            vertical = CoineProSpacing.One,
        ),
    )
}

@Composable
private fun PickerRow(
    label: String,
    subtitle: String?,
    @DrawableRes icon: Int,
    selected: Boolean,
    accent: Color?,
    onClick: () -> Unit,
    onHelp: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        // TradingView's own glyph. On an indicator it is tinted with that indicator's line colour,
        // so the icon and the swatch are one thing rather than two: the row says "this draws a
        // channel, in this colour" in a single mark.
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when {
                accent != null && selected -> accent
                accent != null -> accent.copy(alpha = INACTIVE_ICON_ALPHA)
                selected -> CoineProColors.Accent
                else -> CoineProColors.TextMuted
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
        if (selected && accent == null) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.bodyLarge,
                color = CoineProColors.Accent,
            )
        }
        if (onHelp != null) HelpDot(onClick = onHelp)
    }
}

/**
 * The «؟».
 *
 * TradingView's circled question mark rather than a Persian «؟» set in text. The typed character
 * was wrong twice over: at this size it reads as punctuation belonging to the label rather than as
 * a control, and it inherits the text font, so it sat at a different weight and baseline from every
 * other mark in the row.
 */
@Composable
private fun HelpDot(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_help_circle),
            contentDescription = HELP_LABEL,
            modifier = Modifier.size(18.dp),
            tint = CoineProColors.TextMuted,
        )
    }
}

/** Said once, on the types it is true of: their x axis is not a clock. */
private const val TIME_FREE_NOTE = "مستقل از زمان — هر میله با حرکت قیمت ساخته می‌شود"

/** Read aloud in place of the icon, which has no text of its own. */
private const val HELP_LABEL = "راهنما"

/** An unselected indicator keeps its colour, faintly, so the list still colour-codes itself. */
private const val INACTIVE_ICON_ALPHA = 0.45f
