package com.coinepro.app.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.app.R
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.rememberCoineProHaptics

/**
 * «ایده‌ها»: the signal list and the community board, in one destination.
 *
 * ### Why they are one place
 *
 * They had a tab each, and between them they took a third of the bottom bar to answer one question
 * twice — *is there an opportunity here?* — once from a model and once from another reader. A bar
 * that spends two of six positions on two answers to one question is a bar organised around the
 * app's module list rather than around what somebody opens the app to do.
 *
 * They are genuinely different content and the switch says so. What they are not is different
 * *jobs*, and root navigation is a list of jobs.
 *
 * ### A segmented switch, not a swipe pager
 *
 * A pager would be the fashionable answer and it is the wrong one here. Both faces are long,
 * flickable lists of their own; a horizontal pager over a vertical list means every diagonal drag
 * is a coin toss between scrolling the feed and changing the page, and on a right-to-left screen
 * it also means the swipe direction is the opposite of what a reader coming from an English app
 * expects. Two keys and a tap is unambiguous in both directions.
 *
 * ### The screens are handed in, not built here
 *
 * [signals] and [community] are the existing screens, composed by the shell with the controllers,
 * guest gates and navigation callbacks they already had. This file is a frame: it owns which of
 * the two is showing and nothing else. That is what keeps `signals` and `community` working as
 * routes of their own — a saved back stack that names one still opens exactly that screen, with no
 * tab bar over it.
 */
@Composable
fun IdeasScreen(
    signals: @Composable () -> Unit,
    community: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** Which face opens first. The shell passes the reader's last one where it knows it. */
    initial: IdeasFace = IdeasFace.SIGNALS,
) {
    var face by rememberSaveable { mutableStateOf(initial) }
    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        IdeasSwitch(face = face, onSelect = { face = it })
        HorizontalDivider(color = CoineProColors.BorderSubtle)
        when (face) {
            IdeasFace.SIGNALS -> signals()
            IdeasFace.COMMUNITY -> community()
        }
    }
}

/** Which of the two halves is showing. */
enum class IdeasFace { SIGNALS, COMMUNITY }

/**
 * The switch: two keys, the chosen one inverted to the primary ink.
 *
 * The same shape the chart's interval keys use — a filled block on a raised neutral — rather than a
 * Material `TabRow`. Three reasons, and the third is the one that matters. A `TabRow` draws an
 * underline that reads as a third rule under a page that already has a divider and a list header;
 * its ripple and indicator animate on every switch, which is decoration on a control pressed twice
 * a session; and it is the shape a reader of this app has already learned means "one of these is
 * in force" everywhere else.
 *
 * No gold. Gold in this app is the brand and the one commercial action on a page, and a selected
 * tab is neither — see the palette rules. The ink inverts instead, which is the strongest possible
 * mark and costs no colour.
 */
@Composable
private fun IdeasSwitch(face: IdeasFace, onSelect: (IdeasFace) -> Unit) {
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                top = CoineProSpacing.One,
                bottom = CoineProSpacing.One,
            )
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        IdeasFace.entries.forEach { option ->
            IdeasKey(
                label = stringFor(option),
                active = option == face,
                onClick = {
                    if (option != face) haptics.select()
                    onSelect(option)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun stringFor(face: IdeasFace): String = stringResource(
    when (face) {
        IdeasFace.SIGNALS -> R.string.ideas_signals
        IdeasFace.COMMUNITY -> R.string.ideas_community
    },
)

@Composable
private fun IdeasKey(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CoineProShapes.small)
            .background(if (active) CoineProColors.TextPrimary else CoineProColors.SurfaceElevated)
            // `selectable` rather than `clickable`: this is a choice between two, and the
            // difference is what TalkBack announces — "selected" against "double tap to activate".
            .selectable(selected = active, role = Role.Tab, onClick = onClick)
            .heightIn(min = KEY_HEIGHT)
            .padding(horizontal = CoineProSpacing.One),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) CoineProColors.Stage else CoineProColors.TextSecondary,
            maxLines = 1,
        )
    }
}

/** The minimum target this design system gives a key a thumb reaches for on a scrolling page. */
private val KEY_HEIGHT = 40.dp
