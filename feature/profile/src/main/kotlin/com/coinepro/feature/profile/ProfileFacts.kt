package com.coinepro.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProRowDivider
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * One thing the app knows — or admits it does not know — about the reader.
 *
 * The whole type exists for the nullable [value], and it is worth saying why it is nullable rather
 * than defaulted to something readable. Membership standing, verification and plan are a server's
 * answers about one account. When the request fails, or the deployment does not serve the route, or
 * the status is a word this build has never heard of, the app has *nothing* to say — and the
 * dangerous thing a profile screen does is fill that silence. «تأیید نشده» printed beside somebody
 * whose verification simply could not be read is a claim about their account that the app is in no
 * position to make, and it is the claim that sends them to support.
 *
 * So null renders as «نامشخص» in the disabled ink, and it does so inside [ProfileFactRow] rather
 * than at the call site: a caller cannot forget the rule, because the rule is not theirs to apply.
 * For the same reason [tone] is ignored when the value is absent — an unknown fact cannot be drawn
 * green, however confident the caller was.
 *
 * [detail] is the server's own sentence, printed as written. Nothing here paraphrases it, and
 * nothing here writes one when the server did not.
 */
data class ProfileFact(
    val label: String,
    /**
     * The answer, already in the right script for what it is.
     *
     * Prose counts arrive in Persian digits, market figures in Latin ones, and this file does not
     * second-guess either — it cannot tell «۱۲ فهرست» from a P&L by looking at the string.
     */
    val value: String?,
    val tone: ProfileFactTone = ProfileFactTone.NEUTRAL,
    /** The server's own words, or a short note the app owns outright. Never a paraphrase. */
    val detail: String? = null,
    /**
     * Where this fact is explained in full, when there is such a screen.
     *
     * Null is ordinary and not a failure: the number of saved drawing templates is a true thing
     * about the reader with no screen of its own, and it is still worth showing. What is not
     * allowed is a row that neither shows a value nor goes anywhere, which is why [value] and this
     * are never both empty by the time a row is drawn — see [ProfileFactList].
     */
    val onOpen: (() -> Unit)? = null,
)

/**
 * What a fact's answer means, in the app's own three colours.
 *
 * Deliberately not one per membership status or per KYC state. A profile row is a glance, and the
 * reader who wants the detail taps through to the screen that owns it; five shades of amber here
 * would be a second, competing vocabulary for states the membership screen already names properly.
 */
enum class ProfileFactTone {
    /** Nothing is being asserted beyond the value itself — a count, a name, a date. */
    NEUTRAL,

    /** Settled in the reader's favour: approved, verified, active. */
    SETTLED,

    /** Someone is still working on it — the server, the exchange, a reviewer. */
    WAITING,

    /** Refused, expired, or failed in a way the reader has to act on. */
    REFUSED,
}

/**
 * Whether a card built from these would draw a single row.
 *
 * A row that neither shows a value nor goes anywhere is not drawn, so a list made entirely of those
 * is a card made of nothing. Exposed rather than left inside [ProfileFactList] because a
 * `LazyColumn` with a `spacedBy` arrangement pays for an item that renders nothing: three empty
 * cards on a fresh account left three eight-point gaps stacked under the hero, which reads as a
 * layout that lost its content rather than as an account with none yet.
 */
fun List<ProfileFact>.anyDrawable(): Boolean = any { it.value != null || it.onOpen != null }

/**
 * A titled card of facts.
 *
 * Facts with neither a value nor a destination are dropped here rather than drawn empty, and the
 * card disappears entirely when that leaves nothing. A section heading over a card of five dashes is
 * how a profile screen ends up looking broken on the exact accounts — new ones, offline ones —
 * where it most needs to look calm.
 */
@Composable
fun ProfileFactList(
    title: String,
    facts: List<ProfileFact>,
    modifier: Modifier = Modifier,
    /** One line under the card, for something true of every row in it. Dropped with the card. */
    note: String? = null,
) {
    val shown = facts.filter { it.value != null || it.onOpen != null }
    if (shown.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.padding(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                top = CoineProSpacing.One,
                bottom = CoineProSpacing.Half,
            ),
        )
        CoineProCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
            contentPadding = PaddingValues(0.dp),
        ) {
            shown.forEachIndexed { index, fact ->
                if (index > 0) CoineProRowDivider()
                ProfileFactRow(fact)
            }
        }
        note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                modifier = Modifier.padding(
                    start = CoineProSpacing.Gutter,
                    end = CoineProSpacing.Gutter,
                    top = CoineProSpacing.Half,
                ),
            )
        }
    }
}

/**
 * One row: what it is on the reading side, what it says on the other.
 *
 * The label is aligned `TextAlign.Right` rather than `End` throughout this app, because these
 * screens are Persian first and a resolved alignment is one fewer thing that changes meaning when a
 * single string happens to start with a Latin ticker.
 */
@Composable
private fun ProfileFactRow(fact: ProfileFact) {
    val haptics = rememberCoineProHaptics()
    val open = fact.onOpen
    val unknown = fact.value == null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (open == null) {
                    Modifier
                } else {
                    Modifier.clickable {
                        haptics.select()
                        open()
                    }
                },
            )
            // Forty-four, which is the floor for anything a thumb has to hit. A fact row with no
            // destination inherits it too: the rows in one card must sit on the same rhythm, and a
            // 32dp row wedged between two 44dp ones reads as a rendering fault rather than as a
            // row that happens not to be tappable.
            .heightIn(min = 44.dp)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fact.label,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Right,
            )
            fact.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                    textAlign = TextAlign.Right,
                )
            }
        }
        Text(
            text = fact.value ?: stringResource(R.string.profile_fact_unknown),
            style = MaterialTheme.typography.labelLarge,
            // The tone is discarded along with the value. A caller that hands over a null answer
            // and a SETTLED tone has contradicted itself, and the honest half of that pair is the
            // null one.
            color = if (unknown) CoineProColors.TextDisabled else fact.tone.ink(),
            textAlign = TextAlign.Right,
        )
        if (open != null) {
            Icon(
                // The drawable mirrors itself in RTL, so "forward" stays forward in both scripts.
                painter = painterResource(CoineProIcons.ChevronForward),
                contentDescription = null,
                tint = CoineProColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProfileFactTone.ink(): Color = when (this) {
    ProfileFactTone.NEUTRAL -> CoineProColors.TextPrimary
    ProfileFactTone.SETTLED -> CoineProColors.Buy
    ProfileFactTone.WAITING -> CoineProColors.Warning
    ProfileFactTone.REFUSED -> CoineProColors.Sell
}
