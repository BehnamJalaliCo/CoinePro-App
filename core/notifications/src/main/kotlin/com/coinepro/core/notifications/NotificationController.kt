package com.coinepro.core.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationController(
    private val gateway: NotificationGateway,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NotificationCenterState())
    val state: StateFlow<NotificationCenterState> = _state.asStateFlow()

    fun clear() {
        _state.value = NotificationCenterState()
    }

    fun refresh() {
        scope.launch {
            _state.update { it.copy(loading = true, lastError = null) }
            runCatching {
                val page = gateway.notifications()
                val alerts = gateway.alerts()
                val preferences = gateway.preferences()
                Triple(page, alerts, preferences)
            }.onSuccess { (page, alerts, preferences) ->
                _state.value = NotificationCenterState(
                    loading = false,
                    notifications = page.items,
                    unread = page.unread,
                    alerts = alerts,
                    preferences = preferences,
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(loading = false, lastError = error.message ?: "Notification center unavailable")
                }
            }
        }
    }

    fun markRead() {
        scope.launch {
            runCatching { gateway.markNotificationsRead() }
                .onSuccess { _state.update { it.copy(unread = 0) } }
        }
    }

    fun updatePreferences(value: PushPreferences) {
        scope.launch {
            runCatching { gateway.updatePreferences(value) }
                .onSuccess { prefs -> _state.update { it.copy(preferences = prefs, lastError = null) } }
                .onFailure { error -> _state.update { it.copy(lastError = error.message) } }
        }
    }

    fun createAlert(
        symbol: String,
        condition: PriceAlertCondition,
        value: Double,
        trigger: PriceAlertTrigger,
    ) {
        if (symbol.isBlank() || value <= 0) return
        scope.launch {
            runCatching { gateway.createAlert(symbol, condition, value, trigger) }
                .onSuccess { created ->
                    _state.update { it.copy(alerts = listOf(created) + it.alerts, lastError = null) }
                }
                .onFailure { error -> _state.update { it.copy(lastError = error.message ?: "Alert creation failed") } }
        }
    }

    fun setAlertActive(alert: PriceAlert, active: Boolean) {
        scope.launch {
            runCatching { gateway.setAlertActive(alert.id, active) }
                .onSuccess { updated ->
                    _state.update { current ->
                        current.copy(alerts = current.alerts.map { if (it.id == updated.id) updated else it })
                    }
                }
                .onFailure { error -> _state.update { it.copy(lastError = error.message) } }
        }
    }

    fun deleteAlert(alertId: String) {
        scope.launch {
            runCatching { gateway.deleteAlert(alertId) }
                .onSuccess { removed ->
                    if (removed) _state.update { it.copy(alerts = it.alerts.filterNot { row -> row.id == alertId }) }
                }
                .onFailure { error -> _state.update { it.copy(lastError = error.message) } }
        }
    }

    suspend fun registerDevice(token: String, appVersion: String?, locale: String?): Boolean =
        gateway.registerDevice(token, appVersion, locale)
}
