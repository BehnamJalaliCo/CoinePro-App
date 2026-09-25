package com.coinepro.core.chart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit

/**
 * Everything the Compose chart needs from its platform, in one file — the web port's seam (W1b).
 *
 * `:chart-core` has the same thing for the engine (`ChartPlatform.kt`, five functions). This is the
 * drawing layer's, and it is longer because drawing touches more of the platform: a time zone, a
 * typeface, the display's refresh rate, a stylus predictor, a magnifier, a native shadow, an image
 * decoder, two icons and a handful of strings.
 *
 * ### The rule every `actual` here follows
 *
 * **The Android `actual` is the code that was there before, moved and not changed.** Every line
 * below was lifted out of `CoineProChart.kt`, `DrawingRenderer.kt` or `ChartLegendOverlay.kt` and
 * put behind a name; the phone runs the same calls in the same order. The golden screenshot tests
 * are what hold that, and they are why this refactor could be done at all.
 *
 * The browser `actual` is new, and where the browser genuinely cannot do something it **does
 * without and says so in its own KDoc** — no frame-rate vote, no stylus prediction, no magnifier
 * lens. It never fakes the phone's behaviour. `DeepNesting.kt` in `:namascript` is the shape.
 */

/**
 * A time zone as the platform knows one.
 *
 * On Android this *is* `java.time.ZoneId` — an `actual typealias`, so every Android caller keeps
 * passing the `ZoneId` it always passed and not one call site changes. In the browser it wraps the
 * engine's own [ChartZone].
 */
expect abstract class ChartTimeZone

/** Iran's zone: where the product's readers are, and the chart's default. */
internal expect val ChartTehranZone: ChartTimeZone

/** The device's own zone. */
internal expect fun systemChartTimeZone(): ChartTimeZone

/** The same zone as the engine's platform-free [ChartZone]. */
internal expect fun ChartTimeZone.toChartZone(): ChartZone

/** `epochSeconds` in [zone], formatted with a `DateTimeFormatter` pattern and Latin month names. */
internal expect fun formatZoned(epochSeconds: Long, zone: ChartTimeZone, pattern: String): String

/** The local calendar day `epochSeconds` falls on in [zone], as days since 1970-01-01. */
internal expect fun localEpochDay(epochSeconds: Long, zone: ChartTimeZone): Long

/** A time-axis label in Solar Hijri. See `formatTimeTick`'s `jalali`. */
internal expect fun persianTimeTick(tick: TimeTick, at: Long, spanSeconds: Long, zone: ChartTimeZone): String

/** IRANYekanX, for the axis and every price the canvas prints. */
internal expect val ChartLatinFontFamily: FontFamily

/** The window's width, in dp — the phone/tablet split some drawing decisions take. */
@Composable
internal expect fun screenWidthDp(): Int

/** Whether the device reads Persian — the default for the axis calendar. */
@Composable
internal expect fun deviceReadsPersian(): Boolean

/**
 * What the chart asks of the view it is drawn in: a refresh-rate vote while it moves, and the
 * stylus's predicted next point for live ink.
 */
internal interface ChartHost {
    /** Ask for the display's highest rate while [high], and let it go otherwise. */
    fun requestHighFrameRate(high: Boolean)

    /** Where the stylus will be next frame, or null when nothing can say. */
    val predicted: Offset?

    /** Forget the prediction — the stroke ended. */
    fun resetPrediction()
}

@Composable
internal expect fun rememberChartHost(): ChartHost

/** Feeds raw stylus events to [host]'s predictor, consuming nothing. */
internal expect fun Modifier.recordStylus(host: ChartHost): Modifier

/** The lens over a held drawing handle. */
internal expect fun Modifier.chartMagnifier(
    sourceCenter: () -> Offset,
    zoom: Float,
    size: DpSize,
    cornerRadius: Dp,
): Modifier

/** A filled path with a soft shadow under it — the crosshair's price chip. */
internal expect fun DrawScope.drawSoftShadowedPath(path: Path, fill: Color, blur: Float)

/** A reader's picture, decoded, or null for bytes that are not one. */
internal expect fun decodeDrawingImage(bytes: ByteArray): ImageBitmap?

/** [block] under a lock where the platform has threads; plainly where it has one. */
internal expect inline fun <T> chartLocked(lock: Any, block: () -> T): T

/** The press behaviour every small control in this product has. */
@Composable
internal expect fun Modifier.chartControl(onClick: () -> Unit): Modifier

/** An instrument's logo, or nothing — never a lettered disc. */
@Composable
internal expect fun ChartAssetLogo(symbol: String, size: Dp)

/** The two glyphs the canvas itself draws. */
enum class ChartGlyph { BELL, LONG_SHORT }

@Composable
internal expect fun chartGlyph(glyph: ChartGlyph): Painter

/**
 * The legend's way back, drawn inside its button. [glyph] is [ChartMarks.backLtr] or
 * [ChartMarks.backRtl]; [pointsRight] says which.
 *
 * The phone prints the arrow character, as it always has. A browser page has only IRANYekanX,
 * whose nearest mark is a thin «›» that readers did not take for a way out, so the page draws
 * the arrow itself, on a disc.
 */
@Composable
internal expect fun ChartBackMark(glyph: String, pointsRight: Boolean, colour: Color, fontSize: TextUnit)

/** The legend's own words — the only prose on the canvas. */
enum class ChartText {
    LEGEND_HIDE,
    LEGEND_SHOW,
    LEGEND_SETTINGS,
    LEGEND_MORE,
    LEGEND_REMOVE,
    LEGEND_CONTROLS_CLOSE,
    LEGEND_CONTROLS_OPEN,
    LEGEND_BACK,
}

@Composable
internal expect fun chartText(text: ChartText): String

/**
 * The few marks the canvas prints that are not letters or digits — a separator, a delta, the
 * legend's small controls.
 *
 * On the phone these are the characters they always were, and the system's fallback fonts draw
 * the ones IRANYekanX does not have. A browser page has no fallback: the only face in it is
 * IRANYekanX (one typeface, by the owner's rule), so a mark outside it would be an empty box. The
 * browser's `actual` therefore picks, for each, the nearest mark the typeface *does* carry.
 */
internal expect object ChartMarks {
    /** Between two facts on one line — U+00B7 on the phone. */
    val separator: String
    /** Names the change row and the measure tool's move. */
    val change: String
    val visible: String
    val hidden: String
    val settings: String
    val remove: String
    val backLtr: String
    val backRtl: String
    val expand: String
    val collapse: String
    /** Before the «+N» of a collapsed row, space included. */
    val overflow: String
    val rising: String
    val falling: String
    /** Between a missing picture's two captions, spaces included. */
    val captionJoin: String
    /** A reading with no value. See [NO_VALUE]. */
    val noValue: String
}
