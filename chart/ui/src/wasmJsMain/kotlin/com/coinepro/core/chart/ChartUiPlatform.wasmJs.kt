package com.coinepro.core.chart

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image as SkiaImage

/*
 * The browser's side of `ChartUiPlatform.kt`.
 *
 * Where the browser cannot do what the phone does, this does without and says so on the spot:
 * no refresh-rate vote (a browser paints at the display's rate already), no stylus prediction, no
 * magnifier lens, no native shadow. None of these is faked.
 */

/**
 * The page's own settings for the chart, set once by the web app before the first frame.
 *
 * State rather than plain fields so that switching the terminal's language redraws the axis and
 * the legend at once.
 */
object ChartWeb {
    /** Whether the reader chose Persian. The product's default, as on the phone. */
    var persian: Boolean by mutableStateOf(true)

    /** IRANYekanX, loaded from the bundle's bytes by the web app — see `:web`'s font loader. */
    var fontFamily: FontFamily by mutableStateOf(FontFamily.Default)

    /** An instrument's logo, or null to draw none — never a lettered disc. */
    var assetLogo: (@Composable (symbol: String, size: Dp) -> Unit)? by mutableStateOf(null)
}

/** A fixed-offset zone. Iran has kept +03:30 all year since 2022, so a fixed offset is exact. */
actual abstract class ChartTimeZone {
    abstract val zone: ChartZone
}

private class WebChartTimeZone(override val zone: ChartZone) : ChartTimeZone()

internal actual val ChartTehranZone: ChartTimeZone = WebChartTimeZone(ChartZone.fixed(TEHRAN_OFFSET_SECONDS))

/** The browser's zone, asked per moment — see `systemChartZone` in `:chart-core`. */
internal actual fun systemChartTimeZone(): ChartTimeZone = WebChartTimeZone(systemChartZone())

internal actual fun ChartTimeZone.toChartZone(): ChartZone = zone

private const val TEHRAN_OFFSET_SECONDS = 12_600L

private val MONTH_NAMES = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/**
 * The pattern letters the chart uses — `yyyy`, `yy`, `MMM`, `d`, `HH`, `mm` — and no others. An
 * unknown letter is copied through, so a mistake shows in the label rather than hiding in it.
 */
internal actual fun formatZoned(epochSeconds: Long, zone: ChartTimeZone, pattern: String): String {
    val local = epochSeconds + zone.zone.offsetSeconds(epochSeconds)
    val day = floorDivide(local, SECONDS_PER_DAY)
    val secondOfDay = local - day * SECONDS_PER_DAY
    val date = civilOfEpochDay(day)
    val hour = (secondOfDay / 3_600L).toInt()
    val minute = ((secondOfDay % 3_600L) / 60L).toInt()
    val out = StringBuilder(pattern.length + 4)
    var index = 0
    while (index < pattern.length) {
        val letter = pattern[index]
        var run = 1
        while (index + run < pattern.length && pattern[index + run] == letter) run++
        when {
            letter == 'y' && run == 2 -> out.append(pad2(date[0] % 100))
            letter == 'y' -> out.append(date[0])
            letter == 'M' && run >= 3 -> out.append(MONTH_NAMES[date[1] - 1])
            letter == 'M' && run == 2 -> out.append(pad2(date[1]))
            letter == 'M' -> out.append(date[1])
            letter == 'd' && run >= 2 -> out.append(pad2(date[2]))
            letter == 'd' -> out.append(date[2])
            letter == 'H' && run >= 2 -> out.append(pad2(hour))
            letter == 'H' -> out.append(hour)
            letter == 'm' && run >= 2 -> out.append(pad2(minute))
            letter == 'm' -> out.append(minute)
            else -> repeat(run) { out.append(letter) }
        }
        index += run
    }
    return out.toString()
}

internal actual fun localEpochDay(epochSeconds: Long, zone: ChartTimeZone): Long =
    floorDivide(epochSeconds + zone.zone.offsetSeconds(epochSeconds), SECONDS_PER_DAY)

/**
 * The same four shapes as the phone's, from the same conversion: `JalaliDate` in `:core:common`
 * (Borkowski's break table), ported line for line because that module is Android's.
 */
internal actual fun persianTimeTick(tick: TimeTick, at: Long, spanSeconds: Long, zone: ChartTimeZone): String {
    val day = jalaliOfEpochDay(localEpochDay(at, zone)) ?: return UNREPRESENTABLE
    val year = day[0].persianDigits()
    val month = JALALI_MONTHS[day[1] - 1]
    val short = "${day[2].persianDigits()} $month"
    val clock = LRI + formatZoned(at, zone, "HH:mm") + PDI
    return when (tick.unit) {
        TimeTickUnit.YEAR -> year
        TimeTickUnit.MONTH -> if (spanSeconds >= SPAN_MULTI_YEAR) "$month $year" else month
        TimeTickUnit.WEEK, TimeTickUnit.DAY -> short
        TimeTickUnit.HOUR, TimeTickUnit.MINUTE -> clock
        null -> when {
            spanSeconds >= SPAN_MULTI_YEAR -> "$month $year"
            spanSeconds >= SPAN_MULTI_DAY -> short
            else -> clock
        }
    }
}

internal actual val ChartLatinFontFamily: FontFamily get() = ChartWeb.fontFamily

@Composable
internal actual fun screenWidthDp(): Int {
    val width = LocalWindowInfo.current.containerSize.width
    return (width / LocalDensity.current.density).toInt()
}

@Composable
internal actual fun deviceReadsPersian(): Boolean = ChartWeb.persian

/** A browser paints at the display's own rate and has no stylus predictor: nothing to ask for. */
private object WebChartHost : ChartHost {
    override fun requestHighFrameRate(high: Boolean) = Unit
    override val predicted: Offset? get() = null
    override fun resetPrediction() = Unit
}

@Composable
internal actual fun rememberChartHost(): ChartHost = WebChartHost

/** Does without: there is no predictor to feed. The live stroke is drawn to the last real point. */
internal actual fun Modifier.recordStylus(host: ChartHost): Modifier = this

/** Does without: a pointer does not cover what it is placing, so there is no lens to show. */
internal actual fun Modifier.chartMagnifier(
    sourceCenter: () -> Offset,
    zoom: Float,
    size: DpSize,
    cornerRadius: Dp,
): Modifier = this

/** Does without the blur: the chip is drawn plain rather than with a faked shadow. */
internal actual fun DrawScope.drawSoftShadowedPath(path: Path, fill: Color, blur: Float) {
    drawPath(path, color = fill)
}

internal actual fun decodeDrawingImage(bytes: ByteArray): ImageBitmap? =
    runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/** One thread: nothing to lock. */
@Suppress("NOTHING_TO_INLINE", "UNUSED_PARAMETER")
internal actual inline fun <T> chartLocked(lock: Any, block: () -> T): T = block()

@Composable
internal actual fun Modifier.chartControl(onClick: () -> Unit): Modifier =
    pointerHoverIcon(PointerIcon.Hand).clickable(interactionSource = null, indication = null, onClick = onClick)

@Composable
internal actual fun ChartAssetLogo(symbol: String, size: Dp) {
    ChartWeb.assetLogo?.invoke(symbol, size)
}

@Composable
internal actual fun chartGlyph(glyph: ChartGlyph): Painter {
    val vector = remember(glyph) {
        when (glyph) {
            ChartGlyph.BELL -> glyphVector("bell", BELL_PATHS)
            ChartGlyph.LONG_SHORT -> glyphVector("longshort", LONG_SHORT_PATHS)
        }
    }
    return rememberVectorPainter(vector)
}

/**
 * The page's own arrow, on a disc of the title's colour — see the `expect` for why the page does
 * not print a mark here. The same arrow as the phone's app bar used to hold (Material's
 * `arrow_back`, 24-unit viewport), turned to point the way the reader's language came from.
 */
@Composable
internal actual fun ChartBackMark(glyph: String, pointsRight: Boolean, colour: Color, fontSize: TextUnit) {
    val painter = rememberVectorPainter(remember { backArrowVector() })
    Box(
        modifier = Modifier
            .size(BACK_DISC_DP)
            .clip(CircleShape)
            .background(colour.copy(alpha = BACK_DISC_ALPHA))
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(colour),
            modifier = Modifier
                .size(BACK_ARROW_DP)
                .graphicsLayer { scaleX = if (pointsRight) -1f else 1f },
        )
    }
}

private fun backArrowVector(): ImageVector =
    ImageVector.Builder(
        name = "back",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        addPath(pathData = addPathNodes(BACK_ARROW_PATH), fill = SolidColor(Color.Black))
    }.build()

private const val BACK_ARROW_PATH = "M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z"
private val BACK_DISC_DP = 24.dp
private val BACK_ARROW_DP = 18.dp
private const val BACK_DISC_ALPHA = 0.16f

@Composable
internal actual fun chartText(text: ChartText): String {
    val persian = ChartWeb.persian
    return when (text) {
        ChartText.LEGEND_HIDE -> if (persian) "پنهان کردن" else "Hide"
        ChartText.LEGEND_SHOW -> if (persian) "نمایش دادن" else "Show"
        ChartText.LEGEND_SETTINGS -> if (persian) "تنظیمات" else "Settings"
        ChartText.LEGEND_REMOVE -> if (persian) "حذف" else "Remove"
        ChartText.LEGEND_CONTROLS_CLOSE -> if (persian) "بستن کنترل‌ها" else "Close the controls"
        ChartText.LEGEND_CONTROLS_OPEN -> if (persian) "کنترل‌های اندیکاتورها" else "Indicator controls"
        ChartText.LEGEND_BACK -> if (persian) "بازگشت" else "Back"
    }
}

/*
 * The two glyphs, from the same path data as `tv_bell.xml` and `tv_tool_longshort.xml` in
 * `:core:designsystem` — the vendored set, 28-unit viewport. The browser cannot read an Android
 * vector resource, so `check_web_glyphs` in `check-cross-phase-consistency.py` compares these
 * strings with the XML on every commit.
 */
private val BELL_PATHS = listOf(
    "m 19.54 4.5 3.96 4.32 -.74 .68 -3.96 -4.32 .74 -.68ZM 7.46 4.5 3.5 8.82l .74 .68L 8.2 5.18l -.74 -.68ZM 19.74 10.33A 7.5 7.5 0 0 1 21 14.5v .5h 1v -.5a 8.5 8.5 0 1 0 -8.5 8.5h .5v -1h -.5a 7.5 7.5 0 1 1 6.24 -11.67Z",
    "M 13 9v 5h -3v 1h 4V 9h -1ZM 19 20v -4h 1v 4h 4v 1h -4v 4h -1v -4h -4v -1h 4Z",
)

private val LONG_SHORT_PATHS = listOf(
    "M 5.5 20c 1.2 0 2.22 .86 2.45 2H 25v 1H 7.95a 2.5 2.5 0 1 1 -2.45 -3m 0 1a 1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0 -3M 25 18H 5v -1h 20zm -11 -4h 3v 1h -4V 9h 1zM 5.5 4c 1.2 0 2.22 .86 2.45 2H 25v 1H 7.95A 2.5 2.5 0 1 1 5.5 4m 0 1a 1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0 -3",
)

private fun glyphVector(name: String, paths: List<String>): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 28f,
        viewportHeight = 28f,
    ).apply {
        paths.forEach { data -> addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black)) }
    }.build()

// ── Calendar arithmetic ────────────────────────────────────────────────────────────────────────

private const val SECONDS_PER_DAY = 86_400L
/** `PersianDateTime.UNREPRESENTABLE` is an em dash, which IRANYekanX does not carry. See [ChartMarks]. */
private val UNREPRESENTABLE: String get() = ChartMarks.noValue
private const val LRI = '⁦'
private const val PDI = '⁩'

private val JALALI_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

private fun Int.persianDigits(): String = toString().map { c ->
    if (c in '0'..'9') '۰' + (c - '0') else c
}.joinToString("")

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()

private fun floorDivide(value: Long, divisor: Long): Long {
    val quotient = value / divisor
    return if (value % divisor != 0L && (value xor divisor) < 0L) quotient - 1L else quotient
}

/** Howard Hinnant's `civil_from_days`: `[year, month, day]`. */
private fun civilOfEpochDay(epochDay: Long): IntArray {
    val z = epochDay + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    return intArrayOf((if (m <= 2) y + 1 else y).toInt(), m, d)
}

/** Howard Hinnant's `days_from_civil`. */
private fun epochDayOfCivil(year: Int, month: Int, day: Int): Long {
    val y = (if (month <= 2) year - 1 else year).toLong()
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097 + doe - 719_468
}

private val BREAKS = intArrayOf(
    -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
    1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
)

/** `JalaliDate.fromGregorianOrNull`, on a day number: `[year, month, day]`, or null off the table. */
private fun jalaliOfEpochDay(epochDay: Long): IntArray? {
    val gregorianYear = civilOfEpochDay(epochDay)[0]
    var jalaliYear = gregorianYear - 621
    val (leap, march) = jalaliYearStart(jalaliYear) ?: return null
    val firstDay = epochDayOfCivil(jalaliYear + 621, 3, march)
    var offset = epochDay - firstDay
    if (offset >= 0) {
        if (offset <= 185) return intArrayOf(jalaliYear, 1 + (offset / 31).toInt(), (offset % 31).toInt() + 1)
        offset -= 186
    } else {
        jalaliYear -= 1
        offset += 179
        if (leap == 1) offset += 1
    }
    return intArrayOf(jalaliYear, 7 + (offset / 30).toInt(), (offset % 30).toInt() + 1)
}

/** `JalaliDate.calculate`: the leap indicator and the March day Nowruz falls on, or null off the table. */
private fun jalaliYearStart(jalaliYear: Int): Pair<Int, Int>? {
    if (jalaliYear < BREAKS.first() || jalaliYear >= BREAKS.last()) return null
    val gregorianYear = jalaliYear + 621
    var leapJ = -14
    var previousBreak = BREAKS[0]
    var jump = 0
    for (index in 1 until BREAKS.size) {
        val current = BREAKS[index]
        jump = current - previousBreak
        if (jalaliYear < current) break
        leapJ += (jump / 33) * 8 + (jump % 33) / 4
        previousBreak = current
    }
    var n = jalaliYear - previousBreak
    leapJ += (n / 33) * 8 + (n % 33 + 3) / 4
    if (jump % 33 == 4 && jump - n == 4) leapJ += 1
    val leapG = gregorianYear / 4 - ((gregorianYear / 100 + 1) * 3) / 4 - 150
    val march = 20 + leapJ - leapG
    if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
    var leap = ((n + 1) % 33 - 1) % 4
    if (leap == -1) leap = 4
    return leap to march
}

/**
 * The nearest mark IRANYekanX carries, for each one it does not — measured against the typeface's
 * own character map, not guessed. Where the phone's mark is a picture (an eye, a cross, an arrow)
 * this is the closest typographic sign; where it is a word-like mark (a delta, «nothing») it is the
 * sign a reader already reads that way.
 */
internal actual object ChartMarks {
    actual val separator: String = "•"
    actual val change: String = "±"
    actual val visible: String = "•"
    actual val hidden: String = "°"
    actual val settings: String = "¦"
    actual val remove: String = "×"
    actual val backLtr: String = "‹"
    actual val backRtl: String = "›"
    actual val expand: String = "…"
    actual val collapse: String = "−"
    actual val overflow: String = "› "
    actual val rising: String = "+"
    actual val falling: String = "−"
    actual val captionJoin: String = " • "
    actual val noValue: String = "…"
}
