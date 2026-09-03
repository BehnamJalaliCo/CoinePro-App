package com.coinepro.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.PRO_CHART_FA
import com.coinepro.core.designsystem.ProChartMark
import com.coinepro.core.designsystem.ProChartWordmark
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.continuousMotionAllowed
import kotlinx.coroutines.delay

/**
 * The launch: the mark streams in, then the name, black on white, and the app is behind it.
 *
 * ### What the owner asked for
 *
 * TradingView's phone app opens on a white screen, its mark draws itself in, its name follows, and
 * a second or two later the chart is there. «در حد ۱ تا ۲ ثانیه … پس‌زمینهٔ سفید و لوگو و نوشته
 * کاملاً مشکی». This is that, with the owner's own mark and name.
 *
 * ### How it moves
 *
 * One clock, [SPLASH_MS] long, and everything is read off it:
 *
 *  * the **mark** wipes in from left to right over the first third while easing up from 88 % to
 *    full size — a shape being drawn rather than a picture being faded;
 *  * the **name** wipes in from its reading edge — the right in Persian, the left in English —
 *    over the middle third, after the mark has landed;
 *  * the whole sheet **holds** and then fades over the last [FADE_MS], and the app underneath, which
 *    has been composing the whole time, is simply there.
 *
 * A wipe rather than a blur or a glow, because the house rules allow neither and because a wipe
 * is what "streaming in" looks like; and one progress value rather than a chain of animations,
 * because a chain is the kind of thing that leaves a frame behind when the reader rotates the phone.
 *
 * ### Reduced motion
 *
 * With animations off — `continuousMotionAllowed()` reads the platform scale — the sheet shows the
 * finished lockup for [STILL_MS] and goes. A person who turned animations off asked not to watch
 * shapes draw themselves, and the screenshot renders read the same flag so a capture is the still.
 *
 * ### White and black, in both themes
 *
 * The owner asked for exactly that, and it is also right: the launch has no theme yet to follow,
 * and a sheet that changes colour with the theme is a sheet the reader sees flash from one to the
 * other on the first frame. [Color.White] and [Color.Black] by name, deliberately, not the palette.
 *
 * ### English draws one asset
 *
 * `prochart_wordmark_is_lockup` says the Latin wordmark already carries the mark, so there the
 * lockup alone is drawn and the wipe reveals mark then name on its own. See `ProChartLockup`.
 */
@Composable
fun LaunchSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether to draw the sheet in or show it finished. The device's animation setting, by default. */
    moving: Boolean = continuousMotionAllowed(),
) {
    val finished by rememberUpdatedState(onFinished)
    val progress = remember { Animatable(if (moving) 0f else 1f) }
    LaunchedEffect(moving) {
        if (moving) {
            progress.animateTo(1f, tween(SPLASH_MS, easing = LinearEasing))
        } else {
            delay(STILL_MS.toLong())
        }
        finished()
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lockupOnly = booleanResource(DesignR.bool.prochart_wordmark_is_lockup)
    val t = progress.value
    val sheetAlpha = if (moving) 1f - phase(t, FADE_FROM, 1f) else 1f
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = sheetAlpha }
            .background(Color.White)
            .semantics { contentDescription = PRO_CHART_FA },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LOCKUP_GAP),
        ) {
            if (!lockupOnly) {
                val drawn = phase(t, 0f, MARK_UNTIL)
                ProChartMark(
                    tint = Color.Black,
                    modifier = Modifier
                        .height(MARK_HEIGHT)
                        .width(MARK_HEIGHT)
                        .graphicsLayer {
                            val scale = MARK_SCALE_FROM + (1f - MARK_SCALE_FROM) * drawn
                            scaleX = scale
                            scaleY = scale
                        }
                        .wipe(drawn, fromLeft = true),
                )
            }
            val named = if (lockupOnly) phase(t, 0f, NAME_UNTIL) else phase(t, NAME_FROM, NAME_UNTIL)
            ProChartWordmark(
                tint = Color.Black,
                modifier = Modifier
                    .width(if (lockupOnly) LOCKUP_WIDTH else NAME_WIDTH)
                    // The Latin lockup reads left to right whatever the page does.
                    .wipe(named, fromLeft = !rtl || lockupOnly),
            )
        }
    }
}

/** Where [t] stands between [from] and [to], clamped to 0..1. */
private fun phase(t: Float, from: Float, to: Float): Float =
    ((t - from) / (to - from)).coerceIn(0f, 1f)

/**
 * Reveal the content from one edge to the other, by [fraction] of its width.
 *
 * A clip rather than an alpha: the ink arrives as a front moving across the shape, which is what
 * a stroke being drawn looks like, and it needs no blur to look continuous.
 */
private fun Modifier.wipe(fraction: Float, fromLeft: Boolean): Modifier = drawWithContent {
    val shown = size.width * fraction
    if (fromLeft) {
        clipRect(right = shown) { this@drawWithContent.drawContent() }
    } else {
        clipRect(left = size.width - shown) { this@drawWithContent.drawContent() }
    }
}

/** The whole launch, and the still shown instead when animations are off. */
private const val SPLASH_MS = 1800
private const val STILL_MS = 900
private const val FADE_MS = 250

/** Phases of the one clock, as fractions of [SPLASH_MS]. */
private const val MARK_UNTIL = 0.36f
private const val NAME_FROM = 0.34f
private const val NAME_UNTIL = 0.72f
private const val FADE_FROM = 1f - FADE_MS.toFloat() / SPLASH_MS

private const val MARK_SCALE_FROM = 0.88f

private val MARK_HEIGHT = 96.dp
private val NAME_WIDTH = 176.dp
private val LOCKUP_WIDTH = 240.dp
private val LOCKUP_GAP = 24.dp
