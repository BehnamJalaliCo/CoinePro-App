package com.coinepro.app.ideas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
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
 * The switch: two keys in a tray, the chosen one lifted out of it.
 *
 * ### The fault this fixes, which was the switch reading backwards
 *
 * «روی سیگنال می‌زنم، انجمن باز می‌شود؛ روی انجمن می‌زنم، سیگنال.» The panes were never swapped —
 * what was swapped was which key *looked* chosen. The keys were drawn straight onto the page:
 * `SurfaceRaised` for the selected one and `Surface` for the other. In the dark theme that reads
 * correctly. In the light theme `SurfaceRaised` **is** the page — both are `#FFFFFF` — so the
 * chosen key vanished into the background and the unchosen one, a grey block, was the only marked
 * thing on the row. Every reader of the light theme was told the opposite of the truth, and the
 * screenshot that came with the report shows exactly that: the signals pane open, «انجمن» in a
 * grey chip.
 *
 * ### Why a tray fixes it and a different colour would not
 *
 * "Raised" is a statement about a container, not a shade: a block can only look lifted *out of*
 * something. `CoineProSegmentedControl` learned this already — see its own note — and the answer is
 * the same one here. The row sits in a `Surface` tray with an edge, and the selected key is
 * `SurfaceRaised` with its own hairline, which is lighter than its tray in the dark theme and
 * whiter than it in the light one. The mark is then correct in both themes for the same reason
 * rather than by two coincidences.
 *
 * No gold. Gold in this app is the brand and the one commercial action on a page, and a selected
 * tab is neither — see the palette rules. The ink and the lift carry it, and cost no colour.
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
                top = CoineProSpacing.Half,
                bottom = CoineProSpacing.Half,
            )
            // The tray. A control has to look like a container before the key inside it can look
            // lifted out of one — which is the whole of the fault above.
            .clip(CoineProShapes.small)
            .background(CoineProColors.Surface)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.small)
            .padding(TRAY_PADDING)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(TRAY_PADDING),
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
            // **The raised neutral, not the primary ink.**
            //
            // Inverting to `TextPrimary` is the strongest mark the palette has, and it was too
            // strong for a control pressed twice a session: on the dark theme the selected key was
            // a near-white block — the brightest object on a page of dark rows — and on the light
            // theme it was near-black. Either way the switch was louder than the content it
            // switches. `SurfaceRaised` is what every other "one of these is in force" in this app
            // uses, and the bold label on primary ink carries the rest.
            //
            // Transparent when it is not chosen, so the tray shows through: the unchosen key is
            // *the container*, and giving it a fill of its own is what made the light theme read
            // the switch backwards.
            .background(if (active) CoineProColors.SurfaceRaised else Color.Transparent)
            .then(
                // The edge belongs to the chosen key alone. It is what separates a white block
                // from a white page on the light theme, where the fill on its own cannot.
                if (active) {
                    Modifier.border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.small)
                } else {
                    Modifier
                },
            )
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The key's height.
 *
 * Thirty-eight rather than forty: two points off a control that spans the width, and the switch and
 * its padding now come to fifty rather than fifty-six. It is still a comfortable target — the row
 * is full-width and half of it is one key.
 */
private val KEY_HEIGHT = 38.dp

/** The tray's own inset, and the gap between the two keys. One number, so the tray reads even. */
private val TRAY_PADDING = 4.dp
