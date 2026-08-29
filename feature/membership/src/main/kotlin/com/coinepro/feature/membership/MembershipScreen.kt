package com.coinepro.feature.membership

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPageHeading
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.guest.GuestTrackRecord
import com.coinepro.core.guest.MembershipTerms
import com.coinepro.core.membership.MembershipController

/**
 * The membership tab.
 *
 * It used to be a status card and, when the server asked for one, a UID form — which answered
 * «کجای کارم؟» only for a reader who already knew what the four steps were. Everything that
 * explained them lived on the guest home, where a signed-in member never goes.
 *
 * So the tab now renders [MembershipJourneyPanel], the same surface the locked signals screen
 * shows. One implementation on purpose: these two screens answer the same question, and two
 * implementations of one answer is how a UID form ends up on one of them and not the other.
 *
 * The screen keeps only what is its own — the page heading, and the scroll.
 */
@Composable
fun MembershipScreen(
    controller: MembershipController,
    /** Exchanges that accept a UID. From the server; a superset of the copy-trading list. */
    uidExchanges: List<String>,
    modifier: Modifier = Modifier,
    /** The server's terms. Null before they arrive, and then the panel states the steps unlinked. */
    terms: MembershipTerms? = null,
    /** Closed signals with their real outcome, where the server has a gradeable record. */
    trackRecord: GuestTrackRecord? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        item {
            CoineProPageHeading(
                title = stringResource(R.string.membership_status_title),
                eyebrow = stringResource(R.string.membership_eyebrow),
                modifier = Modifier.padding(horizontal = 0.dp),
            )
        }
        item {
            MembershipJourneyPanel(
                controller = controller,
                modifier = Modifier.fillMaxWidth(),
                terms = terms,
                // The caller's list wins where it has one: the app reads it from the same terms
                // object, and an empty list here means the terms had not arrived when it looked.
                uidExchanges = uidExchanges.ifEmpty { terms?.uidExchanges.orEmpty() },
                trackRecord = trackRecord,
                // Nothing sits behind this screen to reload. On the signals list there is, which
                // is the one place the approved state needs a button rather than a sentence.
                onAccessGranted = null,
                headline = stringResource(R.string.membership_status_title),
            )
        }
    }
}
