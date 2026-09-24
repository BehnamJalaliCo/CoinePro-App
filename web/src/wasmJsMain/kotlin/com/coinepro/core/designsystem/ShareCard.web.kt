@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.core.designsystem

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.coinepro.core.common.BrandConfig
import com.coinepro.web.WebFonts
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.paragraph.Alignment
import org.jetbrains.skia.paragraph.Direction
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider

/*
 * The browser's `ShareCard` and `ShareImage`, standing in for the phone's (`core/designsystem`,
 * excluded from this module's build because they draw with `android.graphics`).
 *
 * The same card: 1080 square, the same margins, sizes, colours and order, the same bidi rule — here
 * through Skia's paragraph layout, which is what `StaticLayout` is on Android: a paragraph with a
 * base direction in which a Latin ticker keeps its own. Sharing it saves the PNG, or hands it to the
 * browser's share sheet where there is one.
 */
object ShareCard {
    const val SIZE = 1080

    fun render(context: Context, content: ShareCardContent, dark: Boolean = true, rtl: Boolean = true): Bitmap {
        val palette = if (dark) CoineProDarkPalette else CoineProLightPalette
        val surface = Surface.makeRasterN32Premul(SIZE, SIZE)
        val canvas = surface.canvas
        canvas.clear(palette.stage.toArgb())

        val fonts = FontCollection().apply {
            val provider = TypefaceFontProvider()
            listOf("iranyekanx_regular", "iranyekanx_bold").forEach { name ->
                WebFonts.bytesOf(name)?.let { bytes ->
                    FontMgr.default.makeFromData(Data.makeFromBytes(bytes))?.let { provider.registerTypeface(it, "IRANYekanX") }
                }
            }
            setAssetFontManager(provider)
            setDefaultFontManager(FontMgr.default)
        }

        var cursor = MARGIN.toFloat()
        content.image?.let { image ->
            val frame = Rect.makeXYWH(MARGIN.toFloat(), cursor, (SIZE - MARGIN * 2).toFloat(), IMAGE_HEIGHT)
            val skia = Image.makeFromBitmap(org.jetbrains.skia.Bitmap().apply {
                allocN32Pixels(image.width, image.height)
            })
            val src = image.image.asSkiaImage()
            val scale = maxOf(frame.width / src.width, frame.height / src.height)
            val sw = (frame.width / scale).coerceAtMost(src.width.toFloat())
            val sh = (frame.height / scale).coerceAtMost(src.height.toFloat())
            val left = ((src.width - sw) / 2).coerceAtLeast(0f)
            val top = ((src.height - sh) / 2).coerceAtLeast(0f)
            canvas.save()
            canvas.clipRRect(RRect.makeLTRB(frame.left, frame.top, frame.right, frame.bottom, RADIUS), true)
            canvas.drawImageRect(src, Rect.makeXYWH(left, top, sw, sh), frame)
            canvas.restore()
            skia.close()
            cursor = frame.bottom + GAP
        }

        fun block(text: String, size: Float, bold: Boolean, colour: Int, maxLines: Int = MAX_WRAP): Float {
            if (text.isBlank()) return 0f
            val style = ParagraphStyle().apply {
                direction = if (rtl) Direction.RTL else Direction.LTR
                alignment = Alignment.START
                this.maxLinesCount = maxLines
                ellipsis = "…"
            }
            val paragraph = ParagraphBuilder(style, fonts).apply {
                pushStyle(TextStyle().apply {
                    fontFamilies = arrayOf("IRANYekanX")
                    fontSize = size
                    color = colour
                    fontStyle = if (bold) org.jetbrains.skia.FontStyle.BOLD else org.jetbrains.skia.FontStyle.NORMAL
                })
                addText(text)
            }.build()
            paragraph.layout((SIZE - MARGIN * 2).toFloat())
            paragraph.paint(canvas, MARGIN.toFloat(), cursor)
            return paragraph.height
        }

        cursor += block(content.title, TITLE_SIZE, true, palette.textPrimary.toArgb()) + GAP_TIGHT
        content.subtitle?.let { cursor += block(it, BODY_SIZE, false, palette.textMuted.toArgb()) + GAP }
        val headColour = when (content.tone) {
            ShareCardTone.UP -> palette.marketUp
            ShareCardTone.DOWN -> palette.marketDown
            ShareCardTone.NEUTRAL -> palette.accent
        }.toArgb()
        cursor += block(content.headline, HEADLINE_SIZE, true, headColour) + GAP
        for (line in content.lines.take(MAX_LINES)) cursor += block(line, BODY_SIZE, false, palette.textSecondary.toArgb()) + GAP_TIGHT

        // Footer: the mark on the leading edge, the link on the trailing one, on the card's floor.
        fun footer(text: String, colour: Int, bold: Boolean, alignRightEdge: Boolean, baselineFromFloor: Float = 0f) {
            val style = ParagraphStyle().apply {
                direction = if (rtl) Direction.RTL else Direction.LTR
                alignment = if (alignRightEdge) Alignment.RIGHT else Alignment.LEFT
                maxLinesCount = 1
            }
            val p = ParagraphBuilder(style, fonts).apply {
                pushStyle(TextStyle().apply {
                    fontFamilies = arrayOf("IRANYekanX"); fontSize = MARK_SIZE; color = colour
                    fontStyle = if (bold) org.jetbrains.skia.FontStyle.BOLD else org.jetbrains.skia.FontStyle.NORMAL
                })
                addText(text)
            }.build()
            p.layout((SIZE - MARGIN * 2).toFloat())
            p.paint(canvas, MARGIN.toFloat(), SIZE - MARGIN - p.alphabeticBaseline - baselineFromFloor)
        }
        content.badge?.takeIf { it.isNotBlank() }?.let { footer(it, palette.textMuted.toArgb(), false, rtl, MARK_SIZE + GAP_TIGHT) }
        footer(content.mark, palette.accent.toArgb(), true, rtl)
        footer(content.link, palette.textMuted.toArgb(), false, !rtl)

        val snapshot = surface.makeImageSnapshot()
        return snapshot.toComposeImageBitmap().asAndroidBitmap()
    }

    private const val MARGIN = 72
    private const val IMAGE_HEIGHT = 480f
    private const val RADIUS = 32f
    private const val GAP = 28f
    private const val GAP_TIGHT = 12f
    private const val TITLE_SIZE = 52f
    private const val HEADLINE_SIZE = 104f
    private const val BODY_SIZE = 38f
    private const val MARK_SIZE = 32f
    private const val MAX_LINES = 3
    private const val MAX_WRAP = 2
}

enum class ShareCardTone { UP, DOWN, NEUTRAL }

data class ShareCardContent(
    val title: String,
    val subtitle: String? = null,
    val headline: String,
    val tone: ShareCardTone = ShareCardTone.NEUTRAL,
    val lines: List<String> = emptyList(),
    val image: Bitmap? = null,
    val mark: String = BrandConfig.DISPLAY_NAME,
    val link: String = BrandConfig.WEB_HOST,
    val badge: String? = null,
)

private fun sharePngJs(bytes: JsAny, name: String): Unit = js(
    """(function () {
        try {
            var blob = new Blob([bytes], { type: 'image/png' });
            var file = (typeof File !== 'undefined') ? new File([blob], name, { type: 'image/png' }) : null;
            if (file && navigator.canShare && navigator.canShare({ files: [file] })) {
                navigator.share({ files: [file] }).catch(function () {});
                return;
            }
            var a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = name;
            document.body.appendChild(a); a.click(); a.remove();
            setTimeout(function () { URL.revokeObjectURL(a.href); }, 10000);
        } catch (e) {}
    })()""",
)

private fun downloadPngJs(bytes: JsAny, name: String): Unit = js(
    """(function () {
        try {
            var blob = new Blob([bytes], { type: 'image/png' });
            var a = document.createElement('a');
            a.href = URL.createObjectURL(blob);
            a.download = name;
            document.body.appendChild(a); a.click(); a.remove();
            setTimeout(function () { URL.revokeObjectURL(a.href); }, 10000);
        } catch (e) {}
    })()""",
)

private fun copyPngJs(bytes: JsAny): Boolean = js(
    """(function () {
        try {
            if (!navigator.clipboard || typeof ClipboardItem === 'undefined') return false;
            var blob = new Blob([bytes], { type: 'image/png' });
            navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]).catch(function () {});
            return true;
        } catch (e) { return false; }
    })()""",
)

private fun newUint8(length: Int): JsAny = js("new Uint8Array(length)")
private fun setByte(array: JsAny, index: Int, value: Int): Unit = js("array[index] = value")

object ShareImage {
    /** Saves or shares [image] as `[name].png`. False when it could not be encoded. */
    fun share(context: Context, image: Bitmap, name: String): Boolean = runCatching {
        val png = image.image.asSkiaImage().encodeToData(EncodedImageFormat.PNG)?.bytes ?: return false
        val array = newUint8(png.size)
        png.forEachIndexed { i, b -> setByte(array, i, b.toInt() and 0xff) }
        sharePngJs(array, name.filter(Char::isLetterOrDigit).ifEmpty { "chart" } + ".png")
        true
    }.getOrDefault(false)

    /** Puts [image] on the clipboard as a PNG. False where the browser has no image clipboard. */
    fun copy(context: Context, image: Bitmap, name: String): Boolean = runCatching {
        copyPngJs(pngArray(image) ?: return false)
    }.getOrDefault(false)

    /** Downloads [image] as `[name].png` — never the share sheet, which is [share]'s. */
    fun save(context: Context, image: Bitmap, name: String): Boolean = runCatching {
        downloadPngJs(pngArray(image) ?: return false, name.filter(Char::isLetterOrDigit).ifEmpty { "chart" } + ".png")
        true
    }.getOrDefault(false)

    private fun pngArray(image: Bitmap): JsAny? {
        val png = image.image.asSkiaImage().encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
        val array = newUint8(png.size)
        png.forEachIndexed { i, b -> setByte(array, i, b.toInt() and 0xff) }
        return array
    }
}

private fun androidx.compose.ui.graphics.ImageBitmap.asSkiaImage(): Image = Image.makeFromBitmap(this.asSkiaBitmap())
