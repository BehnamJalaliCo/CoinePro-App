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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.coinepro.core.datastore.ChartColourTemplate
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR

/**
 * The chart's colour templates: which palette it paints with, and how to make another.
 *
 * ### Why this is inside the layouts sheet
 *
 * Because a palette is part of the apparatus a reader looks through, which is exactly what a layout
 * is — `ChartLayout.colourTemplate` has always had a field for it. It is also the discipline this
 * screen keeps: the chart's bar is full, and a control used once a month does not get a button on
 * it. Somebody changing how their chart looks is already in «چیدمان‌ها».
 *
 * ### The two that cannot be deleted
 *
 * The store ships a dark and a light template and refuses to delete or overwrite either, so that a
 * reader who has made their chart unreadable always has a way back. They are stored under machine
 * names — `dark` and `light` — because a storage layer that shipped Persian would have to be
 * migrated the day that changed; the Persian is [persianName], here, where the words belong.
 *
 * ### Making one
 *
 * Six colours, named for what they paint, seeded from whatever is currently on the chart so that a
 * reader making a small change starts from the palette they are looking at rather than from black.
 * A free colour wheel is deliberately not offered: it produces charts whose text is the colour of
 * their own background, and there is no way back from that except deleting the template.
 */
@Composable
internal fun ColourTemplateSection(
    templates: List<ChartColourTemplate>,
    /** What the chart is painting with now, or null for the theme's own colours. */
    selected: ChartColourTemplate?,
    onSelect: (ChartColourTemplate?) -> Unit,
    onSave: (ChartColourTemplate) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        SectionLabel("رنگ‌های چارت")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            // «تم برنامه» first, because it is the state a reader who has never been here is in,
            // and because it is the way back from a template that turned out to be unreadable.
            ThemeChip(
                label = "تم برنامه",
                active = selected == null,
                onClick = { onSelect(null) },
            )
            templates.forEach { template ->
                ColourTemplateChip(
                    template = template,
                    active = template.id == selected?.id,
                    onClick = { onSelect(template) },
                    onDelete = if (template.isBuiltIn) null else ({ onDelete(template.id) }),
                )
            }
        }

        Text(
            text = "رنگ‌ها روی خود نمودار می‌نشیند و همراه چیدمان ذخیره می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )

        Text(
            text = if (editing) "بستن ساخت قالب رنگ" else "ساختن قالب رنگ تازه",
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.Gold,
            modifier = Modifier
                .clip(CoineProShapes.small)
                .clickable { editing = !editing }
                .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
        )

        if (editing) {
            HorizontalDivider(color = CoineProColors.Border)
            ColourTemplateEditor(
                seed = selected ?: ChartColourTemplate.Dark,
                onSave = { template ->
                    onSave(template)
                    onSelect(template)
                    editing = false
                },
            )
        }
    }
}

/**
 * Six colours and a name, starting from a palette that already works.
 *
 * Seeded rather than blank because every one of the six has to be legible against the other five,
 * and a reader assembling that from nothing gets it wrong on the first try. Starting from the
 * palette on screen means the common case — "the same, but the green is too dark" — is one tap.
 */
@Composable
private fun ColourTemplateEditor(
    seed: ChartColourTemplate,
    onSave: (ChartColourTemplate) -> Unit,
) {
    var name by rememberSaveable(seed.id) { mutableStateOf("") }
    var up by rememberSaveable(seed.id) { mutableStateOf(seed.up) }
    var down by rememberSaveable(seed.id) { mutableStateOf(seed.down) }
    var grid by rememberSaveable(seed.id) { mutableStateOf(seed.grid) }
    var background by rememberSaveable(seed.id) { mutableStateOf(seed.background) }
    var text by rememberSaveable(seed.id) { mutableStateOf(seed.text) }
    var crosshair by rememberSaveable(seed.id) { mutableStateOf(seed.crosshair) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        ColourSlot("کندل صعودی", up, TEMPLATE_ACCENTS) { up = it }
        ColourSlot("کندل نزولی", down, TEMPLATE_ACCENTS) { down = it }
        ColourSlot("شبکه", grid, TEMPLATE_NEUTRALS) { grid = it }
        ColourSlot("پس‌زمینه", background, TEMPLATE_NEUTRALS) { background = it }
        ColourSlot("متن محور", text, TEMPLATE_NEUTRALS) { text = it }
        ColourSlot("نشانگر", crosshair, TEMPLATE_NEUTRALS) { crosshair = it }

        // The six colours as they would actually sit together, before anything is saved. A row of
        // swatches says what each one is; this says whether they work, which is the only question
        // that matters and the one a list of six answers badly.
        ColourPreview(up = up, down = down, grid = grid, background = background, text = text)

        CoineProTextField(
            value = name,
            onValueChange = { name = it },
            label = "نام قالب رنگ",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        CoineProPrimaryButton(
            text = "ذخیرهٔ قالب رنگ",
            onClick = {
                onSave(
                    newColourTemplate(
                        name = name,
                        up = up,
                        down = down,
                        grid = grid,
                        background = background,
                        text = text,
                        crosshair = crosshair,
                        now = System.currentTimeMillis(),
                    ),
                )
                name = ""
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One of the six, named for what it paints, with the colours it may be. */
@Composable
private fun ColourSlot(
    label: String,
    value: Long,
    palette: List<Long>,
    onPick: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        SectionLabel(label)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            palette.forEach { candidate ->
                Box(
                    modifier = Modifier
                        .size(SWATCH)
                        .clip(CircleShape)
                        .background(Color(candidate.toULong() shl COLOUR_SHIFT))
                        .border(
                            width = if (candidate == value) 2.dp else 1.dp,
                            color = if (candidate == value) CoineProColors.Gold else CoineProColors.Border,
                            shape = CircleShape,
                        )
                        .clickable { onPick(candidate) },
                )
            }
        }
    }
}

/**
 * The palette drawn as a chart would draw it: a ground, two rules, two candles and a label.
 *
 * Twelve density-independent pixels of nothing would say the same as a swatch row. This says
 * whether the grid is visible against the background and whether the text can be read on it, which
 * are the two ways a hand-made palette actually fails.
 */
@Composable
private fun ColourPreview(up: Long, down: Long, grid: Long, background: Long, text: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(Color(background.toULong() shl COLOUR_SHIFT))
            .border(1.dp, CoineProColors.Border, CoineProShapes.small)
            .padding(CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Box(
            modifier = Modifier
                .size(width = PREVIEW_CANDLE, height = PREVIEW_HEIGHT)
                .background(Color(up.toULong() shl COLOUR_SHIFT)),
        )
        Box(
            modifier = Modifier
                .size(width = PREVIEW_CANDLE, height = PREVIEW_HEIGHT)
                .background(Color(down.toULong() shl COLOUR_SHIFT)),
        )
        Box(
            modifier = Modifier
                .size(width = PREVIEW_RULE, height = PREVIEW_HEIGHT)
                .background(Color(grid.toULong() shl COLOUR_SHIFT)),
        )
        Text(
            text = "نمونه",
            style = MaterialTheme.typography.labelSmall,
            color = Color(text.toULong() shl COLOUR_SHIFT),
        )
    }
}

/** One saved palette, shown as its own four leading colours plus the way to be rid of it. */
@Composable
private fun ColourTemplateChip(
    template: ChartColourTemplate,
    active: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(if (active) CoineProTint.fill(CoineProColors.Gold, CoineProColors.Surface) else CoineProColors.Surface)
            .border(
                1.dp,
                if (active) CoineProTint.edge(CoineProColors.Gold) else CoineProColors.Border,
                CoineProShapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(template.up, template.down, template.background, template.text).forEach { value ->
                Box(
                    modifier = Modifier
                        .size(CHIP_DOT)
                        .clip(CircleShape)
                        .background(Color(value.toULong() shl COLOUR_SHIFT)),
                )
            }
        }
        Text(
            text = template.persianName,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CoineProColors.Gold else CoineProColors.TextSecondary,
        )
        onDelete?.let { delete ->
            Icon(
                painter = painterResource(DesignR.drawable.tv_trash2),
                contentDescription = "حذف قالب رنگ",
                tint = CoineProColors.TextMuted,
                modifier = Modifier
                    .size(CHIP_GLYPH)
                    .clickable(onClick = delete),
            )
        }
    }
}

/** The chip standing for "no template at all", which is not the same as the dark built-in. */
@Composable
private fun ThemeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(if (active) CoineProTint.fill(CoineProColors.Gold, CoineProColors.Surface) else CoineProColors.Surface)
            .border(
                1.dp,
                if (active) CoineProTint.edge(CoineProColors.Gold) else CoineProColors.Border,
                CoineProShapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.OneHalf),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CoineProColors.Gold else CoineProColors.TextSecondary,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        fontWeight = FontWeight.Normal,
    )
}

/**
 * What a template is called on screen.
 *
 * The two built-ins carry machine names on disk for the reason given on `ChartColourTemplate.name`,
 * and this is where they get Persian ones. Anything the reader made keeps the name they typed —
 * translating that would be the app renaming somebody's own work.
 */
internal val ChartColourTemplate.persianName: String
    get() = when (id) {
        ChartColourTemplate.BUILT_IN_DARK_ID -> "تیره"
        ChartColourTemplate.BUILT_IN_LIGHT_ID -> "روشن"
        else -> name
    }

/**
 * A template ready to be written, under the name the reader typed.
 *
 * The id is generated from the clock rather than from the name, because the store keys on it and
 * two templates are allowed to share a name — and because a name-derived id would collide with a
 * built-in the moment somebody called their palette «dark».
 */
internal fun newColourTemplate(
    name: String,
    up: Long,
    down: Long,
    grid: Long,
    background: Long,
    text: Long,
    crosshair: Long,
    now: Long,
): ChartColourTemplate = ChartColourTemplate(
    id = "colour_" + now.toString(TEMPLATE_ID_RADIX),
    name = name.trim(),
    up = up,
    down = down,
    grid = grid,
    background = background,
    text = text,
    crosshair = crosshair,
)

/**
 * The colours a candle may be.
 *
 * Both themes' greens and reds first, because the overwhelming majority of readers are adjusting
 * one of those rather than replacing it — then blue and amber, which is the pair that stays
 * distinguishable under a red-green deficiency and is what a reader with one actually needs. See
 * `comparisonColour` in `core:chart`, where the same argument produced the same four hues.
 */
private val TEMPLATE_ACCENTS: List<Long> = listOf(
    0xFF00B15C,
    0xFF0E8A4C,
    0xFFF6465D,
    0xFFC9203A,
    0xFF4C9AFF,
    0xFFE69F00,
    0xFF00C2D1,
    0xFFB07AA1,
)

/**
 * The colours a ground, a grid, a label or a crosshair may be.
 *
 * A ladder from near-black to near-white with no hue in it, because these four are the parts of a
 * chart that must not compete with the candles. Offering a coloured background here is offering a
 * chart nobody can read prices on, and it is the first thing a reader would try.
 */
private val TEMPLATE_NEUTRALS: List<Long> = listOf(
    0xFF070A0F,
    0xFF11161D,
    0xFF1E2329,
    0xFF3A424D,
    0xFF707A88,
    0xFF848E9C,
    0xFFB7BDC6,
    0xFFE8EBEF,
    0xFFF7F8FA,
)

/** See the same constant in `ChartScreen`: a packed ARGB long sits in the high half of a word. */
private const val COLOUR_SHIFT = 32

/** Base thirty-six, so a millisecond clock becomes a short id rather than thirteen digits. */
private const val TEMPLATE_ID_RADIX = 36

private val SWATCH = 30.dp
private val CHIP_DOT = 7.dp
private val CHIP_GLYPH = 14.dp
private val PREVIEW_CANDLE = 8.dp
private val PREVIEW_RULE = 2.dp
private val PREVIEW_HEIGHT = 22.dp
