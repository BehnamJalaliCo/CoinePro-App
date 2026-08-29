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
import androidx.compose.foundation.layout.heightIn
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
    /**
     * Which titled block of the list this row belongs to.
     *
     * The list was flat and ten rows long, which is the shape a settings screen reaches just before
     * people stop reading it: «حذف حساب» and «ظاهر برنامه» were the same kind of line, one above the
     * other, distinguished only by their words. Grouping is not decoration here — it is what lets a
     * reader skip eight rows they did not come for.
     *
     * Defaulted so that a caller who has not been updated still produces exactly the list it
     * produced before, under the account heading, in the order it passed.
     */
    val group: ProfileGroup = ProfileGroup.ACCOUNT,
    val onClick: () -> Unit,
)

/**
 * The three blocks of the account list, in the order they are always drawn.
 *
 * Declaration order *is* the layout order, and that is deliberate: a reader who has learned that
 * signing out is at the bottom of this page should not have to learn again because a row was added
 * to the middle. [SESSION] is last for the same reason the deletion row is last within it — the two
 * rows that end something are the two rows a thumb must not reach by accident.
 */
enum class ProfileGroup {
    /** What the account *is*: the membership, the verification behind it. */
    ACCOUNT,

    /** How the app behaves for this reader: notifications, lock, palette, help. */
    APP,

    /** Ending things — signing out, and deleting. */
    SESSION,
}

private fun ProfileGroup.titleRes(): Int = when (this) {
    ProfileGroup.ACCOUNT -> R.string.profile_account_section
    ProfileGroup.APP -> R.string.profile_app_section
    ProfileGroup.SESSION -> R.string.profile_session_section
}

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
    /**
     * Who this reader is, as the **server** said it: plan, membership standing, verification.
     *
     * Nothing in this list is computed here. Every entry whose answer the server did not give
     * arrives with a null value and is drawn as unknown — see [ProfileFact], which is where that
     * rule is enforced rather than trusted.
     */
    standing: List<ProfileFact> = emptyList(),
    /**
     * What the reader has actually done: the journal, the practice account, the closed trades,
     * the lessons. Read from their own records, and each row goes to the screen that owns it.
     */
    record: List<ProfileFact> = emptyList(),
    /**
     * What the reader has built up inside this app — lists, layouts, templates, alerts, scripts.
     *
     * A year of somebody's work lives in a dozen separate stores and, until this card, was visible
     * only by opening the twelve screens that hold it. Some of these rows have nowhere to go
     * (a drawing template belongs to the chart's tool rail and has no page of its own); they still
     * earn their place, because the count itself is the thing worth knowing.
     */
    library: List<ProfileFact> = emptyList(),
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

        // Standing before record before library, and that order is the answer to the question
        // «پروفایل کاربر رو جامع بکن» rather than an arrangement of it. What the server says about
        // this account can stop them using the app today; what they have done is history; what they
        // own is inventory. A screen that opens on inventory buries the one card that might be
        // telling somebody why their signals are locked.
        if (standing.anyDrawable()) {
            item {
                ProfileFactList(
                    title = stringResource(R.string.profile_standing_section),
                    facts = standing,
                )
            }
        }
        if (record.anyDrawable()) {
            item {
                ProfileFactList(
                    title = stringResource(R.string.profile_record_section),
                    facts = record,
                )
            }
        }
        if (library.anyDrawable()) {
            item {
                ProfileFactList(
                    title = stringResource(R.string.profile_library_section),
                    facts = library,
                    // Said once, under the card, rather than on seven rows. None of this is on a
                    // server — there is no route for a watchlist or a chart layout on either
                    // backend — so a reader about to reinstall is entitled to know that before
                    // they do, and not afterwards.
                    note = stringResource(R.string.profile_library_note),
                )
            }
        }

        item {
            IdentityCard(
                displayName = profile.displayName,
                tagline = profile.tagline,
                onSetDisplayName = onSetDisplayName,
                onSetTagline = onSetTagline,
            )
        }

        // Grouped by the enum's own order, not by the caller's. The caller decides which block a
        // row belongs to and the order of rows *within* a block; where the blocks sit on the page
        // is fixed here, so no future edit to the call site can move «خروج» up the screen.
        ProfileGroup.entries.forEach { group ->
            val rows = actions.filter { it.group == group }
            if (rows.isEmpty()) return@forEach
            item(key = "group-" + group.name) {
                Text(
                    text = stringResource(group.titleRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(
                        start = CoineProSpacing.Gutter,
                        end = CoineProSpacing.Gutter,
                        top = CoineProSpacing.One,
                    ),
                )
            }
            item(key = "rows-" + group.name) {
                CoineProCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CoineProSpacing.Gutter),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    rows.forEachIndexed { index, action ->
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
            // The floor for anything a thumb has to hit. The padding alone reached it for a
            // one-line row and fell under it for a row whose label wrapped to a shorter style.
            .heightIn(min = 44.dp)
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
            tint = CoineProColors.TextMuted,
        )
    }
}
