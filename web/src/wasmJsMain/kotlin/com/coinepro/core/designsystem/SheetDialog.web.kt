package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The browser's twin of the phone's `SheetDialog`: the scrim is a property of the dialog here, and
 * Compose Multiplatform's default is sixty per cent black — the dimming the audit measured behind
 * every desktop dialog, live-preview sheets included (DIALOGS-08).
 */
// The scrim is still an experimental property in Compose Multiplatform 1.9.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SheetDialog(onDismiss: () -> Unit, scrimAlpha: Float, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            scrimColor = Color.Black.copy(alpha = scrimAlpha),
        ),
        content = content,
    )
}
