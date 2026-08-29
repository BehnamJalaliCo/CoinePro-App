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
        MessageKey.SESSION_NOT_REVALIDATED -> R.string.message_session_not_revalidated
        MessageKey.TELEGRAM_SIGN_IN_NOT_CONFIGURED -> R.string.message_telegram_sign_in_not_configured
        MessageKey.TELEGRAM_SIGN_IN_CONFIG_UNAVAILABLE -> R.string.message_telegram_sign_in_config_unavailable
        MessageKey.SIGNALS_UNAVAILABLE -> R.string.message_signals_unavailable
        MessageKey.SIGNAL_HISTORY_UNAVAILABLE -> R.string.message_signal_history_unavailable
        MessageKey.SIGNAL_DETAILS_UNAVAILABLE -> R.string.message_signal_details_unavailable
        MessageKey.CACHED_HISTORY_SHOWN -> R.string.message_cached_history_shown
        MessageKey.NOTIFICATION_CENTER_UNAVAILABLE -> R.string.message_notification_center_unavailable
        MessageKey.NOTIFICATION_PREFERENCES_NOT_SAVED -> R.string.message_notification_preferences_not_saved
        MessageKey.ALERT_SYMBOL_UNSUPPORTED -> R.string.message_alert_symbol_unsupported
        MessageKey.ALERT_VALUE_INVALID -> R.string.message_alert_value_invalid
        MessageKey.ALERT_NOT_CREATED -> R.string.message_alert_not_created
        MessageKey.ALERT_NOT_UPDATED -> R.string.message_alert_not_updated
        MessageKey.ALERT_NOT_DELETED -> R.string.message_alert_not_deleted
        MessageKey.MARKETS_UNAVAILABLE -> R.string.message_markets_unavailable
        MessageKey.AI_JOB_EXPIRED -> R.string.message_ai_job_expired
        MessageKey.AI_ENTITLEMENT_REQUIRED -> R.string.message_ai_entitlement_required
        MessageKey.AI_RESULT_UNUSABLE -> R.string.message_ai_result_unusable
        MessageKey.AI_GENERATION_FAILED -> R.string.message_ai_generation_failed
        MessageKey.AI_SYMBOL_UNSUPPORTED -> R.string.message_ai_symbol_unsupported
        MessageKey.AI_MESSAGE_EMPTY -> R.string.message_ai_message_empty
        MessageKey.AI_MESSAGE_TOO_LONG -> R.string.message_ai_message_too_long
        MessageKey.AI_IMAGE_TOO_LARGE -> R.string.message_ai_image_too_large
        MessageKey.AI_IMAGE_TYPE_UNSUPPORTED -> R.string.message_ai_image_type_unsupported
        MessageKey.AI_CONVERSATION_CHANGED -> R.string.message_ai_conversation_changed
    }
