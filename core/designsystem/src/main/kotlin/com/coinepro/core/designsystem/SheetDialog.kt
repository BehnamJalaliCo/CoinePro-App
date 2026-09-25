package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * The window a sheet opens in on an expanded screen, dimming the page by [scrimAlpha].
 *
 * Its own file because the dimming is the one thing the two platforms spell differently. Android
 * dims the page through the dialog *window*, at the theme's sixty per cent unless told otherwise;
 * the browser's twin (`web/src/wasmJsMain/.../SheetDialog.web.kt`) passes a scrim colour to Compose
 * Multiplatform's dialog, whose default is the same sixty. Either way the sheet's own request — none
 * at all behind a live preview — is what reaches the glass.
 */
@Composable
internal fun SheetDialog(onDismiss: () -> Unit, scrimAlpha: Float, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(scrimAlpha) }
        content()
    }
}
