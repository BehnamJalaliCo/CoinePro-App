package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage

/**
 * Resolves a controller-produced [UiMessage] into display text in the active language.
 *
 * This is the only place a [MessageKey] becomes words, so adding a key without adding its resource
 * here fails the build rather than silently rendering an enum name.
 */
@Composable
fun UiMessage.resolve(): String = when (this) {
    is UiMessage.Local -> stringResource(key.resourceId)
    is UiMessage.Server -> text
    is UiMessage.Prefixed -> "${stringResource(prefix.resourceId)} ${detail.resolve()}"
}

private val MessageKey.resourceId: Int
    get() = when (this) {
        MessageKey.SIGNALS_UNAVAILABLE -> R.string.message_signals_unavailable
        MessageKey.SIGNAL_HISTORY_UNAVAILABLE -> R.string.message_signal_history_unavailable
        MessageKey.SIGNAL_DETAILS_UNAVAILABLE -> R.string.message_signal_details_unavailable
        MessageKey.CACHED_HISTORY_SHOWN -> R.string.message_cached_history_shown
    }
