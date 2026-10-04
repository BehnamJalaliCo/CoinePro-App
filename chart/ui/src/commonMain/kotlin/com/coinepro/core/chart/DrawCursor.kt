package com.coinepro.core.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TradingView's phone placement, in the owner's recording (5.20.0): a cursor that is *not* the
 * finger.
 *
 * A tap that places a point under the fingertip places it somewhere the reader cannot see — the
 * finger is over it — and on a phone that is the whole reason a trend line never quite meets the
 * wick it was aimed at. TradingView's mobile app answers with a pointer of its own: a dot and two
 * dashed rules in the middle of the plot, moved by dragging anywhere on the screen the way a laptop
 * trackpad moves a mouse, and a tap that puts the point *where the dot is*. Between points the
 * shape being drawn is stretched from the last anchor to the dot, so the reader sees the line they
 * are about to commit before they commit it.
 *
 * A mouse needs none of this — it is already a pointer the hand does not cover — so with a mouse
 * the dot follows the hover and the click places directly, with the same live stretch.
 */
internal object DrawCursor {
    /** TradingView's drawing blue, the colour its own placement pointer is drawn in. */
    val Accent = Color(0xFF2962FF)

    /** The dot: a filled centre and a stage-coloured ring, so it reads on a candle of either colour. */
    const val DOT_DP = 5f
    const val RING_DP = 1.5f

    /**
     * Which line of the banner applies, and where in the count it is.
     *
     * Two steps a point, the way TradingView counts them: «move the cursor to the point», then «tap
     * to set it». [moved] is whether the cursor has travelled since the last point went down — the
     * difference between the two halves of a step.
     */
    fun step(points: Int, placed: Int, moved: Boolean, variable: Boolean): Step {
        val text = when {
            !moved && placed == 0 -> ChartText.DRAW_MOVE_START
            !moved -> ChartText.DRAW_MOVE_NEXT
            placed == 0 && points > 1 -> ChartText.DRAW_TAP_FIRST
            !variable && placed >= points - 1 -> ChartText.DRAW_TAP_FINISH
            else -> ChartText.DRAW_TAP_NEXT
        }
        // A path or a polyline has no last point to count towards, so it has no «of N» either.
        if (variable || points <= 0) return Step(index = 0, total = 0, text = text)
        val index = (2 * placed + if (moved) 2 else 1).coerceAtMost(2 * points)
        return Step(index = index, total = 2 * points, text = text)
    }

    data class Step(val index: Int, val total: Int, val text: ChartText)

    /** «۱ از ۴» or «1 of 4»: a count in prose, so Persian digits in Persian. */
    fun count(value: Int, persian: Boolean): String {
        val latin = value.toString()
        if (!persian) return latin
        return buildString { latin.forEach { digit -> append(if (digit in '0'..'9') (PERSIAN_ZERO + (digit - '0')) else digit) } }
    }

    /** U+06F0, the Extended Arabic-Indic zero; the nine after it are the Persian digits. */
    private const val PERSIAN_ZERO = '۰'
}

/**
 * The pointer: two dashed rules across the plot and the dot where they cross.
 *
 * Drawn at the cursor's exact position rather than on the nearest bar, because it is the position
 * the tap will place — a pointer drawn a few pixels from where it places is the very imprecision
 * this whole mode exists to remove.
 */
internal fun DrawScope.drawPlacementCursor(at: Offset, plotWidth: Float, plotHeight: Float, stage: Color) {
    val hairline = 1.dp.toPx()
    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
    val rule = DrawCursor.Accent.copy(alpha = 0.85f)
    drawLine(rule, Offset(at.x, 0f), Offset(at.x, plotHeight), hairline, pathEffect = dash)
    drawLine(rule, Offset(0f, at.y), Offset(plotWidth, at.y), hairline, pathEffect = dash)
    drawCircle(stage, (DrawCursor.DOT_DP + DrawCursor.RING_DP).dp.toPx(), at)
    drawCircle(DrawCursor.Accent, DrawCursor.DOT_DP.dp.toPx(), at)
}

/**
 * The anchors already down, as TradingView rings them while the next one is being aimed.
 */
internal fun DrawScope.drawPendingAnchors(anchors: List<Offset>, colour: Color, stage: Color) {
    val radius = 6.dp.toPx()
    anchors.forEach { point ->
        drawCircle(stage, radius, point)
        drawCircle(colour, radius, point, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * The line of instructions over the plot while a point is being aimed: «۱ از ۴ · نشانگر را روی
 * نقطه‌ی شروع ببرید», and a bin that drops the drawing.
 *
 * Drawn by the chart itself rather than by the page, because it is the chart that knows how many
 * points the tool takes, how many are down and whether the cursor has moved since the last one.
 */
@Composable
internal fun DrawStepBanner(
    step: DrawCursor.Step,
    persian: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plate = Color(0xF2202430)
    val chip = Color(0xFF363A45)
    val ink = Color(0xFFF0F3FA)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .height(BANNER_HEIGHT_DP.dp)
            .background(plate, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (step.total > 0) {
            Box(
                modifier = Modifier
                    .background(chip, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = chartText(ChartText.DRAW_STEP_OF)
                        .replace("%1\$s", DrawCursor.count(step.index, persian))
                        .replace("%2\$s", DrawCursor.count(step.total, persian)),
                    color = ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = chartText(step.text),
            color = ink,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val cancel = chartText(ChartText.DRAW_CANCEL)
        Box(
            modifier = Modifier
                .size(36.dp)
                .chartControl(onClick = onCancel)
                .semantics { contentDescription = cancel },
            contentAlignment = Alignment.Center,
        ) {
            val bin = remember { binVector() }
            Image(
                painter = rememberVectorPainter(bin),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ink),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** A waste bin on a 24-unit grid, in strokes, so it is the same picture on the phone and the web. */
private fun binVector(): ImageVector {
    val builder = ImageVector.Builder(
        name = "draw-cancel",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    listOf(
        "M4,7 L20,7",
        "M9,7 L9,4.5 L15,4.5 L15,7",
        "M6,7 L7,20 L17,20 L18,7",
        "M10,11 L10,16.5",
        "M14,11 L14,16.5",
    ).forEach { data ->
        builder.addPath(
            pathData = addPathNodes(data),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        )
    }
    return builder.build()
}

/** The id the stretched preview is drawn under; no placed drawing is ever given it. */
internal const val PREVIEW_DRAWING_ID = Long.MIN_VALUE

/** The banner's height, which is also how far the legend is pushed down while it shows. */
internal const val BANNER_HEIGHT_DP = 44f
