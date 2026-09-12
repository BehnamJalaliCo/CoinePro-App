package com.coinepro.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider

/**
 * A figure whose digits roll to their new values instead of being replaced (run Ω2).
 *
 * ### What this is for and what it is not for
 *
 * It is for **one** number per screen: the live price a reader is watching. A price that snaps from
 * `77,004.19` to `77,004.23` gives the eye nothing to follow, so a reader checking whether the market
 * is moving has to compare a number against their memory of it a second ago. A digit that slides up
 * when it counts up says «moving, and which way» before any figure has been read.
 *
 * It is emphatically **not** for a list. Forty rows of rolling digits is a slot machine, and the
 * [Modifier.coineProPriceFlash] tint is what a list uses instead — one signal that says *which rows*
 * moved, which is the question a list answers. This says *how* one price moved, which is the question
 * a header answers.
 *
 * ### Why it is per character and not per number
 *
 * Because only the characters that changed should move. Animating the whole string slides `77,004`
 * for a tick in the last decimal, which is five digits of motion reporting one digit of news — and it
 * is the single most common way this effect is got wrong. Each position is its own
 * [AnimatedContent], keyed on the character at it.
 *
 * ### Why the width cannot move
 *
 * Every numeric style in this app is tabular ([numeric]) so a `1` and an `8` are the same width, which
 * is what makes a per-character animation possible at all: without it each digit's box would resize as
 * it rolled and the whole figure would jitter sideways. The style is forced tabular here rather than
 * trusted, because a caller passing `titleLarge` would otherwise get the jitter and no explanation.
 *
 * A figure that changes *length* — `9,999` to `10,000` — adds a position, and the new one fades in
 * rather than sliding, because there is no previous character for it to have come from.
 */
@Composable
fun CoineProRollingNumber(
    /** The figure, already formatted. This composable never formats and never rounds. */
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    /**
     * Which way the digits travel: up for a rise, down for a fall, and no motion for neither.
     *
     * The caller decides, because only the caller knows whether the *number* went up — a formatted
     * string cannot be compared, `9.99` is a longer string than `10.0` and a shorter number.
     */
    direction: Int = 0,
) {
    val animate = continuousMotionAllowed()
    // Tabular, always. See the note above: the whole effect rests on a digit box that cannot resize.
    val figures = remember(style) { style.numeric().copy(textDirection = TextDirection.Ltr) }
    if (!animate) {
        Text(text = text, style = figures, color = color, modifier = modifier)
        return
    }
    // Left to right, whatever the paragraph around it is doing. A price is a Latin figure and its
    // digits are ordered most-significant-first in every locale this app ships; laying the row out
    // right-to-left in Persian would print `19.400,77`.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            for (index in text.indices) {
                val character = text[index]
                AnimatedContent(
                    targetState = character,
                    transitionSpec = {
                        val enter = when {
                            // A digit that has counted up came from below, so it enters from below
                            // and the old one leaves upward. The reverse for a fall. A separator —
                            // a comma, a decimal point — never moves at all, so its `when` arm
                            // below keeps it out of this entirely.
                            direction > 0 -> slideInVertically { height -> height } + fadeIn(FADE)
                            direction < 0 -> slideInVertically { height -> -height } + fadeIn(FADE)
                            else -> fadeIn(FADE)
                        }
                        val exit = when {
                            direction > 0 -> slideOutVertically { height -> -height } + fadeOut(FADE)
                            direction < 0 -> slideOutVertically { height -> height } + fadeOut(FADE)
                            else -> fadeOut(FADE)
                        }
                        enter togetherWith exit
                    },
                    label = "digit-$index",
                ) { shown ->
                    Text(text = shown.toString(), style = figures, color = color)
                }
            }
        }
    }
}

/**
 * How long a digit takes to travel.
 *
 * A hundred and sixty: the same order as the price flash it accompanies, and short enough that a pair
 * of ticks arriving inside a third of a second do not queue up into a visible lag behind the feed.
 */
private val FADE = tween<Float>(durationMillis = 160)
