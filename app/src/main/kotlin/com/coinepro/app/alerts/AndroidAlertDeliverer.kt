package com.coinepro.app.alerts

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.coinepro.app.MainActivity
import com.coinepro.app.R
import com.coinepro.app.notifications.minuteOfDay
import com.coinepro.app.notifications.priceAlertChannelId
import com.coinepro.core.common.BidiText
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertMessageTemplate
import com.coinepro.core.notifications.AlertSound
import com.coinepro.core.notifications.NotificationCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * How a fired alert actually reaches the reader, and what is written down when it does not.
 *
 * ### Every path out of here produces an answer
 *
 * There is no branch that returns quietly. A missing notification permission, quiet hours, an app
 * that is not open, a notification the system refused — each of them comes back as
 * [AlertDeliveryOutcome.Failed] carrying a sentence the reader can read in the alert's own history.
 * That is the entire reason the audit log exists: somebody who set an alert *stopped watching*, and
 * when nothing arrives they cannot tell whether the app failed or the market never got there. Every
 * one of these sentences answers that question, and a silent `return` would put the question back.
 *
 * ### The four channels, and what each of them can actually control
 *
 * [AlertChannel.PUSH] is the notification. [AlertChannel.IN_APP] is a banner in the running app, and
 * it is independent — see [InAppAlertBus]. [AlertChannel.SOUND] and [AlertChannel.VIBRATE] are not
 * destinations at all: they describe *how* the notification arrives, and from Android 8 both of them
 * belong to the notification channel rather than to the notification. So they choose between the
 * three price-alert channels rather than setting flags on the builder, and with `PUSH` switched off
 * they have nothing to attach to and say so.
 */
@Singleton
class AndroidAlertDeliverer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: NotificationSettingsStore,
    private val inApp: InAppAlertBus,
) : AlertDeliverer {

    override suspend fun deliver(fired: FiredAlert): AlertDeliveryOutcome {
        val channels = fired.alert.channels
        if (channels.isEmpty()) return AlertDeliveryOutcome.Failed(NO_CHANNELS)

        val refused = mutableListOf<String>()
        var reached = false

        if (AlertChannel.IN_APP in channels) {
            if (inApp.publish(fired)) reached = true else refused += APP_NOT_OPEN
        }
        if (AlertChannel.PUSH in channels) {
            val failure = post(fired)
            if (failure == null) reached = true else refused += failure
        } else if (AlertChannel.SOUND in channels || AlertChannel.VIBRATE in channels) {
            refused += SOUND_WITHOUT_PUSH
        }

        // One channel reaching the reader is enough. The refusals of the others are not worth a
        // failure line of their own — the reader was told, which is the question the log answers.
        return if (reached) {
            AlertDeliveryOutcome.Delivered
        } else {
            AlertDeliveryOutcome.Failed(refused.joinToString(SEPARATOR).ifEmpty { NO_CHANNELS })
        }
    }

    /**
     * Posts the notification, or names the reason it was not posted.
     *
     * The reader's own notification settings are consulted here rather than by the evaluator,
     * because being suppressed by quiet hours is a *delivery* outcome and not a reason the alert did
     * not fire. The condition was met; the app chose not to make a noise about it at four in the
     * morning, and the log says exactly that. Losing that distinction is how «my alert never went
     * off» becomes unanswerable.
     */
    private suspend fun post(fired: FiredAlert): String? {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return NO_PERMISSION
        }
        val current = settings.settings.first()
        if (!current.shouldShow(NotificationCategory.PRICE_ALERT, fired.atEpochMillis, minuteOfDay())) {
            return SUPPRESSED
        }
        return runCatching { notify(fired) }
            .exceptionOrNull()
            ?.let { failure -> listOfNotNull(POST_FAILED, failure.message).joinToString(": ") }
    }

    private fun notify(fired: FiredAlert) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // The chart for *this* symbol, not the activity list. See `AlertDeepLink` for why the
            // difference is the whole feature: the reader was woken in order to look at a chart.
            data = Uri.parse(AlertDeepLink.chart(fired.symbol, fired.timeframe))
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val identity = notificationId(fired)
        val body = bodyOf(fired)
        val pending = PendingIntent.getActivity(
            context,
            identity,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelFor(fired))
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(context.getString(R.string.alert_fired_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            // Silent covers sound *and* vibration, which is what an alert with neither asked for.
            // With either of them on, the chosen channel decides and this must stay false.
            .setSilent(AlertChannel.SOUND !in fired.alert.channels && AlertChannel.VIBRATE !in fired.alert.channels)
            .build()
        NotificationManagerCompat.from(context).notify(identity, notification)
    }

    /**
     * The sentence the notification carries.
     *
     * The reader's own wording where they wrote one, already rendered and bidi-isolated by
     * [AlertMessageTemplate]. Where they did not, the app's own Persian sentence rather than the
     * template's bare «symbol price» — which is deliberately wordless, because it is also what a
     * preview and an audit line show, and a fallback carrying prose would put words in the reader's
     * mouth every time they cleared the field. The prose belongs here, where it can be a string
     * resource, and the Latin runs inside it are isolated for the same reason they are everywhere
     * else in this app: an un-isolated price inside a right-to-left sentence reorders against its
     * neighbours.
     */
    private fun bodyOf(fired: FiredAlert): String =
        if (fired.alert.message.isNullOrBlank()) {
            context.getString(
                R.string.alert_fired_body,
                BidiText.isolateLtr(fired.symbol),
                BidiText.isolateLtr(AlertMessageTemplate.formatPrice(fired.price)),
            )
        } else {
            fired.body
        }

    /**
     * Which of the three price-alert channels this one alert arrives on.
     *
     * The loud one only where the reader pushed *this* alert past
     * [AlertSound.LOUD_THRESHOLD] themselves; it plays on the alarm output, which is a real
     * escalation and not something to infer. Vibrate-without-sound needs its own channel because on
     * Android 8 and later there is no other way to express it. Everything else is the ordinary
     * price-alert channel, which the reader already controls from the notification itself.
     */
    private fun channelFor(fired: FiredAlert): String =
        priceAlertChannelId(fired.alert.channels, fired.alert.effectiveSoundLevel)

    /**
     * One notification per alert **per symbol**.
     *
     * A watchlist alert fires independently for each member, and keying the notification on the
     * alert alone would have the second member quietly replace the first — the reader would be told
     * about one move and never learn about the other.
     */
    private fun notificationId(fired: FiredAlert): Int = (fired.alert.id + ID_SEPARATOR + fired.symbol).hashCode()

    private companion object {
        const val ID_SEPARATOR = "|"

        /** Between two refusal sentences in one audit note. */
        const val SEPARATOR = "، "

        const val NO_CHANNELS = "هیچ روشی برای این هشدار روشن نیست"
        const val APP_NOT_OPEN = "برنامه باز نبود"
        const val NO_PERMISSION = "اجازهٔ اعلان داده نشده است"
        const val SUPPRESSED = "اعلان‌ها خاموش یا در ساعات سکوت بود"
        const val SOUND_WITHOUT_PUSH = "صدا و لرزش بدون اعلان اثری ندارد"
        const val POST_FAILED = "سیستم اعلان را نپذیرفت"
    }
}
