package com.coinepro.core.notifications

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.model.MarketPlatform
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationControllerTest {

    @Test
    fun `one failing read does not discard the others`() = runTest {
        val gateway = FakeNotificationGateway(
            preferencesError = IllegalStateException("preferences down"),
        )
        val controller = controller(gateway)

        controller.refresh()
        runCurrent()

        val state = controller.state.value
        assertEquals("The list must survive a preferences failure", 1, state.notifications.size)
        assertEquals(1, state.alerts.size)
        assertNull(
            "Only the notification list is worth reporting a failure for",
            state.lastMessage,
        )
    }

    @Test
    fun `a failed notification read reports it and keeps what was already known`() = runTest {
        val gateway = FakeNotificationGateway(notificationsError = IllegalStateException("down"))
        val controller = controller(gateway)

        controller.refresh()
        runCurrent()

        assertEquals(
            UiMessage.Local(MessageKey.NOTIFICATION_CENTER_UNAVAILABLE),
            controller.state.value.lastMessage,
        )
    }

    @Test
    fun `the unread count clears only after the server confirms`() = runTest {
        val gateway = FakeNotificationGateway()
        val controller = controller(gateway)
        controller.refresh()
        runCurrent()
        assertEquals(3, controller.state.value.unread)

        gateway.markReadError = IllegalStateException("write failed")
        controller.markRead()
        runCurrent()
        assertEquals(
            "A badge cleared on a failed write comes back on the next refresh, which reads as new mail",
            3,
            controller.state.value.unread,
        )

        gateway.markReadError = null
        controller.markRead()
        runCurrent()
        assertEquals(0, controller.state.value.unread)
        assertTrue(controller.state.value.notifications.all { it.read })
    }

    @Test
    fun `an alert for the other platform's market is refused before it is sent`() = runTest {
        val gateway = FakeNotificationGateway()
        val controller = controller(gateway)

        controller.createAlert("BTCUSDT", PriceAlertCondition.ABOVE, 60_000.0, PriceAlertTrigger.ONCE)
        runCurrent()

        assertEquals(0, gateway.createCalls)
        assertEquals(
            UiMessage.Local(MessageKey.ALERT_SYMBOL_UNSUPPORTED),
            controller.state.value.lastMessage,
        )
    }

    @Test
    fun `a non-finite alert price never reaches the server`() = runTest {
        val gateway = FakeNotificationGateway()
        val controller = controller(gateway)

        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, -1.0)) {
            controller.createAlert("XAUUSD", PriceAlertCondition.ABOVE, value, PriceAlertTrigger.ONCE)
            runCurrent()
            assertEquals(
                UiMessage.Local(MessageKey.ALERT_VALUE_INVALID),
                controller.state.value.lastMessage,
            )
        }
        assertEquals(0, gateway.createCalls)
    }

    @Test
    fun `a delete the server did not confirm leaves the alert on screen`() = runTest {
        val gateway = FakeNotificationGateway(deleteConfirms = false)
        val controller = controller(gateway)
        controller.refresh()
        runCurrent()

        controller.deleteAlert("a1")
        runCurrent()

        assertEquals(
            "The alert is still armed server-side; hiding it would surprise the reader later",
            1,
            controller.state.value.alerts.size,
        )
        assertEquals(
            UiMessage.Local(MessageKey.ALERT_NOT_DELETED),
            controller.state.value.lastMessage,
        )
    }

    @Test
    fun `a refused preference change leaves the stored setting alone`() = runTest {
        val gateway = FakeNotificationGateway()
        val controller = controller(gateway)
        controller.refresh()
        runCurrent()
        val before = controller.state.value.preferences

        gateway.preferencesWriteError = IllegalStateException("nope")
        controller.updatePreferences(before.copy(priceAlerts = false))
        runCurrent()

        assertEquals(
            "Showing the toggle in its new position would claim push is off when it is not",
            before,
            controller.state.value.preferences,
        )
        assertEquals(
            UiMessage.Local(MessageKey.NOTIFICATION_PREFERENCES_NOT_SAVED),
            controller.state.value.lastMessage,
        )
    }

    private fun kotlinx.coroutines.test.TestScope.controller(gateway: FakeNotificationGateway) =
        NotificationController(gateway, this, MarketPlatform.COINEPRO_FX)
}

private class FakeNotificationGateway(
    var notificationsError: Throwable? = null,
    var preferencesError: Throwable? = null,
    var preferencesWriteError: Throwable? = null,
    var markReadError: Throwable? = null,
    var deleteConfirms: Boolean = true,
) : NotificationGateway {
    var createCalls = 0

    override suspend fun registerDevice(token: String, appVersion: String?, locale: String?) = true

    override suspend fun unregisterDevice(token: String) = true

    override suspend fun preferences(): PushPreferences {
        preferencesError?.let { throw it }
        return PushPreferences()
    }

    override suspend fun updatePreferences(preferences: PushPreferences): PushPreferences {
        preferencesWriteError?.let { throw it }
        return preferences
    }

    override suspend fun notifications(limit: Int): NotificationPage {
        notificationsError?.let { throw it }
        return NotificationPage(
            items = listOf(
                AppNotification(
                    kind = "new_signal",
                    title = "سیگنالِ تازه",
                    body = "طلا خرید",
                    data = mapOf("signal_id" to "945"),
                    timestampEpochMillis = 1_787_663_200_000L,
                    read = false,
                ),
            ),
            unread = 3,
        )
    }

    override suspend fun markNotificationsRead() {
        markReadError?.let { throw it }
    }

    override suspend fun alerts() = listOf(
        PriceAlert(
            id = "a1",
            market = "forex",
            symbol = "XAUUSD",
            condition = PriceAlertCondition.CROSS_UP,
            value = 4_700.0,
            trigger = PriceAlertTrigger.RECURRING,
            expiresAtEpochMillis = null,
            active = true,
            createdAtEpochMillis = 1_787_663_247_170L,
            lastTriggeredAtEpochMillis = null,
        ),
    )

    override suspend fun createAlert(
        symbol: String,
        condition: PriceAlertCondition,
        value: Double,
        trigger: PriceAlertTrigger,
    ): PriceAlert {
        createCalls++
        return alerts().first()
    }

    override suspend fun setAlertActive(alertId: String, active: Boolean) =
        alerts().first().copy(active = active)

    override suspend fun deleteAlert(alertId: String) = deleteConfirms
}
