@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage

/*
 * The phone's drawables, in the browser — read from the very files Android packages.
 *
 * `core/designsystem/src/main/res/drawable/` holds 1,150 vector drawables, all generated from SVG by
 * `scripts/design/svg-to-vector.py`, plus two WebP marks. The bundle carries them unchanged under
 * `drawable/`, and this reads one when it is first drawn: the browser's own `DOMParser` for the XML,
 * Compose's own `addPathNodes` for the path grammar. One set of pictures, two platforms, no second
 * drawing of anything — and no megabytes of path strings compiled into the Wasm binary for icons a
 * visit never shows.
 *
 * What the generator emits is a small dialect, measured across all 1,150 files on 2026-09-23:
 * `vector`, `group` (translate only), `path` with a fill or a stroke, `fillType`, and linear
 * gradients through `aapt:attr`. That is what is read. Anything else is skipped rather than guessed.
 */

private fun warnJs(message: String): Unit = js("console.warn(message)")

private fun parseXml(text: String): JsAny? =
    js("(function () { var d = new DOMParser().parseFromString(text, 'application/xml'); return d.getElementsByTagName('parsererror').length ? null : d.documentElement; })()")

private fun childCount(node: JsAny): Int = js("node.children.length")

private fun childAt(node: JsAny, index: Int): JsAny = js("node.children[index]")

private fun tagOf(node: JsAny): String = js("node.tagName")

private fun attr(node: JsAny, name: String): String? = js("node.getAttribute('android:' + name)")

private fun rawAttr(node: JsAny, name: String): String? = js("node.getAttribute(name)")

/** `#RGB`, `#ARGB`, `#RRGGBB` or `#AARRGGBB`, the four forms Android accepts. */
internal fun parseAndroidColor(value: String?): Color? {
    val hex = value?.removePrefix("#") ?: return null
    if (value.firstOrNull() != '#') return null
    val expanded = when (hex.length) {
        3 -> "FF" + hex.map { "$it$it" }.joinToString("")
        4 -> hex.map { "$it$it" }.joinToString("")
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    val argb = expanded.toLongOrNull(16) ?: return null
    return Color(argb.toInt())
}

/** The same colour as an ARGB int, for `android.graphics.Color.parseColor`. */
fun parseAndroidColorArgb(value: String): Int? = parseAndroidColor(value)?.let { c ->
    ((c.alpha * 255 + 0.5f).toInt() shl 24) or ((c.red * 255 + 0.5f).toInt() shl 16) or
        ((c.green * 255 + 0.5f).toInt() shl 8) or (c.blue * 255 + 0.5f).toInt()
}

private fun dimension(value: String?): Float? =
    value?.removeSuffix("dp")?.removeSuffix("dip")?.removeSuffix("px")?.toFloatOrNull()

/** A drawable's XML as a Compose vector, or null when it is not one this dialect reads. */
internal fun vectorFromXml(name: String, text: String): ImageVector? {
    val root = parseXml(text) ?: return null
    if (tagOf(root) != "vector") return null
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = (dimension(attr(root, "width")) ?: 24f).dp,
        defaultHeight = (dimension(attr(root, "height")) ?: 24f).dp,
        viewportWidth = dimension(attr(root, "viewportWidth")) ?: 24f,
        viewportHeight = dimension(attr(root, "viewportHeight")) ?: 24f,
        autoMirror = attr(root, "autoMirrored") == "true",
    )
    addChildren(builder, root)
    return builder.build()
}

private fun addChildren(builder: ImageVector.Builder, node: JsAny) {
    for (index in 0 until childCount(node)) {
        val child = childAt(node, index)
        when (tagOf(child)) {
            "group" -> {
                builder.addGroup(
                    rotate = dimension(attr(child, "rotation")) ?: 0f,
                    pivotX = dimension(attr(child, "pivotX")) ?: 0f,
                    pivotY = dimension(attr(child, "pivotY")) ?: 0f,
                    scaleX = dimension(attr(child, "scaleX")) ?: 1f,
                    scaleY = dimension(attr(child, "scaleY")) ?: 1f,
                    translationX = dimension(attr(child, "translateX")) ?: 0f,
                    translationY = dimension(attr(child, "translateY")) ?: 0f,
                )
                addChildren(builder, child)
                builder.clearGroup()
            }
            "path" -> addPath(builder, child)
        }
    }
}

private fun addPath(builder: ImageVector.Builder, node: JsAny) {
    val data = attr(node, "pathData") ?: return
    val nodes = runCatching { addPathNodes(data) }.getOrNull() ?: return
    var fill: Brush? = parseAndroidColor(attr(node, "fillColor"))?.let(::SolidColor)
    var stroke: Brush? = parseAndroidColor(attr(node, "strokeColor"))?.let(::SolidColor)
    // `<aapt:attr name="android:fillColor"><gradient …>` — the inline form the generator writes.
    for (index in 0 until childCount(node)) {
        val child = childAt(node, index)
        if (tagOf(child) != "aapt:attr") continue
        val gradient = (0 until childCount(child)).map { childAt(child, it) }
            .firstOrNull { tagOf(it) == "gradient" }
            ?.let(::gradientBrush) ?: continue
        when (rawAttr(child, "name")) {
            "android:fillColor" -> fill = gradient
            "android:strokeColor" -> stroke = gradient
        }
    }
    builder.addPath(
        pathData = nodes,
        pathFillType = if (attr(node, "fillType") == "evenOdd") PathFillType.EvenOdd else PathFillType.NonZero,
        fill = fill,
        fillAlpha = dimension(attr(node, "fillAlpha")) ?: 1f,
        stroke = stroke,
        strokeAlpha = dimension(attr(node, "strokeAlpha")) ?: 1f,
        strokeLineWidth = dimension(attr(node, "strokeWidth")) ?: 0f,
        strokeLineCap = when (attr(node, "strokeLineCap")) {
            "round" -> StrokeCap.Round
            "square" -> StrokeCap.Square
            else -> StrokeCap.Butt
        },
        strokeLineJoin = when (attr(node, "strokeLineJoin")) {
            "round" -> StrokeJoin.Round
            "bevel" -> StrokeJoin.Bevel
            else -> StrokeJoin.Miter
        },
    )
}

private fun gradientBrush(node: JsAny): Brush? {
    val start = Offset(dimension(attr(node, "startX")) ?: 0f, dimension(attr(node, "startY")) ?: 0f)
    val end = Offset(dimension(attr(node, "endX")) ?: 0f, dimension(attr(node, "endY")) ?: 0f)
    val stops = (0 until childCount(node)).map { childAt(node, it) }
        .filter { tagOf(it) == "item" }
        .mapNotNull { item ->
            val colour = parseAndroidColor(attr(item, "color")) ?: return@mapNotNull null
            (dimension(attr(item, "offset")) ?: 0f) to colour
        }
    val colours = if (stops.isNotEmpty()) {
        stops
    } else {
        listOfNotNull(
            parseAndroidColor(attr(node, "startColor"))?.let { 0f to it },
            parseAndroidColor(attr(node, "centerColor"))?.let { 0.5f to it },
            parseAndroidColor(attr(node, "endColor"))?.let { 1f to it },
        )
    }
    if (colours.size < 2) return colours.firstOrNull()?.second?.let(::SolidColor)
    return Brush.linearGradient(colorStops = colours.toTypedArray(), start = start, end = end)
}

/** One drawable as the browser has it: a vector, a bitmap, or known to be missing. */
sealed interface WebDrawable {
    data class Vector(val image: ImageVector) : WebDrawable
    data class Bitmap(val image: ImageBitmap) : WebDrawable
    data object Missing : WebDrawable
}

/**
 * Every drawable the page has asked for, loaded once.
 *
 * State, so the first frame draws nothing where an icon is still arriving and the next one draws
 * it, without the caller doing anything. A name the bundle does not have is remembered as missing,
 * which keeps a list of symbols from asking for the same absent logo on every scroll.
 */
object Drawables {
    private val loaded = mutableStateMapOf<String, WebDrawable>()
    private val inFlight = HashSet<String>()

    private val scope = kotlinx.coroutines.MainScope()

    fun peek(name: String): WebDrawable? = loaded[name]

    /**
     * Fetches [name] unless it is loaded or already on its way. Readers see it through [peek].
     *
     * The fetch belongs to the page, not to the composable that first asked. A row composed for one
     * frame and dropped — a list settling, a sheet opening — used to cancel it half way, and the name
     * stayed «on its way» for the rest of the visit: three toolbar icons that never drew.
     */
    fun ensure(name: String) {
        if (name in loaded || !inFlight.add(name)) return
        scope.launch {
            try {
                load(name)
            } finally {
                inFlight.remove(name)
            }
        }
    }

    private suspend fun load(name: String) {
        val path = DrawableIndex.pathOf(name)
        val result = when {
            path == null -> WebDrawable.Missing
            path.endsWith(".xml") -> fetchText(path)?.let { vectorFromXml(name, it) }?.let { WebDrawable.Vector(it) }
            else -> fetchBytes(path)
                ?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
                ?.let { WebDrawable.Bitmap(it) }
        } ?: WebDrawable.Missing
        if (result == WebDrawable.Missing) warnJs("drawable missing: $name ($path)")
        loaded[name] = result
    }
}

/** Where each drawable is in the bundle — `drawable-index.json`, from `indexTerminalDrawables`. */
object DrawableIndex {
    private var english: Map<String, String> = emptyMap()
    private var persian: Map<String, String> = emptyMap()

    suspend fun load(): Boolean {
        val root = fetchText("drawable-index.json")?.let(::parseJson) ?: return false
        english = objectOfStringsPublic(root, "en")
        persian = objectOfStringsPublic(root, "fa")
        return true
    }

    fun pathOf(name: String): String? = (if (Strings.persian) persian else english)[name]
}

/** A drawable by its Android name — `tv_bell`, `asset_btc`, `nav_home` — or null until it arrives. */
@Composable
fun rememberDrawable(name: String): WebDrawable? {
    LaunchedEffect(name) { Drawables.ensure(name) }
    // A read of the state map, so this recomposes the moment the drawable lands.
    return Drawables.peek(name)
}

/** The painter for a drawable, or null while it loads or when the bundle has none by that name. */
@Composable
fun drawablePainter(name: String): Painter? = when (val drawable = rememberDrawable(name)) {
    is WebDrawable.Vector -> rememberVectorPainter(drawable.image)
    is WebDrawable.Bitmap -> remember(drawable) { BitmapPainter(drawable.image) }
    WebDrawable.Missing, null -> null
}

/** An icon by its Android name, tinted like `Icon` tints — or nothing while it loads. */
@Composable
fun DrawableIcon(name: String, size: Dp, tint: Color?, modifier: Modifier = Modifier, description: String? = null) {
    val painter = drawablePainter(name) ?: return
    Image(
        painter = painter,
        contentDescription = description,
        modifier = modifier.size(size),
        colorFilter = tint?.let { ColorFilter.tint(it) },
    )
}
