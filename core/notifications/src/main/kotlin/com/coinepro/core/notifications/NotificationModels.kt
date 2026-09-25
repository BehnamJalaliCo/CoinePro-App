package com.coinepro.core.notifications

import com.coinepro.core.common.UiMessage

enum class PriceAlertCondition(val wireValue: String) {
    ABOVE("above"),
    BELOW("below"),
    CROSS_UP("cross_up"),
    CROSS_DOWN("cross_down"),
    CROSS("cross"),

    /** A channel, a move over bars or an indicator, described by [PriceAlert.spec] (5.17.0, CoinePro-FX). */
    SPEC("spec"),
}

enum class PriceAlertTrigger(val wireValue: String) {
    ONCE("once"),
    RECURRING("recurring"),
}

data class PushPreferences(
    val newSignals: Boolean = true,
    val signalUpdates: Boolean = true,
    val priceAlerts: Boolean = true,
    val announcements: Boolean = true,
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
    /** The advanced condition, for [PriceAlertCondition.SPEC] (5.17.0). See `ServerAlertSpec`. */
    val spec: Map<String, String>? = null,
    /** How the server delivers it: `push`, `telegram`, `email`. */
    val channels: List<String> = listOf("push"),
)

data class NotificationCenterState(
    val loading: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val unread: Int = 0,
    /** The server held back older entries; the screen says so rather than implying completeness. */
    val hasMoreNotifications: Boolean = false,
    val alerts: List<PriceAlert> = emptyList(),
    val preferences: PushPreferences = PushPreferences(),
    /**
     * Server wording when the server gave any, and owned copy otherwise — never an exception's own
     * text, which is a status line rather than something a reader can act on.
     */
    val lastMessage: UiMessage? = null,
)
