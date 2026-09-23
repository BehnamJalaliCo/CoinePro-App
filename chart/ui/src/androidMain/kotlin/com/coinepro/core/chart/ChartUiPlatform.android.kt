package com.coinepro.core.chart

import android.graphics.BitmapFactory
import androidx.compose.foundation.magnifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProLatinFontFamily
import com.coinepro.core.designsystem.coineProControl
import com.coinepro.core.designsystem.R as DesignR
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The phone's side of `ChartUiPlatform.kt`. Every body here is the code that sat at the call site
 * before the web port, moved and not changed — see that file's rule.
 */

actual typealias ChartTimeZone = ZoneId

internal actual val ChartTehranZone: ChartTimeZone = ZoneId.of("Asia/Tehran")

internal actual fun systemChartTimeZone(): ChartTimeZone = ZoneId.systemDefault()

internal actual fun ChartTimeZone.toChartZone(): ChartZone = asChartZone()

internal actual fun formatZoned(epochSeconds: Long, zone: ChartTimeZone, pattern: String): String =
    Instant.ofEpochSecond(epochSeconds).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, Locale.US))

internal actual fun localEpochDay(epochSeconds: Long, zone: ChartTimeZone): Long =
    Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate().toEpochDay()

internal actual fun persianTimeTick(tick: TimeTick, at: Long, spanSeconds: Long, zone: ChartTimeZone): String {
    val moment = Instant.ofEpochSecond(at)
    val date = moment.atZone(zone).toLocalDate()
    val day = JalaliDate.fromGregorianOrNull(date) ?: return PersianDateTime.UNREPRESENTABLE
    return when (tick.unit) {
        TimeTickUnit.YEAR -> day.year.toPersianDigits()
        TimeTickUnit.MONTH ->
            if (spanSeconds >= SPAN_MULTI_YEAR) day.monthName + " " + day.year.toPersianDigits()
            else day.monthName
        TimeTickUnit.WEEK, TimeTickUnit.DAY -> day.formatShort()
        TimeTickUnit.HOUR, TimeTickUnit.MINUTE -> PersianDateTime.clock(moment, zone)
        // The even-spread case, which is the one place the *span* rather than the boundary decides.
        null -> when {
            spanSeconds >= SPAN_MULTI_YEAR -> day.monthName + " " + day.year.toPersianDigits()
            spanSeconds >= SPAN_MULTI_DAY -> day.formatShort()
            else -> PersianDateTime.clock(moment, zone)
        }
    }
}

internal actual val ChartLatinFontFamily: FontFamily = CoineProLatinFontFamily

@Composable
internal actual fun screenWidthDp(): Int = LocalConfiguration.current.screenWidthDp

/**
 * Read from the configuration rather than from `Locale.getDefault()`: this app sets its language
 * per-app, so the process default and the configuration can legitimately disagree — and the
 * configuration is the one every other date in the app is already drawn from.
 */
@Composable
internal actual fun deviceReadsPersian(): Boolean =
    LocalConfiguration.current.locales[0]?.language == AppLanguage.PERSIAN.tag

@Composable
internal actual fun rememberChartHost(): ChartHost {
    val view = LocalView.current
    return remember(view) { AndroidChartHost(view) }
}

/** [ChartFrameRate] and [ChartStrokePredictor], behind the one name the canvas talks to. */
internal class AndroidChartHost(private val view: android.view.View) : ChartHost {
    val strokePredictor = ChartStrokePredictor(view)

    override fun requestHighFrameRate(high: Boolean) = ChartFrameRate.request(view, high)

    override val predicted: Offset? get() = strokePredictor.predicted

    override fun resetPrediction() = strokePredictor.reset()
}

/** The predictor speaks MotionEvent; this feeds it and consumes nothing. */
@OptIn(ExperimentalComposeUiApi::class)
internal actual fun Modifier.recordStylus(host: ChartHost): Modifier =
    pointerInteropFilter { event ->
        (host as? AndroidChartHost)?.strokePredictor?.record(event)
        false
    }

/** Android's own magnifier. Below Android 9 the modifier is a no-op, which is the platform's answer. */
internal actual fun Modifier.chartMagnifier(
    sourceCenter: () -> Offset,
    zoom: Float,
    size: DpSize,
    cornerRadius: Dp,
): Modifier = magnifier(sourceCenter = { sourceCenter() }, zoom = zoom, size = size, cornerRadius = cornerRadius)

/**
 * One filled path with a soft shadow under it.
 *
 * ### Why this reaches for the framework's paint
 *
 * A blur is the one effect Compose's `DrawScope` has no expression for, and the modifier that does
 * — `Modifier.blur` — is banned in this repository and would be the wrong tool anyway: it is a
 * render-effect pass over a whole layer, per frame, and what is wanted here is four points of
 * softness under one small chip. `Paint.setShadowLayer` is the platform's own answer, it costs
 * nothing when nothing asks for it, and it is what a shadow on a canvas is drawn with.
 *
 * The house rule it does **not** break is the one about coloured glows: [SHADOW_INK] is black at low
 * alpha, in both themes, which is exactly what `check-motion-policy.sh` requires of every shadow in
 * the product.
 *
 * ### The paint is shared, and that is safe here
 *
 * One instance for the module rather than one per frame, because this is called on every pointer
 * move while a crosshair is up and a `Paint` per frame at 120 Hz is 120 allocations a second in the
 * one place this chart has spent three runs removing them from. Every draw happens on the UI thread
 * — a `DrawScope` has nowhere else to happen — and the paint is fully reconfigured on each call, so
 * nothing survives between them.
 */
internal actual fun DrawScope.drawSoftShadowedPath(path: Path, fill: Color, blur: Float) {
    drawIntoCanvas { canvas ->
        val paint = chipShadowPaint
        paint.reset()
        paint.isAntiAlias = true
        paint.color = fill.toArgb()
        paint.setShadowLayer(blur, 0f, blur * SHADOW_DROP, SHADOW_INK.toArgb())
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
    }
}

/** See [drawSoftShadowedPath] for why there is one of these rather than one per frame. */
private val chipShadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

internal actual fun decodeDrawingImage(bytes: ByteArray): ImageBitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()?.asImageBitmap()

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun <T> chartLocked(lock: Any, block: () -> T): T = synchronized(lock, block)

@Composable
internal actual fun Modifier.chartControl(onClick: () -> Unit): Modifier = coineProControl(onClick = onClick)

@Composable
internal actual fun ChartAssetLogo(symbol: String, size: Dp) = CoineProAssetLogo(symbol = symbol, size = size)

@Composable
internal actual fun chartGlyph(glyph: ChartGlyph): Painter = painterResource(
    when (glyph) {
        ChartGlyph.BELL -> DesignR.drawable.tv_bell
        ChartGlyph.LONG_SHORT -> DesignR.drawable.tv_tool_longshort
    },
)

@Composable
internal actual fun chartText(text: ChartText): String = stringResource(
    when (text) {
        ChartText.LEGEND_HIDE -> DesignR.string.legend_hide
        ChartText.LEGEND_SHOW -> DesignR.string.legend_show
        ChartText.LEGEND_SETTINGS -> DesignR.string.legend_settings
        ChartText.LEGEND_REMOVE -> DesignR.string.legend_remove
        ChartText.LEGEND_CONTROLS_CLOSE -> DesignR.string.legend_controls_close
        ChartText.LEGEND_CONTROLS_OPEN -> DesignR.string.legend_controls_open
        ChartText.LEGEND_BACK -> DesignR.string.legend_back
    },
)

/** The characters these marks always were. The system's fallback fonts draw the ones IRANYekanX lacks. */
internal actual object ChartMarks {
    actual val separator: String = "·"
    actual val change: String = "Δ"
    actual val visible: String = "◉"
    actual val hidden: String = "◌"
    actual val settings: String = "⋮"
    actual val remove: String = "✕"
    actual val backLtr: String = "←"
    actual val backRtl: String = "→"
    actual val expand: String = "⋯"
    actual val collapse: String = "⌃"
    actual val overflow: String = "▸ "
    actual val rising: String = "▲"
    actual val falling: String = "▼"
    actual val captionJoin: String = " — "
    actual val noValue: String = "∅"
}
