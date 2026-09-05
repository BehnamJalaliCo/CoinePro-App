package com.coinepro.app.notifications

import com.coinepro.core.common.BrandConfig
import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.coinepro.app.MainActivity
import com.coinepro.app.positiveSignalId
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.notifications.NotificationSettings
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class CoineProFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushCoordinator: PushCoordinator
    @Inject lateinit var settingsStore: NotificationSettingsStore

    override fun onNewToken(token: String) {
        pushCoordinator.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val category = NotificationCategory.forKind(data["kind"] ?: data["type"] ?: data["category"])

        // Read on this thread on purpose. `onMessageReceived` already runs off the main thread, the
        // read is a single small file, and the alternative — showing the notification and hiding it
        // once the preference arrives — is a notification the reader has already seen.
        val settings = runBlocking { settingsStore.settings.first() }
        if (!settings.shouldShow(category, System.currentTimeMillis(), minuteOfDay())) return

        val title = data["_title"]?.takeIf { it.isNotBlank() } ?: BrandConfig.DISPLAY_NAME
        val body = data["_body"].orEmpty()
        val signalId = positiveSignalId(data["signal_id"])
        val destination = if (signalId != null) {
            Uri.parse("${BrandConfig.SCHEME_PREFIX}signal/$signalId")
        } else {
            Uri.parse("${BrandConfig.SCHEME_PREFIX}activity")
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            this.data = destination
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            signalId?.hashCode() ?: data.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = category
            ?.let(NotificationChannels::channelId)
            ?: NotificationChannels.GENERAL
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (
            android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(this).notify(
                (signalId?.hashCode() ?: (title + body).hashCode()),
                notification,
            )
        }
    }
}

/**
 * Minutes since local midnight, for the quiet-hours window.
 *
 * The device's own calendar, not UTC. Quiet hours are a statement about the reader being asleep,
 * and somebody in Tehran means eleven at night where they are.
 */
internal fun minuteOfDay(calendar: Calendar = Calendar.getInstance()): Int =
    calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

/** Kept for the settings screen's preview of what a category maps to. */
internal fun NotificationSettings.allows(category: NotificationCategory): Boolean =
    shouldShow(category, System.currentTimeMillis(), minuteOfDay())
