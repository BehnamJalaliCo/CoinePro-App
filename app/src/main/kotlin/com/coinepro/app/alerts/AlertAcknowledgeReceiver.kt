package com.coinepro.app.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.coinepro.core.datastore.LocalAlertStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * «دیدم» — the button that stops a repeating alert (run Τ2, B9).
 *
 * ### Why a button and not only a tap
 *
 * `AlertRepeat.UNTIL_ACKNOWLEDGED` repeats until the reader says they have seen it, and the whole
 * value of it is that it keeps going when a tap did not happen. Making the *only* acknowledgement a
 * tap on the notification would mean the one way to stop it is to open the chart — at the moment
 * somebody is driving, in a meeting, or has already decided to do nothing. So there are two, and
 * they mean the same thing: opening it (see `MainActivity`) and this button.
 *
 * It cancels the notification itself as well. A «دیدم» that leaves the banner on the shade reads as
 * a button that did not work, and the next thing the reader does is press it again.
 *
 * ### `goAsync`, because the write is a file
 *
 * A receiver's `onReceive` runs on the main thread and must return quickly; the preferences edit is
 * suspending. `goAsync` holds the process alive for the few milliseconds it takes, and `finish()`
 * is called on **every** path including the failure one — a pending result that is never finished
 * is a process the system cannot reclaim.
 */
@AndroidEntryPoint
class AlertAcknowledgeReceiver : BroadcastReceiver() {

    @Inject lateinit var store: LocalAlertStore

    @Inject lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra(EXTRA_ALERT_ID)?.takeIf(String::isNotBlank) ?: return
        val notification = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val pending = goAsync()
        scope.launch {
            try {
                store.acknowledge(id, System.currentTimeMillis())
                if (notification != 0) NotificationManagerCompat.from(context).cancel(notification)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Namespaced on the package, because an action string is a global. */
        const val ACTION = "com.coinepro.app.alerts.ACKNOWLEDGE"

        /** Which alert. Read by this receiver and by `MainActivity`, which acknowledges on open. */
        const val EXTRA_ALERT_ID = "alert_id"

        /** Which banner to take down with it. Zero means «there is none», not «notification 0». */
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
