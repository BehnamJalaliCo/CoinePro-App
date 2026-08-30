package com.coinepro.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * One line, at the top, saying the phone has no network.
 *
 * ### Why a bar and not a toast
 *
 * Being offline is a *condition*, not an event. A toast announces something that happened and then
 * leaves; a reader who looks at the screen ten seconds later would see a normal-looking app with
 * stale prices and no explanation. The bar stays for exactly as long as the fact does, and leaves
 * by itself the moment the network returns — which is also the notification that it returned, so
 * nothing else has to announce it.
 *
 * ### What it deliberately does not do
 *
 * It does not block anything and it has no dismiss. Every screen in this app is readable offline —
 * the market list holds its last snapshot, the journal and the paper trades are local, the
 * watchlist is on the device — so the bar reports and gets out of the way. An offline dialog over
 * a screen full of usable content would be the app deciding that its own connectivity is more
 * important than the reader's work.
 *
 * It also never says "retry". There is nothing to retry until the network is back, and the app
 * will notice that before the reader can tap anything.
 */
@Composable
fun CoineProOfflineBar(
    online: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = !online,
        modifier = modifier,
        // Expanding rather than sliding over: the bar takes its own row and pushes the screen
        // down, so it never covers the first line of whatever the reader was reading.
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CoineProTint.fill(CoineProColors.Sell, CoineProColors.Stage))
                .padding(horizontal = CoineProSpacing.Gutter, vertical = BAR_VERTICAL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Icon(
                painter = painterResource(CoineProIcons.Warning),
                contentDescription = null,
                tint = CoineProColors.Sell,
                modifier = Modifier.size(GLYPH),
            )
            Text(
                text = stringResource(R.string.offline_bar),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextSecondary,
            )
        }
    }
}

private val BAR_VERTICAL = 6.dp
private val GLYPH = 14.dp

/**
 * The other kind of "the prices are not moving": ours are fine, the venue's relay is not.
 *
 * ### Why this is a separate bar from [CoineProOfflineBar]
 *
 * They look alike and mean opposite things about what the reader should do. Offline is the phone,
 * it clears when the reader walks to a window, and every number on screen is a remembered one. This
 * is the *server's* upstream: the phone is fine, the request succeeded, and the day's figures in
 * front of the reader are correct — it is the live price behind them that has stopped ticking.
 * Wording those as one sentence would tell a reader to check their connection over a problem no
 * connection of theirs can fix.
 *
 * ### Why it exists at all
 *
 * TradeYar's relay sat on its REST fallback for forty-five hours and every probe stayed green,
 * because a degraded tier still answers `200`. This bar is the reader's half of the fix; the
 * server built the alerting for theirs. Neither half alone would have caught it.
 *
 * It is drawn in the warning ink rather than the sell red — this is "older than it looks", not
 * "broken" — and, like the offline bar, it takes its own row, has no dismiss, and leaves by itself.
 */
@Composable
fun CoineProPriceFeedBar(
    /**
     * The relay's health, or null where the server does not report it.
     *
     * Null draws nothing. A deployment that predates the field cannot be distinguished from a
     * healthy one, and inventing a reassuring answer for it is exactly the silence this bar was
     * built to break.
     */
    status: PriceFeedReading?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = status != null,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CoineProTint.fill(CoineProColors.Warning, CoineProColors.Stage))
                .padding(horizontal = CoineProSpacing.Gutter, vertical = BAR_VERTICAL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Icon(
                painter = painterResource(CoineProIcons.Warning),
                contentDescription = null,
                tint = CoineProColors.Warning,
                modifier = Modifier.size(GLYPH),
            )
            Text(
                // Held while the bar animates out, so the sentence does not blank a frame before
                // the row finishes collapsing.
                text = stringResource(
                    when (status ?: PriceFeedReading.PARTIAL) {
                        PriceFeedReading.PARTIAL -> R.string.price_feed_partial
                        PriceFeedReading.FULL -> R.string.price_feed_full
                        PriceFeedReading.UNKNOWN -> R.string.price_feed_unknown
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextSecondary,
            )
        }
    }
}

/**
 * What the bar has to say, as three cases rather than a boolean.
 *
 * `core:designsystem` cannot see `core:marketdata`, so the caller maps the relay's own status onto
 * this — which is the right way round anyway: the design system owns the sentence, and the module
 * that talks to the server owns the reading rule. See `PriceFeedStatus` for that rule.
 */
enum class PriceFeedReading {
    /** Some shards are down; part of the catalogue is frozen and the rest is live. */
    PARTIAL,

    /** Every shard is down. Prices arrive by polling, in steps rather than ticks. */
    FULL,

    /** The relay could not be read at all. Not health, and not drawn as health. */
    UNKNOWN,
}
