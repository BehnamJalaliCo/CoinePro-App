@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.text

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo

/*
 * Material's `Text`, as the shared screens call it — with one thing a page has to do that a phone
 * does not. An emoji is drawn on Android by the system's colour-emoji font; a page draws text with
 * IRANYekanX alone (the owner's one typeface), which has none. So each emoji in a line is drawn by
 * the *browser's* own emoji font — through a 2D canvas, the one place a page can reach it — and
 * placed in the line as an inline picture the size of the text. A line with no emoji is passed
 * straight through. The build points every `import androidx.compose.material3.Text` here.
 */

private fun rasterJs(emoji: String, size: Int, done: (JsAny?, Int, Int) -> Unit): Unit = js(
    """(function () {
        try {
            var c = (typeof OffscreenCanvas !== 'undefined') ? new OffscreenCanvas(size, size) : document.createElement('canvas');
            c.width = size; c.height = size;
            var g = c.getContext('2d');
            g.textAlign = 'center'; g.textBaseline = 'middle';
            g.font = Math.round(size * 0.84) + 'px "Apple Color Emoji","Segoe UI Emoji","Noto Color Emoji",sans-serif';
            g.fillText(emoji, size / 2, size / 2 + size * 0.04);
            var d = g.getImageData(0, 0, size, size).data;
            done(new Int8Array(d.buffer, d.byteOffset, d.byteLength), size, size);
        } catch (e) { done(null, 0, 0); }
    })()""",
)

private object EmojiBitmaps {
    private val cache = HashMap<String, ImageBitmap?>()

    fun of(emoji: String): ImageBitmap? = cache.getOrPut(emoji) {
        var result: ImageBitmap? = null
        rasterJs(emoji, SIZE) { data, w, h ->
            if (data != null) {
                val bytes = com.coinepro.web.net.jsBytesPublic(data)
                result = runCatching {
                    SkiaImage.makeRaster(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), bytes, w * 4)
                        .toComposeImageBitmap()
                }.getOrNull()
            }
        }
        result
    }

    private const val SIZE = 96
}

/** A code point that only an emoji font draws. The pictographic planes and the dingbats that are. */
private fun isEmoji(codePoint: Int): Boolean =
    codePoint >= 0x1F000 || codePoint in 0x2600..0x27BF || codePoint in 0x2B00..0x2BFF || codePoint in 0x1F1E6..0x1F1FF

/** [text] split into runs, each an emoji cluster or not, or null when there is none. */
private fun emojiRuns(text: String): List<Pair<String, Boolean>>? {
    var i = 0
    var any = false
    val runs = ArrayList<Pair<String, Boolean>>()
    val plain = StringBuilder()
    while (i < text.length) {
        val cp = if (text[i].isHighSurrogate() && i + 1 < text.length) ((text[i].code - 0xD800) shl 10) + (text[i + 1].code - 0xDC00) + 0x10000 else text[i].code
        val width = if (cp >= 0x10000) 2 else 1
        if (isEmoji(cp)) {
            if (plain.isNotEmpty()) { runs += plain.toString() to false; plain.clear() }
            var end = i + width
            // A variation selector, a skin tone, a joiner and what it joins: one picture.
            while (end < text.length) {
                val c = text[end].code
                if (c == 0xFE0F || c == 0x200D) { end++; continue }
                if (text[end].isHighSurrogate() && end + 1 < text.length) {
                    val next = ((c - 0xD800) shl 10) + (text[end + 1].code - 0xDC00) + 0x10000
                    if (next in 0x1F3FB..0x1F3FF || (text[end - 1].code == 0x200D && isEmoji(next))) { end += 2; continue }
                }
                break
            }
            runs += text.substring(i, end) to true
            any = true
            i = end
        } else {
            plain.append(text, i, i + width)
            i += width
        }
    }
    if (plain.isNotEmpty()) runs += plain.toString() to false
    return if (any) runs else null
}

private fun withEmoji(runs: List<Pair<String, Boolean>>): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val content = LinkedHashMap<String, InlineTextContent>()
    val text = buildAnnotatedString {
        runs.forEach { (run, emoji) ->
            if (!emoji) {
                append(run)
            } else {
                val id = "emoji:$run"
                appendInlineContent(id, run)
                content.getOrPut(id) {
                    InlineTextContent(Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)) {
                        EmojiBitmaps.of(run)?.let { Image(it, contentDescription = run, modifier = Modifier.fillMaxSize()) }
                    }
                }
            }
        }
    }
    return text to content
}

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val runs = remember(text) { emojiRuns(text) }
    if (runs == null) {
        androidx.compose.material3.Text(
            text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
            onTextLayout = onTextLayout, style = style,
        )
    } else {
        val (annotated, inline) = remember(runs) { withEmoji(runs) }
        androidx.compose.material3.Text(
            text = annotated, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
            inlineContent = inline, onTextLayout = onTextLayout ?: {}, style = style,
        )
    }
}

@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
) {
    androidx.compose.material3.Text(
        text = text, modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
            fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
            lineHeight = lineHeight, overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        inlineContent = inlineContent, onTextLayout = onTextLayout, style = style,
    )
}
