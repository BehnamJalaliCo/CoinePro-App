package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolCategory

/**
 * What one block of the map holds.
 *
 * A sealed type rather than a nullable [SymbolCategory], because there are now two things a map can
 * be cut by and they are not interchangeable. The class answers "what kind of thing is this"; the
 * quote currency answers "what is it priced in", and on a day when the dollar itself is moving that
 * is the more useful cut — every USD-quoted pair falling together is one story, and only the second
 * grouping draws it as one block.
 */
sealed interface HeatmapBucket {
    /** One asset class, as `core:symbols` classifies it from the ticker. */
    data class Class(val category: SymbolCategory) : HeatmapBucket

    /**
     * One quote currency, upper-cased, as the feed spells it.
     *
     * A market with no quote leg — an index, an energy contract — has none, and lands in [None].
     */
    data class Quote(val currency: String) : HeatmapBucket

    /** Everything the current cut has no answer for. Not a failure: an index has no quote leg. */
    data object None : HeatmapBucket
}

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
    /** Null where the market cannot answer the chosen colour question. See [known]. */
    val value: Double?,
) {
    /**
     * Whether this tile is showing data at all.
     *
     * The draw pass hatches the ones that are not. This is the property the whole feature turns on:
     * a tile with no answer must not be able to be mistaken for a tile whose answer was zero, and
     * before this existed those two were the same colour on every palette.
     */
    val known: Boolean get() = value != null
}

/**
 * A block of the map holding one bucket, or the whole map when grouping is off.
 *
 * [header] is the strip the bucket's name is written in, and it is null whenever the block is too
 * short to give a line of text away without the tiles under it becoming unreadable. A name drawn
 * over the tiles instead would be illegible on half the ramps, so the choice is a strip or nothing.
 *
 * The header is also the drill-down target: [HeatmapPlan.bucketAt] hit-tests it, and the screen
 * focuses the block a reader taps. That is how the markets left off by the density cap are reached.
 */
data class HeatmapGroup(
    /** Null when grouping is off: the block is the map, and the map has no one bucket. */
    val bucket: HeatmapBucket?,
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
    /**
     * Markets that answered the colour question, out of the markets on the map.
     *
     * On screen as a sentence, because a map that is a third unknown has to say so in words as well
     * as in hatching — a reader who has just opened the screen needs to know the tiles are still
     * filling in, and a reader whose feed carries nothing needs to know that is not a bug they can
     * fix by waiting.
     */
    val known: Int = groups.sumOf { group -> group.tiles.count(HeatmapTile::known) },
) {
    val tiles: List<HeatmapTile> get() = groups.flatMap { it.tiles }

    val isEmpty: Boolean get() = groups.all { it.tiles.isEmpty() }

    val size: Int get() = groups.sumOf { it.tiles.size }

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

    /**
     * The bucket whose name strip is under a point, or null.
     *
     * Only the strip, never the block: tapping a tile opens that market, and a block's whole area
     * being a drill-down target would mean every tap did two things at once.
     *
     * @param slop pixels the strip is grown by vertically for the purposes of the hit test only,
     *   so a strip drawn thinner than the minimum touch target still meets it. The strip therefore
     *   wins over the tiles immediately above and below it, and that asymmetry is deliberate: a
     *   focus taken by mistake costs one tap to undo, while a chart opened by mistake costs a
     *   navigation and loses the map the reader was reading.
     */
    fun bucketAt(x: Float, y: Float, slop: Float = 0f): HeatmapBucket? {
        for (group in groups.asReversed()) {
            val header = group.header ?: continue
            val target = Rect4(header.x, header.y - slop, header.w, header.h + slop * 2f)
            if (target.contains(x, y)) return group.bucket
        }
        return null
    }
}

/**
 * Which markets the map draws, and in what order.
 *
 * ### Why a map is allowed to leave markets out
 *
 * The catalogue runs to several hundred markets and a phone is about fifty square centimetres. Four
 * hundred tiles on it average under a fifth of a square centimetre each: no ticker fits, no figure
 * fits, and a thumb is wider than three of them. Drawing all of them is not thoroughness, it is a
 * picture with no information in it — which is exactly what the first version of this screen was,
 * and exactly why it read as a wall of names.
 *
 * So the map draws the largest [HeatmapDensity.tiles] by whatever the reader is sizing by, and the
 * rest are reached by focusing a block rather than by squinting at one. Selecting by the *current*
 * weight rather than by a fixed ranking matters: a reader sizing by turnover is asking "where is
 * the money today", and answering it with the fifty markets that were largest last year would be
 * answering a different question.
 */
object HeatmapSelection {

    /**
     * The markets to draw, heaviest first.
     *
     * @param focus when set, only markets in that bucket — the drill-down. The cap still applies
     *   inside it, because one class can be four hundred markets on its own.
     */
    fun select(
        assets: List<HeatmapAsset>,
        options: HeatmapOptions,
        focus: HeatmapBucket? = null,
    ): List<HeatmapAsset> {
        val scoped = if (focus == null) assets else assets.filter { bucketOf(it, options.grouping) == focus }
        // Sorted so that the cap below takes the heaviest rather than the first the catalogue
        // happened to list. [Treemap.layout] does its own descending sort and answers in input
        // order, so this is not for its benefit — it is what makes "the markets left off the map"
        // a defensible set instead of an arbitrary one.
        val ordered = scoped.sortedByDescending { HeatmapMetrics.weightOf(it, options.size) }
        val cap = options.density.tiles ?: return ordered
        return if (ordered.size <= cap) ordered else ordered.take(cap)
    }

    /** Which block a market belongs to under a given cut. */
    fun bucketOf(asset: HeatmapAsset, grouping: HeatmapGrouping): HeatmapBucket = when (grouping) {
        HeatmapGrouping.NONE -> HeatmapBucket.None
        HeatmapGrouping.BY_CLASS -> HeatmapBucket.Class(asset.meta.category)
        HeatmapGrouping.BY_QUOTE -> asset.meta.quote
            ?.takeIf { it.isNotBlank() }
            ?.let { HeatmapBucket.Quote(it.uppercase()) }
            ?: HeatmapBucket.None
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
     * @param groupHeaderHeight the height, in pixels, of the strip a bucket's name is written in.
     *   Pixels rather than dp because this object may not touch a density.
     * @param focus the block the reader has drilled into, or null for the whole map.
     */
    fun plan(
        assets: List<HeatmapAsset>,
        options: HeatmapOptions,
        width: Float,
        height: Float,
        risingIsGreen: Boolean = true,
        mirrored: Boolean = false,
        groupHeaderHeight: Float = 0f,
        focus: HeatmapBucket? = null,
    ): HeatmapPlan {
        val drawn = HeatmapSelection.select(assets, options, focus)
        // The scale is taken over what is drawn rather than over the whole catalogue, so the ramp
        // describes the map the reader is looking at. A scale set by four hundred markets and
        // applied to the forty on screen would wash the visible ones out for reasons invisible to
        // the person reading them.
        val values = drawn.mapNotNull { HeatmapMetrics.valueOf(it, options.colour) }
        val scale = HeatmapMetrics.scaleFor(values, options.colour)
        if (drawn.isEmpty() || width <= 0f || height <= 0f) {
            return HeatmapPlan(emptyList(), scale, options.palette)
        }

        val container = Rect4(0f, 0f, width, height)
        // A drilled-into map is already one bucket, and a strip naming it would repeat the title
        // the screen is already showing above the canvas.
        val cut = if (focus != null) HeatmapGrouping.NONE else options.grouping
        val groups = when (cut) {
            HeatmapGrouping.NONE -> listOf(
                block(null, drawn, container, header = null, options, scale, risingIsGreen),
            )
            HeatmapGrouping.BY_CLASS, HeatmapGrouping.BY_QUOTE -> byBucket(
                assets = drawn,
                grouping = cut,
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
     * Two treemaps, one inside the other: the buckets across the whole canvas, then the markets
     * inside each bucket.
     *
     * The buckets are sized by the same weights their members are, so a reader comparing a coin
     * against a currency pair is comparing like with like whichever level they read at. Ordering
     * them by weight rather than by the enum keeps the largest block in the reading corner, which
     * is where the squarified layout puts the largest tile too.
     */
    private fun byBucket(
        assets: List<HeatmapAsset>,
        grouping: HeatmapGrouping,
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
        val buckets = assets.groupBy { HeatmapSelection.bucketOf(it, grouping) }
            .toList()
            .sortedByDescending { (_, members) -> members.sumOf { weightOf.getValue(it) } }
        val weights = DoubleArray(buckets.size) { index ->
            buckets[index].second.sumOf { weightOf.getValue(it) }
        }
        val rects = Treemap.layout(weights, container.w, container.h)
        return buckets.mapIndexed { index, (bucket, members) ->
            val rect = rects[index]
            // A strip is only worth taking when what is left under it is still a map. Below this
            // the bucket's name would be sitting on two slivers, which tells the reader less than
            // the colours it displaced.
            val showHeader = groupHeaderHeight > 0f && rect.h >= groupHeaderHeight * MIN_HEADER_RATIO
            val header = if (showHeader) Rect4(rect.x, rect.y, rect.w, groupHeaderHeight) else null
            val body = if (header == null) {
                rect
            } else {
                Rect4(rect.x, rect.y + groupHeaderHeight, rect.w, rect.h - groupHeaderHeight)
            }
            block(bucket, members, body, header, options, scale, risingIsGreen)
        }
    }

    /** One treemap laid inside [body], with every tile coloured on the map-wide [scale]. */
    private fun block(
        bucket: HeatmapBucket?,
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
                // Unknown, not neutral. The two used to be the same call and that is the bug this
                // whole rework exists to fix: a market nobody has read must not take the colour of
                // a market that did not move.
                argb = if (value == null) {
                    HeatmapColours.unknown
                } else {
                    HeatmapColours.colourFor(value, scale, options.palette, risingIsGreen)
                },
                value = value,
            )
        }
        val rect = if (header == null) body else Rect4(header.x, header.y, body.w, body.h + header.h)
        return HeatmapGroup(bucket = bucket, rect = rect, header = header, tiles = tiles)
    }

    private fun HeatmapGroup.mirroredIn(width: Float) = copy(
        rect = rect.mirroredIn(width),
        header = header?.mirroredIn(width),
        tiles = tiles.map { it.copy(rect = it.rect.mirroredIn(width)) },
    )

    /**
     * A block must be this many header-heights tall before it is given one.
     *
     * Four, so at least three quarters of the block is still map.
     */
    private const val MIN_HEADER_RATIO = 4f
}
