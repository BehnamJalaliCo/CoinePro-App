package com.coinepro.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.Entitlements

/**
 * Where a wall used to be (F9).
 *
 * A reader who reaches a screen that was behind a tier does not need a lesson, a promotion or a
 * countdown. They need one sentence saying that nothing here is going to ask them for money, and
 * then they need it never to appear again.
 *
 * ### Why it is a banner and not a toast, a dialog or a badge
 *
 * A dialog interrupts somebody who has just arrived somewhere they wanted to go. A toast is gone
 * before it is read and cannot be dismissed on purpose. A permanent badge is a claim the app makes
 * about itself on every visit, which is advertising. A banner that appears once per screen, sits
 * above the content, and closes for ever on a tap is the smallest thing that does the job.
 *
 * ### Dismissed per screen, for ever
 *
 * Through the same `TeachingStore` every coach-mark uses, under a key of its own — so closing it on
 * the academy does not close it on the signal history, and closing it on either survives a
 * reinstall of the composition, a rotation and the process dying. [key] is the screen's name and
 * nothing else; it is stored, so it is chosen once and not renamed.
 *
 * ### It is absent when it would be false
 *
 * With [Entitlements.all] off — the walls back on — this draws nothing at all. A banner that said
 * «free for everyone» beside a locked screen would be the app arguing with itself, and it is the
 * exact reason this reads the entitlement rather than being switched on by hand at each call site.
 */
@Composable
fun CoineProFreeBanner(
    /** The screen this banner belongs to, e.g. `academy`. Stored: choose it once. */
    key: String,
    modifier: Modifier = Modifier,
) {
    if (!Entitlements.all) return
    val dismissals = LocalTeachingDismissals.current
    val token = FREE_PREFIX + key
    // `ready` is false only while the dismissed set is still coming off disk. Drawing during that
    // window and hiding it once the answer lands is a flash on a surface whose entire job is to be
    // seen once and never again.
    val visible = dismissals.ready && token !in dismissals.dismissed
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half)
                .clip(CoineProShapes.medium)
                .background(CoineProColors.SurfaceElevated)
                .border(1.dp, CoineProColors.Border, CoineProShapes.medium)
                .padding(CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.brand_positioning_free),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextPrimary,
                )
            }
            Icon(
                painter = painterResource(CoineProIcons.Close),
                contentDescription = stringResource(R.string.free_banner_dismiss),
                tint = CoineProColors.TextMuted,
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .clickable { dismissals.dismiss(token) }
                    .size(18.dp),
            )
        }
    }
}

/**
 * The token a free banner's dismissal is stored under.
 *
 * Prefixed so it can never collide with a [TeachingSurface] key, which lives in the same set.
 */
private const val FREE_PREFIX = "free:"
