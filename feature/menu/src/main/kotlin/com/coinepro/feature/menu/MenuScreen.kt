package com.coinepro.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProRowDivider
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.model.AvatarSpec

/**
 * The menu: one page that says what this app contains.
 *
 * ### Why a menu and not a better search field
 *
 * Search answers a reader who knows the name of the thing they want. Nothing answered the reader
 * who does not, and that is most people on their first week: twenty-six destinations were reached
 * through a scatter of avatars, cards, toolbar corners and long-presses, and a person who never
 * found the card never found the screen. Every app in this category leads with this page for the
 * same reason.
 *
 * ### It leads with the account, because that is what this kind of menu is for
 *
 * The first block is who the reader is — signed in on which backend, on which plan, or a guest
 * with an offer they can ignore. Not decoration: on a product where entitlements decide what a
 * signal shows, «چرا سیگنال‌ها قفل است» is answered by that block and by nothing else in the app.
 *
 * ### A guest sees the whole menu
 *
 * Account-only rows are **drawn and marked**, never dropped. A menu that hides half of itself
 * teaches a guest that this is a smaller app than it is, and the half it would hide is exactly the
 * argument for making an account. Tapping a marked row offers the sign-in, which is the one thing
 * that would unlock it. See [MenuCatalogue.sections].
 */
@Composable
fun MenuScreen(
    access: MenuAccess,
    /**
     * Where a row goes, by [MenuEntry.id].
     *
     * An id rather than a lambda per row for the reason `AppSurfaces` hands out ids: three of these
     * open a screen that needs a symbol in its path, and this module has no business knowing how
     * the graph is spelled or which market to pick. The shell already resolves those ids for the
     * search screen; the menu deliberately reuses the same ones so there is one such function and
     * not two that can disagree.
     */
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    avatar: AvatarSpec = AvatarSpec.Default,
    /** What this reader is called — their own display name, or the server's. Null for a guest. */
    name: String? = null,
    /** Where to reach them. Null for a guest, and null before the account loads. */
    email: String? = null,
    /** The plan's own Persian name, from the server. Null where there is no subscription. */
    planLabel: String? = null,
    /** Which backend this session is on, named. */
    platformLabel: String? = null,
    /** How many markets are on the reader's own list. A guest has one of these too. */
    watchlistCount: Int = 0,
    /** Offered, never demanded. Null when there is nothing to offer — i.e. already signed in. */
    onSignIn: (() -> Unit)? = null,
) {
    val sections = remember(access) { MenuCatalogue.sections(access) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(bottom = CoineProSpacing.Four),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        item(key = "identity") {
            Identity(
                avatar = avatar,
                name = name,
                email = email,
                planLabel = planLabel,
                platformLabel = platformLabel,
                signedIn = access.signedIn,
                onSignIn = onSignIn,
                onOpenProfile = { onOpen("profile") },
            )
        }

        sections.forEach { section ->
            item(key = "title-" + section.group.name) {
                Text(
                    text = stringResource(section.group.titleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(
                        start = CoineProSpacing.Gutter,
                        end = CoineProSpacing.Gutter,
                        top = CoineProSpacing.One,
                    ),
                )
            }
            item(key = "rows-" + section.group.name) {
                CoineProCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    section.items.forEachIndexed { index, item ->
                        if (index > 0) CoineProRowDivider()
                        MenuRow(
                            item = item,
                            // The reader's own list, counted. A prose count, so Persian digits —
                            // it is not a figure anybody compares against an exchange.
                            value = watchlistCount
                                .takeIf { item.entry.id == "watchlist" && it > 0 }
                                ?.toPersianDigits(),
                            onClick = {
                                // A locked row leads to the one thing that would unlock it. Where
                                // there is no sign-in to offer, it does nothing rather than
                                // navigating into a wall.
                                if (item.locked) onSignIn?.invoke() else onOpen(item.entry.id)
                            },
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            Text(
                text = stringResource(R.string.menu_footer),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            )
        }
    }
}

/**
 * The block that says who is reading.
 *
 * A guest gets the same block with the same avatar — their own, if they chose one — and an
 * invitation rather than a wall. The wording is the owner's standing rule for this app: nobody is
 * pushed into an account, so this states what an account adds and says it once.
 */
@Composable
private fun Identity(
    avatar: AvatarSpec,
    name: String?,
    email: String?,
    planLabel: String?,
    platformLabel: String?,
    signedIn: Boolean,
    onSignIn: (() -> Unit)?,
    onOpenProfile: () -> Unit,
) {
    val shown = name?.trim()?.takeIf(String::isNotEmpty)
        ?: stringResource(if (signedIn) R.string.menu_member_name else R.string.menu_guest_name)
    // Isolated, because every one of these is a Latin run inside a Persian line: an address, a
    // backend's own name. Without the isolate the punctuation of an email walks to the wrong end.
    val second = when {
        !signedIn -> stringResource(R.string.menu_guest_line)
        email != null -> BidiText.isolateLtr(email)
        else -> stringResource(R.string.menu_member_line)
    }
    val standing = listOfNotNull(
        platformLabel?.let(BidiText::isolateLtr),
        planLabel ?: stringResource(R.string.menu_plan_none).takeIf { signedIn },
    ).joinToString(" · ")

    CoineProCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenProfile),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoineProAvatar(spec = avatar, initial = shown.take(1), size = 48.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shown,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = second,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                )
                if (standing.isNotEmpty()) {
                    Text(
                        text = standing,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
            Icon(
                painter = painterResource(CoineProIcons.ChevronForward),
                contentDescription = null,
                tint = CoineProColors.TextMuted,
            )
        }
        if (!signedIn && onSignIn != null) {
            Column(modifier = Modifier.padding(top = CoineProSpacing.OneHalf)) {
                Text(
                    text = stringResource(R.string.menu_guest_offer),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextSecondary,
                )
                CoineProPrimaryButton(
                    text = stringResource(R.string.menu_sign_in),
                    onClick = onSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CoineProSpacing.One),
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    item: MenuItem,
    value: String?,
    onClick: () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    val entry = item.entry
    val ink = when {
        entry.destructive -> CoineProColors.Sell
        item.locked -> CoineProColors.TextSecondary
        else -> CoineProColors.TextPrimary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.select()
                onClick()
            }
            // The floor for anything a thumb has to hit.
            .heightIn(min = 44.dp)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(entry.icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = when {
                entry.destructive -> CoineProColors.Sell
                item.locked -> CoineProColors.TextMuted
                else -> CoineProColors.TextSecondary
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
            entry.bodyRes?.let { body ->
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
        if (item.locked) {
            // Said in words rather than drawn as a padlock. A padlock is read as "you cannot",
            // which is wrong: this reader can, in two taps, and the sentence is what says so.
            Text(
                text = stringResource(R.string.menu_locked),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        } else {
            value?.let { current ->
                Text(
                    text = current,
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
            Icon(
                // Forward in the reading direction, which the drawable mirrors for itself in RTL.
                painter = painterResource(CoineProIcons.ChevronForward),
                contentDescription = null,
                tint = CoineProColors.TextMuted,
            )
        }
    }
}
