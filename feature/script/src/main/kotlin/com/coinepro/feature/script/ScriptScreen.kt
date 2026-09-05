package com.coinepro.feature.script

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.database.SavedScriptEntity
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.designsystem.CoineProSkeleton
import com.coinepro.core.script.ScriptController
import com.coinepro.core.script.ScriptFailure
import com.coinepro.core.script.ScriptEditorState
import com.coinepro.core.script.ScriptInput
import com.coinepro.core.script.ScriptLesson
import com.coinepro.core.script.ScriptLessons
import com.coinepro.core.script.ScriptPreset
import com.coinepro.core.script.ScriptPresets
import com.coinepro.core.script.ScriptReference
import com.coinepro.core.script.ScriptStrategies
import com.coinepro.core.script.ScriptStrategy
import com.coinepro.core.script.toOverlay

/**
 * The NamaScript studio.
 *
 * Four surfaces behind one control, and the order is the reader's likely path through them rather
 * than an alphabet: write, keep, learn, look up.
 *
 * The chart at the top is the whole design. A scripting language whose output you have to go
 * somewhere else to see is one people try once; here the bars the script runs over are the bars on
 * screen, and pressing «اجرا» redraws them in place. That is also why this screen takes its series
 * from the caller rather than fetching its own — the preview has to be the *same* data as the chart
 * the reader came from, not a second copy that could disagree with it.
 */
@Composable
fun ScriptScreen(
    controller: ScriptController,
    symbol: String,
    series: CandleSeries,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val saved by controller.saved.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ScriptTab.EDITOR) }
    // Whether this composition has already decided where to land. One shot, and saveable, so a
    // rotation does not throw the reader back to the library out of a script they are editing.
    var landed by rememberSaveable { mutableStateOf(false) }

    // The controller runs against whatever the chart is showing. Re-running on a new series rather
    // than leaving the old drawing on the new bars: an overlay computed from yesterday's candles
    // drawn over today's is a picture of something that never happened.
    LaunchedEffect(series) {
        controller.setSeries(series)
        if (state.source.isNotBlank() && !series.isEmpty) controller.run()
    }

    // **A first visit lands in the library, not in the editor.**
    //
    // It used to land on a blank editor, on the reasoning that an empty screen is worse than an
    // empty script. That was half right and it hid the whole point of the tab: eleven ready-made
    // strategies ship in this build and the owner opened the studio, saw an empty editor, and
    // reported that there were none. They were one tab across, and a reader with nothing written
    // has no reason to look for a tab.
    //
    // A reader who has something — a saved script, or source restored from a previous session —
    // still lands in the editor, because for them the library is a detour.
    LaunchedEffect(Unit) {
        if (!landed) {
            landed = true
            if (state.source.isBlank() && saved.isEmpty()) tab = ScriptTab.LIBRARY
        }
        // The blank script is still prepared, so the editor is ready the moment they switch to it
        // — an empty editor is the hardest screen in any programming product, and it is now the
        // second thing they see rather than the first.
        if (state.source.isBlank()) controller.openBlank()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage),
    ) {
        Header(symbol = symbol, state = state)
        CoineProTeachingStrip(TeachingSurface.SCRIPT)
        CoineProSegmentedControl(
            options = ScriptTab.entries.map { it to it.label },
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        )
        when (tab) {
            ScriptTab.EDITOR -> EditorTab(
                controller = controller,
                state = state,
                series = series,
                loading = loading,
            )
            ScriptTab.LIBRARY -> LibraryTab(
                saved = saved,
                openId = state.savedId,
                onOpen = controller::open,
                onDelete = controller::delete,
                onOpenPreset = {
                    controller.openPreset(it)
                    tab = ScriptTab.EDITOR
                },
                onOpenStrategy = { strategy, name ->
                    controller.openStrategy(strategy, name)
                    tab = ScriptTab.EDITOR
                },
                onNew = {
                    controller.openBlank()
                    tab = ScriptTab.EDITOR
                },
            )
            ScriptTab.LESSONS -> LessonsTab(
                onTryExample = { example ->
                    controller.edit(example)
                    controller.run()
                    tab = ScriptTab.EDITOR
                },
            )
            ScriptTab.REFERENCE -> ReferenceTab(
                onInsert = { snippet ->
                    controller.edit(state.source.trimEnd() + "\n" + snippet)
                    tab = ScriptTab.EDITOR
                },
            )
        }
    }
}

private enum class ScriptTab(val label: String) {
    EDITOR("ویرایشگر"),
    LIBRARY("کتابخانه"),
    LESSONS("آموزش"),
    REFERENCE("مرجع"),
}

@Composable
private fun Header(symbol: String, state: ScriptEditorState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "نما اسکریپت",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = state.name.ifBlank { "اسکریپت ذخیره‌نشده" } + " · " + symbol,
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/* ------------------------------------------------------------------------ editor */

@Composable
private fun EditorTab(
    controller: ScriptController,
    state: ScriptEditorState,
    series: CandleSeries,
    loading: Boolean,
) {
    // Named for the pane the script's own-pane plots land in, so a reader with three scripts saved
    // can tell which strip belongs to which.
    val overlay = remember(state.result, series, state.name) {
        state.result?.toOverlay(series, state.name.ifBlank { "اسکریپت" })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .background(CoineProColors.Terminal, CoineProShapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading && series.isEmpty -> CoineProSkeleton(
                        modifier = Modifier.fillMaxWidth().padding(CoineProSpacing.Two),
                        height = PREVIEW_HEIGHT - CoineProSpacing.Two * 2,
                        shape = CoineProShapes.small,
                    )
                    series.isEmpty -> Text(
                        "کندلی برای اجرا نیست",
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                    else -> CoineProChart(
                        series = series,
                        modifier = Modifier.fillMaxSize(),
                        decoration = ChartDecoration(
                            overlays = overlay?.overlays.orEmpty(),
                            levels = overlay?.levels.orEmpty(),
                            markers = overlay?.markers.orEmpty(),
                            panes = listOfNotNull(overlay?.pane),
                            signal = overlay?.signal,
                            // The volume pane would compete with the script's own for the little
                            // height a preview has, and a script that wanted volume plotted it.
                            showVolume = false,
                        ),
                    )
                }
            }
        }

        item { CodeField(source = state.source, onChange = controller::edit) }

        state.failure?.let { failure ->
            item { FailureCard(failure = failure) }
        }

        if (state.dirty && state.result != null) {
            item {
                Text(
                    "نمودار هنوز نتیجه‌ی اجرای قبلی را نشان می‌دهد",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                CoineProPrimaryButton(
                    text = if (state.running) "در حال اجرا…" else "اجرا",
                    onClick = controller::run,
                    modifier = Modifier.weight(1f),
                    enabled = !state.running && state.source.isNotBlank() && !series.isEmpty,
                )
                // No disabled state on the neutral pill, so a blank script simply has no save
                // button rather than a dead one — there is nothing to explain about saving nothing.
                if (state.canSave) {
                    CoineProSecondaryButton(
                        text = "ذخیره",
                        onClick = controller::save,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { NameField(name = state.name, onChange = controller::rename) }

        val inputs = state.result?.inputs.orEmpty()
        if (inputs.isNotEmpty()) {
            item {
                SectionTitle("ورودی‌ها", "${inputs.size.toPersianDigits()} ورودی")
            }
            items(inputs, key = ScriptInput::name) { input ->
                InputRow(
                    input = input,
                    onChange = { controller.setInput(input.name, it) },
                )
            }
        }

        val setup = state.result?.setup
        if (setup != null) {
            item { SetupCard(buy = setup.buy, entry = setup.entry, stop = setup.stop, target = setup.target, riskReward = setup.riskReward) }
        }

        val log = state.result?.log.orEmpty()
        if (log.isNotEmpty()) {
            item { SectionTitle("خروجی", null) }
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    log.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
                    }
                }
            }
        }

        if (state.result?.ok == true && state.result?.isEmpty == true) {
            item {
                Text(
                    "اسکریپت بدون خطا اجرا شد ولی چیزی رسم نکرد. برای دیدن نتیجه از plot یا marker استفاده کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * The code field.
 *
 * Left-to-right and monospace, because the source is Latin identifiers and operators even when the
 * strings inside it are Persian — laid out right-to-left, `close - atr * 2` reorders on screen into
 * something that is not what will run. The Persian text inside quotes still shapes correctly; only
 * the *paragraph* direction is forced.
 *
 * Autocorrect and auto-capitalisation are off. A keyboard that helpfully capitalises `close` writes
 * a script that does not compile, and the reader is left looking at an error they did not type.
 */
@Composable
private fun CodeField(source: String, onChange: (String) -> Unit) {
    LtrDirection {
        BasicTextField(
            value = source,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CODE_MIN_HEIGHT)
                .background(CoineProColors.Terminal, CoineProShapes.medium)
                .border(1.dp, CoineProColors.Border, CoineProShapes.medium)
                .padding(CoineProSpacing.OneHalf),
            textStyle = LocalTextStyle.current.merge(
                TextStyle(
                    color = CoineProColors.TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = CODE_TEXT_SIZE,
                    lineHeight = CODE_LINE_HEIGHT,
                    textDirection = TextDirection.Ltr,
                ),
            ),
            cursorBrush = SolidColor(CoineProColors.Gold),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
            ),
        )
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = name,
        onValueChange = onChange,
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .background(CoineProColors.Surface, CoineProShapes.medium)
            .border(1.dp, CoineProColors.Border, CoineProShapes.medium)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        textStyle = LocalTextStyle.current.merge(TextStyle(color = CoineProColors.TextPrimary)),
        cursorBrush = SolidColor(CoineProColors.Gold),
        decorationBox = { field ->
            if (name.isEmpty()) {
                Text(
                    "نام اسکریپت",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextMuted,
                )
            }
            field()
        },
    )
}

/**
 * Why the run stopped, and where.
 *
 * The line and column are printed rather than only used to move a caret: a phone keyboard covers
 * half the field, and «خط ۴» is what lets a reader find the place after they dismiss it. Persian
 * digits, because these are counts in prose and not market figures.
 */
@Composable
private fun FailureCard(failure: ScriptFailure) {
    // The interpreter carries both languages and knows nothing about the app's; the screen picks.
    val language = AppLanguage.fromTag(LocalConfiguration.current.locales[0].language)
    val position = if (language == AppLanguage.ENGLISH) {
        failure.line.toString() to failure.column.toString()
    } else {
        failure.line.toPersianDigits() to failure.column.toPersianDigits()
    }
    CoineProCard(modifier = Modifier.fillMaxWidth(), accent = CoineProColors.Sell) {
        Text(
            text = if (failure.line > 0) {
                stringResource(R.string.script_failure_at, position.first, position.second)
            } else {
                stringResource(R.string.script_failure)
            },
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.Sell,
            fontWeight = FontWeight.Bold,
        )
        Text(failure.text(language), style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextPrimary)
    }
}

/**
 * One `input(...)` as a control.
 *
 * A slider where the script declared both bounds and a plain readout where it did not. An unbounded
 * slider has no meaning — it would have to invent a range, and the invented range is the one that
 * makes the indicator useless at one end.
 */
@Composable
private fun InputRow(input: ScriptInput, onChange: (Double) -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(CoineProSpacing.OneHalf),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(input.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                MarketNumberFormatter.priceAuto(input.value),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.Gold,
                fontWeight = FontWeight.Bold,
            )
        }
        val low = input.minimum
        val high = input.maximum
        if (low != null && high != null && high > low) {
            Slider(
                value = input.value.toFloat().coerceIn(low.toFloat(), high.toFloat()),
                onValueChange = { onChange(it.toDouble()) },
                valueRange = low.toFloat()..high.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "این ورودی بازه‌ای اعلام نکرده؛ مقدارش را در خود کد تغییر دهید.",
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

@Composable
private fun SetupCard(
    buy: Boolean,
    entry: Double,
    stop: Double,
    target: Double?,
    riskReward: Double?,
) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        accent = if (buy) CoineProColors.Buy else CoineProColors.Sell,
    ) {
        Text(
            if (buy) "ستاپ خرید" else "ستاپ فروش",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (buy) CoineProColors.Buy else CoineProColors.Sell,
        )
        SetupRow("ورود", entry)
        SetupRow("حد ضرر", stop)
        target?.let { SetupRow("هدف", it) }
        Text(
            text = riskReward?.let { "ریسک به بازده: " + MarketNumberFormatter.price(it, 2) }
                ?: "اسکریپت هدفی اعلام نکرده، پس نسبت ریسک به بازده محاسبه نمی‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
    }
}

@Composable
private fun SetupRow(label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
        Text(
            MarketNumberFormatter.priceAuto(value),
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
        )
    }
}

/* ------------------------------------------------------------------------ library */

@Composable
private fun LibraryTab(
    saved: List<SavedScriptEntity>,
    openId: Long?,
    onOpen: (SavedScriptEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenPreset: (ScriptPreset) -> Unit,
    onOpenStrategy: (ScriptStrategy, String) -> Unit,
    onNew: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        item {
            CoineProSecondaryButton(
                text = "اسکریپت تازه",
                onClick = onNew,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            SectionTitle(
                "اسکریپت‌های من",
                if (saved.isEmpty()) "هنوز چیزی ذخیره نکرده‌اید" else "${saved.size.toPersianDigits()} اسکریپت",
            )
        }
        items(saved, key = SavedScriptEntity::id) { script ->
            CoineProCard(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(script) },
                accent = if (script.id == openId) CoineProColors.Gold else null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(script.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        // A saved script remembers what it was started from, and that may be a
                        // preset or one of the shipped strategies — the two id spaces are separate
                        // and a saved copy of a strategy should not lose its lineage.
                        val origin = script.presetId?.let { id ->
                            ScriptPresets.byId(id)?.title
                                ?: ScriptStrategies.byId(id)?.let { stringResource(nameOf(it)) }
                        }
                        origin?.let {
                            Text(
                                stringResource(R.string.script_strategy_based_on, it),
                                style = MaterialTheme.typography.labelSmall,
                                color = CoineProColors.TextMuted,
                            )
                        }
                    }
                    TextButton(onClick = { onDelete(script.id) }) {
                        Text("حذف", color = CoineProColors.Sell, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.script_strategies_title),
                stringResource(R.string.script_strategies_count, ScriptStrategies.ALL.size.toPersianDigits()),
            )
        }
        item {
            // Said once, at the top of the list, rather than on every card. Eleven copies of the
            // same sentence is wallpaper, and wallpaper is not read.
            Text(
                stringResource(R.string.script_strategies_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        items(ScriptStrategies.ALL, key = ScriptStrategy::id) { strategy ->
            val name = stringResource(nameOf(strategy))
            CoineProCard(modifier = Modifier.fillMaxWidth().clickable { onOpenStrategy(strategy, name) }) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(descriptionOf(strategy)),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                )
            }
        }
        item {
            SectionTitle("آموزشی", "${ScriptPresets.ALL.size.toPersianDigits()} اسکریپت آماده")
        }
        items(ScriptPresets.ALL, key = ScriptPreset::id) { preset ->
            CoineProCard(modifier = Modifier.fillMaxWidth().clickable { onOpenPreset(preset) }) {
                Text(preset.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(preset.summary, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
                Text(
                    "می‌آموزد: ${preset.teaches}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------ lessons */

@Composable
private fun LessonsTab(onTryExample: (String) -> Unit) {
    // The first lesson is open on arrival. A wall of twelve collapsed titles is a table of
    // contents, and a table of contents is not a course.
    var expanded by rememberSaveable { mutableStateOf(ScriptLessons.ALL.first().id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        itemsIndexed(ScriptLessons.ALL, key = { _, lesson -> lesson.id }) { index, lesson ->
            LessonCard(
                index = index,
                lesson = lesson,
                expanded = expanded == lesson.id,
                onToggle = { expanded = if (expanded == lesson.id) "" else lesson.id },
                onTryExample = onTryExample,
            )
        }
    }
}

@Composable
private fun LessonCard(
    index: Int,
    lesson: ScriptLesson,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTryExample: (String) -> Unit,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Text(
            "${(index + 1).toPersianDigits()}. ${lesson.title}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!expanded) {
            Text(
                lesson.body.first(),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                maxLines = 2,
            )
            return@CoineProCard
        }
        lesson.body.forEach { paragraph ->
            Text(
                paragraph,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
                modifier = Modifier.padding(top = CoineProSpacing.One),
            )
        }
        lesson.example?.let { example ->
            Box(modifier = Modifier.padding(top = CoineProSpacing.One)) { Snippet(example) }
            CoineProSecondaryButton(
                text = "اجرا در ویرایشگر",
                onClick = { onTryExample(example) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CoineProSpacing.One),
            )
        }
        lesson.takeaway?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.Gold,
                modifier = Modifier.padding(top = CoineProSpacing.One),
            )
        }
    }
}

/* ------------------------------------------------------------------------ reference */

@Composable
private fun ReferenceTab(onInsert: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        item { SectionTitle("سری‌های آماده", "بدون محاسبه در دسترس‌اند") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                items(ScriptReference.SERIES) { function ->
                    Box(
                        modifier = Modifier
                            .background(CoineProColors.Surface, CoineProShapes.medium)
                            .border(1.dp, CoineProColors.Border, CoineProShapes.medium)
                            .clickable { onInsert("plot(${function.signature})") }
                            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
                    ) {
                        LtrDirection {
                            Text(
                                function.signature,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = CoineProColors.Gold,
                            )
                        }
                    }
                }
            }
        }
        ScriptReference.GROUPS.forEach { group ->
            item { SectionTitle(group.title, "${group.functions.size.toPersianDigits()} تابع") }
            items(group.functions, key = { it.signature }) { function ->
                CoineProCard(modifier = Modifier.fillMaxWidth().clickable { onInsert(function.signature) }) {
                    Snippet(function.signature)
                    Text(
                        function.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextSecondary,
                        modifier = Modifier.padding(top = CoineProSpacing.Half),
                    )
                    Text(
                        "خروجی: ${function.returns}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
        }
        item { SectionTitle("رنگ‌ها", "${ScriptReference.COLOUR_NAMES.size.toPersianDigits()} رنگ") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                items(ScriptReference.COLOUR_NAMES) { name ->
                    Box(
                        modifier = Modifier
                            .background(CoineProColors.Surface, CoineProShapes.medium)
                            .border(1.dp, CoineProColors.Border, CoineProShapes.medium)
                            .clickable { onInsert("plot(close, color = $name)") }
                            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
                    ) {
                        LtrDirection {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                color = CoineProColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------ shared */

@Composable
private fun SectionTitle(title: String, subtitle: String?) {
    Column(modifier = Modifier.padding(top = CoineProSpacing.One)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
        }
    }
}

/** A run of source, laid out left-to-right like the editor itself. */
@Composable
private fun Snippet(text: String) {
    LtrDirection {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                textDirection = TextDirection.Ltr,
            ),
            color = CoineProColors.Gold,
            modifier = Modifier
                .fillMaxWidth()
                .background(CoineProColors.Terminal, CoineProShapes.medium)
                .padding(CoineProSpacing.One),
        )
    }
}

private val PREVIEW_HEIGHT = 240.dp
private val CODE_MIN_HEIGHT = 200.dp
private val CODE_TEXT_SIZE = 13.sp
private val CODE_LINE_HEIGHT = 20.sp
