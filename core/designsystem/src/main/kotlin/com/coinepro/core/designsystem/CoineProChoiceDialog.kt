package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * One way out of a [CoineProChoiceDialog].
 *
 * The label is a **whole sentence**, not a verb: «ترسیم برود، هشدارها بمانند» rather than «فقط
 * ترسیم». That is the reason this dialog stacks rather than sitting its actions side by side — a
 * choice a reader can only tell apart by reading it cannot be abbreviated onto half a row, and the
 * pair of one-word buttons that fits is the pair that gets tapped without being read.
 */
data class CoineProChoice(
    val label: String,
    /** One line under the label, where the consequence is not obvious from it. */
    val note: String? = null,
    /** Colours this one as the refusal. At most one choice should be. */
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A question with more than two answers.
 *
 * ### Why this is not [CoineProConfirmDialog] with a third button
 *
 * That dialog's rule is the one in its own documentation and it is unchanged: **ask only where
 * recovery is otherwise impossible**, and prefer an undo to a question everywhere else. This one
 * inherits that rule whole. What it adds is the case where the answer is genuinely not binary —
 * where «no» and «yes» leave out the answer the reader actually wants, and a two-button dialog
 * would force them to pick the wrong one and repair it afterwards.
 *
 * The first real case was deleting a drawing that carries alerts: *keep everything*, *delete the
 * line and its alerts*, and *delete the line but keep the alerts* are three different intentions,
 * and the third is the one somebody about to redraw the line is choosing.
 *
 * ### The layout follows from that
 *
 * Choices are full width and stacked, in the order given, **most conservative last** — the dismiss
 * is the bottom row, nearest the thumb, and it is what a back press and a tap outside both do. A
 * reader who acts on reflex therefore changes nothing, which is the same property
 * [CoineProConfirmDialog] gets from putting dismiss first and wider.
 */
@Composable
fun CoineProChoiceDialog(
    title: String,
    message: String,
    /** The actions, in reading order. Two or more; with one this should be a confirm dialog. */
    choices: List<CoineProChoice>,
    /** The bottom row, and what a back press or a tap outside does. */
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    val haptics = rememberCoineProHaptics()
    Dialog(
        onDismissRequest = {
            haptics.select()
            onDismiss()
        },
        properties = DialogProperties(
            // Both true, for [CoineProConfirmDialog]'s reason: walking away is the safe answer to
            // every question this dialog is used for.
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        CoineProChoiceDialogBody(
            title = title,
            message = message,
            choices = choices,
            dismissLabel = dismissLabel,
            onDismiss = {
                haptics.select()
                onDismiss()
            },
            icon = icon,
            modifier = modifier,
        )
    }
}

/**
 * The card without the window.
 *
 * Split out for [CoineProConfirmDialogBody]'s reason: a `Dialog` draws into a window of its own, so
 * an off-device capture of the activity's decor view comes back without it. This is the half the
 * screenshot tests can see.
 */
@Composable
fun CoineProChoiceDialogBody(
    title: String,
    message: String,
    choices: List<CoineProChoice>,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = DIALOG_MARGIN)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = CoineProColors.SurfaceElevated,
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = CoineProSpacing.CardHorizontal,
                vertical = CoineProSpacing.Stack,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
        ) {
            Box(
                modifier = Modifier
                    .size(MARK_PLATE)
                    .clip(CircleShape)
                    .background(CoineProTint.fill(CoineProColors.Gold, CoineProColors.SurfaceElevated))
                    .border(1.dp, CoineProTint.edge(CoineProColors.Gold), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon ?: CoineProIcons.Warning),
                    contentDescription = null,
                    tint = CoineProColors.Gold,
                    modifier = Modifier.size(MARK_GLYPH),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CoineProSpacing.Row),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
            ) {
                choices.forEach { choice ->
                    ChoiceRow(choice = choice, modifier = Modifier.fillMaxWidth())
                }
                CoineProSecondaryButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * One stacked action.
 *
 * Two lines where the choice has a note, and the note is the consequence rather than a restatement
 * of the label — a second line that says the same thing twice is a line the reader learns to skip.
 * The label is allowed to wrap: these are sentences in a language whose words are long, and an
 * ellipsis in the middle of the one sentence a reader has to weigh is the defect this stacking
 * exists to avoid.
 */
@Composable
private fun ChoiceRow(choice: CoineProChoice, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    val ink: Color = if (choice.destructive) CoineProColors.OnAccent else CoineProColors.TextPrimary
    val fill = if (choice.destructive) CoineProColors.Sell else CoineProColors.SurfaceElevated
    Surface(
        onClick = {
            if (choice.destructive) haptics.commit() else haptics.select()
            choice.onClick()
        },
        modifier = modifier.pressScale(interaction, CoineProPress.CONTROL),
        shape = MaterialTheme.shapes.small,
        color = fill,
        border = if (choice.destructive) null else BorderStroke(1.dp, CoineProColors.BorderSubtle),
        interactionSource = interaction,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Row, vertical = CHOICE_VERTICAL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Text(
                text = choice.label,
                style = MaterialTheme.typography.labelLarge,
                color = ink,
                textAlign = TextAlign.Center,
            )
            choice.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (choice.destructive) ink else CoineProColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val DIALOG_MARGIN = 28.dp
private val MARK_PLATE = 44.dp
private val MARK_GLYPH = 22.dp
private val CHOICE_VERTICAL = 12.dp
