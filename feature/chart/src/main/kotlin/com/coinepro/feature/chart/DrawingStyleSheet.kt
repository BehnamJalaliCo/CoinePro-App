package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.LineStyleKind
import com.coinepro.core.common.NumberStyle
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.SHEET_PREVIEW_SCRIM_ALPHA
import com.coinepro.core.designsystem.numeric
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ObjectTree
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.datastore.DrawingTemplateStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * One drawing's settings, in a sheet, with the template store wired to it.
 *
 * The store is read and written here rather than by the screen, because the two queries this needs
 * — the templates for *this drawing's tool*, and that tool's default — change with whichever row
 * the reader opened, and hoisting them would put a piece of this sheet's own state in the shell.
 * Everything that touches the *chart* stays hoisted, because that belongs to the controller.
 *
 * A null store leaves the colour and width controls working and the template half absent, which is
 * what a preview and a test get. It is the right degradation: a reader can still restyle a drawing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DrawingStyleSheet(
    drawing: Drawing,
    store: DrawingTemplateStore?,
    onDismiss: () -> Unit,
    onSetColour: (Long) -> Unit,
    onSetWidth: (Float) -> Unit,
    /** How wide a regression channel's rails sit, in standard deviations. See `Drawing.deviations`. */
    onSetDeviations: (Double) -> Unit,
    onApplyTemplate: (DrawingTemplate) -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit,
    onDelete: () -> Unit,
    /** Null puts the words back on [Drawing.colour]. See `DrawingActions.setTextColour`. */
    onSetTextColour: (Long?) -> Unit = {},
    /** Null puts the wash back on [Drawing.colour]; an alpha below full is the reader's opacity. */
    onSetFillColour: (Long?) -> Unit = {},
    onSetLineStyle: (LineStyleKind) -> Unit = {},
    /** One anchor, moved to a typed price. The coordinates tab. */
    onMovePoint: (index: Int, to: ChartPoint) -> Unit = { _, _ -> },
    onSetLocked: (Boolean) -> Unit = {},
    /** «Save as default»: the next drawing of this tool takes this one's colour and width. */
    onSaveAsDefault: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val templates by remember(drawing.toolId, store) {
        store?.templates(drawing.toolId) ?: flowOf(emptyList<DrawingTemplate>())
    }.collectAsStateWithLifecycle(emptyList())
    val default by remember(drawing.toolId, store) {
        store?.defaultFor(drawing.toolId) ?: flowOf<DrawingTemplate?>(null)
    }.collectAsStateWithLifecycle(null)

    CoineProSheet(
        title = DrawingTools[drawing.toolId]?.label ?: drawing.toolId,
        subtitle = ObjectTree.labelOf(drawing),
        onDismiss = onDismiss,
        // Twenty per cent, so the drawing being restyled stays visible while it is restyled.
        scrimAlpha = SHEET_PREVIEW_SCRIM_ALPHA,
    ) {
        DrawingStyleSheetBody(
            drawing = drawing,
            templates = templates,
            defaultTemplateId = default?.id,
            onSetColour = onSetColour,
            onSetWidth = onSetWidth,
            onSetDeviations = onSetDeviations,
            onApplyTemplate = onApplyTemplate,
            onSaveTemplate = { name ->
                store?.let { target ->
                    val template = templateOf(
                        toolId = drawing.toolId,
                        name = name,
                        colour = drawing.colour,
                        widthDp = drawing.widthDp,
                        now = System.currentTimeMillis(),
                    )
                    scope.launch { runCatching { target.save(template) } }
                }
            },
            onDeleteTemplate = { id ->
                store?.let { target -> scope.launch { runCatching { target.delete(id) } } }
            },
            onSetDefaultTemplate = { id ->
                store?.let { target ->
                    scope.launch { runCatching { target.setDefault(drawing.toolId, id) } }
                }
            },
            onBringToFront = onBringToFront,
            onSendToBack = onSendToBack,
            onDelete = onDelete,
            onSetTextColour = onSetTextColour,
            onSetFillColour = onSetFillColour,
            onSetLineStyle = onSetLineStyle,
            onMovePoint = onMovePoint,
            onSetLocked = onSetLocked,
            onSaveAsDefault = onSaveAsDefault,
        )
    }
}

/** The three pages of a drawing's settings, as the reference names them. */
internal enum class DrawingSettingsTab { STYLE, COORDINATES, VISIBILITY }

/**
 * One drawing's own settings: how it looks, where it sits, and when it shows.
 *
 * ### Three tabs, the reference's three
 *
 * **Style** is the colour, the width, the dash, the wash and the words; **Coordinates** is every
 * anchor as a price and a moment the reader can type; **Visibility** is the lock and the layer.
 * The tabs are what keep a sheet with this many controls from becoming a scroll of everything.
 *
 * ### Why the templates live here and not on the toolbar
 *
 * "Save as template" belongs where the thing being saved is — the drawing the reader has just got
 * looking right. Somewhere else it becomes a command whose subject has to be guessed. The sheet is
 * opened from the object tree's own row, so the drawing is named at the top of it and there is
 * never a question about which one is being restyled.
 *
 * ### What a template carries, and what it deliberately does not
 *
 * A colour and a width, per tool, and nothing else. That is the whole of what a reader adjusts on a
 * drawing over and over, and every field beyond those two is another way for two saved templates to
 * differ in a way nobody can see. See `DrawingTemplate`.
 *
 * A **locked** drawing shows its style and cannot change it, which is what the lock is for. The
 * controls are dimmed rather than absent, so the reader can see there is something to unlock — and
 * the visibility tab is where the lock itself is.
 */
@Composable
internal fun DrawingStyleSheetBody(
    drawing: Drawing,
    /** Every template saved for this drawing's tool, newest first. */
    templates: List<DrawingTemplate>,
    /** The template this tool reaches for by default, or null. */
    defaultTemplateId: String?,
    onSetColour: (Long) -> Unit,
    onSetWidth: (Float) -> Unit,
    /** See the same parameter on [DrawingStyleSheet]. Drawn only for the tools that have rails. */
    onSetDeviations: (Double) -> Unit,
    onApplyTemplate: (DrawingTemplate) -> Unit,
    onSaveTemplate: (name: String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onSetDefaultTemplate: (String?) -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit,
    onDelete: () -> Unit,
    onSetTextColour: (Long?) -> Unit = {},
    onSetFillColour: (Long?) -> Unit = {},
    onSetLineStyle: (LineStyleKind) -> Unit = {},
    onMovePoint: (index: Int, to: ChartPoint) -> Unit = { _, _ -> },
    onSetLocked: (Boolean) -> Unit = {},
    onSaveAsDefault: () -> Unit = {},
    /** Which tab opens first; a preview picks the one it wants pictured. */
    initialTab: DrawingSettingsTab = DrawingSettingsTab.STYLE,
) {
    var tab by rememberSaveable(drawing.id) { mutableStateOf(initialTab) }
    val editable = !drawing.locked

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CoineProSpacing.Gutter)
            .padding(bottom = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        CoineProSegmentedControl(
            options = listOf(
                DrawingSettingsTab.STYLE to stringResource(R.string.drawing_settings_style),
                DrawingSettingsTab.COORDINATES to stringResource(R.string.drawing_settings_coordinates),
                DrawingSettingsTab.VISIBILITY to stringResource(R.string.drawing_settings_visibility),
            ),
            selected = tab,
            onSelect = { tab = it },
        )
        if (!editable && tab != DrawingSettingsTab.VISIBILITY) {
            Text(
                text = "این ترسیم قفل است. برای تغییر رنگ یا ضخامت، اول قفلش را باز کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Warning,
            )
        }
        when (tab) {
            DrawingSettingsTab.STYLE -> StyleTab(
                drawing = drawing,
                editable = editable,
                templates = templates,
                defaultTemplateId = defaultTemplateId,
                onSetColour = onSetColour,
                onSetWidth = onSetWidth,
                onSetDeviations = onSetDeviations,
                onSetTextColour = onSetTextColour,
                onSetFillColour = onSetFillColour,
                onSetLineStyle = onSetLineStyle,
                onApplyTemplate = onApplyTemplate,
                onSaveTemplate = onSaveTemplate,
                onDeleteTemplate = onDeleteTemplate,
                onSetDefaultTemplate = onSetDefaultTemplate,
                onSaveAsDefault = onSaveAsDefault,
            )
            DrawingSettingsTab.COORDINATES -> CoordinatesTab(
                drawing = drawing,
                editable = editable,
                onMovePoint = onMovePoint,
            )
            DrawingSettingsTab.VISIBILITY -> VisibilityTab(
                drawing = drawing,
                onSetLocked = onSetLocked,
                onBringToFront = onBringToFront,
                onSendToBack = onSendToBack,
                onDelete = onDelete,
            )
        }
    }
}

/** Colour, width, dash, wash, words; then the templates. */
@Composable
private fun StyleTab(
    drawing: Drawing,
    editable: Boolean,
    templates: List<DrawingTemplate>,
    defaultTemplateId: String?,
    onSetColour: (Long) -> Unit,
    onSetWidth: (Float) -> Unit,
    onSetDeviations: (Double) -> Unit,
    onSetTextColour: (Long?) -> Unit,
    onSetFillColour: (Long?) -> Unit,
    onSetLineStyle: (LineStyleKind) -> Unit,
    onApplyTemplate: (DrawingTemplate) -> Unit,
    onSaveTemplate: (name: String) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onSetDefaultTemplate: (String?) -> Unit,
    onSaveAsDefault: () -> Unit,
) {
    val tool = DrawingTools[drawing.toolId]
    var name by rememberSaveable(drawing.id) { mutableStateOf("") }
    var custom by rememberSaveable(drawing.id) { mutableStateOf("") }
    val holdsText = DrawingActions.holdsText(drawing.toolId)
    val washes = DrawingActions.washes(drawing.toolId)

    StyleLabel("رنگ")
    SwatchGrid(chosen = drawing.colour, enabled = editable, onPick = onSetColour)
    // «Custom»: six hex digits, which is the one way to name a colour the palette lacks that
    // every reader who wants one already knows.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProTextField(
            value = custom,
            onValueChange = { custom = it.take(HEX_LENGTH + 1) },
            label = stringResource(R.string.drawing_settings_custom_colour),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f),
        )
        val parsed = parseHexColour(custom)
        Box(
            modifier = Modifier
                .size(SWATCH)
                .clip(CircleShape)
                .background(parsed?.let { Color(it.toULong() shl COLOUR_SHIFT) } ?: CoineProColors.SurfaceElevated)
                .border(1.dp, CoineProColors.Border, CircleShape)
                .clickable(enabled = editable && parsed != null) { parsed?.let(onSetColour) },
        )
    }

    StyleLabel("ضخامت")
    // Four segments, each drawn with the stroke it sets rather than named — the reference's.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        DRAWING_WIDTHS.forEach { (label, width) ->
            WidthSegment(
                label = label,
                widthDp = width,
                colour = Color(drawing.colour.toULong() shl COLOUR_SHIFT),
                active = drawing.widthDp == width,
                enabled = editable,
                onClick = { onSetWidth(width) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    StyleLabel("خط")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        LINE_STYLES.forEach { (label, style) ->
            StylePill(
                text = label,
                active = drawing.lineStyle == style,
                enabled = editable,
                onClick = { onSetLineStyle(style) },
            )
        }
    }

    if (washes) {
        HorizontalDivider(color = CoineProColors.Border)
        StyleLabel("پُرشدگی")
        SwatchGrid(
            chosen = drawing.fillColour?.let { it or ALPHA_MASK },
            enabled = editable,
            onPick = { hue -> onSetFillColour(withAlpha(hue, fillOpacity(drawing))) },
            followLine = drawing.fillColour == null,
            onFollowLine = { onSetFillColour(null) },
        )
        // The wash's opacity, live: the slider writes the fill colour's alpha and the renderer
        // reads an alpha below full as the reader's own. Percent, Latin, as a market-adjacent
        // figure on a control.
        val opacity = fillOpacity(drawing)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Slider(
                value = opacity,
                onValueChange = { next ->
                    val hue = drawing.fillColour ?: drawing.colour
                    onSetFillColour(withAlpha(hue, next))
                },
                enabled = editable,
                valueRange = MIN_FILL_OPACITY..1f,
                colors = SliderDefaults.colors(
                    thumbColor = CoineProColors.AccentFill,
                    activeTrackColor = CoineProColors.AccentFill,
                    inactiveTrackColor = CoineProColors.Border,
                ),
                modifier = Modifier.weight(1f).height(SLIDER_HEIGHT),
            )
            Text(
                text = NumberStyle.percent((opacity * PERCENT).toDouble(), 0),
                style = MaterialTheme.typography.labelMedium.numeric(),
                color = CoineProColors.TextSecondary,
            )
        }
    }

    if (holdsText) {
        HorizontalDivider(color = CoineProColors.Border)
        StyleLabel("متن")
        SwatchGrid(
            chosen = drawing.textColour,
            enabled = editable,
            onPick = onSetTextColour,
            followLine = drawing.textColour == null,
            onFollowLine = { onSetTextColour(null) },
        )
    }

    // The regression channel's own number, and the only tool on this chart that has one.
    //
    // It was frozen at 2.0 since the geometry was written — a literal default in
    // `DrawingGeometryA.regressionChannel` that no call site ever overrode — so every regression
    // channel anybody has ever drawn in this app has had the same rails. Two standard deviations
    // is the common convention and it is not the only one: a reader working a quiet range wants
    // one, and somebody marking the extremes of a volatile session wants three.
    //
    // Discrete steps rather than a slider, for the same reason [DRAWING_WIDTHS] is: a slider on
    // a phone is a drag that has to be repeated to land on a round number, and «۲» is the value
    // people talk about. The range is `DrawingActions.MIN_DEVIATIONS`..`MAX_DEVIATIONS`, and the
    // transform clamps anyway, so a stored value from outside it cannot draw an unusable channel.
    if (drawing.toolId == DEVIATION_TOOL) {
        HorizontalDivider(color = CoineProColors.Border)
        StyleLabel("پهنای کانال، بر حسب انحراف معیار")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            DEVIATION_CHOICES.forEach { value ->
                StylePill(
                    // A market figure — it is read against the chart, not spoken — so Latin.
                    text = formatDeviations(value),
                    active = kotlin.math.abs(drawing.deviations - value) < DEVIATION_EPSILON,
                    enabled = editable,
                    onClick = { onSetDeviations(value) },
                )
            }
        }
    }

    HorizontalDivider(color = CoineProColors.Border)

    // «Save as default»: the next one of this tool looks like this one. One tap, no name.
    CoineProSecondaryButton(
        text = stringResource(R.string.drawing_settings_save_default),
        onClick = onSaveAsDefault,
        modifier = Modifier.fillMaxWidth(),
    )

    StyleLabel("قالب‌های " + (tool?.label ?: drawing.toolId))
    if (templates.isEmpty()) {
        Text(
            text = "هنوز قالبی برای این ابزار ذخیره نشده. رنگ و ضخامت دلخواهتان را بگذارید و پایین ذخیره کنید.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    } else {
        templates.forEach { template ->
            TemplateRow(
                template = template,
                isDefault = template.id == defaultTemplateId,
                enabled = editable,
                onApply = { onApplyTemplate(template) },
                onSetDefault = {
                    onSetDefaultTemplate(if (template.id == defaultTemplateId) null else template.id)
                },
                onDelete = { onDeleteTemplate(template.id) },
            )
        }
    }

    CoineProTextField(
        value = name,
        onValueChange = { name = it },
        label = "نام قالب تازه",
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    CoineProPrimaryButton(
        text = "ذخیره‌ی رنگ و ضخامت فعلی به‌عنوان قالب",
        onClick = {
            onSaveTemplate(name)
            name = ""
        },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Every anchor of the drawing: its price in a field the reader can retype, its moment beside it.
 *
 * The price is editable and the time is not, on purpose: a level is the thing somebody types
 * («put it at 2,650.00»), a moment is the thing they drag. A typed time would also need a calendar
 * in two scripts, which is a sheet of its own.
 */
@Composable
private fun CoordinatesTab(
    drawing: Drawing,
    editable: Boolean,
    onMovePoint: (index: Int, to: ChartPoint) -> Unit,
) {
    drawing.points.forEachIndexed { index, point ->
        var typed by rememberSaveable(drawing.id, index, point.price) {
            mutableStateOf(NumberStyle.fixed(point.price, PRICE_DECIMALS))
        }
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            // A prose count, so Persian digits.
            StyleLabel(stringResource(R.string.drawing_settings_point, (index + 1).toPersianDigits()))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                CoineProTextField(
                    value = typed,
                    onValueChange = { next ->
                        typed = next
                        next.foldDigitsToLatin().toDoubleOrNull()?.let { price ->
                            if (editable) onMovePoint(index, point.copy(price = price))
                        }
                    },
                    label = stringResource(R.string.drawing_settings_price),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = PersianDateTime.moment(Instant.ofEpochSecond(point.time)),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
    }
}

/** The lock, the drawing's own timeframe, and its place in the stack. */
@Composable
private fun VisibilityTab(
    drawing: Drawing,
    onSetLocked: (Boolean) -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.drawing_settings_lock),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.drawing_settings_lock_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        Switch(
            checked = drawing.locked,
            onCheckedChange = onSetLocked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CoineProColors.OnAccent,
                checkedTrackColor = CoineProColors.AccentFill,
                uncheckedThumbColor = CoineProColors.TextMuted,
                uncheckedTrackColor = CoineProColors.SurfaceElevated,
            ),
        )
    }
    drawing.timeframe?.let { frame ->
        HorizontalDivider(color = CoineProColors.Border)
        StyleLabel(stringResource(R.string.drawing_settings_timeframe))
        Text(
            text = frame,
            style = MaterialTheme.typography.labelMedium.numeric(),
            color = CoineProColors.TextSecondary,
        )
        Text(
            text = stringResource(R.string.drawing_settings_timeframe_note),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }

    HorizontalDivider(color = CoineProColors.Border)

    StyleLabel("جای این ترسیم")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        StylePill(text = "بردن به جلو", active = false, enabled = true, onClick = onBringToFront)
        StylePill(text = "بردن به عقب", active = false, enabled = true, onClick = onSendToBack)
        StylePill(
            text = "حذف",
            active = false,
            enabled = !drawing.locked,
            tone = CoineProColors.Sell,
            onClick = onDelete,
        )
    }
}

/**
 * The twelve swatches, six across, with «مثل خط» leading where the colour may follow the line.
 *
 * [chosen] is compared on the hue alone, so a wash at forty per cent still lights the swatch it
 * was mixed from.
 */
@Composable
private fun SwatchGrid(
    chosen: Long?,
    enabled: Boolean,
    onPick: (Long) -> Unit,
    followLine: Boolean = false,
    onFollowLine: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        DRAWING_COLOURS.chunked(SWATCHES_ACROSS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                row.forEach { value ->
                    ColourSwatch(
                        colour = Color(value.toULong() shl COLOUR_SHIFT),
                        selected = chosen != null && (chosen and RGB_MASK) == (value and RGB_MASK),
                        enabled = enabled,
                        onClick = { onPick(value) },
                    )
                }
            }
        }
        onFollowLine?.let { follow ->
            StylePill(text = "مثل خط", active = followLine, enabled = enabled, onClick = follow)
        }
    }
}

/** A width choice drawn as the stroke it sets, in the drawing's own colour. */
@Composable
private fun WidthSegment(
    label: String,
    widthDp: Float,
    colour: Color,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = if (enabled) colour else CoineProColors.TextDisabled
    Box(
        modifier = modifier
            .height(WIDTH_SEGMENT_HEIGHT)
            .clip(CoineProShapes.small)
            .background(if (active) CoineProColors.SurfaceElevated else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) CoineProTint.edge(CoineProColors.Gold) else CoineProColors.Border,
                shape = CoineProShapes.small,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = CoineProSpacing.OneHalf),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(WIDTH_SEGMENT_STROKE_BOX)) {
            val y = size.height / 2
            drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = widthDp.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** The alpha byte of the fill colour as a fraction, or the tool's own — read as full — when unset. */
private fun fillOpacity(drawing: Drawing): Float {
    val fill = drawing.fillColour ?: return 1f
    val alpha = ((fill ushr ALPHA_SHIFT) and BYTE).toInt()
    return if (alpha == BYTE.toInt()) 1f else alpha / BYTE.toFloat()
}

/** [hue] with its alpha replaced by [opacity]; full opacity stores as full alpha, «the tool's». */
private fun withAlpha(hue: Long, opacity: Float): Long {
    val alpha = (opacity.coerceIn(0f, 1f) * BYTE).toLong() and BYTE
    return (hue and RGB_MASK) or (alpha shl ALPHA_SHIFT)
}

/** `#RRGGBB` or `RRGGBB`, case-insensitive, to a packed opaque ARGB; null for anything else. */
internal fun parseHexColour(text: String): Long? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != HEX_LENGTH || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return digits.toLong(HEX_RADIX) or ALPHA_MASK
}

/**
 * The template strip that sits above the tool rail.
 *
 * ### Why it is here and not another button
 *
 * The chart's bar is full and must not grow. The tool sheet is where a reader is *already* choosing
 * what to draw, and "draw a trend line the way I always draw trend lines" is one decision, not two
 * — so the templates for whatever tool is armed sit at the top of the same sheet, and picking one
 * arms the tool and sets the style in a single tap.
 *
 * Absent entirely when nothing is armed or the armed tool has no saved templates, rather than shown
 * empty. A permanent empty strip above a rail of ninety-one tools is chrome reporting an absence.
 */
@Composable
internal fun ToolTemplateRow(
    tool: DrawingTool?,
    templates: List<DrawingTemplate>,
    defaultTemplateId: String?,
    onApply: (DrawingTemplate) -> Unit,
) {
    if (tool == null || templates.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        StyleLabel("قالب‌های " + tool.label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            templates.forEach { template ->
                val colour = Color(template.colour.toULong() shl COLOUR_SHIFT)
                Row(
                    modifier = Modifier
                        .clip(CoineProPillShape)
                        .background(CoineProTint.fill(colour, CoineProColors.Surface))
                        .border(
                            width = if (template.id == defaultTemplateId) 1.dp else 0.dp,
                            color = if (template.id == defaultTemplateId) {
                                CoineProTint.edge(CoineProColors.Gold)
                            } else {
                                Color.Transparent
                            },
                            shape = CoineProPillShape,
                        )
                        .clickable { onApply(template) }
                        .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                ) {
                    Box(modifier = Modifier.size(SWATCH_DOT).clip(CircleShape).background(colour))
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** One saved template, with the two things a reader does to one that already exists. */
@Composable
private fun TemplateRow(
    template: DrawingTemplate,
    isDefault: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(CoineProColors.Surface),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(CoineProShapes.small)
                .clickable(enabled = enabled, onClick = onApply)
                .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Box(
                modifier = Modifier
                    .size(SWATCH_DOT)
                    .clip(CircleShape)
                    .background(Color(template.colour.toULong() shl COLOUR_SHIFT)),
            )
            Text(
                text = template.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) CoineProColors.TextPrimary else CoineProColors.TextDisabled,
            )
        }
        // The star is «use this one unless I say otherwise», which is a different decision from
        // «put it on this drawing now» — so it is its own target rather than a long press.
        Box(
            modifier = Modifier
                .size(ACTION)
                .clip(CircleShape)
                .clickable(onClick = onSetDefault),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.tv_star),
                contentDescription = if (isDefault) "برداشتن از پیش‌فرض" else "پیش‌فرض این ابزار",
                tint = if (isDefault) CoineProColors.Gold else CoineProColors.TextDisabled,
                modifier = Modifier.size(GLYPH),
            )
        }
        Box(
            modifier = Modifier
                .size(ACTION)
                .clip(CircleShape)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.tv_trash2),
                contentDescription = "حذف قالب",
                tint = CoineProColors.TextMuted,
                modifier = Modifier.size(GLYPH),
            )
        }
    }
}

/** One colour to choose from, drawn as the colour rather than named. */
@Composable
private fun ColourSwatch(
    colour: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SWATCH)
            .clip(CircleShape)
            .background(colour)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CoineProColors.Gold else CoineProColors.Border,
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
    )
}

/** An outlined pill, the same shape the interval strip uses, for a choice inside a sheet. */
@Composable
private fun StylePill(
    text: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tone: Color = CoineProColors.Gold,
) {
    val ink = when {
        !enabled -> CoineProColors.TextDisabled
        active -> tone
        else -> CoineProColors.TextMuted
    }
    Box(
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(if (active) CoineProTint.fill(tone, CoineProColors.Surface) else Color.Transparent)
            .border(1.dp, if (active) CoineProTint.edge(tone) else CoineProColors.Border, CoineProPillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = ink)
    }
}

/** A quiet heading inside the style sheet, for a control that needs one word of context. */
@Composable
private fun StyleLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        fontWeight = FontWeight.Normal,
    )
}

/**
 * The colours a drawing may be.
 *
 * Eight, and the list is not arbitrary. The first is the app's own gold, which is what a drawing is
 * placed in. The next four are the comparison palette — blue, amber, teal, mauve — chosen there to
 * stay apart from each other *and* from the market green and red under a red-green deficiency, and
 * the same argument applies to a line drawn over candles. The last three are the two market colours
 * and a neutral grey, which readers use deliberately: a support marked in green and a resistance in
 * red is the oldest convention on a chart, and refusing it because the colours mean something else
 * elsewhere would be the app overruling the reader on their own drawing.
 *
 * A free colour wheel is deliberately not offered. It produces drawings nobody can see against the
 * chart's ground, which is a support call rather than a feature.
 */
internal val DRAWING_COLOURS: List<Long> = listOf(
    Drawing.DEFAULT_DRAWING_COLOUR,
    0xFF4C9AFF,
    0xFFE69F00,
    0xFF00C2D1,
    0xFFB07AA1,
    0xFF00B15C,
    0xFFF6465D,
    0xFFB7BDC6,
    // The reference's twelve: four more that read against both grounds — white, a violet, an
    // orange and a rose — and a hex field beside them for the one colour nobody listed.
    0xFFFFFFFF,
    0xFF7C4DFF,
    0xFFFF7043,
    0xFFEC407A,
)

/**
 * The four widths, named rather than numbered.
 *
 * A width is a measurement in density-independent pixels and «۱٫۶» in a Persian sheet reads as a
 * price with a decimal separator, not as a thickness. The names say what the reader is choosing.
 */
internal val DRAWING_WIDTHS: List<Pair<String, Float>> = listOf(
    "نازک" to 1f,
    "معمولی" to DEFAULT_DRAWING_WIDTH_DP,
    "ضخیم" to 2.5f,
    "خیلی ضخیم" to 4f,
)

/**
 * The one tool whose rails are a number the reader may set.
 *
 * Named rather than tested against `DrawingActions` because the check is «does this sheet draw the
 * control», which is a question about this screen. Every other tool renders nothing for it, and a
 * width row on a tool with no rails would be a control that does nothing.
 */
private const val DEVIATION_TOOL = "regression"

/**
 * The widths offered, in standard deviations.
 *
 * Two is the convention and sits in the middle. One is a channel that hugs the fit, which is what a
 * reader marking a quiet range wants; three reaches the extremes of a volatile session. The ends are
 * `DrawingActions.MIN_DEVIATIONS` and half of `MAX_DEVIATIONS` — five is offered by the transform
 * and is not offered here, because a five-sigma rail is off the plot on nearly every chart and a
 * choice that looks like it did nothing is worse than one that is absent.
 */
private val DEVIATION_CHOICES: List<Double> = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0)

/**
 * A deviation as the pill prints it: `2` rather than `2.0`, `1.5` as it is.
 *
 * `Locale.US` because this is a market figure in a Persian sheet, and an unqualified format on this
 * app's default locale prints «۲٫۵» into a row of Latin numerals.
 */
private fun formatDeviations(value: Double): String =
    if (value == value.toInt().toDouble()) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }

/** How close two deviations have to be to be the same choice. A pill either lights or it does not. */
private const val DEVIATION_EPSILON = 0.01

/** See the same constant in `ChartScreen`: a packed ARGB long sits in the high half of a word. */
private const val COLOUR_SHIFT = 32

/** Large enough to tap; six across on a phone with the row's gaps. */
private val SWATCH = 32.dp
private const val SWATCHES_ACROSS = 6

/** The width segments: a row of four, each showing its stroke on a short rule. */
private val WIDTH_SEGMENT_HEIGHT = 40.dp
private val WIDTH_SEGMENT_STROKE_BOX = 12.dp
private val SLIDER_HEIGHT = 24.dp

/** Packed ARGB arithmetic for the fill's opacity. */
private const val ALPHA_SHIFT = 24
private const val BYTE = 0xFFL
private const val RGB_MASK = 0x00FFFFFFL
private const val ALPHA_MASK = 0xFF000000L
private const val MIN_FILL_OPACITY = 0.02f
private const val PERCENT = 100f
private const val HEX_LENGTH = 6
private const val HEX_RADIX = 16
private const val PRICE_DECIMALS = 2

/** The colour dot beside a template's name. */
private val SWATCH_DOT = 10.dp

/** The hit rect of the star and the bin on a template row. */
private val ACTION = 40.dp

private val GLYPH = 16.dp
