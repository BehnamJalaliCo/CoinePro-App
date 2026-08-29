package com.coinepro.feature.chart

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ObjectTree
import com.coinepro.core.datastore.ChartColourTemplate
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.datastore.DrawingTemplateStore
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.chart.ChartTypePicker
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.IndicatorPicker
import com.coinepro.core.chart.Replay
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.backtest.Backtest
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProGoldRule
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.help.CoineProHelpSheet
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The chart's working surface: everything you *do* to a chart, on its own page.
 *
 * The chart page is for reading a market. This is for working on it, and the split is the owner's
 * call after the toolbar version of this failed on its own terms: eight labelled buttons could not
 * fit a phone's width, so the last three were measured against nothing and the strip grew to two
 * hundred and sixty points of empty black.
 *
 * A page rather than a stack of sheets buys three things a sheet cannot. It has room to say what
 * each thing *is* — an indicator picker in a sheet is a list of fifty names, here it is a list of
 * fifty names with what each one answers. It can show state — how many indicators are on, what is
 * drawn, whether replay is running — where a sheet only ever showed one of those at a time. And it
 * survives rotation and back, so setting up a chart is not something a stray tap can dismiss.
 *
 * Six sections, in the order a chart is actually built: what it draws, what is measured on it, what
 * is drawn over it, what has been drawn, how it is tested, and what is saved.
 */
@Composable
fun ChartStudioScreen(
    controller: ChartController,
    modifier: Modifier = Modifier,
    /** Opens the NamaScript studio on this symbol. Null on a build without it. */
    onOpenScript: (() -> Unit)? = null,
    layouts: List<ChartLayout>? = null,
    onSaveLayout: ((ChartLayout) -> Unit)? = null,
    /** Removes one layout **by id**. A name is not an identity: two layouts may share one. */
    onDeleteLayout: ((String) -> Unit)? = null,
    /**
     * Opens chart vision on this symbol, or null on a deployment that does not offer it.
     *
     * Offered here rather than on the chart page because it is a job rather than a reading, and
     * because it is the one entry that has to be able to *refuse*: the model reads six bar lengths
     * and this chart may be on any of fifteen, or on a minute count the reader typed. See
     * [ChartUiState.aiVisionRefusal].
     */
    onOpenChartVision: (() -> Unit)? = null,
    /** Leaves the studio and returns to the chart, so a change can be looked at. */
    onBackToChart: (() -> Unit)? = null,
    /**
     * Opens the two-chart screen on this symbol, or null on a build without the route.
     *
     * Here rather than on the chart's bar, and that is the discipline this screen exists for: the
     * bar is full, a second chart is a job rather than a reading, and «استودیو» is already where
     * the jobs are. See [ChartPanesScreen] for why the cap is two.
     */
    onOpenPanes: (() -> Unit)? = null,
    /** Bound to the controller here too, because the studio may be the screen that opens first. */
    symbolChartStates: SymbolChartStateStore? = null,
    chartLayoutStore: ChartLayoutStore? = null,
    /** The reader's saved drawing styles. See the same parameter on [ChartScreen]. */
    drawingTemplates: DrawingTemplateStore? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf(StudioSection.INDICATORS) }
    var helpId by rememberSaveable { mutableStateOf<String?>(null) }
    /** Which drawing's own settings are open, or null. Opened from the object tree's row. */
    var styling by rememberSaveable { mutableStateOf<Long?>(null) }
    val help = rememberHelpCatalog(helpId != null)
    val helpEntry = helpId?.let { help?.get(it) }
    val onHelp: (String) -> Unit = { helpId = it }

    // Bound before `start`, and `start` is idempotent — the chart screen may already have run it.
    // Binding again is what makes the studio safe as the first screen a deep link opens.
    LaunchedEffect(controller) {
        controller.bindStores(symbolChartStates, chartLayoutStore)
        controller.start()
    }

    val storeScope = rememberCoroutineScope()

    val objectTree = remember(state.drawing.drawings, state.hiddenDrawingIds) {
        ObjectTree.treeOf(state.drawing.drawings, state.hiddenDrawingIds)
    }

    val colourTemplates by remember(chartLayoutStore) {
        chartLayoutStore?.templates() ?: flowOf(emptyList<ChartColourTemplate>())
    }.collectAsStateWithLifecycle(emptyList())

    val armedToolId = state.drawing.tool?.id
    val armedDefault by remember(armedToolId, drawingTemplates) {
        if (armedToolId == null || drawingTemplates == null) {
            flowOf<DrawingTemplate?>(null)
        } else {
            drawingTemplates.defaultFor(armedToolId)
        }
    }.collectAsStateWithLifecycle(null)

    // The same rule as on the chart page: a tool with a default template arrives wearing it. Both
    // rails arm the same controller, so a default honoured by only one of them would be a default
    // that depends on which screen the reader happened to be on.
    LaunchedEffect(armedToolId, armedDefault?.id) {
        val template = armedDefault ?: return@LaunchedEffect
        controller.setDrawingStyle(template.colour, template.widthDp)
    }

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        StudioHeader(symbol = state.symbol, timeframe = state.interval.label, onBackToChart = onBackToChart)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = CoineProSpacing.Six),
        ) {
            item {
                SectionCard(
                    section = StudioSection.TYPE,
                    open = section == StudioSection.TYPE,
                    detail = ChartCatalog.CHART_TYPES.firstOrNull { it.type == state.chartType }?.label.orEmpty(),
                    onToggle = { section = if (section == StudioSection.TYPE) StudioSection.NONE else StudioSection.TYPE },
                ) {
                    ChartTypePicker(
                        selected = state.chartType,
                        onSelect = controller::setChartType,
                        onHelp = onHelp,
                        hasVolume = state.series.hasVolume,
                    )
                }
            }
            item {
                SectionCard(
                    section = StudioSection.INDICATORS,
                    open = section == StudioSection.INDICATORS,
                    detail = if (state.activeIndicators.isEmpty()) {
                        "هیچ‌کدام روشن نیست"
                    } else {
                        state.activeIndicators.size.toPersianDigits() + " روشن"
                    },
                    onToggle = { section = if (section == StudioSection.INDICATORS) StudioSection.NONE else StudioSection.INDICATORS },
                ) {
                    // No dismiss on select: switching four indicators on is four taps, and a
                    // surface that closed after each one would turn that into twelve.
                    IndicatorPicker(
                        active = state.activeIndicators,
                        onToggle = { controller.toggleIndicator(it.id) },
                        onHelp = onHelp,
                        // The MT5 forex feed reports no volume, and the fourteen studies that are
                        // arithmetic on a volume column would draw a flat line of zeros there —
                        // which reads as a market with no participants rather than as a feed that
                        // does not carry the column.
                        hasVolume = state.series.hasVolume,
                        periods = state.indicatorPeriods,
                        onSetPeriod = controller::setIndicatorPeriod,
                    )
                }
            }
            item {
                SectionCard(
                    section = StudioSection.TOOLS,
                    open = section == StudioSection.TOOLS,
                    detail = state.drawing.tool?.label ?: (DrawingTools.ALL.size.toPersianDigits() + " ابزار"),
                    onToggle = { section = if (section == StudioSection.TOOLS) StudioSection.NONE else StudioSection.TOOLS },
                ) {
                    ToolRail(
                        selected = state.drawing.tool?.id,
                        onSelect = { tool ->
                            controller.arm(tool)
                            // Arming a tool is the one action here whose whole point is on the
                            // other screen: a reader who picked a trend line means to draw one now.
                            onBackToChart?.invoke()
                        },
                        onHelp = onHelp,
                    )
                }
            }
            if (state.drawing.drawings.isNotEmpty()) {
                item {
                    SectionCard(
                        section = StudioSection.DRAWINGS,
                        open = section == StudioSection.DRAWINGS,
                        detail = state.drawing.drawings.size.toPersianDigits() + " ترسیم",
                        onToggle = { section = if (section == StudioSection.DRAWINGS) StudioSection.NONE else StudioSection.DRAWINGS },
                    ) {
                        ObjectTreeSheetBody(
                            groups = objectTree,
                            drawings = state.drawing.drawings,
                            selectedId = state.drawing.selectedId,
                            onSelect = { id ->
                                controller.selectDrawing(id)
                                // Selecting puts the handles on the canvas, which is on the other
                                // screen — so this is one of the two actions here whose whole
                                // point is over there, like arming a tool above.
                                onBackToChart?.invoke()
                            },
                            onToggleHidden = controller::toggleDrawingHidden,
                            onToggleLocked = { node ->
                                controller.setDrawingLocked(node.id, !node.locked)
                            },
                            onDelete = controller::deleteDrawing,
                            onReorder = controller::reorderDrawing,
                            onOpenStyle = { id -> styling = id },
                        )
                    }
                }
            }
            item {
                ActionRow(
                    title = "بازپخش نوار",
                    body = "نمودار را عقب می‌برد و کندل‌به‌کندل جلو می‌آورد — تمرین تصمیم، بدون دیدن آینده.",
                    action = if (state.replay.isOn) "در حال اجرا" else "شروع",
                    icon = DesignR.drawable.tv_play,
                    enabled = state.series.bars.size >= Replay.MINIMUM_BARS,
                    disabledNote = "برای بازپخش کندل کافی نیست.",
                ) {
                    controller.enterReplay()
                    onBackToChart?.invoke()
                }
            }
            onOpenPanes?.let { open ->
                item {
                    ActionRow(
                        title = "دو نمودار هم‌زمان",
                        body = "دو نمودار روی هم، هرکدام با نماد و بازه و اندیکاتور خودش. هم‌گام‌سازی نماد و بازه اختیاری است.",
                        action = "باز کردن",
                        icon = DesignR.drawable.tv_layout_grid,
                        onClick = open,
                    )
                }
            }
            item {
                SectionCard(
                    section = StudioSection.BACKTEST,
                    open = section == StudioSection.BACKTEST,
                    detail = if (state.series.bars.size >= Backtest.MINIMUM_BARS) {
                        state.series.bars.size.toPersianDigits() + " کندل"
                    } else {
                        "کندل کافی نیست"
                    },
                    onToggle = { section = if (section == StudioSection.BACKTEST) StudioSection.NONE else StudioSection.BACKTEST },
                ) {
                    if (state.series.bars.size >= Backtest.MINIMUM_BARS) {
                        BacktestSheetBody(bars = state.series.bars, symbol = state.symbol)
                    } else {
                        Text(
                            text = "بک‌تست دست‌کم " + Backtest.MINIMUM_BARS.toPersianDigits() + " کندل می‌خواهد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextMuted,
                        )
                    }
                }
            }
            onOpenChartVision?.let { open ->
                item {
                    ActionRow(
                        title = "تحلیل تصویری چارت",
                        body = "تصویر همین چارت را می‌فرستد و ساختار، سوگیری و یک ستاپ پیشنهادی را برمی‌گرداند.",
                        action = "فرستادن",
                        icon = DesignR.drawable.tv_scan_line,
                        // Asked before the request, not after it. Forwarding an interval the
                        // endpoint refuses turns a reader's own choice of bar length into a
                        // server-worded failure they cannot act on.
                        enabled = state.aiVisionRefusal == null,
                        disabledNote = state.aiVisionRefusal.orEmpty(),
                        onClick = open,
                    )
                }
            }
            onOpenScript?.let { open ->
                item {
                    ActionRow(
                        title = "نما اسکریپت",
                        body = "اندیکاتور خودتان را بنویسید و همین‌جا روی این نماد ببینید. ده اسکریپت آماده و یک دورهٔ کوتاه همراهش است.",
                        action = "نوشتن",
                        icon = DesignR.drawable.tv_code2,
                        onClick = open,
                    )
                }
            }
            if (layouts != null) {
                item {
                    SectionCard(
                        section = StudioSection.LAYOUTS,
                        open = section == StudioSection.LAYOUTS,
                        detail = if (layouts.isEmpty()) "چیدمانی ذخیره نشده" else layouts.size.toPersianDigits() + " چیدمان",
                        onToggle = { section = if (section == StudioSection.LAYOUTS) StudioSection.NONE else StudioSection.LAYOUTS },
                    ) {
                        Column {
                            LayoutSheetBody(
                                layouts = layouts,
                                current = state,
                                onApply = controller::applyLayout,
                                onSave = { name -> onSaveLayout?.invoke(studioLayout(state, name)) },
                                onDelete = { id -> onDeleteLayout?.invoke(id) },
                            )
                            chartLayoutStore?.let { store ->
                                ColourTemplateSection(
                                    templates = colourTemplates,
                                    selected = state.colourTemplate,
                                    onSelect = controller::setColourTemplate,
                                    onSave = { template ->
                                        storeScope.launch { runCatching { store.saveTemplate(template) } }
                                    },
                                    onDelete = { id ->
                                        if (state.colourTemplate?.id == id) {
                                            controller.setColourTemplate(null)
                                        }
                                        storeScope.launch { runCatching { store.deleteTemplate(id) } }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    styling?.let { id ->
        val drawing = state.drawing.drawings.firstOrNull { it.id == id }
        if (drawing == null) {
            styling = null
        } else {
            DrawingStyleSheet(
                drawing = drawing,
                store = drawingTemplates,
                onDismiss = { styling = null },
                onSetColour = { colour ->
                    controller.applyTemplateToDrawing(drawing.id, colour, drawing.widthDp)
                    controller.setDrawingStyle(colour, drawing.widthDp)
                },
                onSetWidth = { width ->
                    controller.applyTemplateToDrawing(drawing.id, drawing.colour, width)
                    controller.setDrawingStyle(drawing.colour, width)
                },
                onApplyTemplate = { template ->
                    controller.applyTemplateToDrawing(drawing.id, template.colour, template.widthDp)
                    controller.setDrawingStyle(template.colour, template.widthDp)
                },
                onBringToFront = { controller.bringDrawingToFront(drawing.id) },
                onSendToBack = { controller.sendDrawingToBack(drawing.id) },
                onDelete = {
                    controller.deleteDrawing(drawing.id)
                    styling = null
                },
            )
        }
    }

    helpEntry?.let { entry -> CoineProHelpSheet(entry = entry, onDismiss = { helpId = null }) }
}

/**
 * Which section is open.
 *
 * One at a time, and [NONE] so every section can be closed. Six open lists on one page is a page
 * nobody can find anything on — the accordion is what makes a screen this dense scannable.
 */
private enum class StudioSection(val title: String) {
    NONE(""),
    TYPE("نوع چارت"),
    INDICATORS("اندیکاتورها"),
    TOOLS("ابزار ترسیم"),
    DRAWINGS("ترسیم‌های روی چارت"),
    BACKTEST("بک‌تست"),
    LAYOUTS("چیدمان‌ها"),
}

@Composable
private fun StudioHeader(symbol: String, timeframe: String, onBackToChart: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "استودیوی چارت",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = "$symbol · $timeframe",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
            onBackToChart?.let {
                Row(
                    modifier = Modifier
                        .clip(CoineProShapes.small)
                        .background(CoineProColors.SurfaceElevated)
                        .clickable(onClick = it)
                        .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.tv_chart_candles),
                        contentDescription = null,
                        tint = CoineProColors.Gold,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "نمودار",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Gold,
                    )
                }
            }
        }
        CoineProGoldRule(modifier = Modifier.padding(top = CoineProSpacing.One))
    }
}

/**
 * One collapsible section.
 *
 * The [detail] line is what makes the closed state worth reading: «۴ روشن» beside "اندیکاتورها"
 * answers the question a reader opened the studio to ask, without them opening anything.
 */
@Composable
private fun SectionCard(
    section: StudioSection,
    open: Boolean,
    detail: String,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half)
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(if (open) CoineProColors.Surface else CoineProColors.Stage),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (open) CoineProColors.Gold else CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
            // The list's own caret, turned. Ninety degrees from "go there" is "open this", and
            // rotating the glyph the app already ships beats adding a second one that has to be
            // kept in the same style by hand. The signs look backwards because the drawable is
            // auto-mirrored in a right-to-left layout: it points *right* by the time it is
            // rotated, so clockwise is down and anticlockwise is up.
            Icon(
                painter = painterResource(DesignR.drawable.icon_caret_left),
                contentDescription = null,
                tint = CoineProColors.TextMuted,
                modifier = Modifier
                    .size(15.dp)
                    .rotate(if (open) -90f else 90f),
            )
        }
        if (open) {
            // Bounded on purpose. Every section body is itself a list, and a list inside a list
            // with no ceiling is measured against infinity — which Compose refuses outright. A cap
            // gives the inner list a height to scroll within, and it is also the right shape for
            // the reader: an open section that pushed five others off the screen would defeat the
            // accordion it lives in.
            Box(
                modifier = Modifier
                    .heightIn(max = SECTION_MAX)
                    .padding(bottom = CoineProSpacing.One),
            ) {
                content()
            }
        }
    }
}

/** A section that is one action rather than a list — replay, and the script studio. */
@Composable
private fun ActionRow(
    title: String,
    body: String,
    action: String,
    icon: Int,
    enabled: Boolean = true,
    disabledNote: String? = null,
    onClick: () -> Unit,
) {
    val tone = if (enabled) CoineProColors.Gold else CoineProColors.TextDisabled
    Row(
        modifier = Modifier
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half)
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(CoineProColors.Surface)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CoineProShapes.extraSmall)
                .background(CoineProTint.fill(tone, CoineProColors.Surface)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = CoineProColors.TextPrimary)
            Text(
                text = if (enabled) body else (disabledNote ?: body),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (enabled) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Gold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * How tall an open section may grow.
 *
 * Around half a phone: enough that a picker shows six or seven rows at once, short enough that the
 * section headings above and below it stay on screen so the reader never loses the shape of the
 * page.
 */
private val SECTION_MAX = 380.dp

/**
 * This chart's apparatus, ready to be saved under [name].
 *
 * The same assembly the chart page's sheet does, and deliberately a second small function rather
 * than a shared one: the two screens are in the same module and both go through
 * [ChartUiState.toLayout], which is where the fields actually live. What differs is only the id,
 * and an id generated in one place for two screens would collide if a reader saved from both
 * within the same millisecond.
 */
private fun studioLayout(state: ChartUiState, name: String): ChartLayout {
    val now = System.currentTimeMillis()
    return state.toLayout(
        id = "layout_s" + now.toString(STUDIO_ID_RADIX),
        name = name,
        createdAt = now,
        updatedAt = now,
    )
}

/** Base thirty-six, so a millisecond clock becomes a short id rather than thirteen digits. */
private const val STUDIO_ID_RADIX = 36
