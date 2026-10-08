package com.coinepro.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.coinepro.app.R
import com.coinepro.core.designsystem.AssetArt
import com.coinepro.core.designsystem.AssetArtwork
import kotlin.math.roundToInt

/**
 * A market's mark as a bitmap, for the surfaces Compose does not draw (5.27.0): the widgets'
 * `RemoteViews` and a notification's large icon.
 *
 * The same composition as `CoineProAssetLogo` — a coin on one disc, a pair as the base in front of
 * a smaller quote, the ticker's letters where there is no mark — so a market looks the same on the
 * home screen, in the shade and in the app.
 */
object WidgetLogo {

    fun bitmap(context: Context, symbol: String, sizeDp: Int): Bitmap? = runCatching {
        val size = (sizeDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(8)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (val art = AssetArtwork.of(symbol)) {
            is AssetArt.Single -> disc(context, canvas, RectF(0f, 0f, size.toFloat(), size.toFloat()), art.res, null, 0)
            is AssetArt.Monogram -> disc(context, canvas, RectF(0f, 0f, size.toFloat(), size.toFloat()), null, art.label, art.tint)
            is AssetArt.Pair -> {
                val back = size * BACK
                val front = size * FRONT
                disc(context, canvas, RectF(size - back, size - back, size.toFloat(), size.toFloat()), art.quote, art.quoteLabel, art.quoteTint)
                // The notch: a ring in the plate's colour, so the front disc reads as in front.
                val notch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ContextCompat.getColor(context, R.color.widget_stage) }
                canvas.drawCircle(front / 2f, front / 2f, front / 2f + size * NOTCH, notch)
                disc(context, canvas, RectF(0f, 0f, front, front), art.base, art.baseLabel, art.baseTint)
            }
        }
        bitmap
    }.getOrNull()

    private fun disc(context: Context, canvas: Canvas, bounds: RectF, res: Int?, label: String?, tint: Int) {
        val drawable = res?.let { ContextCompat.getDrawable(context, it) }
        canvas.save()
        canvas.clipPath(Path().apply { addOval(bounds, Path.Direction.CW) })
        if (drawable != null) {
            // Cropped to the disc, as the app crops it: a mark drawn on a square fills the circle.
            drawable.setBounds(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt(), bounds.bottom.toInt())
            drawable.draw(canvas)
        } else {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(36, Color.red(tint), Color.green(tint), Color.blue(tint)) }
            canvas.drawOval(bounds, fill)
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tint
                textAlign = Paint.Align.CENTER
                textSize = bounds.height() * if ((label?.length ?: 0) > 2) 0.30f else 0.38f
                typeface = runCatching { ResourcesCompat.getFont(context, com.coinepro.core.designsystem.R.font.iranyekanx_bold) }.getOrNull()
                    ?: Typeface.DEFAULT_BOLD
            }
            val baseline = bounds.centerY() - (text.descent() + text.ascent()) / 2f
            canvas.drawText(label.orEmpty(), bounds.centerX(), baseline, text)
        }
        canvas.restore()
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = ContextCompat.getColor(context, R.color.widget_border)
        }
        canvas.drawOval(RectF(bounds.left + 0.5f, bounds.top + 0.5f, bounds.right - 0.5f, bounds.bottom - 0.5f), ring)
    }

    private const val FRONT = 0.74f
    private const val BACK = 0.50f
    private const val NOTCH = 0.03f
}
