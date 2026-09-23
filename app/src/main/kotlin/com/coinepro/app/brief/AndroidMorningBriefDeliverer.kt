package com.coinepro.app.brief

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.coinepro.app.MainActivity
import com.coinepro.app.R
import com.coinepro.app.alerts.AlertDeepLink
import com.coinepro.app.notifications.NotificationChannels
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.MorningBrief
import com.coinepro.core.notifications.NotificationCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The morning brief on the reader's lock screen.
 *
 * ### Quiet hours are not consulted, and that is deliberate
 *
 * Every other notification in this app passes `NotificationSettings.shouldShow`, which drops a
 * message that lands inside the reader's quiet window. The brief does not, for one reason: **its
 * hour is one the reader typed.** Silently swallowing a brief scheduled for 06:30 by somebody
 * whose quiet hours run to 07:00 would be the app overruling the more specific of two instructions
 * it was given, and the reader would see a feature that does nothing. The contradiction belongs on
 * the settings screen, where both numbers are visible, and not here.
 *
 * The master switch and the category switch **are** respected, in `MorningBriefEngine` — before the
 * fetch, so a reader who turned the brief off does not pay for one.
 *
 * ### A tap opens the mover's chart, not the app
 *
 * The reader was told a market moved; the thing they want next is that market. The same deep link
 * a fired alert uses, for the same reason, and on a quiet day with no mover it falls back to the
 * app's own front door rather than to a chart of nothing.
 */
@Singleton
class AndroidMorningBriefDeliverer @Inject constructor(
    @ApplicationContext private val context: Context,
) : MorningBriefDeliverer {

    override suspend fun deliver(brief: MorningBrief, sparkline: CandleSeries?) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { post(brief, sparkline) }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun post(brief: MorningBrief, sparkline: CandleSeries?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            brief.mover?.let { data = Uri.parse(AlertDeepLink.chart(it)) }
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = brief.body
        val picture = brief.mover?.let { BriefSparkline.of(sparkline) }
        val builder = NotificationCompat.Builder(
            context,
            NotificationChannels.channelId(NotificationCategory.MORNING_BRIEF),
        )
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(brief.headline)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
        // The picture where there is one, the long text where there is not. Never both: a
        // `BigPictureStyle` carries its own summary line, and setting a `BigTextStyle` after it
        // replaces the picture rather than adding to it — which is how a sparkline silently stops
        // appearing after somebody adds a line of copy.
        if (picture != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(picture)
                    .setSummaryText(body),
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }
        // One id for every brief, so today's replaces yesterday's rather than stacking. A shade
        // with six mornings in it is six notifications nobody reads.
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    private companion object {
        /** Stable and singular. See the note at the `notify` call. */
        const val NOTIFICATION_ID = 0x0B21E
    }
}

/**
 * When the last brief went out, in this device's preferences.
 *
 * Two values rather than one: the instant, for the log and for a future «last brief» line, and the
 * **local day** it belonged to, which is what the once-a-day guard actually compares. A day number
 * derived from the stored instant at read time would move when the reader crosses a time zone, and
 * the brief would then either arrive twice or not at all on the day they flew.
 */
@Singleton
class PreferencesBriefDeliveryStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BriefDeliveryStore {

    override suspend fun lastDeliveredAt(): Long? =
        dataStore.data.first()[AT]?.takeIf { it > 0L }

    override suspend fun lastDeliveredDay(): Long? = dataStore.data.first()[DAY]

    override suspend fun record(atEpochMillis: Long, localDay: Long) {
        dataStore.edit { preferences ->
            preferences[AT] = atEpochMillis
            preferences[DAY] = localDay
        }
    }

    private companion object {
        val AT = longPreferencesKey("brief_last_at")
        val DAY = longPreferencesKey("brief_last_day")
    }
}
