package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolCategory

/**
 * One market's square: where it is, what colour it takes, and the figure behind that colour.
 *
 * The value is carried alongside the colour rather than recomputed at the draw site, because the
 * label prints the same number the fill was derived from and the two drifting apart — a tile
 * coloured on the session change and labelled with the period change — is the kind of fault nobody
 * notices until a reader acts on it.
 */
data class HeatmapTile(
    val asset: HeatmapAsset,
    val rect: Rect4,
    val argb: Long,
    /** Null where the market cannot answer the chosen colour question. The tile draws neutral. */
    val value: Double?,
)

/**
 * A block of the map holding one asset class, or the whole map when grouping is off.
 *
 * [header] is the strip the class name is written in, and it is null whenever the block is too
 * short to give a line of text away without the tiles under it becoming unreadable. A name drawn
 * over the tiles instead would be illegible on half the ramps, so the choice is a strip or nothing.
 */
data class HeatmapGroup(
    /** Null when grouping is off: the block is the map, and the map has no one class. */
    val category: SymbolCategory?,
    val rect: Rect4,
    val header: Rect4?,
    val tiles: List<HeatmapTile>,
)

/**
 * A whole map, laid out and coloured, ready to be drawn or hit-tested.
 *
 * Built once per change of data, options or size and then reused for both, which is what keeps the
 * tap and the picture in agreement. Computing the geometry inside the draw pass and again inside
 * the gesture handler is the classic way to end up with a canvas whose tiles open the wrong symbol
 * near the edges.
 */
data class HeatmapPlan(
    val groups: List<HeatmapGroup>,
    /** The value the ramp reaches full strength at. The legend and the sheet both print it. */
    val scale: Double,
    val palette: HeatmapPalette,
) {
    val tiles: List<HeatmapTile> get() = groups.flatMap { it.tiles }

    val isEmpty: Boolean get() = groups.all { it.tiles.isEmpty() }

    /**
     * The tile under a point, or null.
     *
     * Searched in reverse so that the last tile drawn wins, matching what the reader can see. The
     * rectangles do not overlap, so this only ever matters on a shared edge.
     */
    fun tileAt(x: Float, y: Float): HeatmapTile? {
        for (group in groups.asReversed()) {
            for (tile in group.tiles.asReversed()) {
                if (tile.rect.contains(x, y)) return tile
            }
        }
        return null
    }
}

/**
 * Turns a list of markets and a set of options into a laid-out, coloured map.
 *
 * Pure: no Compose, no resources, no theme. Everything the drawing needs that depends on the device
 * — the size of the canvas, the reader's writing direction, the reader's buy/sell convention — is
 * passed in, which is what lets the whole map be built and asserted on in a unit test.
 */
object HeatmapPlanner {

    /**
     * @param mirrored true for a right-to-left reader, which is this app's default. The mirroring
     *   is applied to the finished plan rather than to the canvas so that the tap and the picture
     *   share one coordinate system; see [Rect4.mirroredIn].
     * @param groupHeaderHeight the height, in pixels, of the strip a class name is written in.
     *   Pixels rather than dp because this object may not touch a density.
     */
    fun plan(
        assets: List<HeatmapAsset>,
        options: HeatmapOptions,
        width: Float,
        height: Float,
        risingIsGreen: Boolean = true,
        mirrored: Boolean = false,
        groupHeaderHeight: Float = 0f,
    ): HeatmapPlan {
        val values = assets.mapNotNull { HeatmapMetrics.valueOf(it, options.colour) }
        val scale = HeatmapMetrics.scaleFor(values, options.colour)
        if (assets.isEmpty() || width <= 0f || height <= 0f) {
            return HeatmapPlan(emptyList(), scale, options.palette)
        }

        val container = Rect4(0f, 0f, width, height)
        val groups = when (options.grouping) {
            HeatmapGrouping.NONE -> listOf(
                block(null, assets, container, header = null, options, scale, risingIsGreen),
            )
            HeatmapGrouping.BY_CLASS -> byClass(
                assets = assets,
                container = container,
                options = options,
                scale = scale,
                risingIsGreen = risingIsGreen,
                groupHeaderHeight = groupHeaderHeight,
            )
        }

        val placed = if (!mirrored) groups else groups.map { it.mirroredIn(width) }
        return HeatmapPlan(placed, scale, options.palette)
    }

    /**
     * Two treemaps, one inside the other: the classes across the whole canvas, then the markets
     * inside each class.
     *
     * The classes are sized by the same weights their members are, so a reader comparing a coin
     * against a currency pair is comparing like with like whichever level they read at. Ordering
     * them by weight rather than by the enum keeps the largest class in the reading corner, which
     * is where the squarified layout puts the largest tile too.
     */
    private fun byClass(
        assets: List<HeatmapAsset>,
        container: Rect4,
        options: HeatmapOptions,
        scale: Double,
        risingIsGreen: Boolean,
        groupHeaderHeight: Float,
    ): List<HeatmapGroup> {
        // Weighed once and kept, rather than recomputed for the sort and again for the layout. The
        // fallback path reaches into the classifier and the ranking tables, and a two-hundred-tile
        // map that walks them twice on every option change is a frame the reader watches drop.
        val weightOf = assets.associateWith { HeatmapMetrics.weightOf(it, options.size) }
        val buckets = assets.groupBy { it.meta.category }
            .toList()
            .sortedByDescending { (_, members) -> members.sumOf { weightOf.getValue(it) } }
        val weights = DoubleArray(buckets.size) { index ->
            buckets[index].second.sumOf { weightOf.getValue(it) }
        }
        val rects = Treemap.layout(weights, container.w, container.h)
        return buckets.mapIndexed { index, (category, members) ->
            val rect = rects[index]
            // A strip is only worth taking when what is left under it is still a map. Below this
            // the class name would be sitting on two slivers, which tells the reader less than the
            // colours it displaced.
            val showHeader = groupHeaderHeight > 0f && rect.h >= groupHeaderHeight * MIN_HEADER_RATIO
            val header = if (showHeader) Rect4(rect.x, rect.y, rect.w, groupHeaderHeight) else null
            val body = if (header == null) {
                rect
            } else {
                Rect4(rect.x, rect.y + groupHeaderHeight, rect.w, rect.h - groupHeaderHeight)
            }
            block(category, members, body, header, options, scale, risingIsGreen)
        }
    }

    /** One treemap laid inside [body], with every tile coloured on the map-wide [scale]. */
    private fun block(
        category: SymbolCategory?,
        members: List<HeatmapAsset>,
        body: Rect4,
        header: Rect4?,
        options: HeatmapOptions,
        scale: Double,
        risingIsGreen: Boolean,
    ): HeatmapGroup {
        val weights = DoubleArray(members.size) { HeatmapMetrics.weightOf(members[it], options.size) }
        val rects = Treemap.layout(weights, body.w, body.h)
        val tiles = members.mapIndexed { index, asset ->
            val value = HeatmapMetrics.valueOf(asset, options.colour)
            HeatmapTile(
                asset = asset,
                rect = rects[index].let { Rect4(it.x + body.x, it.y + body.y, it.w, it.h) },
                argb = if (value == null) {
                    HeatmapColours.neutralOf(options.palette)
                } else {
                    HeatmapColours.colourFor(value, scale, options.palette, risingIsGreen)
                },
                value = value,
            )
        }
        val rect = if (header == null) body else Rect4(header.x, header.y, body.w, body.h + header.h)
        return HeatmapGroup(category = category, rect = rect, header = header, tiles = tiles)
    }

    private fun HeatmapGroup.mirroredIn(width: Float) = copy(
        rect = rect.mirroredIn(width),
        header = header?.mirroredIn(width),
        tiles = tiles.map { it.copy(rect = it.rect.mirroredIn(width)) },
    )

    /**
     * A class block must be this many header-heights tall before it is given one.
     *
     * Four, so at least three quarters of the block is still map.
     */
    private const val MIN_HEADER_RATIO = 4f
}
