package com.coinepro.feature.heatmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProDarkPalette
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProLightPalette
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LocalCoineProPalette
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.symbols.SymbolCategory

/**
 * The market heatmap: one screen, one canvas, one sheet.
 *
 * A heatmap answers a question no list answers — *where is the money moving right now* — and it
 * answers it in one glance because area and colour are read before any text is. Everything that
 * would compete with that glance has been kept off the screen: there is no toolbar of chips, no
 * second chart, no legend unless the phone is tall enough to hold one without squeezing the map.
 * The four choices that change what the map means live in a sheet, because they are set once and
 * then not thought about again.
 *
 * The version that takes a [MarketSearchController] is the one the app wires; the version that
 * takes a plain list is the one a caller with its own figures — real capitalisations, real volumes
 * — uses to get a proportional map instead of a ranked one.
 */
@Composable
fun HeatmapScreen(
    controller: MarketSearchController,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    // Mapped once per catalogue change rather than per frame: the filter reaches into the artwork
    // table, which is a set of several thousand names.
    val assets = remember(state.results) { heatmapAssetsFrom(state.results) }
    HeatmapScreen(
        assets = assets,
        onOpenSymbol = onOpenSymbol,
        modifier = modifier,
        loading = state.loading,
        onRefresh = controller::refresh,
    )
}

/** The map over a list the caller already holds. See the overload above for the app's own wiring. */
@Composable
fun HeatmapScreen(
    assets: List<HeatmapAsset>,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    var options by rememberSaveable(stateSaver = OptionsSaver) {
        mutableStateOf(HeatmapOptions())
    }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    val palette = LocalCoineProPalette.current
    // The reader's buy/sell convention, read back out of the palette rather than stored twice.
    // `CoineProTheme` implements the red-up convention by exchanging `buy` and `sell`, so the
    // question "is a rise green" is answered by asking whether `buy` is still the theme's own
    // green. A canvas never sees a composable colour, so this is the only way the map can find out.
    val risingIsGreen = palette.buy ==
        (if (palette.isDark) CoineProDarkPalette.buy else CoineProLightPalette.buy)

    // The value the ramp reaches full strength at, for the legend and for the sheet's caption. The
    // plan works it out again from the same inputs with the same pure function, so the two cannot
    // disagree; passing it down instead would mean the map could be drawn on one scale and
    // described with another.
    val scale = remember(assets, options.colour) {
        HeatmapMetrics.scaleFor(
            assets.mapNotNull { HeatmapMetrics.valueOf(it, options.colour) },
            options.colour,
        )
    }

    BoxWithConstraints(modifier.fillMaxSize().background(CoineProColors.Stage)) {
        // The legend is the first thing to go. On a short phone it would take a fifth of the map's
        // height to restate what the sheet already says, and a crowded map is worse than an
        // unlabelled scale — which is why the scale is printed in the sheet either way.
        val roomForLegend = maxHeight >= LEGEND_MIN_HEIGHT

        Column(Modifier.fillMaxSize()) {
            CoineProListHeader(
                title = stringResource(R.string.heatmap_title),
                subtitle = stringResource(R.string.heatmap_subtitle, assets.size.toPersianDigits()),
                actions = {
                    if (onRefresh != null) {
                        CoineProHeaderAction(
                            icon = CoineProIcons.Refresh,
                            label = stringResource(R.string.heatmap_refresh),
                            onClick = onRefresh,
                        )
                    }
                    CoineProHeaderAction(
                        icon = CoineProIcons.Settings,
                        label = stringResource(R.string.heatmap_settings),
                        onClick = { sheetOpen = true },
                    )
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    assets.isEmpty() && loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = CoineProColors.Gold,
                        strokeWidth = 2.dp,
                    )

                    assets.isEmpty() -> CoineProEmptyState(
                        message = stringResource(R.string.heatmap_empty),
                        hint = stringResource(R.string.heatmap_empty_hint),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> HeatmapCanvas(
                        assets = assets,
                        options = options,
                        risingIsGreen = risingIsGreen,
                        onOpenSymbol = onOpenSymbol,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (roomForLegend && assets.isNotEmpty()) {
                HeatmapLegend(
                    scale = scale,
                    palette = options.palette,
                    risingIsGreen = risingIsGreen,
                )
            }
        }
    }

    if (sheetOpen) {
        HeatmapSettingsSheet(
            options = options,
            scale = scale,
            onOptions = { options = it },
            onDismiss = { sheetOpen = false },
        )
    }
}

/**
 * The map itself: flat fills, a hairline between them, and nothing else.
 *
 * The whole picture is one [Canvas] rather than a grid of composables, and at two hundred markets
 * that is not an optimisation but the difference between a map and a dropped frame. It also means
 * every coordinate here is a plain float, which is why the layout, the mirroring and the hit test
 * are all done once in [HeatmapPlanner] and shared: a canvas that computes its geometry twice
 * eventually opens the wrong symbol.
 */
@Composable
private fun HeatmapCanvas(
    assets: List<HeatmapAsset>,
    options: HeatmapOptions,
    risingIsGreen: Boolean,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptics = rememberCoineProHaptics()
    // A generous cache because the same few dozen tickers are re-measured every frame the map is
    // dragged past; the default of eight would miss on almost all of them.
    val measurer = rememberTextMeasurer(cacheSize = 96)
    val hairline = CoineProColors.Stage
    val groupInk = CoineProColors.TextMuted
    val names = categoryNames()

    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val headerHeight = with(density) { GROUP_HEADER.toPx() }
        val plan = remember(assets, options, width, height, mirrored, risingIsGreen) {
            HeatmapPlanner.plan(
                assets = assets,
                options = options,
                width = width,
                height = height,
                risingIsGreen = risingIsGreen,
                mirrored = mirrored,
                groupHeaderHeight = if (options.grouping == HeatmapGrouping.BY_CLASS) headerHeight else 0f,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(plan) {
                    detectTapGestures { offset ->
                        val tile = plan.tileAt(offset.x, offset.y) ?: return@detectTapGestures
                        haptics.select()
                        onOpenSymbol(tile.asset.symbol)
                    }
                },
        ) {
            val stroke = 1.dp.toPx()
            val inset = TILE_PADDING.toPx()
            val minLabel = Size(MIN_LABEL_WIDTH.toPx(), MIN_LABEL_HEIGHT.toPx())
            plan.groups.forEach { group ->
                group.tiles.forEach { tile ->
                    drawTile(tile, stroke, inset, minLabel, hairline, measurer)
                }
                val header = group.header
                val category = group.category
                if (header != null && category != null) {
                    drawGroupName(
                        label = names.getValue(category),
                        header = header,
                        ink = groupInk,
                        mirrored = mirrored,
                        padding = inset,
                        measurer = measurer,
                    )
                }
            }
        }
    }
}

/**
 * One tile: the fill, the hairline, and the label if — and only if — the label fits.
 *
 * A clipped label is worse than none. Half a ticker reads as a different ticker, and Persian and
 * Latin glyphs both lose their identity when they are cut vertically, so the test here is whether
 * the measured text fits *entirely* inside the tile's padding box. Nothing is scaled down to make
 * it fit either: a map with four type sizes on it stops being scannable.
 */
private fun DrawScope.drawTile(
    tile: HeatmapTile,
    stroke: Float,
    inset: Float,
    minLabel: Size,
    hairline: Color,
    measurer: TextMeasurer,
) {
    val rect = tile.rect
    if (rect.w <= 0f || rect.h <= 0f) return
    val topLeft = Offset(rect.x, rect.y)
    val size = Size(rect.w, rect.h)
    drawRect(color = Color(tile.argb.toInt()), topLeft = topLeft, size = size)
    // The separator is the page showing through, not a border drawn on the tile. That keeps the map
    // one object: at a glance the reader sees a mosaic, not two hundred cards.
    drawRect(color = hairline, topLeft = topLeft, size = size, style = Stroke(width = stroke))

    val box = rect.inset(inset)
    if (box.w < minLabel.width || box.h < minLabel.height) return

    val ink = Color(HeatmapColours.labelInkFor(tile.argb).toInt())
    val ticker = measurer.measure(tile.asset.label, tickerStyle(ink))
    if (ticker.size.width > box.w || ticker.size.height > box.h) return

    val figure = tile.value?.let { measurer.measure(MarketNumberFormatter.signedPercent(it), figureStyle(ink)) }
    val both = figure != null &&
        figure.size.width <= box.w &&
        ticker.size.height + figure.size.height <= box.h
    val blockHeight = ticker.size.height + if (both) figure.size.height else 0
    var y = box.y + (box.h - blockHeight) / 2f
    drawText(ticker, topLeft = Offset(box.x + (box.w - ticker.size.width) / 2f, y))
    if (both && figure != null) {
        y += ticker.size.height
        drawText(figure, topLeft = Offset(box.x + (box.w - figure.size.width) / 2f, y))
    }
}

/**
 * The class name, in the block's reading corner.
 *
 * On its own strip rather than over the tiles: a name laid on the map would have to survive both
 * ends of every ramp, and there is no ink that reads on near-white and near-black at once.
 */
private fun DrawScope.drawGroupName(
    label: String,
    header: Rect4,
    ink: Color,
    mirrored: Boolean,
    padding: Float,
    measurer: TextMeasurer,
) {
    val measured = measurer.measure(label, groupStyle(ink))
    if (measured.size.width > header.w - padding * 2f) return
    val x = if (mirrored) header.right - padding - measured.size.width else header.x + padding
    val y = header.y + (header.h - measured.size.height) / 2f
    drawText(measured, topLeft = Offset(x, y))
}

/**
 * The colour scale, drawn as the ramp itself rather than as three swatches.
 *
 * Sampled in steps because the alternative is a gradient, and this product does not use gradients
 * outside the brand mark and the chart's own fill. Twenty-four steps at this width is one step per
 * couple of points, which the eye reads as continuous anyway.
 */
@Composable
private fun HeatmapLegend(
    scale: Double,
    palette: HeatmapPalette,
    risingIsGreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val figure = MarketNumberFormatter.price(scale, decimals = 1)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoineProSpacing.Gutter,
                vertical = CoineProSpacing.One,
            ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Canvas(Modifier.fillMaxWidth().height(LEGEND_BAR)) {
            val step = size.width / LEGEND_STEPS
            for (index in 0 until LEGEND_STEPS) {
                val t = (index + 0.5) / LEGEND_STEPS * 2.0 - 1.0
                val colour = HeatmapColours.colourFor(t, 1.0, palette, risingIsGreen)
                // Drawn left to right in the reader's own direction: the canvas does not mirror, so
                // in Persian the bar is reversed here to keep the losses on the reading edge.
                val x = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width - (index + 1) * step
                } else {
                    index * step
                }
                drawRect(
                    color = Color(colour.toInt()),
                    topLeft = Offset(x, 0f),
                    size = Size(step + 1f, size.height),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendLabel(stringResource(R.string.heatmap_scale_low, figure))
            LegendLabel(stringResource(R.string.heatmap_scale_zero))
            LegendLabel(stringResource(R.string.heatmap_scale_high, figure))
        }
    }
}

@Composable
private fun LegendLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
    )
}

/** The class names, resolved once so the draw pass never touches a resource lookup. */
@Composable
private fun categoryNames(): Map<SymbolCategory, String> = mapOf(
    SymbolCategory.FOREX to stringResource(R.string.heatmap_class_forex),
    SymbolCategory.CRYPTO to stringResource(R.string.heatmap_class_crypto),
    SymbolCategory.METAL to stringResource(R.string.heatmap_class_metal),
    SymbolCategory.INDEX to stringResource(R.string.heatmap_class_index),
    SymbolCategory.ENERGY to stringResource(R.string.heatmap_class_energy),
    SymbolCategory.OTHER to stringResource(R.string.heatmap_class_other),
)

/**
 * The ticker, in Latin, pinned left-to-right.
 *
 * A canvas inherits the layout direction, and a bare Latin ticker inside a right-to-left run picks
 * up the paragraph direction: `BTC/USDT` comes back reordered. Pinning the run is the fix, and it
 * is the same fix the chart's own axis labels use.
 */
private fun tickerStyle(ink: Color) = TextStyle(
    color = ink,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
    textDirection = TextDirection.Ltr,
)

/** The change figure. Latin digits, because it is a market number. */
private fun figureStyle(ink: Color) = TextStyle(
    color = ink,
    fontSize = 10.sp,
    fontWeight = FontWeight.Normal,
    textDirection = TextDirection.Ltr,
)

private fun groupStyle(ink: Color) = TextStyle(
    color = ink,
    fontSize = 11.sp,
    fontWeight = FontWeight.Bold,
)

/** Four enum ordinals. Small enough to survive process death in a saved-instance bundle. */
private val OptionsSaver = listSaver<HeatmapOptions, Int>(
    save = { listOf(it.size.ordinal, it.colour.ordinal, it.palette.ordinal, it.grouping.ordinal) },
    restore = {
        HeatmapOptions(
            size = HeatmapSize.entries[it[0]],
            colour = HeatmapColour.entries[it[1]],
            palette = HeatmapPalette.entries[it[2]],
            grouping = HeatmapGrouping.entries[it[3]],
        )
    },
)

/** Below this the legend would be taking height the map needs more. */
private val LEGEND_MIN_HEIGHT = 420.dp

private val LEGEND_BAR = 8.dp

private const val LEGEND_STEPS = 24

/** The strip a class name is written in, when a class block is tall enough to give one up. */
private val GROUP_HEADER = 18.dp

/** Text is inset by this much on every side of a tile. */
private val TILE_PADDING = 3.dp

/**
 * The smallest padding box worth measuring text against.
 *
 * A cheap pre-test, so a map of two hundred tiles does not lay out two hundred strings only to
 * discover that most of them do not fit. The real test is the measured one that follows it, and
 * these two numbers are deliberately smaller than any label actually needs — a pre-test that
 * rejected a tile the text would have fitted in would be a silently blank tile.
 */
private val MIN_LABEL_WIDTH = 26.dp

private val MIN_LABEL_HEIGHT = 14.dp
