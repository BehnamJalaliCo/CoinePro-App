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
