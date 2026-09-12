package com.coinepro.core.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.coinepro.core.designsystem.R as DesignR

/**
 * The chip in the price gutter that offers something at the price the pointer is on.
 *
 * ### Why it exists
 *
 * `onRequestAlertAt` was wired to a long press on one of the chart's own levels and to nothing
 * else — so a reader could set an alert at a pivot the app had drawn for them and could not set one
 * at a price they had picked themselves, and nothing on screen said the gesture existed at all. The
 * gutter is where every terminal puts this and it is the first place a reader's finger goes.
 *
 * ### Two actions, not one (run Ω2)
 *
 * A price a reader has stopped on is a price they want one of exactly two things at: *tell me when
 * it gets here*, or *put me in here*. Offering only the first meant the other half of the gesture —
 * the one the whole paper-trading side of the app is for — had no route from the chart at all. So the
 * chip carries a bell and a position tool when both are available, and stays the bare `+` when only
 * the alert is: a two-button chip on a build with one action would be a button and a gap.
 *
 * Both are one tap and neither commits anything. The bell opens the alert composer at this price; the
 * position tool opens the setup with this price as its entry. Nothing is saved by touching the chip.
 *
 * ### Why it lingers after the finger lifts
 *
 * Because on a phone the chip appears *under* the finger that summoned it. A control that vanishes
 * on release can only be pressed by someone who already knows it is there, which is the same defect
 * as the long press it replaces. It stays for [ALERT_LINGER_MILLIS] at the price it was left on,
 * which is long enough to move a thumb and short enough that it never becomes furniture.
 *
 * The price is printed on it, at the axis' own precision, because "alert me at roughly here" is not
 * a thing a reader means: they are looking at the number, and the number is what they will be
 * agreeing to.
 */
@Composable
internal fun PriceAxisAlertAffordance(
    /** Where the plot and its gutters are. Null until the first draw has published a frame. */
    frame: PlotFrame?,
    /** Canvas y of the pointer in the gutter, or null when it is not there. */
    pointerY: Float?,
    /** The price at that y, already resolved against the drawn viewport. */
    price: Double?,
    /** How the axis would print that price — percent, index or price, at its own precision. */
    label: String,
    palette: ChartPalette,
    onRequestAlertAt: (Double) -> Unit,
    /**
     * Open a paper order at this price, where the build has one (run Ω2).
     *
     * Null on a chart with no route to the setup — a preview, a thumbnail, the share renderer — and
     * then the chip is the single `+` it has always been.
     */
    onRequestOrderAt: ((Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (frame == null || pointerY == null || price == null) return
    val density = LocalDensity.current
    val height = with(density) { CHIP_HEIGHT_DP.toPx() }
    val top = (pointerY - height / 2f).coerceAtLeast(0f)
    val width = if (onRequestOrderAt == null) CHIP_WIDTH_DP else CHIP_WIDTH_TWO_DP
    // Against the gutter it belongs to, growing into the plot: the chip is wider than the gutter,
    // and hanging it off the canvas edge would cut the price in half.
    val left = if (frame.tagsOnRight) {
        frame.right - with(density) { width.toPx() } + with(density) { CHIP_BLEED_DP.toPx() }
    } else {
        frame.left - with(density) { CHIP_BLEED_DP.toPx() }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .height(CHIP_HEIGHT_DP)
                .clip(RoundedCornerShape(CHIP_RADIUS_DP))
                .background(palette.crosshair)
                // The whole chip is still the alert, which is what it was and what a reader who has
                // learned it expects. The position tool is a target inside it, not a mode switch.
                .clickable { onRequestAlertAt(price) }
                .padding(horizontal = CHIP_PADDING_DP)
                .semantics { contentDescription = ALERT_LABEL },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CHIP_PADDING_DP),
        ) {
            if (onRequestOrderAt == null) {
                Text(text = "+", color = palette.stage, fontSize = PLUS_SIZE_SP.sp)
            } else {
                // A bell rather than a plus once there are two of them: «+ and a position tool» does
                // not say which of the two the plus is.
                Icon(
                    painter = painterResource(DesignR.drawable.tv_bell),
                    contentDescription = null,
                    tint = palette.stage,
                    modifier = Modifier.size(GLYPH_DP),
                )
            }
            Text(text = label, color = palette.stage, fontSize = axisFontSizeSp(isPriceAxis = true).sp)
            onRequestOrderAt?.let { order ->
                Icon(
                    painter = painterResource(DesignR.drawable.tv_tool_longshort),
                    contentDescription = ORDER_LABEL,
                    tint = palette.stage,
                    modifier = Modifier
                        .size(GLYPH_DP)
                        .clickable { order(price) },
                )
            }
        }
    }
}

/**
 * How long the chip stays after the pointer leaves the gutter.
 *
 * Two and a half seconds: one to notice it, one to reach it. Longer and a reader who was only
 * rescaling the axis is left with a button sitting over their chart.
 */
internal const val ALERT_LINGER_MILLIS = 2_500L

/** Tall enough to be a tap target on a chart that is otherwise all drag gestures. */
private val CHIP_HEIGHT_DP = 26.dp

/** Wide enough for a six-figure price and the plus. */
private val CHIP_WIDTH_DP = 84.dp

/** The same, plus the position tool and the gap before it. */
private val CHIP_WIDTH_TWO_DP = 108.dp

/** How far the chip overhangs the plot, so it reads as attached to the axis rather than floating. */
private val CHIP_BLEED_DP = 2.dp

private val CHIP_RADIUS_DP = 4.dp
private val CHIP_PADDING_DP = 4.dp
private const val PLUS_SIZE_SP = 13f

/** A glyph inside a 26 dp chip, with the padding above and below it left visible. */
private val GLYPH_DP = 14.dp

/** What TalkBack says. Persian, plain, and it names the action rather than the glyph. */
private const val ALERT_LABEL = "افزودن هشدار در این قیمت"

/** The second action, named the same way. */
private const val ORDER_LABEL = "باز کردن معامله‌ی آزمایشی در این قیمت"
