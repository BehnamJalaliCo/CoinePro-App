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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheetEmpty
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.R as DesignR

/**
 * The chart-type list.
 *
 * Eleven rows in two groups, and the grouping is the whole point. Six of these eleven have no clock
 * on the x axis at all — a Renko brick appears when price moves, not when time passes — and that is
 * the single fact a reader needs before choosing one. It was previously printed as a subtitle under
 * each of the six, which said the same sentence six times; said once, over a heading, it is a
 * distinction rather than a repetition.
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
    val timed = ChartCatalog.CHART_TYPES.filter { it.type.isTimeBased }
    val untimed = ChartCatalog.CHART_TYPES.filterNot { it.type.isTimeBased }
    LazyColumn(
        modifier = modifier.fillMaxWidth().background(CoineProColors.Surface),
        contentPadding = PaddingValues(bottom = CoineProSpacing.Two),
    ) {
        item { GroupHeader("زمان‌محور") }
        items(timed, key = { it.type }) { option ->
            PickerRow(
                label = option.label,
                icon = option.icon,
                selected = option.type == selected,
                accent = null,
                onClick = { onSelect(option.type) },
                onHelp = onHelp?.let { { it(option.helpId) } },
            )
        }
        item { GroupHeader("قیمت‌محور — هر میله با حرکت قیمت ساخته می‌شود، نه با گذر زمان") }
        items(untimed, key = { it.type }) { option ->
            PickerRow(
                label = option.label,
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
 * The indicator list.
 *
 * Same chrome as the drawing tools — a search field over a filter row over the list — because these
 * two sheets sit one tap apart on the same toolbar and a reader should not have to learn each of
 * them separately. The tools get a grid because a drawing tool has a picture of itself; indicators
 * get a list because they do not, and a grid of twenty identical wave glyphs would be a puzzle.
 *
 * The filter is by pane, which is the useful distinction rather than an alphabet: a reader adding a
 * third overlay to the price is making a different decision from one opening a fourth pane below
 * it, and the list should say which they are about to do.
 */
@Composable
fun IndicatorPicker(
    active: Set<String>,
    onToggle: (IndicatorOption) -> Unit,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
    /**
     * The lookbacks the reader has changed, and how to change them. Both null means no stepper —
     * which is what a picker used as a read-only list wants.
     */
    periods: Map<String, Int> = emptyMap(),
    onSetPeriod: ((String, Int) -> Unit)? = null,
) {
    var pane by remember { mutableStateOf<IndicatorPane?>(null) }
    var query by remember { mutableStateOf("") }

    // Typing overrides the chips rather than intersecting with them, exactly as in the tool rail.
    // Somebody who types «مکدی» wants MACD, not "MACD if it happens to be in the pane I last
    // tapped" — and an empty result the reader cannot explain is the worst outcome of two filters
    // combining quietly.
    val searching = query.isNotBlank()
    val shown = when {
        searching -> ChartCatalog.matchingIndicators(query)
        pane != null -> ChartCatalog.INDICATORS.filter { it.pane == pane }
        else -> ChartCatalog.INDICATORS
    }

    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Surface)) {
        CoineProSheetSearch(
            value = query,
            onValueChange = { query = it },
            placeholder = "جست‌وجوی اندیکاتور",
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
        )
        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        if (!searching) {
            CoineProChipRow(
                options = IndicatorPane.entries.map { candidate ->
                    CoineProChip(
                        id = candidate.name,
                        label = candidate.label,
                        count = ChartCatalog.INDICATORS.count { it.pane == candidate },
                    )
                },
                selectedId = pane?.name,
                onSelect = { id -> pane = id?.let(IndicatorPane::valueOf) },
                allLabel = "همه",
            )
            Spacer(Modifier.height(CoineProSpacing.One))
        }

        if (shown.isEmpty()) {
            CoineProSheetEmpty("اندیکاتوری با این نام پیدا نشد.")
            return@Column
        }

        // A heading only when the list actually spans both panes. Printing «روی قیمت» over a list
        // the reader just filtered *to* «روی قیمت» is a line of noise.
        val grouped = !searching && pane == null
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = CoineProSpacing.Two),
        ) {
            var last: IndicatorPane? = null
            for (option in shown) {
                if (grouped && option.pane != last) {
                    last = option.pane
                    val heading = option.pane
                    item(key = "h-${heading.name}") { GroupHeader(heading.label) }
                }
                item(key = option.id) {
                    PickerRow(
                        label = option.label,
                        icon = option.icon,
                        selected = option.id in active,
                        accent = Color(option.colour),
                        onClick = { onToggle(option) },
                        // Nine of the fifty have no entry in the shipped catalogue. They get no
                        // «؟» rather than one that opens nothing.
                        onHelp = option.helpId?.let { id -> onHelp?.let { { it(id) } } },
                        // Only on a switched-on indicator, and only where there is one lookback to
                        // change. A stepper on fifty rows at once would be a wall of numbers on a
                        // list whose job is choosing; a reader sets the length of the thing they
                        // have already decided to use.
                        period = ChartCatalog.periodOf(option.id)
                            ?.takeIf { option.id in active && onSetPeriod != null }
                            ?.let { bounds ->
                                PeriodControl(
                                    value = periods[option.id] ?: bounds.default,
                                    bounds = bounds,
                                    onChange = { next -> onSetPeriod?.invoke(option.id, next) },
                                )
                            },
                    )
                }
            }
        }
    }
}

/** What the chip and the heading call each pane. */
private val IndicatorPane.label: String
    get() = when (this) {
        IndicatorPane.PRICE -> "روی قیمت"
        IndicatorPane.SEPARATE -> "در پنل جدا"
        IndicatorPane.STRUCTURE -> "ساختار بازار"
    }

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = Modifier.padding(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            top = CoineProSpacing.OneHalf,
            bottom = CoineProSpacing.Half,
        ),
    )
}

@Composable
private fun PickerRow(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    accent: Color?,
    onClick: () -> Unit,
    onHelp: (() -> Unit)?,
    period: PeriodControl? = null,
) {
    // A selected row is a filled, hairlined card rather than a tick alone at the far end. On a
    // fifty-row list the reader scans down the left of the labels, and a mark parked on the other
    // side of the screen is the last thing they see. The whole row changing state is the first.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoineProSpacing.OneHalf,
                vertical = ROW_GAP,
            )
            .clip(CoineProShapes.small)
            .background(if (selected) CoineProColors.SurfaceElevated else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = accent?.copy(alpha = SELECTED_BORDER_ALPHA) ?: CoineProColors.Accent,
                        shape = CoineProShapes.small,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.OneHalf),
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        period?.let { PeriodStepper(it, accent ?: CoineProColors.Accent) }
        if (selected) {
            Icon(
                painter = painterResource(DesignR.drawable.icon_check_circle),
                contentDescription = SELECTED_LABEL,
                modifier = Modifier.size(18.dp),
                tint = accent ?: CoineProColors.Accent,
            )
        }
        if (onHelp != null) HelpDot(onClick = onHelp)
    }
}

/** One indicator's lookback, and how to move it. See [PeriodStepper]. */
private data class PeriodControl(
    val value: Int,
    val bounds: IndicatorPeriod,
    val onChange: (Int) -> Unit,
)

/**
 * Minus, the number, plus.
 *
 * ### The step is not one
 *
 * Nobody moves an average from 20 to 21. The lengths people use are 9, 14, 20, 21, 50, 100, 200 —
 * so the step scales with the value: single bars up to 20, fives to 50, tens to 100, twenties
 * beyond. Reaching 200 from 20 is then eleven taps rather than a hundred and eighty, and every
 * value on the way is one somebody actually uses.
 *
 * ### The digits are Latin
 *
 * A period is a market figure — it is drawn onto the chart's own legend as «EMA 50», beside
 * prices — and this control has to read the same as the label it produces. The app's rule, and one
 * of the few places in a Persian-first interface where Latin numerals are the correct answer.
 */
@Composable
private fun PeriodStepper(control: PeriodControl, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepperButton(
            glyph = DesignR.drawable.icon_caret_left,
            enabled = control.value > control.bounds.min,
            accent = accent,
            // Left is *down* in both directions, because this is a number line and not a
            // reading order: the minus sits where the smaller values are, mirrored with the
            // layout by the drawable itself.
            onClick = { control.onChange(step(control.value, up = false, bounds = control.bounds)) },
        )
        Text(
            text = control.value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.widthIn(min = PERIOD_WIDTH),
            textAlign = TextAlign.Center,
        )
        StepperButton(
            glyph = DesignR.drawable.icon_caret_right,
            enabled = control.value < control.bounds.max,
            accent = accent,
            onClick = { control.onChange(step(control.value, up = true, bounds = control.bounds)) },
        )
    }
}

@Composable
private fun StepperButton(
    @DrawableRes glyph: Int,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(STEPPER_TAP)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (enabled) accent else CoineProColors.TextDisabled,
        )
    }
}

/** The next value up or down, on the coarsening ladder described in [PeriodStepper]. */
private fun step(value: Int, up: Boolean, bounds: IndicatorPeriod): Int {
    val size = when {
        value < 20 -> 1
        value < 50 -> 5
        value < 100 -> 10
        else -> 20
    }
    // Going down from a boundary uses the *smaller* side's step, so the ladder is symmetric:
    // 50 steps down to 45 rather than to 40, and back up to 50.
    val downSize = when {
        value <= 20 -> 1
        value <= 50 -> 5
        value <= 100 -> 10
        else -> 20
    }
    val next = if (up) value + size else value - downSize
    return next.coerceIn(bounds.min, bounds.max)
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
            // Brighter than the muted text it sat in before, which made it look disabled — it is a
            // control, and on a list of eleven chart types it is the one that answers the question
            // the reader actually has.
            tint = CoineProColors.TextSecondary,
        )
    }
}

/** Read aloud in place of the icon, which has no text of its own. */
private const val HELP_LABEL = "راهنما"

/** Read aloud on the tick, which otherwise announces nothing. */
private const val SELECTED_LABEL = "انتخاب‌شده"

/** An unselected indicator keeps its colour, faintly, so the list still colour-codes itself. */
private const val INACTIVE_ICON_ALPHA = 0.45f

/**
 * A selected row's hairline, at a fraction of the indicator's own colour.
 *
 * Full strength would put a saturated rectangle around every active indicator and turn a list into
 * a set of competing boxes; this is the same 0.34 the design tokens use for a tinted surface.
 */
private const val SELECTED_BORDER_ALPHA = 0.34f

/** Between rows, so a selected card has air around it rather than touching its neighbours. */
private val ROW_GAP = 3.dp

/** Wide enough for three digits, so the row does not shuffle between 9 and 200. */
private val PERIOD_WIDTH = 26.dp

/** The stepper's tap target. Small for a control, but it sits inside a 48dp row. */
private val STEPPER_TAP = 30.dp
