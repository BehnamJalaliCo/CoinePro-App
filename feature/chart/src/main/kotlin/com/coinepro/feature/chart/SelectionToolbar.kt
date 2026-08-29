package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR

/**
 * The strip that appears over the chart the moment something is selected.
 *
 * ### What was wrong before
 *
 * Everything a reader could do to a drawing they had already placed was behind the object tree: a
 * sheet, opened from a toolbar button, whose rows carry a «تنظیمات» that opens a second sheet. To
 * change the colour of the line under your finger you tapped the line, saw handles appear, and
 * then had to go and *find that line again* in a list. The floating toolbar is how every terminal
 * solves this and it is one gesture: select, and the controls are there.
 *
 * ### Why it floats rather than taking a row
 *
 * The chart page is dense and the toolbar under it is full. A permanent row for controls that are
 * meaningless while nothing is selected would cost the chart height on every screen a reader ever
 * looks at, to serve the minority of moments when they are editing. Floating costs nothing when
 * absent — and this is absent unless [state] has a selection.
 *
 * ### What it carries, and the two things it does not
 *
 * Line colour, thickness, the saved templates for this tool, the text of an annotation, duplicate,
 * lock, delete, and the way into the full settings sheet. Multi-select is armed from here too,
 * because this is the first surface that exists once there is something to collect.
 *
 * It does **not** carry a separate text colour, a fill colour or a line style, and that is not an
 * omission of taste. `core:chart`'s `Drawing` has one colour and one width and the renderer reads
 * exactly those; there is nowhere for a second colour or a dash pattern to be stored or drawn. A
 * control that set a value nothing rendered would be precisely the failure this whole wave is
 * about, so the three are recorded as wiring needed rather than mocked up here.
 */
@Composable
internal fun DrawingSelectionToolbar(
    state: DrawingState,
    /** Whether the next canvas tap adds to the selection. See [ChartUiState.multiSelect]. */
    multiSelect: Boolean,
    /** The saved styles for the selected drawing's tool, newest first. Empty hides the menu. */
    templates: List<DrawingTemplate>,
    onSetMultiSelect: (Boolean) -> Unit,
    /** Applies to everything selected, not only the primary. See `DrawingActions.recolourSelection`. */
    onRecolour: (Long) -> Unit,
    onSetWidth: (Float) -> Unit,
    onApplyTemplate: (DrawingTemplate) -> Unit,
    /** Opens the keyboard for a text, callout, note or price label. */
    onEditText: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onSetLocked: (Long, Boolean) -> Unit,
    /** Deletes everything selected. A locked drawing survives it — the lock is enforced in the transform. */
    onDelete: () -> Unit,
    onOpenSettings: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = state.drawings.firstOrNull { it.id == state.selectedId } ?: return
    var panel by remember(primary.id) { mutableStateOf(SelectionPanel.NONE) }
    val locked = primary.locked
    val count = state.selection.size

    Column(
        modifier = modifier
            .clip(CoineProShapes.small)
            .background(CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.Border, CoineProShapes.small)
            .padding(horizontal = CoineProSpacing.Half, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // What is being acted on, in one glance. A prose count, so Persian digits — and only
            // once there is more than one, because «۱ انتخاب» over a single selected line is a
            // label restating the handles the reader can already see.
            if (count > 1) {
                Text(
                    text = count.toPersianDigits() + " انتخاب",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Gold,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
                )
            }
            SelectionAction(
                icon = DesignR.drawable.tv_tool_select,
                label = if (multiSelect) "پایان انتخاب چندتایی" else "انتخاب چندتایی",
                tint = if (multiSelect) CoineProColors.Accent else null,
            ) { onSetMultiSelect(!multiSelect) }
            SelectionAction(
                icon = DesignR.drawable.tv_pencil,
                label = "رنگ و ضخامت",
                tint = if (panel == SelectionPanel.STYLE) CoineProColors.Accent else null,
                enabled = !locked,
            ) { panel = if (panel == SelectionPanel.STYLE) SelectionPanel.NONE else SelectionPanel.STYLE }
            if (templates.isNotEmpty()) {
                SelectionAction(
                    icon = DesignR.drawable.tv_tool_template,
                    label = "قالب‌ها",
                    tint = if (panel == SelectionPanel.TEMPLATES) CoineProColors.Accent else null,
                    enabled = !locked,
                ) {
                    panel = if (panel == SelectionPanel.TEMPLATES) SelectionPanel.NONE else SelectionPanel.TEMPLATES
                }
            }
            // Only the four tools that can hold words. Offering a keyboard on a trend line would be
            // offering to write somewhere the renderer has nothing to draw.
            if (DrawingActions.holdsText(primary.toolId)) {
                SelectionAction(
                    icon = DesignR.drawable.tv_tool_text,
                    label = "متن",
                    enabled = !locked,
                ) { onEditText(primary.id) }
            }
            SelectionAction(icon = DesignR.drawable.icon_copy, label = "تکثیر") { onDuplicate(primary.id) }
            SelectionAction(
                icon = if (locked) DesignR.drawable.tv_lock else DesignR.drawable.tv_unlock,
                label = if (locked) "باز کردن قفل" else "قفل کردن",
                tint = if (locked) CoineProColors.Gold else null,
            ) { onSetLocked(primary.id, !locked) }
            SelectionAction(
                icon = DesignR.drawable.tv_trash2,
                label = "حذف",
                tint = if (locked) null else CoineProColors.Sell,
                enabled = !locked,
                onClick = onDelete,
            )
            SelectionAction(icon = DesignR.drawable.tv_settings2, label = "همهٔ تنظیمات") {
                onOpenSettings(primary.id)
            }
            SelectionAction(icon = DesignR.drawable.icon_x, label = "بستن", onClick = onDismiss)
        }

        when (panel) {
            SelectionPanel.NONE -> Unit
            SelectionPanel.STYLE -> StylePanel(
                drawing = primary,
                onRecolour = onRecolour,
                onSetWidth = onSetWidth,
            )
            SelectionPanel.TEMPLATES -> TemplatePanel(templates = templates, onApply = onApplyTemplate)
        }
    }
}

/**
 * Copy, paste and «پاک کردن همه», at the head of the object tree.
 *
 * ### Why here rather than on the floating strip
 *
 * The strip floats over the chart and every control on it is a glyph. A clipboard is the one part
 * of this that genuinely needs words: «کپی» and «چسباندن» have no glyph a reader recognises out of
 * context, an empty clipboard has to be able to say it is empty, and "delete everything" must
 * never be a nine-point icon beside eight others. The tree already opens from a button that means
 * "the things I have drawn", it has the width for labels, and it is where somebody managing a
 * crowded chart already is.
 *
 * ### Nothing here was reachable
 *
 * `DrawingActions.copySelection`, `paste` and `clear` all existed with tests and no caller at all;
 * the clipboard field on `DrawingState` was written by nothing. This row is what writes it.
 *
 * [onClear] is expected to ask first — every drawing on a chart is the case the confirm dialog's
 * own rules name — and the question is the screen's, not this row's.
 */
@Composable
internal fun DrawingClipboardRow(
    state: DrawingState,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = selectionSummary(state),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClipboardChip(
                text = "کپی انتخاب",
                enabled = state.selection.isNotEmpty(),
                onClick = onCopy,
            )
            ClipboardChip(
                // The count is what makes an empty clipboard legible without a second line: «۰»
                // is never drawn, because the chip is disabled and says the same thing.
                text = if (state.clipboard.isEmpty()) {
                    "چسباندن"
                } else {
                    "چسباندن " + state.clipboard.size.toPersianDigits() + " ترسیم"
                },
                enabled = state.clipboard.isNotEmpty(),
                onClick = onPaste,
            )
            ClipboardChip(
                text = "پاک کردن همه",
                enabled = state.drawings.isNotEmpty(),
                tone = CoineProColors.Sell,
                onClick = onClear,
            )
        }
    }
}

/** One labelled action in the tree's header row. An outlined pill, like every other choice here. */
@Composable
private fun ClipboardChip(
    text: String,
    enabled: Boolean,
    tone: Color = CoineProColors.Gold,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(CoineProPillShape)
            .border(
                width = 1.dp,
                color = if (enabled) CoineProColors.Border else CoineProColors.Border.copy(alpha = DISABLED_EDGE),
                shape = CoineProPillShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) tone else CoineProColors.TextDisabled,
        )
    }
}

/**
 * Which second row is open, or none.
 *
 * One at a time and closed by default, because the strip sits *over* the chart: two open panels is
 * a third of the plot covered by controls, which is the reader's own drawing hidden behind the
 * thing they opened to look at it.
 */
private enum class SelectionPanel { NONE, STYLE, TEMPLATES }

/** The colours and widths, applied to the whole selection rather than to the primary alone. */
@Composable
private fun StylePanel(
    drawing: Drawing,
    onRecolour: (Long) -> Unit,
    onSetWidth: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DRAWING_COLOURS.forEach { value ->
            Box(
                modifier = Modifier
                    .size(SWATCH)
                    .clip(CircleShape)
                    .background(Color(value.toULong() shl SELECTION_COLOUR_SHIFT))
                    .border(
                        width = if (value == drawing.colour) 2.dp else 1.dp,
                        color = if (value == drawing.colour) CoineProColors.Gold else CoineProColors.Border,
                        shape = CircleShape,
                    )
                    .clickable { onRecolour(value) },
            )
        }
        DRAWING_WIDTHS.forEach { (label, width) ->
            val active = drawing.widthDp == width
            Box(
                modifier = Modifier
                    .clip(CoineProPillShape)
                    .background(
                        if (active) {
                            CoineProTint.fill(CoineProColors.Gold, CoineProColors.SurfaceElevated)
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (active) CoineProTint.edge(CoineProColors.Gold) else CoineProColors.Border,
                        shape = CoineProPillShape,
                    )
                    .clickable { onSetWidth(width) }
                    .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) CoineProColors.Gold else CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * The saved styles for this tool, one tap each.
 *
 * The same templates the tool rail offers when arming, offered here for a drawing that already
 * exists — which is the other half of the same idea: «make this one look the way I always make
 * these look» is one decision, and until now it cost a sheet and a scroll.
 */
@Composable
private fun TemplatePanel(templates: List<DrawingTemplate>, onApply: (DrawingTemplate) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        templates.forEach { template ->
            val colour = Color(template.colour.toULong() shl SELECTION_COLOUR_SHIFT)
            Row(
                modifier = Modifier
                    .clip(CoineProPillShape)
                    .background(CoineProTint.fill(colour, CoineProColors.SurfaceElevated))
                    .clickable { onApply(template) }
                    .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(modifier = Modifier.size(TEMPLATE_DOT).clip(CircleShape).background(colour))
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
    }
}

/**
 * One control on the floating strip.
 *
 * Thirty-six rather than the forty-eight the tool rail uses, and the difference is deliberate: this
 * bar sits over the chart, and a row of nine forty-eight-point targets is four hundred and thirty
 * points — wider than the phone. Thirty-six is still above the twenty-four Android will warn about
 * and the row scrolls, so nothing is unreachable; the glyph stays sixteen so the icons are legible
 * against candles.
 */
@Composable
private fun SelectionAction(
    @DrawableRes icon: Int,
    label: String,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ACTION)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(GLYPH),
            tint = when {
                !enabled -> CoineProColors.TextDisabled
                tint != null -> tint
                else -> CoineProColors.TextSecondary
            },
        )
    }
}

/**
 * What the strip says the selection *is*, for the object tree's own header.
 *
 * Persian digits, because these are prose counts rather than market figures. Named here rather than
 * built at the call site so the tree and the toolbar cannot end up describing the same selection
 * two different ways.
 */
internal fun selectionSummary(state: DrawingState): String {
    val count = state.selection.size
    if (count == 0) return "چیزی انتخاب نشده"
    if (count > 1) return count.toPersianDigits() + " ترسیم انتخاب شده"
    val id = state.selectedId ?: return count.toPersianDigits() + " ترسیم انتخاب شده"
    val drawing = state.drawings.firstOrNull { it.id == id } ?: return "چیزی انتخاب نشده"
    return DrawingTools[drawing.toolId]?.label ?: drawing.toolId
}

/** See the same constant in `ChartScreen`: a packed ARGB long sits in the high half of a word. */
private const val SELECTION_COLOUR_SHIFT = 32

private val ACTION = 36.dp

private val GLYPH = 16.dp

private val SWATCH = 24.dp

private val TEMPLATE_DOT = 8.dp

/** How faint a disabled chip's hairline goes. Present, so the chip is still a shape, and quiet. */
private const val DISABLED_EDGE = 0.4f
