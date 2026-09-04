package com.coinepro.feature.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProMotionSpecs
import com.coinepro.core.designsystem.CoineProPercentPill
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProSparkline
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.continuousMotionAllowed
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.symbols.MarketStatus

/** Why a market is not quoting, when it is not. Named separately because one of them passes. */
internal enum class MarketClosure {
    /** The ordinary forex weekend, which ends on Sunday evening without anybody doing anything. */
    WEEKEND,

    /** A close the server reported and did not explain. */
    CLOSED,
}

/**
 * Everything the preview sheet draws, taken from what the list already holds.
 *
 * A data class rather than four arguments to a composable, because the interesting decisions —
 * which price string, whether the move is shown at all, whether there is a line worth drawing —
 * are the ones that are wrong silently, and they are testable here and not inside a sheet.
 *
 * [price] is already formatted and bidi-isolated. [line] is empty when there is nothing to draw
 * rather than short, so the sheet has one condition to check instead of two.
 */
internal data class MarketPreviewState(
    val symbol: String,
    /** The slashed form, isolated left-to-right for a Persian paragraph. */
    val pretty: String,
    val name: String,
    val price: String,
    val changePercent: Double?,
    val line: List<Double>,
    val closure: MarketClosure?,
    val starred: Boolean,
)

/**
 * Build the preview from a row the list is already showing.
 *
 * **Nothing here fetches.** That is the whole point of the feature: on the connection this app is
 * built for, opening a screen to read one number is four seconds the reader does not spend, so the
 * preview is only ever a rearrangement of bytes already in memory. [line] is whatever the sparkline
 * store happens to hold — the markets list asks for a symbol's line as the row scrolls into view,
 * so by the time a finger has rested on that row it is usually there, and when it is not the sheet
 * simply has no picture rather than a spinner over a request.
 *
 * The move is dropped when the market is closed, exactly as the row above it drops it: a stale
 * percentage next to the word «بسته» is a number claiming to be today's.
 */
internal fun previewOf(
    row: MarketSearchRow,
    line: List<Double>,
    starred: Boolean,
    status: MarketStatus,
    /**
     * The day's move, where the caller has a better source for it than the quote.
     *
     * It defaults to the quote's own field, which has been null on every quote either backend has
     * ever returned — so in practice this is how the figure arrives at all. The markets list hands
     * in the day's table, and it has to: a sheet reading a dash while the row it was opened from
     * shows +5.2% is the same number disagreeing with itself one layer apart.
     */
    changePercent: Double? = row.quote?.changePercent,
): MarketPreviewState = MarketPreviewState(
    symbol = row.meta.symbol,
    pretty = BidiText.isolateLtr(row.meta.pretty),
    name = row.meta.description,
    // An em dash, not a zero and not a blank: the feed has not quoted this market, and both of the
    // other two would be read as a price.
    price = row.quote?.price?.let(MarketNumberFormatter::priceAuto) ?: EM_DASH,
    changePercent = changePercent?.takeIf { status.open },
    // Fewer than two points is not a short line, it is no line — one price has no shape, and the
    // renderer would have to invent what a single value looks like.
    line = line.takeIf { it.size >= 2 }.orEmpty(),
    closure = when {
        status.open -> null
        status.weekend -> MarketClosure.WEEKEND
        else -> MarketClosure.CLOSED
    },
    starred = starred,
)

/**
 * The sheet a long press on a market row opens.
 *
 * ### Why a sheet and not a screen
 *
 * The question a reader asks of a list forty rows long is "what is this one doing", and the answer
 * is a price, a percentage and a shape. Today the only way to get it is the chart, which is a
 * route, a candle request and a layout — four seconds on the connection this audience has, for
 * three numbers that were already on the phone. The sheet costs nothing and the list stays visible
 * behind it, so the reader can dismiss and press the next row without losing their place.
 *
 * ### The three actions
 *
 * Star, alert, chart — the three things anybody does with a market they just looked at, and the
 * three the research names. Each is a whole tap target rather than an icon, because this is a
 * one-handed screen and the whole reason for the sheet is that the reader is standing up.
 *
 * An action with nothing behind it is not drawn. A row of three buttons where one does nothing is
 * the same lie a disabled control tells, and the caller is the only thing that knows whether there
 * is an alert composer to open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarketPreviewSheet(
    state: MarketPreviewState,
    onDismiss: () -> Unit,
    onOpenChart: () -> Unit,
    onToggleStar: (() -> Unit)? = null,
    onCreateAlert: (() -> Unit)? = null,
) {
    // The sheet's body is a small transition — 180ms on the design direction's own ladder — and it
    // collapses to nothing when the device has animations turned off. `CoineProMotionSpecs` is not
    // used here because its beats are the web terminal's 100/160/240; the ladder in
    // `docs/DESIGN_DIRECTION.md` is what this screen was specified against.
    val animate = continuousMotionAllowed()
    var arrived by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { arrived = true }
    val progress by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (animate) SMALL_TRANSITION_MS else 0,
            easing = CoineProMotionSpecs.Enter,
        ),
        label = "previewEnter",
    )

    CoineProSheet(
        title = state.pretty,
        subtitle = state.name,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer { alpha = progress }
                .padding(
                    start = CoineProSpacing.Gutter,
                    end = CoineProSpacing.Gutter,
                    bottom = CoineProSpacing.Three,
                ),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            ) {
                Text(
                    text = state.price,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        textDirection = TextDirection.Ltr,
                    ),
                    color = CoineProColors.TextPrimary,
                    // Right, never End: the figure is Latin inside a right-to-left paragraph, and
                    // an End alignment would throw it to the far side of its own box.
                    textAlign = TextAlign.Right,
                    maxLines = 1,
                )
                when {
                    state.changePercent != null -> CoineProPercentPill(
                        percent = state.changePercent,
                        background = CoineProColors.Surface,
                    )
                    // A closed market says so where the move would be. It is the reason the move is
                    // missing, and saying it here is what stops the gap reading as a feed fault.
                    state.closure != null -> Text(
                        text = stringResource(
                            if (state.closure == MarketClosure.WEEKEND) {
                                R.string.search_weekend
                            } else {
                                R.string.search_closed
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                    else -> Unit
                }
            }
            // No line, no box. An empty 40dp rectangle where a shape belongs reads as a chart that
            // failed to draw, and this sheet never asked for one — see `previewOf`.
            if (state.line.isNotEmpty()) {
                CoineProSparkline(
                    values = state.line,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colour = when {
                        state.changePercent == null -> CoineProColors.TextMuted
                        state.changePercent >= 0.0 -> CoineProColors.MarketUp
                        else -> CoineProColors.MarketDown
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                if (onToggleStar != null) {
                    PreviewAction(
                        icon = if (state.starred) {
                            DesignR.drawable.icon_filled_star
                        } else {
                            DesignR.drawable.icon_star
                        },
                        labelRes = if (state.starred) {
                            R.string.watchlist_unstar
                        } else {
                            R.string.watchlist_star
                        },
                        active = state.starred,
                        onClick = onToggleStar,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onCreateAlert != null) {
                    PreviewAction(
                        icon = DesignR.drawable.icon_bell,
                        labelRes = R.string.preview_alert,
                        active = false,
                        onClick = onCreateAlert,
                        modifier = Modifier.weight(1f),
                    )
                }
                PreviewAction(
                    icon = DesignR.drawable.nav_chart,
                    labelRes = R.string.preview_chart,
                    active = false,
                    onClick = onOpenChart,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One of the sheet's three actions.
 *
 * The tint crosses over in 120ms — the design direction's state-response beat — rather than
 * swapping, because starring from here is the one control whose state changes while the reader is
 * looking at it, and an instant swap makes a deliberate act look like a redraw. With animations
 * off the duration is zero and the colour is simply the new one.
 */
@Composable
private fun PreviewAction(
    @DrawableRes icon: Int,
    @StringRes labelRes: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    val interaction = remember { MutableInteractionSource() }
    val animate = continuousMotionAllowed()
    val ink by animateColorAsState(
        targetValue = if (active) CoineProColors.Accent else CoineProColors.TextSecondary,
        animationSpec = tween(durationMillis = if (animate) STATE_RESPONSE_MS else 0),
        label = "previewActionInk",
    )
    val label = stringResource(labelRes)
    Column(
        modifier = modifier
            .clip(CoineProShapes.small)
            .background(CoineProColors.SurfaceElevated)
            .clickable(interaction, null) {
                // Every one of the three changes something the reader would want undone if it were
                // wrong — a list edited, an alert armed, a screen opened — so all three get the
                // committed weight rather than a selection tick.
                haptics.commit()
                onClick()
            }
            // Forty-eight rather than the forty-four floor: these are the primary actions of the
            // sheet, and the reader is one-handed and standing.
            .padding(vertical = CoineProSpacing.OneHalf),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Icon(
            painter = painterResource(icon),
            // Named on the icon and not on the column, so the label under it is not read twice.
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ink,
            maxLines = 1,
        )
    }
}

/** A price the feed has not sent. Not a zero, which would be a claim. */
private const val EM_DASH = "—"

/**
 * The two beats this sheet moves on, from the ladder in `docs/DESIGN_DIRECTION.md`.
 *
 * Named here rather than taken from `CoineProMotionSpecs`, which carries the web terminal's
 * 100/160/240 for the surfaces shared with it. Both are gated on `continuousMotionAllowed()`, so a
 * reader who turned animations off sees the finished state and no transition at all.
 */
private const val STATE_RESPONSE_MS = 120
private const val SMALL_TRANSITION_MS = 180
