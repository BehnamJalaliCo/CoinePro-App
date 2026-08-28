package com.coinepro.feature.profile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.datastore.StoredProfile
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProGoldRule
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProReading
import com.coinepro.core.designsystem.CoineProReadingRow
import com.coinepro.core.designsystem.CoineProRowDivider
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.model.AvatarSpec

/**
 * One row of the account list — a label, a note, and where it goes.
 *
 * [destructive] is the only variant, and it exists for exactly one row: deleting the account. It is
 * drawn in the refusal colour and placed last, because a row that cannot be undone must not sit
 * beside "sign out" looking like its neighbour.
 */
data class ProfileAction(
    val label: String,
    val note: String? = null,
    val destructive: Boolean = false,
    /**
     * The row's own glyph.
     *
     * Not decoration. This list is read by somebody looking for one particular row, and eight lines
     * of identically weighted text is the layout that makes them read all eight. An icon per row is
     * how every settings list worth comparing to works, and the reason is that the second visit is
     * a recognition rather than a search.
     *
     * Nullable so a row with nothing honest to draw shows nothing — a generic dot on one row of
     * eight is worse than no icons at all, because it breaks the column the others formed.
     */
    @DrawableRes val icon: Int? = null,
    /**
     * The setting's current answer, drawn before the chevron.
     *
     * Only for rows that *hold* a value — a theme, a language, a currency. A row that merely opens
     * a screen has no value and leaves this null, because a settings list where every row shows
     * text on the right is a list where the right column means nothing.
     */
    val value: String? = null,
    val onClick: () -> Unit,
)

/**
 * The reader's own page.
 *
 * This is the gold voice — a heading, one hero, a row of readings — because it is a screen about
 * *one thing*, and the thing is a person. It carries no market data at all, which is deliberate:
 * everywhere else in this app a number is moving, and the one page that is about the reader should
 * be the one page that holds still.
 *
 * It serves a **guest** and a **member** from the same composable rather than from two screens.
 * That is the whole point of the guest work: somebody who has not signed in is not shown a
 * different, lesser app with a wall in it — they are shown this page, with their own avatar, their
 * own name if they typed one, their own watchlist counted, and an invitation they can ignore.
 * Two screens would drift, and the lesser one would be the one nobody looked at.
 */
@Composable
fun ProfileScreen(
    profile: StoredProfile,
    modifier: Modifier = Modifier,
    /** What the server calls this reader. Null for a guest, and null before the account loads. */
    accountName: String? = null,
    email: String? = null,
    /** True when nobody is signed in. Changes what the page offers, never what it hides. */
    guest: Boolean = false,
    /** The plan's own Persian name, from the server. Null where there is no subscription. */
    planLabel: String? = null,
    /** Which backend this session is on, named. */
    platformLabel: String? = null,
    /** Three figures about the reader — never about the market. Empty hides the row. */
    readings: List<CoineProReading> = emptyList(),
    onEditAvatar: () -> Unit = {},
    onSetDisplayName: (String?) -> Unit = {},
    onSetTagline: (String?) -> Unit = {},
    /** Offered, never demanded. Null when there is nothing to offer — i.e. already signed in. */
    onSignIn: (() -> Unit)? = null,
    /** The account rows, assembled by the caller because only it knows what this build serves. */
    actions: List<ProfileAction> = emptyList(),
) {
    val shown = profile.displayName ?: accountName
    val initial = (shown ?: stringResource(R.string.profile_guest_name)).trim().take(1)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = CoineProSpacing.Four,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        item {
            Hero(
                avatar = profile.avatar,
                initial = initial,
                name = shown ?: stringResource(R.string.profile_guest_name),
                tagline = profile.tagline
                    ?: email
                    ?: stringResource(
                        if (guest) R.string.profile_guest_tagline else R.string.profile_member_tagline,
                    ),
                planLabel = planLabel,
                platformLabel = platformLabel,
                onEditAvatar = onEditAvatar,
            )
        }

        if (readings.isNotEmpty()) {
            item { CoineProReadingRow(readings) }
        }

        // The invitation, and it is worded as one. The owner's rule for this app is that nobody is
        // pushed into an account — «به زور کسی رو ما ثبت نام نمی‌کنیم» — so this card states what an
        // account adds and what stays free, and there is no second, more insistent version of it
        // anywhere else in the guest experience.
        if (guest && onSignIn != null) {
            item { SignInInvitation(onSignIn = onSignIn) }
        }

        item {
            IdentityCard(
                displayName = profile.displayName,
                tagline = profile.tagline,
                onSetDisplayName = onSetDisplayName,
                onSetTagline = onSetTagline,
            )
        }

        if (actions.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.profile_account_section),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextDisabled,
                    modifier = Modifier.padding(
                        start = CoineProSpacing.Gutter,
                        end = CoineProSpacing.Gutter,
                        top = CoineProSpacing.One,
                    ),
                )
            }
            item {
                CoineProCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CoineProSpacing.Gutter),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    actions.forEachIndexed { index, action ->
                        if (index > 0) CoineProRowDivider()
                        ActionRow(action)
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.profile_local_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            )
        }
    }
}

@Composable
private fun Hero(
    avatar: AvatarSpec,
    initial: String,
    name: String,
    tagline: String,
    planLabel: String?,
    platformLabel: String?,
    onEditAvatar: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // More above than below, and not symmetry for its own sake. There is no top bar on
            // this route, so the avatar is the first thing on the screen and sixteen points put a
            // 112dp disc within a finger's width of the status bar — which reads as an element
            // that has been cut off rather than one that has been placed.
            .padding(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                top = CoineProSpacing.Three,
                bottom = CoineProSpacing.Two,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            CoineProAvatar(
                spec = avatar,
                initial = initial,
                // Eighty, not a hundred and twelve. At 112 the disc plus its top padding took
                // 144dp before the reader's own name appeared — most of the first screen of the
                // page about them spent on one circle.
                size = 80.dp,
                modifier = Modifier.clickable(onClick = onEditAvatar),
                contentDescription = stringResource(R.string.profile_avatar_description),
            )
            // The badge hangs *off* the ring rather than sitting on the artwork. Its first version
            // sat inside the disc and covered a third of the picture — on the one screen whose
            // whole subject is that picture. The stage-coloured border is what separates it from
            // whatever is behind it, since the artwork underneath can be any colour at all.
            Box(
                modifier = Modifier
                    .offset(x = (-6).dp, y = 4.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(CoineProColors.SurfaceElevated)
                    .border(2.dp, CoineProColors.Stage, CircleShape)
                    .clickable(onClick = onEditAvatar),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoineProIcons.Image),
                    contentDescription = stringResource(R.string.profile_edit_avatar),
                    tint = CoineProColors.Accent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = CoineProColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (planLabel != null || platformLabel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                planLabel?.let { Badge(text = it, accent = CoineProColors.Premium) }
                platformLabel?.let { Badge(text = it, accent = CoineProColors.Analysis) }
            }
        }
        CoineProGoldRule(modifier = Modifier.padding(top = CoineProSpacing.Half))
    }
}

@Composable
private fun Badge(text: String, accent: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        modifier = Modifier
            .background(
                CoineProTint.fill(accent, CoineProColors.SurfaceElevated),
                androidx.compose.foundation.shape.CircleShape,
            )
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
    )
}

@Composable
private fun SignInInvitation(onSignIn: () -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringResource(R.string.profile_signin_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.profile_signin_body),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            CoineProPrimaryButton(
                text = stringResource(R.string.profile_signin_action),
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.profile_signin_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

/**
 * The two fields a reader can write about themselves.
 *
 * Edited in place rather than on a second screen, and saved on a button rather than per keystroke:
 * a store write per character would be a disk write per character, and an avatar name that saves
 * halfway through being typed shows the half in the app bar.
 */
@Composable
private fun IdentityCard(
    displayName: String?,
    tagline: String?,
    onSetDisplayName: (String?) -> Unit,
    onSetTagline: (String?) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(displayName) { mutableStateOf(displayName.orEmpty()) }
    var line by rememberSaveable(tagline) { mutableStateOf(tagline.orEmpty()) }

    CoineProCard(modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter)) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            if (!editing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.profile_identity_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    CoineProSecondaryButton(
                        text = stringResource(R.string.profile_identity_edit),
                        onClick = { editing = true },
                    )
                }
                // What this card is *for*, not what it holds. The hero two inches above already
                // shows the name and the line; repeating them here made the top of the page say
                // the same thing twice and pushed the account rows below the fold.
                Text(
                    text = if (displayName == null && tagline == null) {
                        stringResource(R.string.profile_identity_unset)
                    } else {
                        stringResource(R.string.profile_identity_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            } else {
                CoineProTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.profile_field_name),
                    modifier = Modifier.fillMaxWidth(),
                )
                CoineProTextField(
                    value = line,
                    onValueChange = { line = it },
                    label = stringResource(R.string.profile_field_tagline),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                    CoineProPrimaryButton(
                        text = stringResource(R.string.profile_identity_save),
                        onClick = {
                            onSetDisplayName(name)
                            onSetTagline(line)
                            editing = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CoineProSecondaryButton(
                        text = stringResource(R.string.profile_identity_cancel),
                        onClick = {
                            name = displayName.orEmpty()
                            line = tagline.orEmpty()
                            editing = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(action: ProfileAction) {
    val haptics = rememberCoineProHaptics()
    val ink = if (action.destructive) CoineProColors.Sell else CoineProColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.select()
                action.onClick()
            }
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        action.icon?.let { glyph ->
            Icon(
                painter = painterResource(glyph),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                // The destructive row's glyph takes the refusal colour with its label. An icon in
                // the ordinary tint beside red text reads as a row that is only half a warning.
                tint = if (action.destructive) CoineProColors.Sell else CoineProColors.TextSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
            action.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
        action.value?.let { current ->
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
            tint = CoineProColors.TextDisabled,
        )
    }
}
