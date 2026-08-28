package com.coinepro.app.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.coinepro.app.R
import com.coinepro.core.notifications.NotificationCategory

/**
 * One Android channel per category, in four groups.
 *
 * ### Why not one channel, as before
 *
 * A channel is the operating system's own per-category control, and it is better than anything this
 * app could build. Long-press a notification and the reader can silence *that kind* without opening
 * CoinePro, give it a different sound, decide whether it may vibrate, or let it through Do Not
 * Disturb. OKX ships an in-app picker of five alert sounds; that is a worse version of something
 * every Android phone already has, and one nobody knows how to find twice.
 *
 * So the app's own switch answers "send me this at all" and the channel answers "and how loudly".
 * The two are complementary and the settings screen says which is which.
 *
 * ### Importance is not uniform, and that is the substance here
 *
 * A copy trade that failed and a news headline are not the same interruption. High importance makes
 * a heads-up notification with sound; default makes a sound; low is silent in the shade. Getting
 * this wrong in either direction is how an app gets muted altogether — too loud and the reader
 * silences everything, too quiet and they miss the one that cost them money.
 *
 * ### Channels are permanent
 *
 * Android will not let an app change a channel's importance after it is created — that setting
 * belongs to the reader from the moment it exists. So the ids here are a contract: renaming one
 * makes a *new* channel and silently resets whatever the reader had chosen. They are versioned
 * (`v2`) precisely because this release replaces the single `market_events` channel, and the old
 * one is deleted rather than left behind as a dead entry in the system settings list.
 */
object NotificationChannels {

    /** The channel a message with no known category falls back to. */
    const val GENERAL = "general_v2"

    private const val GROUP_TRADING = "group_trading"
    private const val GROUP_MARKET = "group_market"
    private const val GROUP_ACCOUNT = "group_account"
    private const val GROUP_OTHER = "group_other"

    /** The single channel every notification used before this release. */
    private const val LEGACY_MARKET_EVENTS = "market_events"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        listOf(
            NotificationChannelGroup(GROUP_TRADING, context.getString(R.string.channel_group_trading)),
            NotificationChannelGroup(GROUP_MARKET, context.getString(R.string.channel_group_market)),
            NotificationChannelGroup(GROUP_ACCOUNT, context.getString(R.string.channel_group_account)),
            NotificationChannelGroup(GROUP_OTHER, context.getString(R.string.channel_group_other)),
        ).forEach(manager::createNotificationChannelGroup)

        NotificationCategory.entries.forEach { category ->
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId(category),
                    context.getString(category.channelNameRes()),
                    category.importance(),
                ).apply {
                    group = category.group()
                    description = context.getString(category.channelDescriptionRes())
                },
            )
        }

        manager.createNotificationChannel(
            NotificationChannel(
                GENERAL,
                context.getString(R.string.channel_general),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                group = GROUP_OTHER
                description = context.getString(R.string.channel_general_description)
            },
        )

        // Removed rather than left to rot. A stale channel stays in the system's list for ever with
        // nothing ever posted to it, and a reader looking for the switch that stops a notification
        // will find the one that does nothing.
        manager.deleteNotificationChannel(LEGACY_MARKET_EVENTS)
    }

    fun channelId(category: NotificationCategory): String = "cat_" + category.id + "_v2"

    /**
     * How loudly each kind arrives, before the reader changes it.
     *
     * High is reserved for the four things that are either money moving or an opportunity closing.
     * Low is for the two streams — news and marketing — that are worth having and not worth a
     * sound. Everything else is the ordinary default.
     */
    private fun NotificationCategory.importance(): Int = when (this) {
        NotificationCategory.NEW_SIGNAL,
        NotificationCategory.COPY_OPENED,
        NotificationCategory.COPY_FAILED,
        NotificationCategory.SECURITY,
        -> NotificationManager.IMPORTANCE_HIGH

        NotificationCategory.NEWS,
        NotificationCategory.MARKETING,
        -> NotificationManager.IMPORTANCE_LOW

        else -> NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun NotificationCategory.group(): String = when (this) {
        NotificationCategory.NEW_SIGNAL,
        NotificationCategory.TARGET_HIT,
        NotificationCategory.STOP_HIT,
        NotificationCategory.SIGNAL_CLOSED,
        NotificationCategory.COPY_OPENED,
        NotificationCategory.COPY_CLOSED,
        NotificationCategory.COPY_FAILED,
        -> GROUP_TRADING

        NotificationCategory.PRICE_ALERT,
        NotificationCategory.WATCHLIST_MOVE,
        NotificationCategory.NEWS,
        NotificationCategory.CALENDAR,
        NotificationCategory.AI_SETUP,
        -> GROUP_MARKET

        NotificationCategory.SECURITY,
        NotificationCategory.ACCOUNT,
        -> GROUP_ACCOUNT

        NotificationCategory.MARKETING -> GROUP_OTHER
    }
}

/** The reader-facing name of a category, shared by the channel and the settings screen. */
@StringRes
fun NotificationCategory.channelNameRes(): Int = when (this) {
    NotificationCategory.NEW_SIGNAL -> R.string.notify_new_signal
    NotificationCategory.TARGET_HIT -> R.string.notify_target_hit
    NotificationCategory.STOP_HIT -> R.string.notify_stop_hit
    NotificationCategory.SIGNAL_CLOSED -> R.string.notify_signal_closed
    NotificationCategory.COPY_OPENED -> R.string.notify_copy_opened
    NotificationCategory.COPY_CLOSED -> R.string.notify_copy_closed
    NotificationCategory.COPY_FAILED -> R.string.notify_copy_failed
    NotificationCategory.PRICE_ALERT -> R.string.notify_price_alert
    NotificationCategory.WATCHLIST_MOVE -> R.string.notify_watchlist_move
    NotificationCategory.NEWS -> R.string.notify_news
    NotificationCategory.CALENDAR -> R.string.notify_calendar
    NotificationCategory.AI_SETUP -> R.string.notify_ai_setup
    NotificationCategory.SECURITY -> R.string.notify_security
    NotificationCategory.ACCOUNT -> R.string.notify_account
    NotificationCategory.MARKETING -> R.string.notify_marketing
}

@StringRes
fun NotificationCategory.channelDescriptionRes(): Int = when (this) {
    NotificationCategory.NEW_SIGNAL -> R.string.notify_new_signal_note
    NotificationCategory.TARGET_HIT -> R.string.notify_target_hit_note
    NotificationCategory.STOP_HIT -> R.string.notify_stop_hit_note
    NotificationCategory.SIGNAL_CLOSED -> R.string.notify_signal_closed_note
    NotificationCategory.COPY_OPENED -> R.string.notify_copy_opened_note
    NotificationCategory.COPY_CLOSED -> R.string.notify_copy_closed_note
    NotificationCategory.COPY_FAILED -> R.string.notify_copy_failed_note
    NotificationCategory.PRICE_ALERT -> R.string.notify_price_alert_note
    NotificationCategory.WATCHLIST_MOVE -> R.string.notify_watchlist_move_note
    NotificationCategory.NEWS -> R.string.notify_news_note
    NotificationCategory.CALENDAR -> R.string.notify_calendar_note
    NotificationCategory.AI_SETUP -> R.string.notify_ai_setup_note
    NotificationCategory.SECURITY -> R.string.notify_security_note
    NotificationCategory.ACCOUNT -> R.string.notify_account_note
    NotificationCategory.MARKETING -> R.string.notify_marketing_note
}
