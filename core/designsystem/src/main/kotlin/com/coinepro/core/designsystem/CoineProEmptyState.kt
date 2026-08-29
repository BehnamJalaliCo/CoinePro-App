package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
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
 * ### What changed, and why it was flat before
 *
 * The disc was an 8% muted tint over the stage and nothing else — which, in the light theme, is a
 * circle of `#F6F6F7` on white. It was invisible, so a screen that passed an icon looked exactly
 * like one that did not. It has a hairline now, at the same weight every other surface in the
 * system gained, so the mark sits in something.
 *
 * The sentence was `titleSmall` in the secondary ink and the hint was `bodySmall` in the muted one:
 * two greys, four points apart, on a screen with nothing else on it. The fact is the only heading
 * this screen has, so it is `titleMedium` in the primary ink and the hint recedes properly behind
 * it. A hierarchy of one is not a hierarchy.
 *
 * And it arrives rather than appearing — see [coineProEnter]. An empty screen that fades up reads
 * as a screen that finished looking; one that is simply there at frame one reads as one that never
 * started.
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
    StateBlock(
        modifier = modifier,
        icon = icon,
        tint = CoineProColors.TextMuted,
        message = message,
        hint = hint,
        action = action,
        onAction = onAction,
    )
}

/**
 * What a screen shows when it asked for something and was refused.
 *
 * ### Why this is not [CoineProEmptyState] with different words
 *
 * They are the two most-skipped surfaces in any app and they were one surface here, which meant
 * every failure in the product was announced in the same muted grey as "you have no alerts yet".
 * A reader has no way to tell a state they caused from one the server did, and the two need
 * opposite things from them: an empty screen needs patience, a failed one needs a retry.
 *
 * So the difference is carried where a reader reads it before any words — the mark is the warning
 * glyph in a disc tinted with the refusal colour, and the retry is the screen's primary action
 * rather than a neutral pill, because it is the one thing to do here.
 *
 * What it deliberately does not do is shout. The tint is the same 8% every other meaningful surface
 * in this app uses; a full red panel would make a stale quote look like a failed trade.
 *
 * [detail] is the server's own sentence where there is one worth showing. It is placed *under* the
 * plain-language line rather than instead of it: «AI Signal request was rejected by server
 * validation» is true, and it is not what a Persian-reading trader opened the app to be told.
 */
@Composable
fun CoineProErrorState(
    /** The failure, in the reader's language, said plainly. */
    message: String,
    modifier: Modifier = Modifier,
    /** The server's own words, where they add something. Null where they do not. */
    detail: String? = null,
    /** The label on the retry. Null where retrying cannot help. */
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateBlock(
        modifier = modifier,
        icon = CoineProIcons.Warning,
        tint = CoineProColors.Sell,
        message = message,
        hint = detail,
        action = action,
        onAction = onAction,
        actionIsPrimary = true,
    )
}

/**
 * The shape both states share, so the two can never drift into looking like different screens.
 *
 * Private on purpose: the choice a caller makes is "is this empty or did it fail", not "which tint
 * and which button". A third state that is neither would be a third named function here, with its
 * own reason, rather than another parameter.
 */
@Composable
private fun StateBlock(
    modifier: Modifier,
    @DrawableRes icon: Int?,
    tint: Color,
    message: String,
    hint: String?,
    action: String?,
    onAction: (() -> Unit)?,
    actionIsPrimary: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .coineProEnter()
            .padding(horizontal = CoineProSpacing.Four, vertical = CoineProSpacing.Two),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(CoineProTint.fill(tint, CoineProColors.Stage))
                    // The edge is what makes the disc an object. Without it the fill is an 8% tint
                    // over the stage, which in the light theme is a circle nobody can see.
                    .border(1.dp, CoineProTint.edge(tint), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = tint,
                )
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
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
            val pad = Modifier.padding(top = CoineProSpacing.Half)
            if (actionIsPrimary) {
                CoineProPrimaryButton(text = action, onClick = onAction, modifier = pad)
            } else {
                CoineProSecondaryButton(text = action, onClick = onAction, modifier = pad)
            }
        }
    }
}
