package com.coinepro.app.notifications

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.coinepro.app.pulse.MarketPulseStore

/**
 * Whether Android lets the app work while it is closed, and the way to let it (5.29.0).
 *
 * The market notifications are worked out on the phone every quarter of an hour by WorkManager. A
 * phone that holds the app to battery optimisation — the default for an app that did not come from
 * a store, and every Xiaomi, Samsung and Huawei build's habit — defers that work until the reader
 * opens the app, which is exactly «it only notifies me when I open it». The notification settings
 * show this card while the restriction stands, with the one button that lifts it.
 */
object BackgroundDelivery {

    /** True while the app is held to battery optimisation; null where the question does not apply. */
    fun restricted(context: Context): Boolean? = runCatching {
        val power = context.getSystemService(PowerManager::class.java) ?: return@runCatching null
        !power.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrNull()

    /** When the market check last ran on its own, or null if it never has. */
    fun lastCheck(context: Context): Long? = MarketPulseStore(context).lastRunAt()

    /**
     * Android's own «allow in the background?» dialog, or its battery-optimisation list where a
     * build will not show the dialog.
     */
    @SuppressLint("BatteryLife") // Not on Play; this is the app's whole notification feature.
    fun openFix(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(direct) }.onFailure { runCatching { context.startActivity(list) } }
    }
}
