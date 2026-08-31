package com.coinepro.core.designsystem

import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A row that moves rather than teleports.
 *
 * Lists in this app reorder constantly and mostly without being touched. The screener resorts when
 * a column header is tapped and again every time the prices refresh under a live sort. The markets
 * list reranks. An order fills and jumps from working to settled. A signal arrives and lands at the
 * top of the feed. A filter chip removes eleven of nineteen rows.
 *
 * Without this, every one of those is a single frame in which the whole list is a different list.
 * The reader has no way to see that the row they were reading moved rather than vanished, so the
 * only safe reading is to start again from the top — which is exactly what people do, and it is why
 * a fast list can feel slower than a slow one. Two hundred and forty milliseconds of travel is not
 * decoration; it is the only thing carrying identity across the change.
 *
 * ### Where it belongs, and where it does not
 *
 * Only on a list whose contents actually move: reordered, filtered, inserted into, removed from.
 * A settings list, a fixed set of tools, the interval pills — nothing in them ever changes place,
 * so an item animation there is machinery that can only ever cost and never pay.
 *
 * It also requires a stable `key` on the `items(...)` call. Without one, Compose has no way to know
 * that row seven is the row that used to be row two, and the animation has nothing to animate.
 *
 * ### Reduced motion
 *
 * [continuousMotionAllowed] is consulted, and this is a deliberate reading of a setting whose name
 * says "continuous". A person who has turned animations off has asked not to be moved; a list that
 * slides eleven rows past their eye is exactly the thing they turned off, whether or not it loops.
 * They get the instant re-layout, which is what the app did before this existed.
 */
@Composable
fun LazyItemScope.rowMotion(): Modifier =
    if (continuousMotionAllowed()) {
        Modifier.animateItem(
            fadeInSpec = tween(CoineProMotionSpecs.STANDARD_MS, easing = CoineProMotionSpecs.Enter),
            placementSpec = tween(CoineProMotionSpecs.SLOW_MS, easing = CoineProMotionSpecs.Standard),
            fadeOutSpec = tween(CoineProMotionSpecs.FAST_MS, easing = CoineProMotionSpecs.Exit),
        )
    } else {
        Modifier
    }
