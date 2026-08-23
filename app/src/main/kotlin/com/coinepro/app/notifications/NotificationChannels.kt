package com.coinepro.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val MARKET_EVENTS = "market_events"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            MARKET_EVENTS,
            "Market events",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "CoinePro signal, target, stop and price-alert events"
        }
        manager.createNotificationChannel(channel)
    }
}
