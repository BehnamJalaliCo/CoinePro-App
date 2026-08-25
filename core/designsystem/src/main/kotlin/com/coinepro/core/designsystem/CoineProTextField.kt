package com.coinepro.core.designsystem

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

/**
 * A single-line input.
 *
 * Outlined rather than filled: on this direction's stage a filled field is the same block as a
 * card, so the two stack into an indistinguishable slab. The outline is what says "type here".
 *
 * [secret] does two things at once — it masks the value and it marks the field as one whose
 * contents must never be logged or restored from saved state. Callers clear the backing value as
 * soon as it has been sent.
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
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyLarge,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = CoineProColors.TextPrimary,
            unfocusedTextColor = CoineProColors.TextPrimary,
            focusedBorderColor = CoineProColors.Gold,
            unfocusedBorderColor = CoineProColors.Border,
            focusedLabelColor = CoineProColors.Accent,
            unfocusedLabelColor = CoineProColors.TextMuted,
            cursorColor = CoineProColors.Gold,
            focusedContainerColor = CoineProColors.Surface,
            unfocusedContainerColor = CoineProColors.Surface,
        ),
    )
}
