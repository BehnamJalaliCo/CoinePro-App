package com.coinepro.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A single-line input.
 *
 * Outlined rather than filled: on this direction's stage a filled field is the same block as a
 * card, so the two stack into an indistinguishable slab. The outline is what says "type here".
 *
 * [secret] does two things at once — it masks the value and it marks the field as one whose
 * contents must never be logged or restored from saved state. Callers clear the backing value as
 * soon as it has been sent.
 *
 * ### What was missing, and why each of these is not decoration
 *
 * The first version of this field had a value, a label and a keyboard type, and nothing else. That
 * is four ordinary things short of a form somebody can fill in on a phone:
 *
 *  * **A reveal button on a secret.** A password typed blind into a phone keyboard, in a language
 *    whose keyboard many readers have installed alongside another, is a password typed wrong. The
 *    eye is not a convenience; it is the difference between signing in and being locked out. It is
 *    also the reason [secret] no longer implies "no way to check".
 *  * **An error state.** A screen showing a red sentence under a field that is still drawn in its
 *    ordinary colours makes the reader match the message to the field themselves. Two fields and
 *    one message is a guess.
 *  * **A supporting line.** The rule a value has to satisfy belongs under the field *before* it is
 *    broken, not in a message after. "At least eight characters" said up front is help; said after
 *    a rejection it is a reprimand.
 *  * **Autofill.** Android's password manager fills nothing into a field that has not said what it
 *    holds. Every reader with a saved password was typing it by hand.
 */
@Composable
fun CoineProTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    /**
     * Whether this field is the one the message below is about.
     *
     * Colours the outline and the label. Deliberately separate from the message itself, which stays
     * the screen's to place — a form with one summary line under three fields is a legitimate
     * layout, and this only has to say which field it means.
     */
    isError: Boolean = false,
    /** A line under the field, in the muted ink, or in the refusal colour when [isError]. */
    supporting: String? = null,
    /** A trailing glyph. Ignored on a [secret], which uses the slot for its reveal button. */
    trailing: (@Composable () -> Unit)? = null,
    /**
     * What Android's autofill service should offer here — a username, a password, a one-time code.
     *
     * Null means "nothing worth filling", which is the honest answer for a display name or a price.
     * Naming a wrong type is worse than naming none: a field that claims to be a password gets
     * offered one, and the reader's saved credential lands in a search box.
     */
    autofill: ContentType? = null,
) {
    // Local to the field and never hoisted. A reveal that survived navigation would be a password
    // left legible on a screen the reader came back to; and it must not be saved, for the same
    // reason the value itself is not.
    var revealed by remember { mutableStateOf(false) }
    val masked = secret && !revealed

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier = if (autofill != null) {
            modifier.semantics { contentType = autofill }
        } else {
            modifier
        },
        enabled = enabled,
        singleLine = true,
        isError = isError,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyLarge,
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        supportingText = supporting?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isError) CoineProColors.Sell else CoineProColors.TextMuted,
                )
            }
        },
        trailingIcon = when {
            secret -> {
                {
                    IconButton(
                        onClick = { revealed = !revealed },
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (revealed) CoineProIcons.Hidden else CoineProIcons.Visible,
                            ),
                            // Named for what tapping it *does*, not for what it currently shows.
                            // A screen reader announcing "hidden" on a button that reveals is the
                            // most confusing possible reading of an eye glyph.
                            contentDescription = stringResource(
                                if (revealed) R.string.field_hide else R.string.field_reveal,
                            ),
                            tint = CoineProColors.TextSecondary,
                            modifier = Modifier.size(TRAILING_GLYPH),
                        )
                    }
                }
            }
            trailing != null -> trailing
            else -> null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = CoineProColors.TextPrimary,
            unfocusedTextColor = CoineProColors.TextPrimary,
            focusedBorderColor = CoineProColors.Gold,
            unfocusedBorderColor = CoineProColors.Border,
            errorBorderColor = CoineProColors.Sell,
            errorLabelColor = CoineProColors.Sell,
            errorCursorColor = CoineProColors.Sell,
            focusedLabelColor = CoineProColors.Accent,
            unfocusedLabelColor = CoineProColors.TextMuted,
            cursorColor = CoineProColors.Gold,
            focusedContainerColor = CoineProColors.Surface,
            unfocusedContainerColor = CoineProColors.Surface,
            errorContainerColor = CoineProColors.Surface,
        ),
    )
}

private val TRAILING_GLYPH = 20.dp
