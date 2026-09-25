package com.coinepro.feature.chart

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProMotionSpecs
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.coineProControl
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.pageAccent
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong
import com.coinepro.core.designsystem.R as DesignR

/**
 * The pieces TradingView's settings dialogs share, drawn once for every chart dialog that needs them
 * (DIALOGS-11, -23, -24): underline tabs, a 34 dp number field, the Defaults · Cancel · OK footer and
 * a colour swatch whose selection can be seen on any colour.
 */

/**
 * Underline tabs — the reference's Inputs / Style / Visibility. A 40 dp segmented pill made the tab
 * strip the heaviest object in a dialog whose content is three rows; text with a 2 dp rule under the
 * chosen one says the same thing quietly.
 */
@Composable
internal fun <T> ChartDialogTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Two)) {
            options.forEach { (value, label) ->
                val chosen = value == selected
                val rule by animateColorAsState(
                    targetValue = if (chosen) CoineProColors.pageAccent else Color.Transparent,
                    animationSpec = CoineProMotionSpecs.standard(),
                    label = "chartDialogTabRule",
                )
                Box(
                    modifier = Modifier
                        .height(TAB_HEIGHT)
                        .coineProControl(onClick = { onSelect(value) })
                        .drawBehind {
                            val thickness = TAB_RULE.toPx()
                            drawRect(rule, topLeft = Offset(0f, size.height - thickness), size = Size(size.width, thickness))
                        }
                        .semantics { contentDescription = "tab-$label" }
                        .padding(horizontal = CoineProSpacing.Half),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (chosen) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
                    )
                }
            }
        }
        HorizontalDivider(color = CoineProColors.BorderSubtle)
    }
}

/**
 * A number typed rather than stepped (DIALOGS-23): TradingView's 34 px input with its two small
 * arrows. The arrows keep the stepper's one virtue — a nudge without the keyboard — and the field
 * takes «200» in one go where the stepper took eleven taps.
 *
 * Latin figures, because the number is printed on the chart's legend beside prices. A value that
 * does not parse, or falls outside the bounds, is held in the field and not sent until it does; the
 * field shows the committed value again when it loses focus.
 */
@Composable
internal fun ChartNumberField(
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    decimals: Int,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    fun shown(number: Double): String =
        if (decimals == 0) number.roundToLong().toString() else String.format(Locale.ROOT, "%.${decimals}f", number)
    fun rounded(number: Double): Double {
        val scale = 10.0.pow(decimals)
        return ((number * scale).roundToLong() / scale).coerceIn(min, max)
    }
    var text by remember { mutableStateOf(shown(value)) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // An outside change — Defaults, an arrow — rewrites the field unless the reader is typing in it.
    LaunchedEffect(value, focused) {
        if (!focused) text = shown(value)
    }
    val edge by animateColorAsState(
        targetValue = if (focused) CoineProColors.pageAccent else CoineProColors.Border,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "chartNumberEdge",
    )
    Row(
        modifier = modifier
            .width(NUMBER_FIELD_WIDTH)
            .height(NUMBER_FIELD_HEIGHT)
            .clip(CoineProShapes.small)
            .border(1.dp, edge, CoineProShapes.small)
            .padding(start = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                val cleaned = typed.filter { it.isDigit() || it == '.' || it == '-' }
                text = cleaned
                cleaned.toDoubleOrNull()?.takeIf { it in min..max }?.let { onChange(rounded(it)) }
            },
            modifier = Modifier
                .weight(1f)
                .then(if (tag != null) Modifier.semantics { contentDescription = tag } else Modifier),
            interactionSource = interaction,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimals == 0) KeyboardType.Number else KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyMedium.numeric().copy(color = CoineProColors.TextPrimary),
            cursorBrush = SolidColor(CoineProColors.pageAccent),
        )
        Column(modifier = Modifier.width(SPIN_WIDTH)) {
            SpinArrow(DesignR.drawable.icon_caret_up, enabled = value < max) { onChange(rounded(value + step)) }
            SpinArrow(DesignR.drawable.icon_caret_down, enabled = value > min) { onChange(rounded(value - step)) }
        }
    }
}

@Composable
private fun SpinArrow(glyph: Int, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(NUMBER_FIELD_HEIGHT / 2)
            .coineProControl(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = if (enabled) CoineProColors.TextSecondary else CoineProColors.TextDisabled,
        )
    }
}

/**
 * The reference's settings footer: «Defaults» at the reading start, Cancel and OK at the end. Pinned
 * under the scroll by its caller, so the way out is never below the fold.
 */
@Composable
internal fun ChartDialogFooter(
    onCancel: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
    onDefaults: (() -> Unit)? = null,
    /** Something else at the reading start instead of «Defaults» — a drawing's «Template ▾». */
    leading: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = CoineProColors.BorderSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            leading?.invoke()
            if (onDefaults != null) {
                Text(
                    text = stringResource(R.string.chart_dialog_defaults),
                    style = MaterialTheme.typography.labelLarge,
                    color = CoineProColors.TextSecondary,
                    modifier = Modifier
                        .clip(CoineProShapes.small)
                        .coineProControl(onClick = onDefaults)
                        .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.One),
                )
            }
            Spacer(Modifier.weight(1f))
            CoineProSecondaryButton(text = stringResource(R.string.chart_dialog_cancel), onClick = onCancel)
            CoineProPrimaryButton(
                text = stringResource(R.string.chart_dialog_ok),
                onClick = onOk,
                modifier = Modifier.widthIn(min = 72.dp),
            )
        }
    }
}

/**
 * [content] over [footer], the footer always drawn: the content gets whatever height the footer
 * leaves. A `weight` does the same inside a bounded column and collapses the content to nothing in an
 * unbounded one (a preview, a proof test); this measures the footer first either way.
 */
@Composable
internal fun ChartPinnedFooter(
    footer: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(contents = listOf(content, footer ?: {}), modifier = modifier) { (bodies, feet), constraints ->
        val loose = constraints.copy(minHeight = 0)
        val footPlaced = feet.map { it.measure(loose) }
        val footHeight = footPlaced.sumOf { it.height }
        val bodyMax = if (constraints.hasBoundedHeight) (constraints.maxHeight - footHeight).coerceAtLeast(0) else Constraints.Infinity
        val bodyPlaced = bodies.map { it.measure(loose.copy(maxHeight = bodyMax)) }
        val width = (bodyPlaced + footPlaced).maxOfOrNull { it.width }?.coerceAtLeast(constraints.minWidth) ?: constraints.minWidth
        layout(width, bodyPlaced.sumOf { it.height } + footHeight) {
            var y = 0
            (bodyPlaced + footPlaced).forEach { placeable ->
                placeable.place(0, y)
                y += placeable.height
            }
        }
    }
}

/**
 * One colour to choose (DIALOGS-11). The chosen swatch is ringed *outside* a gap in the text ink and
 * carries a check: a gold ring around a gold disc — the default study colour — was invisible.
 */
@Composable
internal fun ChartColourSwatch(
    colour: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = SWATCH_SIZE,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size + SWATCH_RING_ROOM)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.border(2.dp, CoineProColors.TextPrimary, CircleShape) else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(SWATCH_RING_ROOM / 2),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colour)
                .border(1.dp, CoineProColors.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                // Drawn rather than typed: a «✓» is a glyph the typeface may not carry.
                val ink = if (colour.luminance() > SWATCH_LIGHT) Color.Black else Color.White
                Canvas(modifier = Modifier.size(size * CHECK_FRACTION)) {
                    val tick = Path().apply {
                        moveTo(this@Canvas.size.width * 0.1f, this@Canvas.size.height * 0.55f)
                        lineTo(this@Canvas.size.width * 0.4f, this@Canvas.size.height * 0.82f)
                        lineTo(this@Canvas.size.width * 0.92f, this@Canvas.size.height * 0.22f)
                    }
                    drawPath(tick, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}

/**
 * A 34 dp text field for a dialog (DIALOGS-24): TradingView's input height, an edge that takes the
 * accent under focus, and the label as a placeholder. The form-sized [com.coinepro.core.designsystem.CoineProTextField]
 * is 56 dp with a floating label — right for a sign-in form, a slab inside a settings dialog.
 */
@Composable
internal fun ChartTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    numeric: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val edge by animateColorAsState(
        targetValue = if (focused) CoineProColors.pageAccent else CoineProColors.Border,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "chartTextEdge",
    )
    val style = MaterialTheme.typography.bodyMedium.let { if (numeric) it.numeric() else it }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(TEXT_FIELD_HEIGHT)
            .clip(CoineProShapes.small)
            .border(1.dp, edge, CoineProShapes.small)
            .semantics { contentDescription = placeholder },
        enabled = enabled,
        interactionSource = interaction,
        singleLine = true,
        keyboardOptions = keyboardOptions,
        textStyle = style.copy(color = if (enabled) CoineProColors.TextPrimary else CoineProColors.TextDisabled),
        cursorBrush = SolidColor(CoineProColors.pageAccent),
        decorationBox = { inner ->
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = CoineProSpacing.One),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(text = placeholder, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextMuted, maxLines = 1)
                }
                inner()
            }
        },
    )
}

private val TAB_HEIGHT = 40.dp
private val TAB_RULE = 2.dp
private val NUMBER_FIELD_HEIGHT = 34.dp
private val NUMBER_FIELD_WIDTH = 96.dp
private val SPIN_WIDTH = 22.dp
private val SWATCH_SIZE = 28.dp
private val SWATCH_RING_ROOM = 8.dp
private const val SWATCH_LIGHT = 0.5f
private const val CHECK_FRACTION = 0.5f
private const val DISABLED_ALPHA = 0.38f
private val TEXT_FIELD_HEIGHT = 34.dp
