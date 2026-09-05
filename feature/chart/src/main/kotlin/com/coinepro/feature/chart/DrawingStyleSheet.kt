package com.coinepro.feature.chart

import androidx.compose.foundation.background
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
        )
    }
}

/**
 * One drawing's own settings: how it looks, where it sits, and how to keep that look.
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
 * controls are dimmed rather than absent, so the reader can see there is something to unlock.
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
) {
    val tool = DrawingTools[drawing.toolId]
    var name by rememberSaveable(drawing.id) { mutableStateOf("") }
    val editable = !drawing.locked

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        if (!editable) {
            Text(
                text = "این ترسیم قفل است. برای تغییر رنگ یا ضخامت، اول قفلش را باز کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Warning,
            )
        }

        StyleLabel("رنگ")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            DRAWING_COLOURS.forEach { value ->
                ColourSwatch(
                    colour = Color(value.toULong() shl COLOUR_SHIFT),
                    selected = value == drawing.colour,
                    enabled = editable,
                    onClick = { onSetColour(value) },
                )
            }
        }

        StyleLabel("ضخامت")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            DRAWING_WIDTHS.forEach { (label, width) ->
                StylePill(
                    text = label,
                    active = drawing.widthDp == width,
                    enabled = editable,
                    onClick = { onSetWidth(width) },
                )
            }
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
            text = "ذخیرهٔ رنگ و ضخامت فعلی به‌عنوان قالب",
            onClick = {
                onSaveTemplate(name)
                name = ""
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )

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
                enabled = editable,
                tone = CoineProColors.Sell,
                onClick = onDelete,
            )
        }
    }
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

/** Large enough to tap, small enough that eight fit a phone's width with room to scroll. */
private val SWATCH = 32.dp

/** The colour dot beside a template's name. */
private val SWATCH_DOT = 10.dp

/** The hit rect of the star and the bin on a template row. */
private val ACTION = 40.dp

private val GLYPH = 16.dp
