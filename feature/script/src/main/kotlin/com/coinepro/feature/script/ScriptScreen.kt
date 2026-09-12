package com.coinepro.feature.script

import androidx.compose.runtime.ReadOnlyComposable
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.coinepro.core.script.ScriptStrategyReport
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProTextStyles
import androidx.compose.ui.text.TextStyle
import com.coinepro.core.script.ScriptReferenceEn
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import com.coinepro.core.designsystem.proseDigits
import com.coinepro.core.database.SavedScriptEntity
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.inEnglish
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
import com.coinepro.core.script.text
import com.coinepro.core.script.ScriptFailure
import com.coinepro.core.script.ScriptEditorState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.coinepro.core.script.ScriptInputKind
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
    /**
     * Put the script in the editor on the **main chart** — «Add to chart» (4.73.0, run I item 0).
     *
     * The button this feeds is the whole point of the studio: everything a reader wants from a
     * script they wrote happens after it. Null where there is no chart behind this screen, and the
     * button is then absent rather than dead.
     *
     * Answers the instance id it created or updated, so the screen can say «Update on chart» the
     * next time and the caller can open that instance's settings.
     */
    onAddToChart: ((name: String, source: String, overrides: Map<String, Double>) -> String)? = null,
    /**
     * The **names** of the scripts already on the chart, so the button knows which word to use.
     *
     * Names rather than instance ids, because that is the key the caller matches on: adding a
     * script whose name is already on the chart *replaces* it rather than stacking a second copy,
     * which is what a reader iterating in the editor means every time. Two deliberately different
     * copies get two names, and `ChartScript.ordinal` numbers them when they do not.
     */
    onChart: Set<String> = emptySet(),
    /**
     * The **main chart**, for the split view's right half.
     *
     * When this is supplied the split shows the reader's actual chart — their indicators, their
     * drawings, their timeframe — and the studio's two-hundred-bar preview becomes a *sandbox* one
     * chip away. That inversion is run I item 0's second sentence and it is the difference between
     * an editor that previews and an editor that edits the thing you are looking at.
     */
    mainChart: (@Composable (Modifier) -> Unit)? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val saved by controller.saved.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(ScriptTab.EDITOR) }
    // The studio's language, read once: the presets, the blank script and the snippet chips all
    // choose their words with it, and the code inside them travels to the reader's chart.
    val english = inEnglish()
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
        if (state.source.isBlank()) controller.openBlank(english)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage),
    ) {
        Header(symbol = symbol, state = state)
        CoineProTeachingStrip(TeachingSurface.SCRIPT)
        CoineProSegmentedControl(
            options = ScriptTab.entries.map { it to stringResource(it.labelRes) },
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
                onAddToChart = onAddToChart,
                onChart = onChart,
                mainChart = mainChart,
            )
            ScriptTab.LIBRARY -> LibraryTab(
                saved = saved,
                english = english,
                // «Add to chart» from a card, without opening the editor first: a reader picking a
                // ready-made study out of the library wants it *on the chart*, and making them
                // route through Run to get there was the studio pretending to be an IDE.
                onAddToChart = onAddToChart?.let { add ->
                    { name, source -> add(name, source, emptyMap()); Unit }
                },
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
                    controller.openBlank(english)
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

private enum class ScriptTab(@StringRes val labelRes: Int) {
    EDITOR(R.string.script_tab_editor),
    LIBRARY(R.string.script_tab_library),
    LESSONS(R.string.script_tab_lessons),
    REFERENCE(R.string.script_tab_reference),
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
            stringResource(R.string.script_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = state.name.ifBlank { stringResource(R.string.script_unsaved) } + " · " + symbol,
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
    onAddToChart: ((String, String, Map<String, Double>) -> String)? = null,
    onChart: Set<String> = emptySet(),
    mainChart: (@Composable (Modifier) -> Unit)? = null,
) = BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    // Named for the pane the script's own-pane plots land in, so a reader with three scripts saved
    // can tell which strip belongs to which.
    // The fallback name is read here, in composition, and handed to the memo — a resource lookup
    // inside `remember` would be a composable call in a non-composable lambda.
    val untitled = stringResource(R.string.script_untitled)
    val overlay = remember(state.result, series, state.name, untitled) {
        state.result?.toOverlay(series, state.name.ifBlank { untitled })
    }

    /** The chart the script draws on: a strip above the code on a phone, a column beside it on a tablet. */
    val preview: @Composable (Modifier) -> Unit = { previewModifier ->
            Box(
                modifier = previewModifier
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
                        stringResource(R.string.script_no_candles),
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
                            // The script's labels, lines and boxes, as the reader's own drawings.
                            drawings = overlay?.drawings.orEmpty(),
                            // The volume pane would compete with the script's own for the little
                            // height a preview has, and a script that wanted volume plotted it.
                            showVolume = false,
                        ),
                    )
                }
            }
    }

    // **Code | chart, fifty-fifty — and a toggle, decided by the pane's own width.**
    //
    // The preview is the same component either way; only where it sits changes. What changed in
    // 4.72.0 is who decides: it used to ask `showsTwoPanes`, which is a question about the
    // *screen*, and this screen is often a 400-point side panel on a tablet — where a fifty-fifty
    // split leaves the editor two hundred points and `plot(rsi, ti` on a line. The floor is
    // [EDITOR_MIN]; a split needs two of those with the gutter between them, and where that does
    // not fit the split is not offered at all rather than offered and ruinous.
    //
    // Above the floor it is the reader's: somebody writing a long script wants the whole width for
    // a minute and the preview back afterwards (run H item 4).
    val fits = maxWidth >= EDITOR_MIN * 2 + CoineProSpacing.Gutter
    var splitOn by rememberSaveable(fits) { mutableStateOf(fits) }
    val split = fits && splitOn
    /** Whether the split's other half is the rehearsal chart rather than the reader's own. */
    var sandbox by rememberSaveable { mutableStateOf(false) }
    val editorItems: LazyListScope.() -> Unit = {
        if (!split) item { preview(Modifier.fillMaxWidth().height(PREVIEW_HEIGHT)) }

        item { CodeField(source = state.source, onChange = controller::edit, failure = state.failure) }

        // Snippets: a working script in one tap, for a reader who has the idea and not the syntax.
        item { SnippetRow(onInsert = { snippet -> controller.edit((state.source.trimEnd() + "\n\n" + snippet).trimStart()) }) }

        if (fits) {
            item {
                CoineProChipRow(
                    options = listOf(
                        CoineProChip(id = SPLIT_ON, label = stringResource(R.string.script_split_both)),
                        CoineProChip(id = SPLIT_OFF, label = stringResource(R.string.script_split_code)),
                    ),
                    selectedId = if (splitOn) SPLIT_ON else SPLIT_OFF,
                    onSelect = { id -> splitOn = id == SPLIT_ON },
                    compact = true,
                    neutral = true,
                )
            }
        }

        // **Which chart the other half is** — the reader's, or the sandbox (run I item 0.2).
        //
        // The default is the main chart, which is the inversion this run is about: the studio's
        // two-hundred-bar preview was the *destination* before, and it is a rehearsal room. It is
        // still here, one chip away, because a script that throws or draws nonsense is better
        // rehearsed somewhere that is not the chart the reader is trading from.
        if (split && mainChart != null) {
            item {
                CoineProChipRow(
                    options = listOf(
                        CoineProChip(id = TARGET_MAIN, label = stringResource(R.string.script_target_chart)),
                        CoineProChip(id = TARGET_SANDBOX, label = stringResource(R.string.script_target_sandbox)),
                    ),
                    selectedId = if (sandbox) TARGET_SANDBOX else TARGET_MAIN,
                    onSelect = { id -> sandbox = id == TARGET_SANDBOX },
                    compact = true,
                    neutral = true,
                )
            }
        }

        state.failure?.let { failure ->
            item { FailureCard(failure = failure) }
        }

        if (state.dirty && state.result != null) {
            item {
                Text(
                    stringResource(R.string.script_stale),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }

        // **«Add to chart»** — the button the whole studio points at (run I item 0.2).
        //
        // Above Run and full width, because it is the *destination*: Run is «show me what this
        // does», and this is «make it one of my indicators». A reader who has already added this
        // script sees «Update on chart» instead, which hot-swaps the instance and keeps its inputs,
        // its colour and its place in the legend — see `ChartController.setScriptSource`.
        if (onAddToChart != null) {
            item {
                val added = state.name.isNotBlank() && state.name in onChart
                CoineProPrimaryButton(
                    text = stringResource(
                        if (added) R.string.script_update_on_chart else R.string.script_add_to_chart,
                    ),
                    onClick = {
                        onAddToChart(
                            state.name.ifBlank { untitled },
                            state.source,
                            state.overrides,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "script-add-to-chart" },
                    enabled = state.source.isNotBlank(),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                CoineProPrimaryButton(
                    text = stringResource(if (state.running) R.string.script_running else R.string.script_run),
                    onClick = controller::run,
                    modifier = Modifier.weight(1f),
                    enabled = !state.running && state.source.isNotBlank() && !series.isEmpty,
                )
                // No disabled state on the neutral pill, so a blank script simply has no save
                // button rather than a dead one — there is nothing to explain about saving nothing.
                if (state.canSave) {
                    CoineProSecondaryButton(
                        text = stringResource(R.string.script_save),
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
                SectionTitle(
            stringResource(R.string.script_inputs),
            stringResource(R.string.script_inputs_count, inputs.size.proseDigits()),
        )
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

        val strategy = state.result?.strategy
        if (strategy != null) {
            item { StrategyCard(report = strategy) }
        }

        // The console: what the script printed, and what the run cost. The timing line is there
        // on every successful run, so a reader watching a script slow down sees it slow down.
        val log = state.result?.log.orEmpty()
        val result = state.result
        if (log.isNotEmpty() || (result != null && result.ok)) {
            item { SectionTitle(stringResource(R.string.script_console), null) }
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    log.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
                    }
                    if (result != null && result.ok) {
                        Text(
                            consoleTiming(result.elapsedMillis, state.incremental, series.size),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = CoineProColors.TextMuted,
                            modifier = Modifier.semantics { contentDescription = "script-console-timing" },
                        )
                    }
                }
            }
        }

        if (state.result?.ok == true && state.result?.isEmpty == true) {
            item {
                Text(
                    stringResource(R.string.script_drew_nothing),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }

    val padding = PaddingValues(
        start = CoineProSpacing.Gutter,
        end = CoineProSpacing.Gutter,
        bottom = CoineProSpacing.Six,
    )
    if (split) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                content = editorItems,
            )
            val half = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(end = CoineProSpacing.Gutter, bottom = CoineProSpacing.Gutter)
            val other = mainChart.takeIf { !sandbox } ?: preview
            other(half)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            content = editorItems,
        )
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
private fun CodeField(source: String, onChange: (String) -> Unit, failure: ScriptFailure? = null) {
    // The text with its cursor. The controller owns the string; this owns where the caret is,
    // which is what completion and bracket closing need and what a plain `String` cannot carry.
    // The caret starts at the end, where a reader continues a script: the completion strip
    // reads the word before the caret, so a script that opens ending in «ta.sm» offers `ta.sma`
    // at once rather than after a tap into the field.
    val minimapInk = CoineProColors.TextDisabled
    var value by remember { mutableStateOf(TextFieldValue(source, TextRange(source.length))) }
    if (value.text != source) value = value.copy(text = source, selection = TextRange(source.length.coerceAtMost(value.selection.end)))

    val completions = remember(value) { completionsFor(value) }
    val squiggle = CoineProColors.Sell
    val lines = remember(source) { source.split("\n") }
    val lineCount = lines.size
    // The code scrolls sideways rather than wrapping (run H item 4). A wrapped line of NamaScript
    // is three lines that look like three statements — «length =» / «input(14,» / «title =» was
    // what the owner read in the 150 dp panel — and the numbers down the side then stop counting
    // the file. One state, shared by the field and the minimap, so they cannot disagree.
    val codeScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxWidth()) {
        LtrDirection {
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    val closed = autoClose(value, next)
                    value = closed
                    if (closed.text != source) onChange(closed.text)
                },
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
                // Colour by token and a red underline on the failing one — see `NamaSyntax`.
                visualTransformation = remember(failure, squiggle) {
                    NamaSyntaxTransformation(failure?.line, failure?.column, squiggle)
                },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                decorationBox = { field ->
                    Row {
                        // Line numbers: what a diagnostic's «line 7» points at. Latin digits,
                        // because the code beside them is Latin and the two columns are read as one.
                        Column(modifier = Modifier.padding(end = CoineProSpacing.One)) {
                            for (line in 1..lineCount) {
                                Text(
                                    text = line.toString(),
                                    style = TextStyle(
                                        color = CoineProColors.TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = CODE_TEXT_SIZE,
                                        lineHeight = CODE_LINE_HEIGHT,
                                    ),
                                )
                            }
                        }
                        // **How the code stops wrapping.** A horizontally scrolling parent hands
                        // its child an unbounded width, so the field lays every line out at its
                        // full length and there is no wrap point to break at. `softWrap` is not a
                        // parameter of this overload; the constraint is, and it is the same answer.
                        Box(modifier = Modifier.weight(1f).horizontalScroll(codeScroll)) { field() }
                        // The minimap: one bar per line, as long as the line is. It is not a
                        // thumbnail of the text — at this size a thumbnail is grey noise — it is
                        // the *shape* of the file, which is what a reader actually navigates by:
                        // where the inputs stop, where the plotting starts, which block is long.
                        Canvas(
                            modifier = Modifier
                                .width(MINIMAP_WIDTH)
                                .heightIn(min = CODE_MIN_HEIGHT)
                                .fillMaxHeight(),
                        ) {
                            val step = size.height / lineCount.coerceAtLeast(1)
                            val longest = lines.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
                            lines.forEachIndexed { index, line ->
                                if (line.isBlank()) return@forEachIndexed
                                val share = line.trimEnd().length.toFloat() / longest
                                drawRect(
                                    color = minimapInk,
                                    topLeft = Offset(0f, index * step),
                                    size = Size(size.width * share, (step * MINIMAP_BAR).coerceAtLeast(1f)),
                                )
                            }
                        }
                    }
                },
            )
        }
        // The completions for the word under the caret: the reference's own names, so the strip
        // and the reference tab can never disagree about what exists. Tapping one finishes the
        // word and, for a function, opens its parenthesis.
        if (completions.isNotEmpty()) {
            LtrDirection {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
                ) {
                    items(completions, key = { it }) { name ->
                        // The name in gold and, for a function, its arguments beside it in the
                        // muted tone: the signature is the reason to pick one chip over the next.
                        val signature = signatureFor(name)
                        Box(
                            modifier = Modifier
                                .background(CoineProColors.Surface, CoineProShapes.small)
                                .border(1.dp, CoineProColors.Border, CoineProShapes.small)
                                .clickable {
                                    val completed = complete(value, name)
                                    value = completed
                                    onChange(completed.text)
                                }
                                .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
                        ) {
                            Text(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = CoineProColors.Gold)) { append(name) }
                                    val rest = signature.removePrefix(name)
                                    if (rest.isNotEmpty()) withStyle(SpanStyle(color = CoineProColors.TextMuted)) { append(rest) }
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The identifier (with its namespace) ending at the caret, or null when the caret is not on one. */
internal fun wordBeforeCaret(value: TextFieldValue): Pair<Int, String>? {
    if (!value.selection.collapsed) return null
    val end = value.selection.end
    var start = end
    while (start > 0 && (value.text[start - 1].isLetterOrDigit() || value.text[start - 1] == '_' || value.text[start - 1] == '.')) start--
    val word = value.text.substring(start, end)
    return if (word.length >= 2 && word.first().isLetter()) start to word else null
}

internal fun completionsFor(value: TextFieldValue): List<String> {
    val (_, word) = wordBeforeCaret(value) ?: return emptyList()
    return COMPLETION_NAMES.filter { it.startsWith(word) && it != word }.take(COMPLETION_LIMIT)
}

/** [name] put in place of the word under the caret; a function gets its «(» and the caret inside it. */
internal fun complete(value: TextFieldValue, name: String): TextFieldValue {
    val (start, word) = wordBeforeCaret(value) ?: return value
    val isFunction = name !in SERIES_NAMES
    val insertion = if (isFunction) "$name()" else name
    val text = value.text.substring(0, start) + insertion + value.text.substring(start + word.length)
    val caret = start + insertion.length - if (isFunction) 1 else 0
    return TextFieldValue(text, TextRange(caret))
}

/**
 * A typed «(» or «[» brings its closing half with it, the caret between them; a typed «)» over an
 * existing «)» steps over it. Only for a single typed character with a collapsed caret — a paste,
 * a deletion or a selection is left exactly as the keyboard sent it.
 */
internal fun autoClose(before: TextFieldValue, after: TextFieldValue): TextFieldValue {
    if (!after.selection.collapsed || after.text.length != before.text.length + 1) return after
    val caret = after.selection.end
    if (caret == 0 || after.text.substring(0, caret - 1) != before.text.substring(0, caret - 1)) return after
    val typed = after.text[caret - 1]
    val closing = when (typed) {
        '(' -> ')'
        '[' -> ']'
        '"' -> '"'
        else -> null
    }
    val follows = after.text.getOrNull(caret)
    return when {
        typed == ')' && follows == ')' -> TextFieldValue(before.text, TextRange(caret))
        typed == ']' && follows == ']' -> TextFieldValue(before.text, TextRange(caret))
        closing != null && (follows == null || follows == ' ' || follows == ')' || follows == ',' || follows == '\n') ->
            TextFieldValue(after.text.substring(0, caret) + closing + after.text.substring(caret), TextRange(caret))
        else -> after
    }
}

private val SERIES_NAMES: Set<String> = ScriptReference.SERIES.map { it.signature.substringBefore('(') }.toSet()

private val COMPLETION_NAMES: List<String> = (
    ScriptReference.SERIES.map { it.signature.substringBefore('(') } +
        ScriptReference.ALL_GROUPS.flatMap { group -> group.functions.map { it.signature.substringBefore('(').trim() } } +
        ScriptReference.COLOUR_NAMES
    ).distinct().sorted()

private const val COMPLETION_LIMIT = 8

/** The reference's signature for a completion — `ta.sma(close, 20)` for `ta.sma` — or the name itself. */
internal fun signatureFor(name: String): String = SIGNATURES[name] ?: name

private val SIGNATURES: Map<String, String> = (ScriptReference.SERIES + ScriptReference.ALL_GROUPS.flatMap { it.functions })
    .associate { it.signature.substringBefore('(').trim() to it.signature }

/**
 * A working script per idea; tapped in when the reader has the idea and not yet the syntax.
 *
 * **Both halves are resources** since 4.73.0 (run I item 4): the chip's own title, and the source
 * it types in. The source, because a snippet is not a fixed text — it contains `title = "تند"`, and
 * an English reader who taps «Two moving averages» and gets a plot labelled «تند» has been handed a
 * script they cannot read in a language they did not choose. The code is the same in both; the
 * words inside its strings are not, and those words end up on their chart.
 */
data class ScriptSnippet(@StringRes val titleRes: Int, @StringRes val sourceRes: Int)

val SNIPPETS: List<ScriptSnippet> = listOf(
    ScriptSnippet(R.string.script_snippet_cross, R.string.script_snippet_cross_source),
    ScriptSnippet(R.string.script_snippet_rsi, R.string.script_snippet_rsi_source),
    ScriptSnippet(R.string.script_snippet_label, R.string.script_snippet_label_source),
    ScriptSnippet(R.string.script_snippet_strategy, R.string.script_snippet_strategy_source),
)

/**
 * «اجرا در ۱۲ ms · ۲٬۰۰۰ کندل · فقط دنباله» / `Ran in 12 ms · 2,000 bars · tail only`.
 *
 * Latin digits for the milliseconds — that is a measurement — and a prose count for the bars, in
 * the digits of whichever language the panel is drawn in.
 */
@Composable
@ReadOnlyComposable
internal fun consoleTiming(elapsedMillis: Long, incremental: Boolean, bars: Int): String =
    stringResource(R.string.script_ran_in, elapsedMillis.toString(), bars.proseDigits()) +
        if (incremental) stringResource(R.string.script_ran_tail) else ""

@Composable
private fun SnippetRow(onInsert: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(SNIPPETS, key = { it.titleRes }) { snippet ->
            val title = stringResource(snippet.titleRes)
            val source = stringResource(snippet.sourceRes)
            Box(
                modifier = Modifier
                    .background(CoineProColors.Surface, CoineProShapes.small)
                    .border(1.dp, CoineProColors.Border, CoineProShapes.small)
                    .clickable { onInsert(source) }
                    .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half)
                    .semantics { contentDescription = "script-snippet-$title" },
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = CoineProColors.TextSecondary)
            }
        }
    }
}

/** The strategy's figures: what the orders came to over the chart. */
@Composable
private fun StrategyCard(report: ScriptStrategyReport) {
    val positive = report.netPercent >= 0
    CoineProCard(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "script-strategy-report" },
        accent = if (positive) CoineProColors.Buy else CoineProColors.Sell,
    ) {
        Text(stringResource(R.string.script_strategy), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        StrategyRow(stringResource(R.string.script_net_return), (if (positive) "+" else "") + MarketNumberFormatter.price(report.netPercent, 2) + "%", if (positive) CoineProColors.Buy else CoineProColors.Sell)
        StrategyRow(stringResource(R.string.script_closed_trades), report.closedCount.toPersianDigits(), CoineProColors.TextPrimary)
        StrategyRow(stringResource(R.string.script_win_rate), MarketNumberFormatter.price(report.winRate * 100, 1) + "%", CoineProColors.TextPrimary)
        StrategyRow(stringResource(R.string.script_profit_factor), report.profitFactor?.let { MarketNumberFormatter.price(it, 2) } ?: "—", CoineProColors.TextPrimary)
        StrategyRow(stringResource(R.string.script_max_drawdown), MarketNumberFormatter.price(report.maxDrawdownPercent, 2) + "%", CoineProColors.Sell)
        if (report.trades.any { it.open }) {
            Text(
                stringResource(R.string.script_open_trade),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        Text(
            stringResource(R.string.script_fill_terms),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
    }
}

@Composable
private fun StrategyRow(label: String, value: String, colour: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        Text(value, style = CoineProTextStyles.Numeric, color = colour, fontWeight = FontWeight.Bold)
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
                    stringResource(R.string.script_name_label),
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
        failure.line.proseDigits() to failure.column.proseDigits()
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
        val hint = failure.hint(language == AppLanguage.ENGLISH)
        if (hint.isNotBlank()) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier.padding(top = CoineProSpacing.Half),
            )
        }
        if (failure.code != "E000") {
            LtrDirection {
                Text(
                    failure.code,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(top = CoineProSpacing.Half),
                )
            }
        }
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
            when (input.kind) {
                ScriptInputKind.BOOL -> Switch(checked = input.value != 0.0, onCheckedChange = { onChange(if (it) 1.0 else 0.0) })
                ScriptInputKind.TEXT, ScriptInputKind.SOURCE, ScriptInputKind.TIMEFRAME, ScriptInputKind.COLOUR -> Unit
                else -> Text(
                    MarketNumberFormatter.priceAuto(input.value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.Gold,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        when (input.kind) {
            ScriptInputKind.BOOL -> Unit
            ScriptInputKind.TEXT, ScriptInputKind.SOURCE, ScriptInputKind.TIMEFRAME -> ChoiceChips(
                options = input.options,
                selected = input.value.toInt(),
                onSelect = { onChange(it.toDouble()) },
            )
            ScriptInputKind.COLOUR -> ColourChips(selected = input.value.toLong(), onSelect = { onChange(it.toDouble()) })
            ScriptInputKind.NUMBER, ScriptInputKind.INTEGER -> {
                val low = input.minimum
                val high = input.maximum
                if (low != null && high != null && high > low) {
                    val step = input.step ?: if (input.kind == ScriptInputKind.INTEGER) 1.0 else 0.0
                    val steps = if (step > 0) ((high - low) / step).toInt() - 1 else 0
                    Slider(
                        value = input.value.toFloat().coerceIn(low.toFloat(), high.toFloat()),
                        onValueChange = { onChange(if (step > 0) low + kotlin.math.round((it - low) / step) * step else it.toDouble()) },
                        valueRange = low.toFloat()..high.toFloat(),
                        steps = steps.coerceIn(0, 200),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        stringResource(R.string.script_input_no_range),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
        }
    }
}

/** A row of options, the chosen one in gold. Text, source and timeframe inputs all draw this. */
@Composable
private fun ChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LtrDirection {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
        ) {
            itemsIndexed(options) { index, option ->
                val chosen = index == selected
                Box(
                    modifier = Modifier
                        .background(if (chosen) CoineProColors.SurfaceElevated else CoineProColors.Surface, CoineProShapes.small)
                        .border(1.dp, if (chosen) CoineProColors.Gold else CoineProColors.Border, CoineProShapes.small)
                        .clickable { onSelect(index) }
                        .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half)
                        .semantics { contentDescription = "input-option-$option" },
                ) {
                    Text(
                        option,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                        color = if (chosen) CoineProColors.Gold else CoineProColors.TextSecondary,
                    )
                }
            }
        }
    }
}

/** The named colours as swatches; the chosen one ringed in gold. */
@Composable
private fun ColourChips(selected: Long, onSelect: (Long) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
    ) {
        items(ScriptReference.COLOUR_NAMES, key = { it }) { name ->
            val argb = ScriptReference.colourValue(name) ?: return@items
            val chosen = argb == selected
            Box(
                modifier = Modifier
                    .size(SWATCH)
                    .background(Color(argb), CoineProShapes.small)
                    .border(if (chosen) 2.dp else 1.dp, if (chosen) CoineProColors.Gold else CoineProColors.Border, CoineProShapes.small)
                    .clickable { onSelect(argb) }
                    .semantics { contentDescription = "input-colour-$name" },
            )
        }
    }
}

private val SWATCH = 28.dp

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
            stringResource(if (buy) R.string.script_setup_buy else R.string.script_setup_sell),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (buy) CoineProColors.Buy else CoineProColors.Sell,
        )
        SetupRow(stringResource(R.string.script_entry), entry)
        SetupRow(stringResource(R.string.script_stop), stop)
        target?.let { SetupRow(stringResource(R.string.script_target), it) }
        Text(
            text = riskReward
                ?.let { stringResource(R.string.script_risk_reward, MarketNumberFormatter.price(it, 2)) }
                ?: stringResource(R.string.script_no_target),
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
    /** The studio's language. Read once by the caller; `items` is not a composable scope. */
    english: Boolean,
    /** «Add to chart» on a card, or null where there is no chart behind this screen. */
    onAddToChart: ((name: String, source: String) -> Unit)? = null,
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
                text = stringResource(R.string.script_new),
                onClick = onNew,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            SectionTitle(
                stringResource(R.string.script_mine),
                if (saved.isEmpty()) {
                stringResource(R.string.script_none_saved)
            } else {
                stringResource(R.string.script_saved_count, saved.size.proseDigits())
            },
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
                            ScriptPresets.byId(id)?.localised(english)?.title
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
                    onAddToChart?.let { add ->
                        TextButton(onClick = { add(script.name, script.source) }) {
                            Text(
                                stringResource(R.string.script_add_to_chart),
                                color = CoineProColors.Gold,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    TextButton(onClick = { onDelete(script.id) }) {
                        Text(stringResource(R.string.script_delete), color = CoineProColors.Sell, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.script_strategies_title),
                stringResource(R.string.script_strategies_count, ScriptStrategies.ALL.size.proseDigits()),
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
                AddToChartRow(onAddToChart, name, strategy.source)
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.script_lessons_title),
                stringResource(R.string.script_presets_count, ScriptPresets.ALL.size.proseDigits()),
            )
        }
        // In the reader's language, code and all: a preset's `input(title = ...)` becomes a
        // control in their settings sheet and its `plot(title = ...)` a row in their legend, so a
        // translated card over a Persian script would be the worse half of the two.
        items(ScriptPresets.all(english), key = ScriptPreset::id) { preset ->
            CoineProCard(modifier = Modifier.fillMaxWidth().clickable { onOpenPreset(preset) }) {
                Text(preset.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(preset.summary, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
                Text(
                    stringResource(R.string.script_teaches, preset.teaches),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
                AddToChartRow(onAddToChart, preset.title, preset.source)
            }
        }
    }
}

/**
 * «Add to chart» under a library card.
 *
 * On the card rather than only in the editor, because a reader browsing the library is choosing a
 * study, not a text to read: making them open it, run it and then add it put two steps between the
 * thing they pointed at and the thing they wanted.
 */
@Composable
private fun AddToChartRow(onAddToChart: ((String, String) -> Unit)?, name: String, source: String) {
    val add = onAddToChart ?: return
    TextButton(
        onClick = { add(name, source) },
        modifier = Modifier.semantics { contentDescription = "library-add-to-chart" },
    ) {
        Text(
            stringResource(R.string.script_add_to_chart),
            color = CoineProColors.Gold,
            style = MaterialTheme.typography.labelMedium,
        )
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
            "${(index + 1).proseDigits()}. ${lesson.title}",
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
                text = stringResource(R.string.script_open_in_editor),
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
        item { SectionTitle(stringResource(R.string.script_series), stringResource(R.string.script_series_caption)) }
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
        ScriptReference.ALL_GROUPS.forEach { group ->
            item {
            SectionTitle(
                group.title,
                stringResource(R.string.script_functions_count, group.functions.size.proseDigits()),
            )
        }
            items(group.functions, key = { it.signature }) { function ->
                CoineProCard(modifier = Modifier.fillMaxWidth().clickable { onInsert(function.signature) }) {
                    Snippet(function.signature)
                    // The interpreter's table is Persian; the English line sits beside it for a
                    // reader whose app is in English, from the same table the docs are built from.
                    val english = AppLanguage.fromTag(LocalConfiguration.current.locales[0].language) == AppLanguage.ENGLISH
                    Text(
                        if (english) ScriptReferenceEn.summaryFor(function) ?: function.summary else function.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextSecondary,
                        modifier = Modifier.padding(top = CoineProSpacing.Half),
                    )
                    Text(
                        stringResource(R.string.script_returns, function.returns),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
        }
        item {
            SectionTitle(
                stringResource(R.string.script_colours),
                stringResource(R.string.script_colours_count, ScriptReference.COLOUR_NAMES.size.proseDigits()),
            )
        }
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

/** The minimap strip's width: wide enough to show a line's length, narrow enough to be a margin. */
private val MINIMAP_WIDTH = 24.dp

/** How much of a line's row the bar fills, so the map reads as lines rather than as a block. */
private const val MINIMAP_BAR = 0.6f

/**
 * The narrowest an editor column may be: **400 points**, the owner's number for run H item 4.
 *
 * It is where a NamaScript line — `rsi = ta.rsi(close, length)` at the field's own size — stops
 * wrapping. Below it the editor is not cramped, it is a different tool.
 */
internal val EDITOR_MIN = 400.dp

/** The split toggle's two ids. Named so the chip row and the state cannot disagree by a typo. */
/** The split's other half: the reader's chart, or the studio's rehearsal one. */
private const val TARGET_MAIN = "chart"
private const val TARGET_SANDBOX = "sandbox"

private const val SPLIT_ON = "split"
private const val SPLIT_OFF = "code"
