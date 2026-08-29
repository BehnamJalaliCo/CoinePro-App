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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * The market heatmap: one screen, one canvas, two sheets.
 *
 * A heatmap answers a question no list answers — *where is the money moving right now, and what is
 * moving against everything else* — and it answers it in one glance because area and colour are
 * read before any text is. Everything that would compete with that glance has been kept off the
 * screen: there is no toolbar of chips and no second chart. The choices that change what the map
 * means live in a sheet, because they are set once and then not thought about again.
 *
 * ### What was wrong with this screen, since the shape of the fix follows from it
 *
 * The map used to draw every market it was handed, coloured by `MarketQuote.changePercent` — a
 * field that is null on every quote either backend has ever sent. So every tile took the neutral
 * grey, every sizing fell through to the same offline ranking, and the screen was several hundred
 * near-identical squares with tickers on them. The owner's word for it was that it «only writes
 * some symbols' names», and that was exactly right: names were the only variable on it.
 *
 * Three things follow, and they are the whole of this rework.
 *
 * **The figures are obtained rather than assumed.** Where the platform serves the day's table —
 * TradeYar does — [HeatmapController] reads the whole catalogue's change, range, volume and
 * turnover in one request; where it does not, [HeatmapFacts] derives the same figures from each
 * market's daily bars at one request per market, bounded by the controller. Either way the map has
 * a second variable, which is the thing it never had.
 *
 * **An absent figure is drawn as absent.** A tile with no answer is hatched and dashed, never
 * neutral. A map that shows every coin as unchanged is telling the reader something false about the
 * market, and that is worse than showing them nothing.
 *
 * **The map draws what a phone can hold.** [HeatmapDensity] caps the tile count, the blocks are cut
 * by class or by quote currency, and a tap on a block's name strip drills into it. Four hundred
 * tiles on a phone is not a map of four hundred markets; it is a picture of none of them.
 *
 * The version that takes a [MarketSearchController] is the one the app wires; the version that
 * takes a plain list is the one a caller with its own figures uses.
 */
@Composable
fun HeatmapScreen(
    controller: MarketSearchController,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Daily bars, and the difference between a heatmap and a wall of names.
     *
     * Defaulted to null so this screen can be reached before its wiring exists, and so a preview or
     * a render test needs nothing. With it null every tile is honestly hatched and a line above the
     * map says why; with it wired the map is a heatmap. See the module's `## WIRING NEEDED`.
     */
    bars: HeatmapBarSource? = null,
    /**
     * The venue's twenty-four-hour statistics for the whole catalogue, in one call.
     *
     * With it wired the map's day figures cost one request in total instead of one per market, and
     * the candles behind it are left to answer only the period return and the median daily range.
     * Null is a supported state and is what CoinePro-FX gets, because that backend has no such
     * route: the map there is exactly the map that shipped, filled in from bars.
     */
    tickers: HeatmapTickerSource? = null,
) {
    val scope = rememberCoroutineScope()
    val heatmap = remember(controller, bars, tickers, scope) {
        HeatmapController(controller, scope, bars, tickers)
    }
    HeatmapScreen(controller = heatmap, onOpenSymbol = onOpenSymbol, modifier = modifier)
}

/** The map over its own controller. See the overload above for how the app reaches it. */
@Composable
fun HeatmapScreen(
    controller: HeatmapController,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    HeatmapScreen(
        assets = state.assets,
        onOpenSymbol = onOpenSymbol,
        modifier = modifier,
        loading = state.loading,
        resolving = state.resolving,
        canResolve = state.canResolve,
        onRefresh = controller::refresh,
        onPeriod = controller::setPeriod,
    )
}

/** The map over a list the caller already holds. */
@Composable
fun HeatmapScreen(
    assets: List<HeatmapAsset>,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    resolving: Boolean = false,
    canResolve: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    onPeriod: (HeatmapPeriod) -> Unit = {},
) {
    var options by rememberSaveable(stateSaver = OptionsSaver) {
        mutableStateOf(HeatmapOptions())
    }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    // The block the reader has drilled into. Held by its own identity rather than by an index,
    // because the blocks are re-sorted by weight on every option change and an index would follow
    // the wrong one the moment a sizing was switched.
    var focus by remember { mutableStateOf<HeatmapBucket?>(null) }
    var detail by remember { mutableStateOf<HeatmapAsset?>(null) }

    val palette = LocalCoineProPalette.current
    // The reader's buy/sell convention, read back out of the palette rather than stored twice.
    // `CoineProTheme` implements the red-up convention by exchanging `buy` and `sell`, so the
    // question "is a rise green" is answered by asking whether `buy` is still the theme's own
    // green. A canvas never sees a composable colour, so this is the only way the map can find out.
    val risingIsGreen = palette.buy ==
        (if (palette.isDark) CoineProDarkPalette.buy else CoineProLightPalette.buy)

    // What the map will actually draw, worked out once and shared by the header, the coverage line,
    // the legend and the sheet. The canvas derives the same list again from the same pure function
    // rather than being handed it, which is what stops the picture and the sentence above it from
    // describing different maps.
    val drawn = remember(assets, options, focus) { HeatmapSelection.select(assets, options, focus) }
    val scale = remember(drawn, options.colour) {
        HeatmapMetrics.scaleFor(
            drawn.mapNotNull { HeatmapMetrics.valueOf(it, options.colour) },
            options.colour,
        )
    }
    val known = remember(drawn, options.colour) {
        drawn.count { HeatmapMetrics.valueOf(it, options.colour) != null }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(CoineProColors.Stage)) {
        // The legend is the first thing to go. On a short phone it would take a fifth of the map's
        // height to restate what the sheet already says, and a crowded map is worse than an
        // unlabelled scale — which is why the scale is printed in the sheet either way.
        val roomForLegend = maxHeight >= LEGEND_MIN_HEIGHT

        Column(Modifier.fillMaxSize()) {
            CoineProListHeader(
                title = focusTitle(focus) ?: stringResource(R.string.heatmap_title),
                // Both counts in Persian digits: this is prose about how many things there are, not
                // a market figure anybody holds against another terminal.
                subtitle = stringResource(
                    R.string.heatmap_subtitle,
                    drawn.size.toPersianDigits(),
                    assets.size.toPersianDigits(),
                ),
                actions = {
                    if (focus != null) {
                        CoineProHeaderAction(
                            icon = CoineProIcons.Close,
                            label = stringResource(R.string.heatmap_focus_clear),
                            onClick = { focus = null },
                        )
                    }
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
            if (drawn.isNotEmpty()) {
                CoverageLine(
                    known = known,
                    total = drawn.size,
                    resolving = resolving,
                    canResolve = canResolve,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    assets.isEmpty() && loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = CoineProColors.Gold,
                        strokeWidth = 2.dp,
                    )

                    drawn.isEmpty() -> CoineProEmptyState(
                        message = stringResource(R.string.heatmap_empty),
                        hint = stringResource(R.string.heatmap_empty_hint),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> HeatmapCanvas(
                        assets = assets,
                        options = options,
                        focus = focus,
                        risingIsGreen = risingIsGreen,
                        onOpenSymbol = onOpenSymbol,
                        onInspect = { detail = it },
                        onFocus = { focus = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (roomForLegend && drawn.isNotEmpty()) {
                HeatmapLegend(
                    scale = scale,
                    colour = options.colour,
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
            assets = drawn,
            onOptions = { chosen ->
                // The window is the one option the controller has to know about, because the
                // period figure is derived from bars it holds rather than from anything on screen.
                if (chosen.period != options.period) onPeriod(chosen.period)
                // A cut the reader changed underneath a drill-down would leave the map focused on a
                // block that no longer exists — a class filter still applied while the map is now
                // sliced by quote currency — and the reader would see an empty map with no way to
                // tell why. Changing the cut releases the focus.
                if (chosen.grouping != options.grouping) focus = null
                options = chosen
            },
            onDismiss = { sheetOpen = false },
        )
    }

    detail?.let { asset ->
        HeatmapDetailSheet(
            asset = asset,
            period = options.period,
            onOpenChart = {
                detail = null
                onOpenSymbol(asset.symbol)
            },
            onDismiss = { detail = null },
        )
    }
}

/**
 * One sentence saying how much of the map is real.
 *
 * This is the piece of copy the whole rework turns on. A map whose tiles are mostly hatched is
 * either filling in, or has nothing to fill in with, and those two states look identical on the
 * canvas. A reader who cannot tell them apart will wait for a map that is never coming, or refresh
 * a map that was already arriving.
 */
@Composable
private fun CoverageLine(
    known: Int,
    total: Int,
    resolving: Boolean,
    canResolve: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = when {
        !canResolve && known == 0 -> stringResource(R.string.heatmap_coverage_none)
        resolving && known < total -> stringResource(R.string.heatmap_coverage_loading)
        known >= total -> return
        else -> stringResource(
            R.string.heatmap_coverage,
            known.toPersianDigits(),
            total.toPersianDigits(),
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = modifier.padding(
            horizontal = CoineProSpacing.Gutter,
            vertical = CoineProSpacing.Half,
        ),
    )
}

/**
 * The map itself: flat fills, a hairline between them, and a hatch where there is nothing to say.
 *
 * The whole picture is one [Canvas] rather than a grid of composables, and at a hundred and forty
 * markets that is not an optimisation but the difference between a map and a dropped frame. It also
 * means every coordinate here is a plain float, which is why the layout, the mirroring and the hit
 * test are all done once in [HeatmapPlanner] and shared: a canvas that computes its geometry twice
 * eventually opens the wrong symbol.
 */
@Composable
private fun HeatmapCanvas(
    assets: List<HeatmapAsset>,
    options: HeatmapOptions,
    focus: HeatmapBucket?,
    risingIsGreen: Boolean,
    onOpenSymbol: (String) -> Unit,
    onInspect: (HeatmapAsset) -> Unit,
    onFocus: (HeatmapBucket?) -> Unit,
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
    val hatch = Color(HeatmapColours.hatch.toInt())
    val names = categoryNames()
    val canvasLabel = stringResource(R.string.heatmap_canvas)

    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val headerHeight = with(density) { GROUP_HEADER.toPx() }
        val headerSlop = with(density) { GROUP_HEADER_SLOP.toPx() }
        val plan = remember(assets, options, focus, width, height, mirrored, risingIsGreen) {
            HeatmapPlanner.plan(
                assets = assets,
                options = options,
                width = width,
                height = height,
                risingIsGreen = risingIsGreen,
                mirrored = mirrored,
                groupHeaderHeight = if (options.grouping == HeatmapGrouping.NONE) 0f else headerHeight,
                focus = focus,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = canvasLabel }
                .pointerInput(plan) {
                    detectTapGestures(
                        onTap = { offset ->
                            // The name strip first: it sits above its own block's tiles, and a
                            // reader aiming at it must not open whichever market is under it.
                            val bucket = plan.bucketAt(offset.x, offset.y, headerSlop)
                            if (bucket != null) {
                                haptics.select()
                                onFocus(bucket)
                                return@detectTapGestures
                            }
                            val tile = plan.tileAt(offset.x, offset.y) ?: return@detectTapGestures
                            haptics.select()
                            onOpenSymbol(tile.asset.symbol)
                        },
                        // Checking rather than committing: the reader keeps the map. See
                        // [HeatmapDetailSheet].
                        onLongPress = { offset ->
                            val tile = plan.tileAt(offset.x, offset.y) ?: return@detectTapGestures
                            haptics.select()
                            onInspect(tile.asset)
                        },
                    )
                },
        ) {
            val stroke = 1.dp.toPx()
            val inset = TILE_PADDING.toPx()
            val minLabel = Size(MIN_LABEL_WIDTH.toPx(), MIN_LABEL_HEIGHT.toPx())
            plan.groups.forEach { group ->
                group.tiles.forEach { tile ->
                    drawTile(
                        tile = tile,
                        colour = options.colour,
                        stroke = stroke,
                        inset = inset,
                        minLabel = minLabel,
                        hairline = hairline,
                        hatch = hatch,
                        measurer = measurer,
                    )
                }
                val header = group.header
                val bucket = group.bucket
                if (header != null && bucket != null) {
                    drawGroupName(
                        label = bucketName(bucket, names),
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
 * One tile: the fill, the hairline, the hatch if there is nothing behind it, and the label if — and
 * only if — the label fits.
 *
 * A clipped label is worse than none. Half a ticker reads as a different ticker, and Persian and
 * Latin glyphs both lose their identity when they are cut vertically, so the test here is whether
 * the measured text fits *entirely* inside the tile's padding box. Nothing is scaled down to make
 * it fit either: a map with four type sizes on it stops being scannable.
 */
private fun DrawScope.drawTile(
    tile: HeatmapTile,
    colour: HeatmapColour,
    stroke: Float,
    inset: Float,
    minLabel: Size,
    hairline: Color,
    hatch: Color,
    measurer: TextMeasurer,
) {
    val rect = tile.rect
    if (rect.w <= 0f || rect.h <= 0f) return
    val topLeft = Offset(rect.x, rect.y)
    val size = Size(rect.w, rect.h)
    drawRect(color = Color(tile.argb.toInt()), topLeft = topLeft, size = size)
    if (!tile.known) drawHatch(rect, hatch, stroke)
    // The separator is the page showing through, not a border drawn on the tile. That keeps the map
    // one object: at a glance the reader sees a mosaic, not two hundred cards.
    drawRect(color = hairline, topLeft = topLeft, size = size, style = Stroke(width = stroke))

    val box = rect.inset(inset)
    if (box.w < minLabel.width || box.h < minLabel.height) return

    val ink = Color(HeatmapColours.labelInkFor(tile.argb).toInt())
    val ticker = measurer.measure(tile.asset.label, tickerStyle(ink))
    if (ticker.size.width > box.w || ticker.size.height > box.h) return

    // Drawn for an unknown tile too, as an em dash. A tile carrying a ticker and no second line is
    // ambiguous — the reader cannot tell whether the figure was omitted for space or does not
    // exist — and the dash is the one glyph that answers that in the width a small tile has.
    val figure = measurer.measure(HeatmapFormat.tileFigure(tile.value, colour), figureStyle(ink))
    val both = figure.size.width <= box.w && ticker.size.height + figure.size.height <= box.h
    val blockHeight = ticker.size.height + if (both) figure.size.height else 0
    var y = box.y + (box.h - blockHeight) / 2f
    drawText(ticker, topLeft = Offset(box.x + (box.w - ticker.size.width) / 2f, y))
    if (both) {
        y += ticker.size.height
        drawText(figure, topLeft = Offset(box.x + (box.w - figure.size.width) / 2f, y))
    }
}

/**
 * The diagonal hatch that marks a tile with no figure behind it.
 *
 * ### Why the fill alone is not enough
 *
 * The map's whole content is colour, so any colour reserved for "no answer" is a colour taken out
 * of the scale — and on [HeatmapPalette.MONOCHROME], where the ramp spans the entire lightness
 * axis, there is no such colour left to reserve. A hatch is a *texture*, which is orthogonal to
 * every ramp, survives every palette, and is still distinguishable to a reader who sees no colour
 * difference at all. It is also, deliberately, the visual language of "nothing here" that charts
 * have used for a century.
 *
 * Lines rather than a `Brush`: this app does not use gradients outside the brand mark and the
 * chart's own fill, and a hatch built from a repeating shader would be one.
 */
private fun DrawScope.drawHatch(rect: Rect4, ink: Color, stroke: Float) {
    clipRect(left = rect.x, top = rect.y, right = rect.right, bottom = rect.bottom) {
        val step = HATCH_STEP.toPx()
        // Started far enough to the left that the diagonals crossing the top-right corner are drawn
        // too: a hatch that stops at the tile's own left edge leaves a bare triangle in the corner
        // and reads as a rendering fault rather than as a texture.
        var x = rect.x - rect.h
        while (x < rect.right) {
            drawLine(
                color = ink,
                start = Offset(x, rect.bottom),
                end = Offset(x + rect.h, rect.y),
                strokeWidth = stroke,
            )
            x += step
        }
    }
}

/**
 * The block's name, in its reading corner.
 *
 * On its own strip rather than over the tiles: a name laid on the map would have to survive both
 * ends of every ramp, and there is no ink that reads on near-white and near-black at once. The
 * strip is also the drill-down target, which is why it is a full-width band rather than a floating
 * label — at [GROUP_HEADER] tall and the block's full width it clears the minimum touch target on
 * every block big enough to be given one.
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
 * The colour scale, drawn as the ramp itself rather than as three swatches, with the metric it is
 * measuring named beside it and the unknown state shown as its own swatch.
 *
 * The unknown swatch is the part that earns the row's height. A ramp alone tells a reader what a
 * green tile means and leaves them to guess at the hatched ones, and the guess a reader makes is
 * "broken". Showing the hatch in the legend, labelled, turns it into a reading of the market:
 * these are the markets nobody has a figure for yet.
 *
 * Sampled in steps because the alternative is a gradient, and this product does not use gradients
 * outside the brand mark and the chart's own fill. Twenty-four steps at this width is one step per
 * couple of points, which the eye reads as continuous anyway.
 */
@Composable
private fun HeatmapLegend(
    scale: Double,
    colour: HeatmapColour,
    palette: HeatmapPalette,
    risingIsGreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val hatch = Color(HeatmapColours.hatch.toInt())
    val unknownFill = Color(HeatmapColours.unknown.toInt())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoineProSpacing.Gutter,
                vertical = CoineProSpacing.One,
            ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendLabel(colourName(colour))
            Row(
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(LEGEND_SWATCH)) {
                    drawRect(color = unknownFill)
                    drawHatch(Rect4(0f, 0f, size.width, size.height), hatch, 1.dp.toPx())
                }
                LegendLabel(stringResource(R.string.heatmap_legend_unknown))
            }
        }
        Canvas(Modifier.fillMaxWidth().height(LEGEND_BAR)) {
            val step = size.width / LEGEND_STEPS
            for (index in 0 until LEGEND_STEPS) {
                val t = (index + 0.5) / LEGEND_STEPS * 2.0 - 1.0
                val fill = HeatmapColours.colourFor(t, 1.0, palette, risingIsGreen)
                // Drawn left to right in the reader's own direction: the canvas does not mirror, so
                // in Persian the bar is reversed here to keep the losses on the reading edge.
                val x = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width - (index + 1) * step
                } else {
                    index * step
                }
                drawRect(
                    color = Color(fill.toInt()),
                    topLeft = Offset(x, 0f),
                    size = Size(step + 1f, size.height),
                )
            }
        }
        // Formatted by the same function the tiles use, so the ends of the bar are written exactly
        // the way the figure on a tile at that end is written — including the real minus sign, and
        // including the sign living *inside* the left-to-right isolate. Built as a format string
        // with the sign outside it, a Persian paragraph moved the minus to the far end and the
        // bottom of the scale read as a gain.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LegendLabel(HeatmapFormat.tileFigure(-scale, colour))
            LegendLabel(stringResource(R.string.heatmap_scale_zero))
            LegendLabel(HeatmapFormat.tileFigure(scale, colour))
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

/** What the map is currently coloured by, for the legend. */
@Composable
private fun colourName(colour: HeatmapColour): String = when (colour) {
    HeatmapColour.CHANGE -> stringResource(R.string.heatmap_colour_change)
    HeatmapColour.PERFORMANCE -> stringResource(R.string.heatmap_colour_performance)
    HeatmapColour.VOLATILITY -> stringResource(R.string.heatmap_colour_volatility)
    HeatmapColour.RANGE -> stringResource(R.string.heatmap_colour_range)
    HeatmapColour.GAP -> stringResource(R.string.heatmap_colour_gap)
}

/** The header title while a block is focused: the block's own name, so the map says where it is. */
@Composable
private fun focusTitle(focus: HeatmapBucket?): String? {
    val names = categoryNames()
    return focus?.let { bucketName(it, names) }
}

/**
 * What a block is called.
 *
 * A quote currency is printed as the feed spells it — `USDT`, `JPY` — rather than translated. It is
 * a ticker, and a reader comparing this map against an exchange needs the exchange's own word.
 */
private fun bucketName(bucket: HeatmapBucket, names: Map<SymbolCategory, String>): String =
    when (bucket) {
        is HeatmapBucket.Class -> names.getValue(bucket.category)
        is HeatmapBucket.Quote -> bucket.currency
        HeatmapBucket.None -> names.getValue(SymbolCategory.OTHER)
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

/** The figure under the ticker. Latin digits, because it is a market number. */
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

/** Six enum ordinals. Small enough to survive process death in a saved-instance bundle. */
private val OptionsSaver = listSaver<HeatmapOptions, Int>(
    save = {
        listOf(
            it.size.ordinal,
            it.colour.ordinal,
            it.period.ordinal,
            it.palette.ordinal,
            it.grouping.ordinal,
            it.density.ordinal,
        )
    },
    restore = {
        HeatmapOptions(
            size = HeatmapSize.entries[it[0]],
            colour = HeatmapColour.entries[it[1]],
            period = HeatmapPeriod.entries[it[2]],
            palette = HeatmapPalette.entries[it[3]],
            grouping = HeatmapGrouping.entries[it[4]],
            density = HeatmapDensity.entries[it[5]],
        )
    },
)

/** Below this the legend would be taking height the map needs more. */
private val LEGEND_MIN_HEIGHT = 420.dp

private val LEGEND_BAR = 8.dp

/** The unknown swatch in the legend. Square, and large enough for the hatch to read as a hatch. */
private val LEGEND_SWATCH = 12.dp

private const val LEGEND_STEPS = 24

/** How far apart the hatch lines are. Wide enough to read as texture, not as a solid tint. */
private val HATCH_STEP = 6.dp

/**
 * The strip a block's name is written in, when a block is tall enough to give one up.
 *
 * Thirty-two rather than the forty-four a touch target needs, because the strip is height taken
 * away from the map: four blocks at forty-four would spend a fifth of a phone-tall canvas on
 * labels. It reaches the minimum through [GROUP_HEADER_SLOP] instead, which grows the *target*
 * without growing the strip.
 */
private val GROUP_HEADER = 32.dp

/**
 * How far past its drawn edges a name strip is still tappable, on each side.
 *
 * Thirty-two plus six above and six below is forty-four, which is the floor. See
 * [HeatmapPlan.bucketAt] for why the strip is allowed to win the overlap against its own tiles.
 */
private val GROUP_HEADER_SLOP = 6.dp

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
