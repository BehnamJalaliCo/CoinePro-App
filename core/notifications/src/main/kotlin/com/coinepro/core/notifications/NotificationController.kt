package com.coinepro.core.notifications

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationController(
    private val gateway: NotificationGateway,
    private val scope: CoroutineScope,
    private val platform: MarketPlatform = MarketPlatform.COINEPRO_FX,
) {
    private val _state = MutableStateFlow(NotificationCenterState())
    val state: StateFlow<NotificationCenterState> = _state.asStateFlow()

    fun clear() {
        _state.value = NotificationCenterState()
    }

    /**
     * Loads the three parts of the screen independently.
     *
     * They were sequenced before, which meant a failure in any one of them discarded the other two
     * — a preferences call that timed out took the reader's whole notification list with it. They
     * are separate reads answering separate questions, so they now succeed and fail separately and
     * whatever arrived is kept.
     */
    fun refresh() {
        scope.launch {
            _state.update { it.copy(loading = true, lastMessage = null) }

            val notifications = async { runCatching { gateway.notifications() } }
            val alerts = async { runCatching { gateway.alerts() } }
            val preferences = async { runCatching { gateway.preferences() } }

            val page = notifications.await()
            val alertList = alerts.await()
            val prefs = preferences.await()

            _state.update { current ->
                current.copy(
                    loading = false,
                    notifications = page.getOrNull()?.items ?: current.notifications,
                    unread = page.getOrNull()?.unread ?: current.unread,
                    hasMoreNotifications = page.getOrNull()?.hasMore ?: current.hasMoreNotifications,
                    alerts = alertList.getOrNull() ?: current.alerts,
                    preferences = prefs.getOrNull() ?: current.preferences,
                    // Only the notification list is worth reporting a failure for: it is what the
                    // screen is named after, and a reader who can see their alerts and settings is
                    // not looking at a broken screen.
                    lastMessage = page.exceptionOrNull()
                        ?.toNotificationMessage(MessageKey.NOTIFICATION_CENTER_UNAVAILABLE),
                )
            }
        }
    }

    /**
     * Marks everything read.
     *
     * The count is cleared only after the server confirms. Clearing it optimistically would show a
     * zero the server does not agree with, and the next refresh would silently bring the badge back
     * — which reads as new mail arriving rather than as a write that failed.
     */
    fun markRead() {
        scope.launch {
            runCatching { gateway.markNotificationsRead() }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            unread = 0,
                            notifications = current.notifications.map { it.copy(read = true) },
                        )
                    }
                }
        }
    }

    fun updatePreferences(value: PushPreferences) {
        scope.launch {
            runCatching { gateway.updatePreferences(value) }
                .onSuccess { prefs -> _state.update { it.copy(preferences = prefs, lastMessage = null) } }
                .onFailure { error ->
                    // The stored preferences are left as they were. Showing the toggle in its new
                    // position after a failed write would tell the reader push is off when the
                    // server will still be sending it.
                    _state.update {
                        it.copy(
                            lastMessage = error.toNotificationMessage(
                                MessageKey.NOTIFICATION_PREFERENCES_NOT_SAVED,
                            ),
                        )
                    }
                }
        }
    }

    fun createAlert(
        symbol: String,
        condition: PriceAlertCondition,
        value: Double,
        trigger: PriceAlertTrigger,
    ) {
        val safeSymbol = normalizeProductAlertSymbol(symbol, platform)
        if (safeSymbol == null) {
            _state.update { it.copy(lastMessage = UiMessage.of(MessageKey.ALERT_SYMBOL_UNSUPPORTED)) }
            return
        }
        // Checked here as well as on the server, because a non-finite value is not a refusal worth
        // a round trip — it is a number that cannot mean anything.
        if (!value.isFinite() || value <= 0.0) {
            _state.update { it.copy(lastMessage = UiMessage.of(MessageKey.ALERT_VALUE_INVALID)) }
            return
        }
        scope.launch {
            runCatching { gateway.createAlert(safeSymbol, condition, value, trigger) }
                .onSuccess { created ->
                    _state.update { it.copy(alerts = listOf(created) + it.alerts, lastMessage = null) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(lastMessage = error.toNotificationMessage(MessageKey.ALERT_NOT_CREATED))
                    }
                }
        }
    }

    fun setAlertActive(alert: PriceAlert, active: Boolean) {
        scope.launch {
            runCatching { gateway.setAlertActive(alert.id, active) }
                .onSuccess { updated ->
                    _state.update { current ->
                        current.copy(
                            alerts = current.alerts.map { if (it.id == updated.id) updated else it },
                            lastMessage = null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(lastMessage = error.toNotificationMessage(MessageKey.ALERT_NOT_UPDATED))
                    }
                }
        }
    }

    fun deleteAlert(alertId: String) {
        scope.launch {
            runCatching { gateway.deleteAlert(alertId) }
                .onSuccess { removed ->
                    // A response that did not confirm the removal leaves the row in place. The
                    // alert is still armed on the server, and hiding it would mean a notification
                    // arriving later from something the reader believes they deleted.
                    if (removed) {
                        _state.update { current ->
                            current.copy(
                                alerts = current.alerts.filterNot { it.id == alertId },
                                lastMessage = null,
                            )
                        }
                    } else {
                        _state.update { it.copy(lastMessage = UiMessage.of(MessageKey.ALERT_NOT_DELETED)) }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(lastMessage = error.toNotificationMessage(MessageKey.ALERT_NOT_DELETED))
                    }
                }
        }
    }

    suspend fun registerDevice(token: String, appVersion: String?, locale: String?): Boolean =
        gateway.registerDevice(token, appVersion, locale)

    suspend fun unregisterDevice(token: String): Boolean = gateway.unregisterDevice(token)
}
