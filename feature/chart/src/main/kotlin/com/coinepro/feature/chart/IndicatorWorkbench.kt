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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.BarField
import com.coinepro.core.chart.CandlePatterns
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.IndicatorChain
import com.coinepro.core.chart.IndicatorSource
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.IndicatorTemplate
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTint

/**
 * The three things a reader does to a set of indicators once they have chosen them: point one at
 * another, keep the set, and mark the shapes the bars themselves make.
 *
 * All three live in the studio rather than in the chart's indicator sheet, and for one reason: the
 * sheet is a list of eighty-three names that a reader scrolls to find one thing. These are the jobs
 * you do *after* you have found them, they need room to explain themselves, and none of them is
 * something anybody does while reading a market.
 */

/**
 * What each switched-on indicator is computed on — item 80.
 *
 * ### Why this is worth a section
 *
 * Every indicator in this app read the candle series and nothing else, so «RSI روی میانگین هموار» —
 * the shape half of published strategies have — could not be expressed at all. `IndicatorChain` is
 * the engine; this is the only way to reach it.
 *
 * ### Why the source list is what it is
 *
 * A source is either a column of the bars or another *switched-on* indicator's output. The second
 * half is why the row for an indicator changes as the reader turns others on: a chain can only read
 * something that is being computed, and offering a source that is switched off would build a chain
 * `IndicatorChain.evaluate` refuses as a missing source — taking the whole chain down to say what
 * the picker should never have offered.
 *
 * Indicators that read more than one column are listed with their sources fixed and said so. An
 * ATR fed one array would be an ATR in name only: it needs the high and the low, and a picker that
 * let a reader hand it a single series would produce something that looks computed and is not the
 * thing it is labelled.
 */
@Composable
internal fun IndicatorChainSection(
    /** Every indicator switched on, in catalogue order. */
    active: List<String>,
    /** The reader's overrides, sparse. See [ChartUiState.chainSources]. */
    sources: Map<String, IndicatorSource>,
    /** Why the chain will not draw, or null. See [ChartUiState.chainRefusal]. */
    refusal: String?,
    onSetSource: (indicatorId: String, source: IndicatorSource?) -> Unit,
) {
    if (active.isEmpty()) {
        Text(
            text = "اول چند اندیکاتور را روشن کنید تا بتوانید یکی را روی دیگری ببرید.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = "هر اندیکاتور می‌تواند به‌جای قیمت، روی خروجی یک اندیکاتور دیگر حساب شود. " +
                "بیشتر از " + IndicatorChain.MAX_DEPTH.toPersianDigits() +
                " حلقه پشت هم پذیرفته نمی‌شود، چون بعد از آن هیچ‌کس نمی‌داند خط آخر چه چیزی را اندازه می‌گیرد.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        refusal?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Sell,
            )
        }
        active.forEach { id ->
            val option = ChartCatalog.INDICATORS.firstOrNull { it.id == id } ?: return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextPrimary,
                )
                if (!IndicatorChain.canChain(id)) {
                    Text(
                        text = "این اندیکاتور بیش از یک ستون از کندل می‌خواند، پس فقط روی خود کندل‌ها حساب می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextDisabled,
                    )
                } else {
                    val chosen = sources[id]
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    ) {
                        BarField.entries.forEach { field ->
                            val source = IndicatorSource.Bars(field)
                            SourcePill(
                                text = field.persianLabel,
                                active = chosen == source || (chosen == null && field == BarField.CLOSE),
                            ) { onSetSource(id, source) }
                        }
                        // Only the *other* switched-on chainable indicators, and only their real
                        // outputs. A self-reference is a one-node cycle and the engine refuses it;
                        // offering it would be the picker inviting the refusal.
                        active.filter { it != id && IndicatorChain.canChain(it) }.forEach { producer ->
                            val producerLabel = ChartCatalog.INDICATORS
                                .firstOrNull { it.id == producer }?.label ?: producer
                            IndicatorChain.outputsOf(producer).forEachIndexed { index, output ->
                                val source = IndicatorSource.Output(
                                    nodeId = producer,
                                    // Null for the main line, which is what a reader means nine
                                    // times in ten and what keeps a saved template short.
                                    output = output.takeIf { index > 0 },
                                )
                                SourcePill(
                                    text = if (index == 0) producerLabel else "$producerLabel · $output",
                                    active = chosen == source,
                                ) { onSetSource(id, source) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The shapes the bars themselves make — the candlestick patterns.
 *
 * ### Why they are not in the indicator list
 *
 * A pattern produces no value per bar, has no lookback and has no pane, so a row for one in
 * `ChartCatalog.INDICATORS` would be a row that answers none of the questions every consumer of
 * that list asks. They are their own list with their own ids and their own section.
 *
 * ### The sentence above them is the point of the section
 *
 * A pattern is a prior and not a signal, and on a five-minute chart a hammer appears several times
 * an hour. A reader who switches on all seventeen gets a wall of arrows with no reading in it and
 * concludes the feature is noise — which it is, used that way. Saying so here is cheaper than
 * having them find out by trading one.
 */
@Composable
internal fun CandlePatternSection(chosen: Set<String>, onToggle: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = "الگو یک نشانه است، نه سیگنال. روی نمودار پنج‌دقیقه‌ای چند بار در ساعت «چکش» پیدا می‌شود و " +
                "بیشترشان ادامهٔ همان روند است. دو یا سه الگو را روشن کنید، نه همه را.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            CandlePatterns.OPTIONS.forEach { option ->
                val on = option.id in chosen
                val tone = Color(option.colour.toULong() shl WORKBENCH_COLOUR_SHIFT)
                Row(
                    modifier = Modifier
                        .clip(CoineProPillShape)
                        .background(
                            if (on) CoineProTint.fill(tone, CoineProColors.Surface) else Color.Transparent,
                        )
                        .border(
                            width = 1.dp,
                            color = if (on) CoineProTint.edge(tone) else CoineProColors.Border,
                            shape = CoineProPillShape,
                        )
                        .clickable { onToggle(option.id) }
                        .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(modifier = Modifier.size(PATTERN_DOT).clip(CircleShape).background(tone))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on) tone else CoineProColors.TextMuted,
                    )
                }
            }
        }
        if (chosen.isNotEmpty()) {
            Text(
                text = chosen.size.toPersianDigits() + " الگو روی نمودار علامت می‌خورد.",
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextDisabled,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

/**
 * Saved indicator sets — and the one line that says what they are not.
 *
 * ### Why this is not a layout
 *
 * A layout is the *apparatus*: the timeframe, the chart type, the price scale, the palette. A
 * template is the *measurements*, and applying one deliberately touches nothing else. A reader who
 * keeps a set called «واگرایی» wants those four studies on whatever they are currently looking at —
 * not to be moved to the four-hour chart of whatever instrument they happened to be on when they
 * saved it. If applying a template changed the timeframe, the two objects would be the same object
 * with two names and the reader would learn to use only one.
 *
 * ### Why there is no cap
 *
 * TradingView's free tier allows exactly one indicator template and charges for the second. There
 * is no product cap here at all — the store's two hundred is a runaway guard, not a price.
 */
@Composable
internal fun IndicatorTemplateSection(
    templates: List<IndicatorTemplate>,
    /** How many studies are switched on right now, so the save button can say what it will keep. */
    activeCount: Int,
    onApply: (IndicatorTemplate) -> Unit,
    onSave: (name: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = "قالب اندیکاتور فقط خود اندیکاتورها، دوره‌ها و منبعشان را نگه می‌دارد. " +
                "بازهٔ زمانی، نوع چارت، مقیاس و رنگ‌ها دست‌نخورده می‌مانند — آن‌ها کار «چیدمان» است.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        if (templates.isEmpty()) {
            Text(
                text = "هنوز قالبی ذخیره نشده.",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextDisabled,
            )
        } else {
            templates.forEach { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CoineProShapes.small)
                        .background(CoineProColors.Surface),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CoineProShapes.small)
                            .clickable { onApply(template) }
                            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.One),
                    ) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = CoineProColors.TextPrimary,
                        )
                        Text(
                            // A prose count, so Persian digits.
                            text = template.indicators.size.toPersianDigits() + " اندیکاتور",
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    Text(
                        text = "حذف",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Sell,
                        modifier = Modifier
                            .clip(CoineProShapes.small)
                            .clickable { onDelete(template.id) }
                            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
                    )
                }
            }
        }
        HorizontalDivider(color = CoineProColors.Border)
        CoineProTextField(
            value = name,
            onValueChange = { name = it },
            label = "نام قالب تازه",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        CoineProPrimaryButton(
            text = "ذخیرهٔ " + activeCount.toPersianDigits() + " اندیکاتور روشن",
            onClick = {
                onSave(name)
                name = ""
            },
            // A template of nothing is a row that applies nothing, and a reader who saved one would
            // find out only by applying it.
            enabled = name.isNotBlank() && activeCount > 0,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One source a chained indicator may read. An outlined pill, like every other exclusive choice. */
@Composable
private fun SourcePill(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(
                if (active) {
                    CoineProTint.fill(CoineProColors.Accent, CoineProColors.Surface)
                } else {
                    Color.Transparent
                },
            )
            .border(
                width = 1.dp,
                color = if (active) CoineProTint.edge(CoineProColors.Accent) else CoineProColors.Border,
                shape = CoineProPillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) CoineProColors.Accent else CoineProColors.TextMuted,
        )
    }
}

/**
 * What each column of a bar is called, in Persian.
 *
 * The three averages keep their formulas beside the name, isolated left-to-right, because that is
 * how a trader recognises them — «HL2» is a name in this trade and «میانگین سقف و کف» is a
 * description of it. Both are given, so neither reader has to guess.
 */
private val BarField.persianLabel: String
    @Composable get() = when (this) {
        BarField.OPEN -> "باز"
        BarField.HIGH -> "سقف"
        BarField.LOW -> "کف"
        BarField.CLOSE -> "بسته"
        BarField.HL2 -> "میانه " + latin("HL2")
        BarField.HLC3 -> "میانگین " + latin("HLC3")
        BarField.OHLC4 -> "میانگین " + latin("OHLC4")
    }

/**
 * A Latin token inside a Persian label, wrapped so it does not reorder the line.
 *
 * `LtrDirection` is a composable and cannot be used inside a string, so the isolate characters are
 * added by hand — the same job `BidiText.isolateLtr` does for a venue name in the provenance row.
 */
private fun latin(text: String): String = "⁦" + text + "⁩"

/** See the same constant in `ChartScreen`: a packed ARGB long sits in the high half of a word. */
private const val WORKBENCH_COLOUR_SHIFT = 32

/** The colour dot beside a pattern's name. */
private val PATTERN_DOT = 8.dp
