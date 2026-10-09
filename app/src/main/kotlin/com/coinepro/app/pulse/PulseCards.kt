package com.coinepro.app.pulse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.coinepro.app.widget.WidgetLogo

/** One tile of the summary card. */
data class DigestTile(val symbol: String, val name: String, val price: String, val change: String, val direction: Int)

/** One of the screener's movers on the summary card's bottom line. */
data class DigestMover(val symbol: String, val change: String, val direction: Int)

/**
 * The pictures the market notifications carry (5.27.0), drawn rather than fetched so they are
 * there with no network: a card for a move — the mark, the figure, the day's line — and a card for
 * the morning summary.
 *
 * One plate in both themes, the app's dark stage, because a notification's picture sits on
 * whatever the shade is and a dark card with the market colours on it reads on both. Flat colour
 * and hairlines only, the surface rule the app itself keeps.
 */
object PulseCards {

    private const val WIDTH = 1024
    private const val HEIGHT = 512

    private const val STAGE = 0xFF0F0F0F.toInt()
    private const val SURFACE = 0xFF161A21.toInt()
    private const val BORDER = 0xFF2B3139.toInt()
    private const val TEXT = 0xFFF0F1F2.toInt()
    private const val MUTED = 0xFF848E9C.toInt()
    private const val GOLD = 0xFFD8A848.toInt()

    /** The card for one move: the mark and the market, the change large, the last day's line. */
    fun move(
        context: Context,
        symbol: String,
        name: String,
        percent: String,
        price: String,
        caption: String,
        closes: List<Double>,
        colour: Int,
    ): Bitmap? = runCatching {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(STAGE)
        val bold = font(context, bold = true)
        val regular = font(context, bold = false)

        WidgetLogo.bitmap(context, symbol, 40)?.let { logo ->
            canvas.drawBitmap(Bitmap.createScaledBitmap(logo, 104, 104, true), 56f, 52f, null)
        }
        canvas.drawText(symbol, 184f, 98f, text(bold, 46f, TEXT))
        canvas.drawText(name, 184f, 146f, text(regular, 32f, MUTED))

        val right = text(bold, 76f, colour).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(percent, WIDTH - 56f, 110f, right)
        canvas.drawText(price, WIDTH - 56f, 160f, text(regular, 36f, TEXT).apply { textAlign = Paint.Align.RIGHT })

        sparkline(canvas, closes, RectF(56f, 210f, WIDTH - 56f, HEIGHT - 86f), colour)

        canvas.drawText(caption, 56f, HEIGHT - 36f, text(regular, 28f, MUTED))
        canvas.drawText("Pro Chart", WIDTH - 56f, HEIGHT - 36f, text(bold, 28f, GOLD).apply { textAlign = Paint.Align.RIGHT })
        bitmap
    }.getOrNull()

    /** The summary card: four majors in a grid, and the screener's biggest movers beneath. */
    fun digest(
        context: Context,
        title: String,
        tiles: List<DigestTile>,
        moversLabel: String,
        movers: List<DigestMover>,
        colourOf: (Int) -> Int,
    ): Bitmap? = runCatching {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(STAGE)
        val bold = font(context, bold = true)
        val regular = font(context, bold = false)
        canvas.drawText(title, WIDTH - 48f, 64f, text(bold, 36f, TEXT).apply { textAlign = Paint.Align.RIGHT })
        canvas.drawText("Pro Chart", 48f, 64f, text(bold, 28f, GOLD))

        val gap = 20f
        val tileWidth = (WIDTH - 96f - gap) / 2f
        val tileHeight = 132f
        tiles.take(4).forEachIndexed { index, tile ->
            val column = index % 2
            val row = index / 2
            val left = 48f + column * (tileWidth + gap)
            val top = 96f + row * (tileHeight + gap)
            val box = RectF(left, top, left + tileWidth, top + tileHeight)
            canvas.drawRoundRect(box, 24f, 24f, fill(SURFACE))
            canvas.drawRoundRect(box, 24f, 24f, stroke(BORDER))
            WidgetLogo.bitmap(context, tile.symbol, 24)?.let { logo ->
                canvas.drawBitmap(Bitmap.createScaledBitmap(logo, 64, 64, true), left + 22f, top + 34f, null)
            }
            canvas.drawText(tile.name, left + 102f, top + 56f, text(bold, 30f, TEXT))
            canvas.drawText(tile.price, left + 102f, top + 100f, text(regular, 28f, MUTED))
            canvas.drawText(
                tile.change,
                box.right - 22f,
                top + 82f,
                text(bold, 36f, colourOf(tile.direction)).apply { textAlign = Paint.Align.RIGHT },
            )
        }

        if (movers.isNotEmpty()) {
            val y = HEIGHT - 44f
            canvas.drawText(moversLabel, WIDTH - 48f, y, text(regular, 28f, MUTED).apply { textAlign = Paint.Align.RIGHT })
            var x = 48f
            movers.take(3).forEach { mover ->
                val label = "${mover.symbol.removeSuffix("USDT")} ${mover.change}"
                val paint = text(bold, 28f, colourOf(mover.direction))
                val width = paint.measureText(label) + 36f
                val chip = RectF(x, y - 38f, x + width, y + 14f)
                canvas.drawRoundRect(chip, 26f, 26f, fill(SURFACE))
                canvas.drawText(label, x + 18f, y - 2f, paint)
                x += width + 14f
            }
        }
        bitmap
    }.getOrNull()

    private fun sparkline(canvas: Canvas, closes: List<Double>, box: RectF, colour: Int) {
        if (closes.size < 2) return
        val low = closes.min()
        val high = closes.max()
        val span = (high - low).takeIf { it > 0.0 } ?: 1.0
        val step = box.width() / (closes.size - 1)
        val line = Path()
        closes.forEachIndexed { index, close ->
            val x = box.left + index * step
            val y = box.bottom - ((close - low) / span).toFloat() * box.height()
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path(line).apply {
            lineTo(box.right, box.bottom)
            lineTo(box.left, box.bottom)
            close()
        }
        // A flat wash under the line, not a gradient: the app's surface rule.
        canvas.drawPath(area, fill((colour and 0x00FFFFFF) or 0x24000000))
        canvas.drawPath(
            line,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 5f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = colour
            },
        )
        val lastX = box.right
        val lastY = box.bottom - ((closes.last() - low) / span).toFloat() * box.height()
        canvas.drawCircle(lastX, lastY, 9f, fill(colour))
    }

    private fun font(context: Context, bold: Boolean): Typeface = runCatching {
        ResourcesCompat.getFont(
            context,
            if (bold) com.coinepro.core.designsystem.R.font.iranyekanx_bold else com.coinepro.core.designsystem.R.font.iranyekanx_regular,
        )
    }.getOrNull() ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

    private fun text(typeface: Typeface, size: Float, colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = size
        color = colour
    }

    private fun fill(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colour
    }

    private fun stroke(colour: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = colour
    }
}
