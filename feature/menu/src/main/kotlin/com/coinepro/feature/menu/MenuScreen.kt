package com.coinepro.feature.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProRowDivider
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProTint
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
    /**
     * The two settings that decide whether the reader can read the app: the theme and the language.
     *
     * A slot rather than four parameters, because `ThemeMode` lives in `core:datastore` and this
     * module is a directory of rows — giving it a DataStore dependency so it can name an enum would
     * be the wrong trade. The shell passes `AppearanceQuickRow`, which is where those two controls
     * are drawn and where the third one (which colour a rise is) deliberately is not.
     *
     * Here rather than on the profile page because it is one tap from anywhere: «حالت تیره و روشن
     * و زبان باید یه جای دم دست باشه». Null draws nothing at all, which is what the previews and
     * the screenshot tests get.
     */
    appearance: (@Composable () -> Unit)? = null,
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

        appearance?.let { row ->
            item(key = "appearance") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.menu_appearance),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        modifier = Modifier.padding(
                            start = CoineProSpacing.Gutter,
                            end = CoineProSpacing.Gutter,
                            top = CoineProSpacing.One,
                            bottom = CoineProSpacing.Half,
                        ),
                    )
                    row()
                }
            }
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
                // **A group of rows, not a card of them.**
                //
                // Every section was a bordered plate with its own corner radius, and with nine
                // sections that is nine objects a reader separates before reading any of them — on
                // a screen that is a *directory*, where the only question is which word to press.
                // The card was saying «these belong together», which the group's own heading two
                // lines above already says, in type, for free. What is left is a column of rows and
                // a hairline between them, which is what a settings list is everywhere it is done
                // well.
                Column(modifier = Modifier.fillMaxWidth()) {
                    section.items.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                // Inset past the glyph, so the rule starts where the words do and
                                // the column of marks reads as a column rather than as cells.
                                modifier = Modifier.padding(start = ROW_DIVIDER_INSET),
                                thickness = 1.dp,
                                color = CoineProColors.BorderSubtle,
                            )
                        }
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
        // Nothing, for a guest. The card's own offer block two lines below already says which
        // parts of the app need an account and which stay free, at greater length and to more
        // purpose — so this line was the same sentence said twice, in a card that then had to fit
        // a name, two paragraphs, a platform and a button. A member's second line is their address,
        // which appears nowhere else.
        !signedIn -> null
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
                second?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextSecondary,
                    )
                }
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

/**
 * How a measurement addresses one row.
 *
 * A directory whose rows are supposed to share one height is a claim that can be checked, and
 * checking it means being able to name a row rather than hunting for its words — the words are
 * translated, and half of them are the same noun in two languages. The id is the entry's own, which
 * is already the stable identity the shell routes on.
 */
object MenuTestTags {
    fun row(id: String): String = "menu-row-$id"
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
            .testTag(MenuTestTags.row(entry.id))
            .clickable {
                haptics.select()
                onClick()
            }
            // **Fifty, and for a plain row that is an actual height rather than a floor.**
            //
            // `heightIn(min = …)` says a row may be fifty and may be anything above it, which is
            // not a rhythm — it is a rhythm wherever the content happens to be short and a
            // different one wherever it is not. A row whose content is a title and a mark is
            // therefore pinned: `height`, one number, every row the same.
            //
            // The exception is a row that carries a second line, and it is an exception on purpose
            // — see [MenuCatalogue.DESCRIPTIVE_ROWS], which is a short and closed list. Those keep
            // the floor, because pinning them would clip a sentence rather than make a rhythm.
            .then(
                if (entry.bodyRes != null && !item.locked) {
                    Modifier.heightIn(min = ROW_HEIGHT)
                } else {
                    Modifier.height(ROW_HEIGHT)
                },
            )
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // **The glyph, bare.**
        //
        // It sat on a 36 pt filled square, and the argument for that was real: a plate gives every
        // row the same optical left edge whatever shape is inside it. It is also thirty-six points
        // of fill per row, nine sections deep, and what it produced is a page that reads as a
        // catalogue of features rather than as a list of places to go — every row equally loud and
        // the reader's eye with nowhere to land. The reference's own directory sets its marks bare.
        //
        // The alignment the plate was buying is bought instead by a fixed GLYPH_COLUMN: the mark is
        // centred in a column of a known width, so thirty different outlines still start their
        // words at the same x. Same guarantee, none of the fill.
        Box(
            modifier = Modifier.width(GLYPH_COLUMN),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(entry.icon),
                contentDescription = null,
                modifier = Modifier.size(GLYPH),
                tint = when {
                    entry.destructive -> CoineProColors.Sell
                    item.locked -> CoineProColors.TextDisabled
                    else -> CoineProColors.TextSecondary
                },
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = ink,
            )
            // **Not on a locked row.** The badge beside it already says the useful thing, and a
            // grey sentence under a grey badge is two quiet lines saying one fact.
            entry.bodyRes?.takeIf { !item.locked }?.let { body ->
                Text(
                    text = stringResource(body),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.locked) {
            // Said in words rather than drawn as a padlock. A padlock is read as "you cannot",
            // which is wrong: this reader can, in two taps, and the sentence is what says so.
            //
            // **On a plate, and not bare.** Bare text in this row is vertically centred against a
            // body that wraps to two lines, so «با ورود به حساب» landed level with the second line
            // and read as its continuation — one sentence made of two unrelated halves. A plate
            // says it is a badge about the row rather than more of the row's own words.
            Text(
                text = stringResource(R.string.menu_locked),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier
                    .background(CoineProColors.SurfaceRaised, MaterialTheme.shapes.small)
                    .padding(horizontal = CoineProSpacing.Three.times(0.25f), vertical = 2.dp),
            )
        }
        value?.takeIf { !item.locked }?.let { current ->
            Text(
                text = current,
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextSecondary,
            )
        }
        // **On every row, locked or not.** A locked row still goes somewhere — to the one screen
        // that would unlock it — and stripping its chevron made it look like the one row on the
        // page that does nothing, which is the opposite of what it is for.
        Icon(
            // Forward in the reading direction, which the drawable mirrors for itself in RTL.
            painter = painterResource(CoineProIcons.ChevronForward),
            contentDescription = null,
            tint = CoineProColors.TextDisabled,
            // Fourteen, not the icon default's twenty-four. A chevron is punctuation: it says the
            // row goes somewhere, and it should be the quietest thing on it.
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * The column the row's mark is centred in, and the mark itself.
 *
 * Twenty-four wide for a twenty-point glyph, which is what replaces the 36 pt plate: the width is
 * what lines the words up, and it costs no fill. See the note in [MenuRow].
 */
private val GLYPH_COLUMN = 24.dp
private val GLYPH = 20.dp

/**
 * The directory row's own height — an actual height for a plain row, a floor for a descriptive one.
 *
 * Fifty, which is the reference's directory row and comfortably past the 44 floor for anything a
 * thumb has to hit. See [MenuRow] for why the two cases are spelled differently, and
 * [MenuCatalogue.DESCRIPTIVE_ROWS] for how short the second list is.
 */
internal val ROW_HEIGHT = 50.dp

/**
 * Where the rule between two rows starts.
 *
 * The gutter, the glyph column and the step after it — so the hairline begins under the words and
 * the marks read as a column rather than as the first cell of a table.
 */
private val ROW_DIVIDER_INSET = CoineProSpacing.Gutter + GLYPH_COLUMN + CoineProSpacing.OneHalf
