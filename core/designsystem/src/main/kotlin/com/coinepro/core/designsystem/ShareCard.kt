package com.coinepro.core.designsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.coinepro.core.common.BrandConfig

/**
 * **The share card** — 1080 × 1080, drawn rather than screenshotted (run Ω4).
 *
 * ### Why this is not a screenshot of the screen
 *
 * A phone screenshot is 1080 × 2400, has the reader's status bar on it, and is unreadable at the
 * size a social feed shows it. Every card this app posts is a square, because every surface that
 * shows a shared image crops to one, and what survives that crop has to be *designed* — a number
 * large enough to read at a glance, one line saying what it is about, and the mark that says where
 * it came from.
 *
 * ### Why the link is printed rather than embedded
 *
 * There is no QR code and no metadata. The link is drawn as text at the foot of the card, because a
 * card is a picture: it travels through screenshots, re-encodings and messaging apps that strip
 * every byte that is not a pixel, and the only part of it guaranteed to arrive is what somebody can
 * read. It is also short enough to type.
 *
 * ### Why this lives in the design system
 *
 * Three surfaces make cards — a chart, a signal, an Arena result — and a card is a *typeface, a
 * palette and a mark* before it is any of their content. Three renderers would drift on all three,
 * and the first thing to drift would be the brand.
 *
 * Android's own `Canvas` and `StaticLayout` rather than Compose: this draws into a bitmap with no
 * composition, no window and no recomposition, and it has to work from a background thread with a
 * context and nothing else. `StaticLayout` is also the one text API here that does bidirectional
 * layout properly, which a card carrying «BTC/USDT» inside a Persian sentence needs.
 */
object ShareCard {

    /** The square every surface shares. One size, because every feed crops to one. */
    const val SIZE = 1080

    /**
     * Draw a card.
     *
     * Pure apart from reading the font: no view, no composition, no main thread. The caller writes
     * the bitmap wherever it likes.
     *
     * @param dark whether the card is drawn on the dark palette. It follows the reader's theme, for
     *   the reason a screenshot would have: a card that came out white from a black app is a card
     *   from a different app as far as the person receiving it is concerned.
     */
    fun render(
        context: Context,
        content: ShareCardContent,
        dark: Boolean = true,
        /**
         * Whether the card's paragraphs run right to left.
         *
         * The card's *language*, not the string's. Without this, `ALIGN_NORMAL` gives each paragraph
         * the direction of its own first strong character — so «BTC/USDT» and «+4.20%» hang to the
         * left while the Persian sentences under them hang to the right, and a card with two
         * margins reads as two cards. True by default because Persian is the product's default
         * language; a Latin ticker inside an RTL paragraph still keeps its own direction, which is
         * the whole reason this is laid out by `StaticLayout` and not by `drawText`.
         */
        rtl: Boolean = true,
    ): Bitmap {
        val palette = if (dark) CoineProDarkPalette else CoineProLightPalette
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val ground = palette.stage.toArgb()
        canvas.drawColor(ground)

        // `Resources.getFont` rather than `ResourcesCompat`: this module does not depend on
        // androidx.core, the API is API 26 and the app's own floor is API 26. One typeface, the
        // licensed IRANYekanX, exactly as every other surface in this app draws — a card set in the
        // system font would be a card from a different product.
        val regular = context.resources.getFont(R.font.iranyekanx_regular)
        val bold = context.resources.getFont(R.font.iranyekanx_bold)

        // The picture, where there is one: the top 44 % of the card, centre-cropped into a rounded
        // rectangle. Cropped rather than letterboxed — bars down the sides of a chart make a card
        // look like a failed upload, and a chart is readable from its middle.
        var cursor = MARGIN.toFloat()
        content.image?.let { image ->
            val frame = RectF(MARGIN.toFloat(), cursor, (SIZE - MARGIN).toFloat(), cursor + IMAGE_HEIGHT)
            drawCropped(canvas, image, frame)
            cursor = frame.bottom + GAP
        }

        // The instrument, then the headline. The headline is the largest thing on the card and it is
        // the only thing on it in a direction colour: a reader scrolling a feed reads the number and
        // its colour and nothing else, and both of those must be true without the words.
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textSize = TITLE_SIZE
            color = palette.textPrimary.toArgb()
        }
        cursor += drawBlock(canvas, content.title, titlePaint, cursor, rtl) + GAP_TIGHT

        content.subtitle?.let { subtitle ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = regular
                textSize = BODY_SIZE
                color = palette.textMuted.toArgb()
            }
            cursor += drawBlock(canvas, subtitle, paint, cursor, rtl) + GAP
        }

        val headlinePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textSize = HEADLINE_SIZE
            color = when (content.tone) {
                ShareCardTone.UP -> palette.marketUp
                ShareCardTone.DOWN -> palette.marketDown
                ShareCardTone.NEUTRAL -> palette.accent
            }.toArgb()
        }
        cursor += drawBlock(canvas, content.headline, headlinePaint, cursor, rtl) + GAP

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular
            textSize = BODY_SIZE
            color = palette.textSecondary.toArgb()
        }
        for (line in content.lines.take(MAX_LINES)) {
            cursor += drawBlock(canvas, line, bodyPaint, cursor, rtl) + GAP_TIGHT
        }

        // The mark and the link, on the floor of the card rather than after the text: every card has
        // them in the same place whether it carries one line or three, which is what makes a feed of
        // them recognisable as one thing.
        val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bold
            textSize = MARK_SIZE
            color = palette.accent.toArgb()
        }
        val linkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regular
            textSize = MARK_SIZE
            color = palette.textMuted.toArgb()
            textAlign = Paint.Align.RIGHT
        }
        val floor = (SIZE - MARGIN).toFloat()
        // The mark takes the card's leading edge and the link the trailing one, so the footer hangs
        // the same way the text above it does.
        if (rtl) {
            markPaint.textAlign = Paint.Align.RIGHT
            linkPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(content.mark, (SIZE - MARGIN).toFloat(), floor, markPaint)
            canvas.drawText(content.link, MARGIN.toFloat(), floor, linkPaint)
        } else {
            canvas.drawText(content.mark, MARGIN.toFloat(), floor, markPaint)
            canvas.drawText(content.link, (SIZE - MARGIN).toFloat(), floor, linkPaint)
        }
        return bitmap
    }

    /** One block of text, laid out with the bidi algorithm and wrapped to the card's width. */
    private fun drawBlock(canvas: Canvas, text: String, paint: TextPaint, top: Float, rtl: Boolean): Float {
        if (text.isBlank()) return 0f
        val width = SIZE - MARGIN * 2
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width)
            // Every card is built for a Persian reader first, so the paragraph runs right to left
            // and a Latin ticker inside it keeps its own direction. `ALIGN_NORMAL` then means «the
            // start of the paragraph», which is what makes one call correct in both languages.
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(
                if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR,
            )
            .setMaxLines(MAX_WRAP)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(MARGIN.toFloat(), top)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** The picture, centre-cropped into [frame] so it fills it without stretching. */
    private fun drawCropped(canvas: Canvas, image: Bitmap, frame: RectF) {
        val scale = maxOf(frame.width() / image.width, frame.height() / image.height)
        val sourceWidth = (frame.width() / scale).toInt().coerceAtMost(image.width)
        val sourceHeight = (frame.height() / scale).toInt().coerceAtMost(image.height)
        val left = ((image.width - sourceWidth) / 2).coerceAtLeast(0)
        val top = ((image.height - sourceHeight) / 2).coerceAtLeast(0)
        val source = Rect(left, top, left + sourceWidth, top + sourceHeight)
        canvas.save()
        val path = android.graphics.Path().apply { addRoundRect(frame, RADIUS, RADIUS, android.graphics.Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(image, source, frame, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int = android.graphics.Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
    )

    /** The card's gutter, and the picture's share of it. */
    private const val MARGIN = 72
    private const val IMAGE_HEIGHT = 480f
    private const val RADIUS = 32f
    private const val GAP = 28f
    private const val GAP_TIGHT = 12f
    private const val TITLE_SIZE = 52f
    private const val HEADLINE_SIZE = 104f
    private const val BODY_SIZE = 38f
    private const val MARK_SIZE = 32f

    /** Three sentences, and each of them wraps to at most two lines. A card is not a page. */
    private const val MAX_LINES = 3
    private const val MAX_WRAP = 2
}

/** Which colour the card's one large figure is drawn in. See [ShareCard]. */
enum class ShareCardTone { UP, DOWN, NEUTRAL }

/**
 * What goes on a card.
 *
 * A flat value rather than three overloads, because the three surfaces that make cards — a chart, a
 * signal, an Arena result — differ only in what they put in these fields, and a renderer that knew
 * which of the three it was drawing would grow a branch per surface.
 */
data class ShareCardContent(
    /** The instrument, or what the card is about. */
    val title: String,
    /** One line of context: the bar length, the date, the challenge. Null for a card that needs none. */
    val subtitle: String? = null,
    /** The one large figure. A percentage, a score, a verdict. */
    val headline: String,
    val tone: ShareCardTone = ShareCardTone.NEUTRAL,
    /** Up to three sentences. More than three is a page, and a page does not survive a feed. */
    val lines: List<String> = emptyList(),
    /** The chart, where the card has one. Cropped to the top of the card. */
    val image: Bitmap? = null,
    /** The brand, drawn in the accent at the foot. */
    val mark: String = BrandConfig.DISPLAY_NAME,
    /** Where to go to see this. Printed as readable text — see [ShareCard]'s note. */
    val link: String = BrandConfig.WEB_HOST,
)
