package com.coinepro.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * How far a trade has travelled from its stop toward its target.
 *
 * ### The question the list could not answer
 *
 * A signal row carried four numbers — entry, stop, target, live price — and left the reader to do
 * the arithmetic that turns them into the only thing they wanted to know: *is this working?*
 * Four prices at four decimals, in a column, on a phone, is not a question anybody answers by
 * looking. Every serious app that publishes trade ideas draws this bar, and it is drawing the
 * subtraction the reader would otherwise do in their head, badly, on every row.
 *
 * ### The axis is the trade, not the price
 *
 * Zero is the stop and one is the target — always, in that order, for a long and a short alike.
 * The alternative, an axis of ascending price, would put the target on the left for a sell and on
 * the right for a buy, so the same picture would mean opposite things two rows apart. Here the left
 * end is always the end where the money is lost and the right end always the end where it is made,
 * and the reader learns one shape instead of two.
 *
 * That also makes the formula direction-free: `(price − stop) / (target − stop)` is 0 at the stop
 * and 1 at the target whichever way the trade points, because both differences change sign
 * together.
 *
 * ### Not mirrored in Persian
 *
 * The layout is absolute. This is a number line, not a sentence: mirroring it in a right-to-left
 * layout would move the stop to the right and quietly invert what "past the entry" looks like.
 * Numbers in this app are always read left to right for the same reason.
 */
@Composable
fun CoineProSetupProgress(
    entry: Double?,
    stop: Double?,
    target: Double?,
    price: Double?,
    modifier: Modifier = Modifier,
) {
    if (entry == null || stop == null || target == null) return
    val span = target - stop
    // A stop and a target at the same price is not a trade, and dividing by it would put every
    // marker at one end — which would read as a position at its stop rather than as no span at all.
    if (span == 0.0) return

    val entryAt = ((entry - stop) / span).coerceIn(0.0, 1.0).toFloat()
    val priceAt = price?.let { ((it - stop) / span).toFloat() }

    // Forced left-to-right for the whole figure. `Alignment.CenterStart` and `fillMaxWidth(f)` are
    // both layout-direction aware, so in Persian — the app's default — the track would have grown
    // from the right and the stop would have swapped ends with the target. Providing the direction
    // here is one line and covers every child; mirroring each of them by hand is four chances to
    // miss one.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(MARKER),
        ) {
            // The track in two halves, cut at the entry: everything behind the entry is what the trade
            // risks and everything ahead of it is what it is for. Tinted rather than saturated — the
            // marker is the thing to find, and a bar in full red and green would out-shout it.
            Box(
                modifier = Modifier
                    .fillMaxWidth(entryAt)
                    .height(TRACK)
                    .align(Alignment.CenterStart)
                    .clip(CoineProPillShape)
                    .background(CoineProTint.fill(CoineProColors.Sell, CoineProColors.Stage)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f - entryAt)
                    .height(TRACK)
                    .align(Alignment.CenterEnd)
                    .clip(CoineProPillShape)
                    .background(CoineProTint.fill(CoineProColors.Buy, CoineProColors.Stage)),
            )

            // The entry itself, as a notch rather than a dot: it is a boundary between the two halves,
            // and a dot there would be a second thing competing with the price marker.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fraction(entryAt, NOTCH)
                    .width(NOTCH)
                    .height(MARKER)
                    .background(CoineProColors.TextSecondary),
            )

            if (priceAt != null) {
                // Past either end the marker pins rather than disappearing. A trade that has run
                // through its target is the most interesting row in the list, and a bar that showed
                // nothing there would hide exactly the ones a reader is looking for.
                val ink = when {
                    priceAt >= 1f -> CoineProColors.Buy
                    priceAt <= 0f -> CoineProColors.Sell
                    priceAt >= entryAt -> CoineProColors.Buy
                    else -> CoineProColors.Sell
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fraction(priceAt.coerceIn(0f, 1f), MARKER)
                        .width(MARKER)
                        .height(MARKER)
                        .clip(CoineProPillShape)
                        // The same notch the avatar badge uses, in the stage colour, so the marker
                        // reads as an object on the track rather than as a lump of it.
                        .background(CoineProColors.Stage)
                        .padding(1.dp)
                        .clip(CoineProPillShape)
                        .background(ink),
                )
            }
    }
    }
}

/**
 * Places a child [fraction] of the way along, inset by its own [size] so it stays on the track.
 *
 * A percentage offset alone would hang half the marker off the end at both extremes — which reads
 * as a price beyond the stop when it is a price exactly at it.
 */
private fun Modifier.fraction(fraction: Float, size: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val travel = (constraints.maxWidth - size.roundToPx()).coerceAtLeast(0)
        layout(constraints.maxWidth, placeable.height) {
            placeable.place((travel * fraction).toInt(), 0)
        }
    }

/** The track's thickness. */
private val TRACK = 3.dp

/** The price marker, and the height the entry notch spans. */
private val MARKER = 9.dp

/** The entry notch's width. */
private val NOTCH = 2.dp
