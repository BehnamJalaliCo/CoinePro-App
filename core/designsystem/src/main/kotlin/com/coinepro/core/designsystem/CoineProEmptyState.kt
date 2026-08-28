package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * What a screen shows when there is genuinely nothing to show.
 *
 * ### The state that decides whether an app looks finished
 *
 * One grey sentence in the middle of a black screen is what the signals list, the news list and the
 * calendar all showed, and it is indistinguishable from a screen that failed. A reader cannot tell
 * "there are no signals right now" from "this did not load", and the difference is the whole
 * message. Nothing else on the screen is available to help: there are no rows, by definition.
 *
 * So the empty state has to carry the weight on its own, and it does it with three things and no
 * more — a mark, a sentence, and where there is something to do about it, one button:
 *
 * - **The mark** is the screen's own glyph in a tinted disc, not a mascot and not an illustration.
 *   It says *which* screen is empty, which matters when a reader has arrived by a tab they may have
 *   pressed by accident, and it fills the space that otherwise reads as a failure.
 * - **The sentence** states the fact. Not an apology, and never «چیزی یافت نشد» phrased as though
 *   the reader did something wrong.
 * - **The hint** is optional and is where the screen says what *would* fill it — "signals appear
 *   here when the desk publishes one" — because a reader who knows that will come back, and one
 *   who does not concludes the feature is broken.
 * - **The action** is optional too, and there is at most one. Two buttons on an empty screen is a
 *   decision, and the reader has nothing to decide with.
 *
 * The disc is the same 8% tint over the stage the rest of the system uses for a tinted surface, so
 * it belongs to the app rather than reading as a piece of artwork somebody dropped in.
 */
@Composable
fun CoineProEmptyState(
    /** The fact, stated plainly. */
    message: String,
    modifier: Modifier = Modifier,
    /** The screen's own glyph. Null draws the sentence alone, which is the old behaviour. */
    @DrawableRes icon: Int? = null,
    /** What would fill this screen, where the reader would otherwise assume it is broken. */
    hint: String? = null,
    /** At most one. Null where there is nothing the reader can do. */
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Four, vertical = CoineProSpacing.Two),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CoineProTint.fill(CoineProColors.TextMuted, CoineProColors.Stage)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = CoineProColors.TextMuted,
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null && onAction != null) {
            CoineProSecondaryButton(
                text = action,
                onClick = onAction,
                modifier = Modifier.padding(top = CoineProSpacing.Half),
            )
        }
    }
}
