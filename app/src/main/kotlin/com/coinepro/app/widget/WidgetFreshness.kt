package com.coinepro.app.widget

import android.content.Context
import com.coinepro.app.R
import com.coinepro.core.common.toPersianDigits

/**
 * How old the widget's prices are, in words.
 *
 * ### Why this is not optional
 *
 * A widget that shows a price with no time on it shows yesterday's price exactly as confidently as
 * this second's, and the reader cannot tell the difference. On a weather widget that is a mild
 * annoyance; on a trading one it is somebody acting on a number that stopped being true while
 * their phone was in a tunnel.
 *
 * ### The buckets
 *
 * Coarse on purpose. A widget is read in a glance from arm's length, and «۳۸ ثانیه پیش» is more
 * precision than that glance can use — it also changes every second, which on a surface that
 * redraws rarely means it is usually wrong anyway. Four answers: now, minutes, hours, older than
 * that. Each is true for as long as it is shown.
 *
 * Persian digits, because this is a count in prose — «۵ دقیقه» — and not a market figure. The
 * prices in the rows beside it are Latin, which is the app's rule and is the reason the two look
 * different: one is language and the other is data.
 */
object WidgetFreshness {

    fun describe(
        context: Context,
        capturedAtEpochMillis: Long,
        nowEpochMillis: Long,
        stale: Boolean,
    ): String {
        // Nothing has ever been fetched. Saying "now" would be the one lie this whole file exists
        // to prevent.
        if (capturedAtEpochMillis <= 0L) return context.getString(R.string.widget_never)
        val age = nowEpochMillis - capturedAtEpochMillis
        // A clock moved backwards — the reader changed the time, or the network corrected it. The
        // honest answer is not a negative age; it is that we no longer know.
        if (age < 0L) return context.getString(R.string.widget_never)

        val text = when {
            age < JUST_NOW_MILLIS -> context.getString(R.string.widget_now)
            age < HOUR_MILLIS -> context.getString(
                R.string.widget_minutes,
                (age / MINUTE_MILLIS).toInt().toPersianDigits(),
            )
            age < DAY_MILLIS -> context.getString(
                R.string.widget_hours,
                (age / HOUR_MILLIS).toInt().toPersianDigits(),
            )
            else -> context.getString(R.string.widget_old)
        }
        // A failed refresh is said out loud rather than hidden behind an age that keeps climbing.
        // The two are different facts: "an hour old" may be fine, "an hour old and we tried and
        // could not" is a reason to open the app.
        return if (stale) context.getString(R.string.widget_stale, text) else text
    }

    /** Under this, "now". Anything shorter is precision a glance cannot use. */
    private const val JUST_NOW_MILLIS = 60_000L
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 3_600_000L
    private const val DAY_MILLIS = 86_400_000L
}
