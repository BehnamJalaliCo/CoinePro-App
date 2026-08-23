package com.coinepro.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNotificationSignalIdTest {
    @Test
    fun exposesOnlyPositivePersistedSignalId() {
        fun notification(raw: String?) = AppNotification(
            kind = "signal",
            title = "Signal update",
            body = "",
            data = raw?.let { mapOf("signal_id" to it) }.orEmpty(),
            timestampEpochMillis = 1L,
            read = false,
        )

        assertEquals(42L, notification("42").signalId)
        assertNull(notification(null).signalId)
        assertNull(notification("0").signalId)
        assertNull(notification("-7").signalId)
        assertNull(notification("invalid").signalId)
    }
}
