package com.coinepro.core.notifications

import com.coinepro.core.common.UiMessage

enum class PriceAlertCondition(val wireValue: String) {
    ABOVE("above"),
    BELOW("below"),
    CROSS_UP("cross_up"),
    CROSS_DOWN("cross_down"),
    CROSS("cross"),
}

enum class PriceAlertTrigger(val wireValue: String) {
    ONCE("once"),
    RECURRING("recurring"),
}

data class PushPreferences(
    val newSignals: Boolean = true,
    val signalUpdates: Boolean = true,
    val priceAlerts: Boolean = true,
)

data class AppNotification(
    val kind: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val timestampEpochMillis: Long,
    val read: Boolean,
) {
    val signalId: Long?
        get() = data["signal_id"]?.toLongOrNull()?.takeIf { it > 0L }
}

data class PriceAlert(
    val id: String,
    val market: String,
    val symbol: String,
    val condition: PriceAlertCondition,
    val value: Double,
    val trigger: PriceAlertTrigger,
    /** Epoch milliseconds, like the two timestamps below it. Null when the server sent none. */
    val expiresAtEpochMillis: Long?,
    val active: Boolean,
    val createdAtEpochMillis: Long,
    val lastTriggeredAtEpochMillis: Long?,
)

data class NotificationCenterState(
    val loading: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val unread: Int = 0,
    val alerts: List<PriceAlert> = emptyList(),
    val preferences: PushPreferences = PushPreferences(),
    /**
     * Server wording when the server gave any, and owned copy otherwise — never an exception's own
     * text, which is a status line rather than something a reader can act on.
     */
    val lastMessage: UiMessage? = null,
)
