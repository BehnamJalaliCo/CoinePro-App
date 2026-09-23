@file:Suppress("unused", "UNUSED_PARAMETER")

package android.graphics

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.Surface

/** A picture, held as Compose's `ImageBitmap` — the form every browser surface draws. */
class Bitmap internal constructor(val image: ImageBitmap) {
    val width: Int get() = image.width
    val height: Int get() = image.height
    val config: Config get() = Config.ARGB_8888
    fun recycle() {}
    val isRecycled: Boolean get() = false
    fun hasAlpha(): Boolean = image.hasAlpha

    /** PNG or JPEG through Skia's own encoders — the same formats the phone writes. */
    fun compress(format: CompressFormat, quality: Int, stream: java.io.OutputStream): Boolean {
        val skia = SkiaImage.makeFromBitmap(image.asSkiaBitmap())
        val data = skia.encodeToData(
            when (format) {
                CompressFormat.PNG -> EncodedImageFormat.PNG
                CompressFormat.JPEG -> EncodedImageFormat.JPEG
                else -> EncodedImageFormat.WEBP
            },
            quality,
        ) ?: return false
        stream.write(data.bytes)
        stream.flush()
        return true
    }

    enum class CompressFormat { PNG, JPEG, WEBP, WEBP_LOSSY, WEBP_LOSSLESS }
    enum class Config { ARGB_8888, RGB_565, ALPHA_8, HARDWARE }

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap = Bitmap(ImageBitmap(width, height))
        fun createBitmap(source: Bitmap): Bitmap = source
        fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap =
            createBitmap(source, x, y, width, height, null, false)

        /** A region of [source] through [matrix], as Android draws it — onto a surface sized to the result. */
        fun createBitmap(source: Bitmap, x: Int, y: Int, width: Int, height: Int, matrix: Matrix?, filter: Boolean): Bitmap {
            val m = matrix ?: Matrix()
            val corners = listOf(0f to 0f, width.toFloat() to 0f, 0f to height.toFloat(), width.toFloat() to height.toFloat()).map { m.map(it.first, it.second) }
            val minX = corners.minOf { it.first }; val maxX = corners.maxOf { it.first }
            val minY = corners.minOf { it.second }; val maxY = corners.maxOf { it.second }
            val outW = kotlin.math.max(1, kotlin.math.round(maxX - minX).toInt())
            val outH = kotlin.math.max(1, kotlin.math.round(maxY - minY).toInt())
            val surface = Surface.makeRasterN32Premul(outW, outH)
            val canvas = surface.canvas
            canvas.translate(-minX, -minY)
            canvas.concat(org.jetbrains.skia.Matrix33(m.a, m.b, m.c, m.d, m.e, m.f, 0f, 0f, 1f))
            val skia = SkiaImage.makeFromBitmap(source.image.asSkiaBitmap())
            canvas.drawImageRect(skia, SkiaRect.makeXYWH(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat()), SkiaRect.makeWH(width.toFloat(), height.toFloat()))
            return Bitmap(surface.makeImageSnapshot().toComposeImageBitmap())
        }

        fun createScaledBitmap(source: Bitmap, width: Int, height: Int, filter: Boolean): Bitmap {
            val surface = Surface.makeRasterN32Premul(width, height)
            val skia = SkiaImage.makeFromBitmap(source.image.asSkiaBitmap())
            surface.canvas.drawImageRect(skia, SkiaRect.makeWH(width.toFloat(), height.toFloat()))
            return Bitmap(surface.makeImageSnapshot().toComposeImageBitmap())
        }
    }
}

object BitmapFactory {
    class Options { var inJustDecodeBounds = false; var inSampleSize = 1; var outWidth = 0; var outHeight = 0; var inPreferredConfig: Bitmap.Config? = null }

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? = decodeByteArray(data, offset, length, null)

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int, options: Options?): Bitmap? {
        val image = runCatching { SkiaImage.makeFromEncoded(data.copyOfRange(offset, offset + length)) }.getOrNull() ?: return null
        options?.let { it.outWidth = image.width; it.outHeight = image.height }
        if (options?.inJustDecodeBounds == true) return null
        val sample = (options?.inSampleSize ?: 1).coerceAtLeast(1)
        val bitmap = Bitmap(image.toComposeImageBitmap())
        return if (sample == 1) bitmap else Bitmap.createScaledBitmap(bitmap, maxOf(1, image.width / sample), maxOf(1, image.height / sample), true)
    }

    fun decodeStream(stream: java.io.InputStream?): Bitmap? = stream?.readBytes()?.let { decodeByteArray(it, 0, it.size) }
    fun decodeStream(stream: java.io.InputStream?, padding: Rect?, options: Options?): Bitmap? =
        stream?.readBytes()?.let { decodeByteArray(it, 0, it.size, options) }
    fun decodeFile(path: String): Bitmap? = runCatching { java.io.File(path).readBytes() }.getOrNull()?.let { decodeByteArray(it, 0, it.size) }
}

class Rect(var left: Int = 0, var top: Int = 0, var right: Int = 0, var bottom: Int = 0) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}

class RectF(var left: Float = 0f, var top: Float = 0f, var right: Float = 0f, var bottom: Float = 0f) {
    fun width(): Float = right - left
    fun height(): Float = bottom - top
}

object Color {
    const val TRANSPARENT = 0
    const val BLACK = -0x1000000
    const val WHITE = -1
    fun argb(a: Int, r: Int, g: Int, b: Int): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(r: Int, g: Int, b: Int): Int = argb(255, r, g, b)
    fun alpha(c: Int): Int = c ushr 24
    fun red(c: Int): Int = (c shr 16) and 0xff
    fun green(c: Int): Int = (c shr 8) and 0xff
    fun blue(c: Int): Int = c and 0xff
    fun parseColor(s: String): Int = com.coinepro.web.parseAndroidColorArgb(s) ?: throw IllegalArgumentException("Unknown color")
}

object Typeface {
    const val NORMAL = 0
    const val BOLD = 1
    const val ITALIC = 2
    const val BOLD_ITALIC = 3
}

/** An affine transform: x' = a·x + b·y + c, y' = d·x + e·y + f — `android.graphics.Matrix`'s subset. */
class Matrix {
    internal var a = 1f; internal var b = 0f; internal var c = 0f
    internal var d = 0f; internal var e = 1f; internal var f = 0f

    private fun post(na: Float, nb: Float, nc: Float, nd: Float, ne: Float, nf: Float): Boolean {
        val a2 = na * a + nb * d; val b2 = na * b + nb * e; val c2 = na * c + nb * f + nc
        val d2 = nd * a + ne * d; val e2 = nd * b + ne * e; val f2 = nd * c + ne * f + nf
        a = a2; b = b2; c = c2; d = d2; e = e2; f = f2
        return true
    }

    fun reset() { a = 1f; b = 0f; c = 0f; d = 0f; e = 1f; f = 0f }
    fun postRotate(degrees: Float): Boolean {
        val r = degrees * kotlin.math.PI.toFloat() / 180f
        val cos = kotlin.math.cos(r); val sin = kotlin.math.sin(r)
        return post(cos, -sin, 0f, sin, cos, 0f)
    }
    fun postRotate(degrees: Float, px: Float, py: Float): Boolean { postTranslate(-px, -py); postRotate(degrees); return postTranslate(px, py) }
    fun postScale(sx: Float, sy: Float): Boolean = post(sx, 0f, 0f, 0f, sy, 0f)
    fun postScale(sx: Float, sy: Float, px: Float, py: Float): Boolean { postTranslate(-px, -py); postScale(sx, sy); return postTranslate(px, py) }
    fun postTranslate(dx: Float, dy: Float): Boolean = post(1f, 0f, dx, 0f, 1f, dy)
    fun preScale(sx: Float, sy: Float): Boolean { a *= sx; d *= sx; b *= sy; e *= sy; return true }
    fun setRotate(degrees: Float) { reset(); postRotate(degrees) }
    fun setScale(sx: Float, sy: Float) { reset(); postScale(sx, sy) }
    internal fun map(x: Float, y: Float): Pair<Float, Float> = (a * x + b * y + c) to (d * x + e * y + f)
}

/** `android.graphics.Canvas` over Compose's canvas on the bitmap's own pixels. */
class Canvas(bitmap: Bitmap) {
    private val canvas = androidx.compose.ui.graphics.Canvas(bitmap.image)
    fun drawColor(color: Int) {
        canvas.drawRect(0f, 0f, 1e6f, 1e6f, androidx.compose.ui.graphics.Paint().apply { this.color = androidx.compose.ui.graphics.Color(color) })
    }
    fun drawPath(path: Path, paint: Paint) = canvas.drawPath(path.compose, paint.compose())
    fun drawLine(x0: Float, y0: Float, x1: Float, y1: Float, paint: Paint) =
        canvas.drawLine(androidx.compose.ui.geometry.Offset(x0, y0), androidx.compose.ui.geometry.Offset(x1, y1), paint.compose())
    fun drawRect(l: Float, t: Float, r: Float, b: Float, paint: Paint) = canvas.drawRect(l, t, r, b, paint.compose())
    fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) =
        canvas.drawCircle(androidx.compose.ui.geometry.Offset(cx, cy), radius, paint.compose())
}

class Path() {
    internal val compose = androidx.compose.ui.graphics.Path()
    constructor(other: Path) : this() { compose.addPath(other.compose) }
    fun moveTo(x: Float, y: Float) = compose.moveTo(x, y)
    fun lineTo(x: Float, y: Float) = compose.lineTo(x, y)
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) = compose.quadraticTo(x1, y1, x2, y2)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) = compose.cubicTo(x1, y1, x2, y2, x3, y3)
    fun close() = compose.close()
    fun reset() = compose.reset()
    enum class Direction { CW, CCW }
}

class Paint(flags: Int = 0) {
    var color: Int = Color.BLACK
    var style: Style = Style.FILL
    var strokeWidth: Float = 0f
    var strokeCap: Cap = Cap.BUTT
    var strokeJoin: Join = Join.MITER
    var isAntiAlias: Boolean = flags and ANTI_ALIAS_FLAG != 0
    var alpha: Int
        get() = Color.alpha(color)
        set(value) { color = (color and 0x00ffffff) or (value shl 24) }
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Cap { BUTT, ROUND, SQUARE }
    enum class Join { MITER, ROUND, BEVEL }
    internal fun compose(): androidx.compose.ui.graphics.Paint = androidx.compose.ui.graphics.Paint().also { p ->
        p.color = androidx.compose.ui.graphics.Color(color)
        p.isAntiAlias = isAntiAlias
        p.style = if (style == Style.STROKE) androidx.compose.ui.graphics.PaintingStyle.Stroke else androidx.compose.ui.graphics.PaintingStyle.Fill
        p.strokeWidth = strokeWidth
        p.strokeCap = when (strokeCap) { Cap.ROUND -> androidx.compose.ui.graphics.StrokeCap.Round; Cap.SQUARE -> androidx.compose.ui.graphics.StrokeCap.Square; else -> androidx.compose.ui.graphics.StrokeCap.Butt }
        p.strokeJoin = when (strokeJoin) { Join.ROUND -> androidx.compose.ui.graphics.StrokeJoin.Round; Join.BEVEL -> androidx.compose.ui.graphics.StrokeJoin.Bevel; else -> androidx.compose.ui.graphics.StrokeJoin.Miter }
    }
    companion object { const val ANTI_ALIAS_FLAG = 1 }
}
