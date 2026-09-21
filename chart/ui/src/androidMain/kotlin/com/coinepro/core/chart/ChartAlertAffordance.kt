package com.coinepro.core.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
 *
 * ### It is **in** the gutter, not over the plot (run Ω-FIX item 7)
 *
 * It used to be a single row 84 or 108 points wide, right-aligned to the canvas and bleeding two
 * points past the axis hairline. The gutter this chart draws is about sixty-four, so between twenty
 * and forty-four points of chip sat on the candles — and during a scrub, which is when it appears,
 * that is a plate over the exact part of the chart the reader is scrubbing. The owner's device
 * report is unambiguous: «پیل «77,124.2 ⇄» روی پلات هنگام scrub».
 *
 * So the chip is exactly [PlotFrame.tagGutterWidth] wide, laid out from the axis hairline outwards,
 * and it stacks instead of stretching: the price on its own line, the one or two actions in a row
 * underneath. Nothing of it crosses onto the plot at any gutter width, and the two glyphs get a
 * bigger target than they had in a row that was fighting a six-figure price for the same points.
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
    // The gutter's own width, in pixels, and the chip is exactly that. A canvas with the axis off
    // has none, and then there is nowhere to put this that is not the plot — so it is not drawn.
    val gutterPx = frame.tagGutterWidth
    if (gutterPx <= 0f) return
    val gutter = with(density) { gutterPx.toDp() }
    val height = with(density) { CHIP_HEIGHT_DP.toPx() }
    val top = (pointerY - height / 2f).coerceAtLeast(0f)
    val left = alertChipLeft(frame)
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .width(gutter)
                .height(CHIP_HEIGHT_DP)
                .clip(RoundedCornerShape(CHIP_RADIUS_DP))
                .background(palette.crosshair)
                // The whole chip is still the alert, which is what it was and what a reader who has
                // learned it expects. The position tool is a target inside it, not a mode switch.
                .clickable { onRequestAlertAt(price) }
                .semantics { contentDescription = ALERT_LABEL },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The price first and on its own line, because it is what the reader is agreeing to and
            // it is the widest thing here — the gutter is sized to hold exactly this text.
            Text(
                text = label,
                color = palette.stage,
                fontSize = axisFontSizeSp(isPriceAxis = true).sp,
                maxLines = 1,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CHIP_PADDING_DP),
            ) {
                if (onRequestOrderAt == null) {
                    Text(text = "+", color = palette.stage, fontSize = PLUS_SIZE_SP.sp)
                } else {
                    // A bell rather than a plus once there are two of them: «+ and a position tool»
                    // does not say which of the two the plus is.
                    Icon(
                        painter = painterResource(DesignR.drawable.tv_bell),
                        contentDescription = null,
                        tint = palette.stage,
                        modifier = Modifier.size(GLYPH_DP),
                    )
                }
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
}

/**
 * The chip's left edge in canvas pixels — the axis hairline, on whichever side the tags are.
 *
 * Separate from the composable because it is the part of run Ω-FIX item 7 that can be wrong and the
 * only part that can be asserted without a renderer. Together with [PlotFrame.tagGutterWidth] as
 * the chip's width, it is the whole claim: `left` is where the plot stops and `left + width` is
 * where the canvas does, so no pixel of the chip is over the candles. There is no bleed — two
 * points of overhang was the smaller half of what made this a plate on the plot.
 */
internal fun alertChipLeft(frame: PlotFrame): Float =
    if (frame.tagsOnRight) frame.right else frame.left - frame.tagGutterWidth

/**
 * How long the chip stays after the pointer leaves the gutter.
 *
 * Two and a half seconds: one to notice it, one to reach it. Longer and a reader who was only
 * rescaling the axis is left with a button sitting over their chart.
 */
internal const val ALERT_LINGER_MILLIS = 2_500L

/**
 * Two rows: the price over the actions.
 *
 * Forty points rather than twenty-six, and the extra fourteen are what buying the width back cost —
 * the chip is now as wide as the gutter and no wider, so the price and the glyphs cannot share a
 * line. It is still entirely inside the axis, which is the point, and it is a *larger* target than
 * the row was.
 */
private val CHIP_HEIGHT_DP = 40.dp

private val CHIP_RADIUS_DP = 4.dp
private val CHIP_PADDING_DP = 4.dp
private const val PLUS_SIZE_SP = 13f

/** A glyph on the chip's action row: small enough that two of them fit a sixty-four point gutter. */
private val GLYPH_DP = 14.dp

/** What TalkBack says. Persian, plain, and it names the action rather than the glyph. */
private const val ALERT_LABEL = "افزودن هشدار در این قیمت"

/** The second action, named the same way. */
private const val ORDER_LABEL = "باز کردن معامله‌ی آزمایشی در این قیمت"
