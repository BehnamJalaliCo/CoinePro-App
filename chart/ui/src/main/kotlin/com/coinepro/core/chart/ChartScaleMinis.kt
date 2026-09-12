package com.coinepro.core.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * **A** and **L** at the foot of the price gutter (run Ω2).
 *
 * ### What they are, and why two letters are the right label
 *
 * `A` fits the visible bars to the plot; `L` makes the axis logarithmic. Both already existed and
 * neither could be found: auto-fit was a *double tap on the gutter* and the log switch was three taps
 * into the scale sheet. Every terminal a trader has used puts exactly these two in exactly this
 * corner, labelled with exactly these letters — TradingView's `auto` and `log`, abbreviated because a
 * phone's gutter is fifty-six points wide — so a reader who has used one finds them without looking,
 * and a reader who has not is one tap from discovering what they do and one more from undoing it.
 *
 * Two letters rather than two glyphs for the same reason: there is no icon for «logarithmic» that
 * anybody reads as logarithmic, and an invented one would be a symbol nobody can look up.
 *
 * ### Why they are lit rather than labelled
 *
 * A lit `A` means the scale is *not* fitted — it is the state a reader would want to leave, and the
 * button is the way out of it. A lit `L` means the axis is logarithmic. That is the same convention
 * every other toggle on this chart uses: the accent says «this is on», never «press here».
 */
@Composable
internal fun PriceAxisScaleMinis(
    /** Where the plot and its gutters are. Null until the first draw has published a frame. */
    frame: PlotFrame?,
    /** The bottom of the price plot in canvas pixels — the minis sit just above it. */
    plotBottom: Float,
    /** Whether the price scale has been stretched off its fit. A lit `A` is the way back. */
    manualScale: Boolean,
    /** Whether the axis is logarithmic. */
    logarithmic: Boolean,
    palette: ChartPalette,
    /** Fit the visible bars. Always offered — the chart owns its own viewport. */
    onAutoScale: () -> Unit,
    /**
     * Flip the axis between regular and logarithmic.
     *
     * Null where the caller holds the scale mode and offers no setter, and then only `A` is drawn.
     * A dead `L` would be worse than no `L`: a reader who pressed it would conclude the chart is
     * broken rather than that the build has no route.
     */
    onToggleLogarithmic: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (frame == null || frame.tagGutterWidth <= 0f) return
    val density = LocalDensity.current
    val stack = with(density) { (MINI_DP * 2 + MINI_GAP_DP).toPx() }
    // Above the time axis and inside the gutter, so the letters never sit over a candle. Clamped at
    // zero for the short-canvas case — a 100 dp thumbnail has no room for this and gets nothing.
    val top = plotBottom - stack - with(density) { MINI_INSET_DP.toPx() }
    if (top < 0f) return
    val left = if (frame.tagsOnRight) {
        frame.right + (frame.rightGutter - with(density) { MINI_DP.toPx() }) / 2f
    } else {
        (frame.leftGutter - with(density) { MINI_DP.toPx() }) / 2f
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) },
            verticalArrangement = Arrangement.spacedBy(MINI_GAP_DP),
        ) {
            Mini(
                letter = "A",
                lit = manualScale,
                palette = palette,
                description = AUTO_LABEL,
                onClick = onAutoScale,
            )
            onToggleLogarithmic?.let { toggle ->
                Mini(
                    letter = "L",
                    lit = logarithmic,
                    palette = palette,
                    description = LOG_LABEL,
                    onClick = toggle,
                )
            }
        }
    }
}

/**
 * One letter in a box.
 *
 * The ground is the crosshair's grey at a low alpha rather than a surface colour, for the reason the
 * gutter chip uses the crosshair too: this sits *on the chart*, and the chart's own greys are the
 * only ones that do not read as a piece of the app pasted over the plot.
 */
@Composable
private fun Mini(
    letter: String,
    lit: Boolean,
    palette: ChartPalette,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(MINI_DP)
            .clip(RoundedCornerShape(MINI_RADIUS_DP))
            .background(if (lit) palette.crosshair else palette.crosshair.copy(alpha = MINI_GROUND_ALPHA))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = if (lit) palette.stage else palette.text,
            fontSize = MINI_TEXT_SP.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Eighteen points: the smallest square that reads as a button beside a 10-point axis label. */
private val MINI_DP = 18.dp

/** Enough that the two do not read as one tall button. */
private val MINI_GAP_DP = 3.dp

/** How far the stack sits above the time axis. */
private val MINI_INSET_DP = 4.dp

private val MINI_RADIUS_DP = 3.dp
private const val MINI_TEXT_SP = 10f

/** How present an unlit mini is: a shape you can find, not a shape you keep noticing. */
private const val MINI_GROUND_ALPHA = 0.18f

/** What TalkBack says. Persian, and it names the action rather than the letter. */
private const val AUTO_LABEL = "جا دادن قیمت‌های دیده‌شده در صفحه"

private const val LOG_LABEL = "محور قیمت لگاریتمی"
