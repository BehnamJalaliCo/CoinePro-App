package com.coinepro.app.brief

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.coinepro.core.chart.CandleSeries

/**
 * The mover's night, as one line, for the notification the reader sees before they open anything.
 *
 * ### Why a picture at all, on a surface that already has two sentences
 *
 * Because the sentences answer «what» and the line answers «how», and the second one is the
 * question a chart exists for. «XAUUSD fell 2.4 %» is the same sentence whether the fall happened
 * in one hour or was a slow bleed all night, and those are different mornings. Android expands a
 * notification with a `BigPictureStyle` in place, so the reader gets the shape without a tap and
 * without the app being started.
 *
 * ### It is a closing line and not a chart, on purpose
 *
 * No axes, no grid, no candles, no labels. Everything that would need a scale a reader could read
 * is left out, because a scale at this size cannot be read and a number nobody can check is worse
 * than no number. What survives is the shape, which is the thing that cannot mislead: it is the
 * closes of the same bars the coach's sentence was written from.
 *
 * ### Nothing here decides anything
 *
 * Every judgement — draw or refuse, which way the night went, where each point lands — is in
 * [BriefSparklineShape], under an ordinary unit test. What is left here is paint. The reason that
 * split exists rather than being tidiness is written out in that file.
 */
object BriefSparkline {

    /** The image's size. A notification's big picture is wide and short; this is 2:1 at xhdpi. */
    const val WIDTH = 720
    const val HEIGHT = 360

    /** The line, or null where [BriefSparklineShape] says there is not enough of one to draw. */
    fun of(series: CandleSeries?, bars: Int = BriefSparklineShape.BARS): Bitmap? {
        val shape = BriefSparklineShape.of(series, bars) ?: return null

        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        val ink = if (shape.rising) UP else DOWN
        val usableWidth = WIDTH - INSET * 2f
        val usableHeight = HEIGHT - INSET * 2f

        val path = Path()
        shape.points.forEachIndexed { index, point ->
            val x = INSET + point.x * usableWidth
            val y = INSET + point.y * usableHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // The fill first, so the stroke sits on top of its own edge rather than under it.
        val fill = Path(path).apply {
            lineTo(WIDTH - INSET, HEIGHT - INSET)
            lineTo(INSET, HEIGHT - INSET)
            close()
        }
        canvas.drawPath(fill, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tinted(ink) })
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = ink
            },
        )
        return bitmap
    }

    /** The line's colour at a sixth, for the area under it. */
    private fun tinted(colour: Int): Int = Color.argb(
        (Color.alpha(colour) * FILL_ALPHA).toInt(),
        Color.red(colour),
        Color.green(colour),
        Color.blue(colour),
    )

    private const val INSET = 24f
    private const val STROKE = 6f
    private const val FILL_ALPHA = 0.16f

    // The app's own market colours, spelled here rather than read from `CoineProColors`: this draws
    // into a system surface with the system's own background behind it, and the design system's
    // values are theme-aware Compose colours that have no meaning outside a composition.
    private const val BACKGROUND = 0xFF11141A.toInt()
    private const val UP = 0xFF2EBD85.toInt()
    private const val DOWN = 0xFFF6465D.toInt()
}
