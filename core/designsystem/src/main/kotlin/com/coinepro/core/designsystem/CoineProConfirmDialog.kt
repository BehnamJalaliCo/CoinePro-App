package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * The question this app asks before it does something a reader cannot take back.
 *
 * ### Why this exists at all
 *
 * Until this file there was not one `AlertDialog` in the whole product, and the shape of the
 * profile screen made that a real hazard rather than a stylistic gap: «خروج از حساب» sat one row
 * above «حذف حساب», both were a single tap, and the first of them silently threw away every
 * session on both backends. A thumb that lands one row low on a moving bus loses an account.
 *
 * ### The rule about *which* actions get one
 *
 * A confirmation is a tax on every reader who meant it, paid so the one who did not can recover.
 * Charge it only where recovery is otherwise impossible:
 *
 *  - **Ask** when the action destroys something the app cannot rebuild — an account, a sign-out
 *    that discards tokens, a saved layout, every drawing on a chart, an exchange key.
 *  - **Do not ask** when the action is reversible, or when an undo is cheaper than a question. A
 *    star that can be un-starred, a filter, a closed sheet — those get a [CoineProSnackbar] with
 *    an undo at most, and usually nothing.
 *
 * The second half is the half that gets forgotten, and a product that confirms everything has
 * taught its readers to tap «تایید» without reading, which is worse than never having asked.
 *
 * ### The dialog itself
 *
 * Built on `androidx.compose.ui.window.Dialog` rather than Material's `AlertDialog` for the same
 * reason [CoineProSheet] does not use Material's default handle: the stock chrome is a light-theme
 * card with its own radius, its own padding and a text button pair that ignores this app's press
 * feedback and haptics. This one is drawn from the same tokens as everything else.
 *
 * [destructive] is not decoration. It swaps the confirm button to the refusal colour and reverses
 * nothing else — in particular the dismiss action stays the *wider*, calmer of the two, so the
 * button a reader hits by reflex is the one that does nothing.
 */
@Composable
fun CoineProConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Whether the confirmed action cannot be undone. Colours the confirm button, nothing else. */
    destructive: Boolean = false,
    /** A glyph above the title. Defaults to a warning mark for destructive questions. */
    @DrawableRes icon: Int? = null,
) {
    val haptics = rememberCoineProHaptics()
    val mark = icon ?: if (destructive) CoineProIcons.Warning else CoineProIcons.Info
    val markColor = if (destructive) CoineProColors.Sell else CoineProColors.Gold

    Dialog(
        onDismissRequest = {
            haptics.select()
            onDismiss()
        },
        properties = DialogProperties(
            // Both true. A question a reader can walk away from is a question, not a trap; and
            // walking away is the safe answer in every case this dialog is used for.
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        CoineProConfirmDialogBody(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            dismissLabel = dismissLabel,
            onConfirm = {
                haptics.commit()
                onConfirm()
            },
            onDismiss = {
                haptics.select()
                onDismiss()
            },
            destructive = destructive,
            mark = mark,
            markColor = markColor,
            modifier = modifier,
        )
    }
}

/**
 * The dialog's card without the window.
 *
 * Split out for the same reason [CoineProSheetBody] is: a `Dialog` draws into a window of its own,
 * so an off-device capture of the activity's decor view comes back without it. This is the half
 * the screenshot tests can see.
 */
@Composable
fun CoineProConfirmDialogBody(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    @DrawableRes mark: Int = CoineProIcons.Warning,
    markColor: Color = CoineProColors.Sell,
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
                    .background(CoineProTint.fill(markColor, CoineProColors.SurfaceElevated))
                    .border(1.dp, CoineProTint.edge(markColor), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(mark),
                    contentDescription = null,
                    tint = markColor,
                    modifier = Modifier.size(MARK_GLYPH),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CoineProSpacing.Row),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Row),
            ) {
                // Dismiss first and wider. In a right-to-left layout that puts it under the thumb
                // that is already resting there, which is the point: the reflex answer is "no".
                CoineProSecondaryButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    modifier = Modifier.weight(DISMISS_WEIGHT),
                )
                ConfirmButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    destructive = destructive,
                    modifier = Modifier.weight(CONFIRM_WEIGHT),
                )
            }
        }
    }
}

/**
 * The confirm half.
 *
 * Not [CoineProPrimaryButton] with a colour parameter, because that button reads
 * [LocalPageAccent] and the accent of the screen underneath a "delete this forever" question is
 * whatever domain the reader happened to be in. The consequence colours this button, not the
 * domain — which is exactly the rule [PageAccent.DESTRUCTIVE] states.
 */
@Composable
private fun ConfirmButton(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val fill = if (destructive) CoineProColors.Sell else CoineProColors.Gold
    Surface(
        onClick = onClick,
        modifier = modifier.pressScale(interaction, CoineProPress.CTA),
        shape = MaterialTheme.shapes.small,
        color = fill,
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier.padding(vertical = BUTTON_VERTICAL),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = CoineProColors.OnAccent,
                maxLines = 1,
            )
        }
    }
}

private val DIALOG_MARGIN = 28.dp
private val MARK_PLATE = 44.dp
private val MARK_GLYPH = 22.dp
private val BUTTON_VERTICAL = 12.dp

/**
 * The dismiss button is the wider of the two, by a sixth.
 *
 * Small enough that nobody reads it as a layout accident, large enough that a thumb aimed between
 * them lands on the harmless one.
 */
private const val DISMISS_WEIGHT = 1.15f
private const val CONFIRM_WEIGHT = 1f
