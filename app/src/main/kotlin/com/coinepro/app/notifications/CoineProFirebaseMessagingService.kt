package com.coinepro.app.notifications

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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CoineProFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var pushCoordinator: PushCoordinator

    override fun onNewToken(token: String) {
        pushCoordinator.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["_title"]?.takeIf { it.isNotBlank() } ?: "CoinePro"
        val body = data["_body"].orEmpty()
        val signalId = positiveSignalId(data["signal_id"])
        val destination = if (signalId != null) {
            Uri.parse("coinepro://signal/$signalId")
        } else {
            Uri.parse("coinepro://activity")
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
        val notification = NotificationCompat.Builder(this, NotificationChannels.MARKET_EVENTS)
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
