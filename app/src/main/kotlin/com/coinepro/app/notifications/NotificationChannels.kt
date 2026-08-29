package com.coinepro.app.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import com.coinepro.app.R
import com.coinepro.core.notifications.AlertChannel
import com.coinepro.core.notifications.AlertSound
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

    /**
     * A price alert the reader has deliberately made louder than the rest of the phone.
     *
     * ### Why a second channel rather than a bumped version of the first
     *
     * [channelVersion] exists to *correct* a channel whose shipped default was wrong, and correcting
     * it costs every reader whatever they had customised. This is not a correction: both channels are
     * right, and they are for different alerts. One review of this category of app puts it plainly —
     * *a beep is not enough to alert someone busy at work* — and
     * [com.coinepro.core.notifications.AlertSound] is the reader's own answer to it, per alert. The
     * two must be separately silenceable, because somebody who turns down the ordinary price alerts
     * has not asked to lose the one they set for the level they have been waiting three weeks for.
     *
     * The sound goes out on the **alarm** usage, which is the only mechanism Android offers for
     * "louder than a notification": it plays at the alarm volume, which is the volume people leave up.
     * That is a real escalation and it is why nothing reaches this channel unless the reader pushed
     * that one alert past [com.coinepro.core.notifications.AlertSound.LOUD_THRESHOLD] themselves.
     */
    const val PRICE_ALERT_LOUD = "price_alert_loud_v1"

    /**
     * A price alert that buzzes and does not make a sound.
     *
     * The combination somebody in a meeting wants, and one the app cannot produce on the ordinary
     * channel: from Android 8 both sound and vibration belong to the channel, so a per-alert choice
     * of «vibrate, no sound» has to be a channel of its own. Doing it from the app instead would mean
     * asking for the vibrate permission in order to reproduce something the notification manager
     * already does on the app's behalf.
     */
    const val PRICE_ALERT_VIBRATE = "price_alert_vibrate_v1"

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

        manager.createNotificationChannel(
            NotificationChannel(
                PRICE_ALERT_LOUD,
                context.getString(R.string.channel_price_alert_loud),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                group = GROUP_MARKET
                description = context.getString(R.string.channel_price_alert_loud_note)
                enableVibration(true)
                setSound(
                    Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                PRICE_ALERT_VIBRATE,
                context.getString(R.string.channel_price_alert_vibrate),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                group = GROUP_MARKET
                description = context.getString(R.string.channel_price_alert_vibrate_note)
                enableVibration(true)
                // Null, not the default: this channel's whole purpose is the buzz without the sound.
                setSound(null, null)
            },
        )

        // Removed rather than left to rot. See `supersededChannelIds`.
        supersededChannelIds().forEach(manager::deleteNotificationChannel)
    }

    fun channelId(category: NotificationCategory): String =
        "cat_" + category.id + "_v" + category.channelVersion()

    /**
     * Which generation of this category's channel to create, and why one of them is not 2.
     *
     * ### Android will not change a channel that already exists
     *
     * `createNotificationChannel` on an existing id updates the name and the description and
     * **silently ignores the importance** — deliberately, because importance is the reader's to
     * set once they have set it, and an app that could raise its own volume would raise it. The
     * only way to ship a corrected default is a new id, and the only cost of a new id is that
     * whatever the reader had customised on the old one does not follow.
     *
     * ### So it is per category, not one suffix for all of them
     *
     * Bumping a shared suffix would reset every category at once — sounds, vibration, the lot —
     * to fix the default on one of them. This way exactly the corrected channel is replaced, and
     * [supersededChannelIds] deletes the version it replaces so the reader is not left with two
     * entries for one thing and no way to tell which is live.
     *
     * A reader who had *lowered* the price alert channel loses that choice, and that is the honest
     * trade: the alternative is leaving everybody else's alerts silent to preserve one person's
     * setting, and they can lower it again in two taps from the notification itself.
     */
    private fun NotificationCategory.channelVersion(): Int = when (this) {
        // 3: shipped at DEFAULT importance, which delivered it silently to the shade — the most
        // common form of "I never got my alert" there is. See `importance()`.
        NotificationCategory.PRICE_ALERT -> 3
        else -> 2
    }

    /**
     * Channel ids this build has replaced, so they can be removed rather than left to rot.
     *
     * A stale channel stays in the system's list for ever with nothing ever posted to it, and a
     * reader looking for the switch that stops a notification will find the one that does nothing.
     */
    private fun supersededChannelIds(): List<String> = buildList {
        add(LEGACY_MARKET_EVENTS)
        NotificationCategory.entries.forEach { category ->
            for (version in 1 until category.channelVersion()) {
                add("cat_" + category.id + "_v" + version)
            }
        }
    }

    /**
     * How loudly each kind arrives, before the reader changes it.
     *
     * High is reserved for the things that are either money moving or an opportunity closing. Low
     * is for the two streams — news and marketing — that are worth having and not worth a sound.
     * Everything else is the ordinary default.
     *
     * **[NotificationCategory.PRICE_ALERT] belongs in the first group and was in the third.** It
     * is the one notification in this whole list that the reader *asked for by name*: they opened
     * a screen, chose a market, typed a number and said tell me. Every other entry here is the app
     * deciding something is worth an interruption; this one is the reader having decided. Delivering
     * it at the default importance meant it arrived silently in the shade — which, in a corpus of
     * reviews of this category of app, is the single most common form of "I never got my alert".
     * The alert fired correctly and nobody was told.
     */
    private fun NotificationCategory.importance(): Int = when (this) {
        NotificationCategory.NEW_SIGNAL,
        NotificationCategory.COPY_OPENED,
        NotificationCategory.COPY_FAILED,
        NotificationCategory.SECURITY,
        NotificationCategory.PRICE_ALERT,
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
        NotificationCategory.ANNOUNCEMENT,
        NotificationCategory.CALENDAR,
        NotificationCategory.AI_SETUP,
        -> GROUP_MARKET

        NotificationCategory.SECURITY,
        NotificationCategory.ACCOUNT,
        -> GROUP_ACCOUNT

        NotificationCategory.MARKETING -> GROUP_OTHER
    }
}

/**
 * Which of the three price-alert channels one fired alert arrives on.
 *
 * ### Why this is a free function rather than a private method on the deliverer
 *
 * It is the whole of the [com.coinepro.core.notifications.AlertSound] feature's observable effect,
 * and it is pure: a set of channels and a number in, a channel id out. As a private method it could
 * only be exercised by posting a real notification on a device, which is precisely how the loud
 * channel came to be dead code — the level had no control, `isLoud` was never true, and nothing
 * anywhere could have noticed. Out here it is three lines and a unit test.
 *
 * The loud channel is reached only where the reader pushed *this* alert past
 * [AlertSound.LOUD_THRESHOLD] themselves and asked for sound at all; it plays on the alarm output, which is a real escalation and not something to infer.
 * Vibrate-without-sound needs its own channel because from Android 8 both belong to the channel
 * rather than to the notification, and there is no other way to express the combination.
 */
fun priceAlertChannelId(channels: Set<AlertChannel>, soundLevel: Float): String = when {
    AlertChannel.SOUND in channels && AlertSound.isLoud(soundLevel) ->
        NotificationChannels.PRICE_ALERT_LOUD

    AlertChannel.SOUND !in channels && AlertChannel.VIBRATE in channels ->
        NotificationChannels.PRICE_ALERT_VIBRATE

    else -> NotificationChannels.channelId(NotificationCategory.PRICE_ALERT)
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
    NotificationCategory.ANNOUNCEMENT -> R.string.notify_announcement
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
    NotificationCategory.ANNOUNCEMENT -> R.string.notify_announcement_note
    NotificationCategory.CALENDAR -> R.string.notify_calendar_note
    NotificationCategory.AI_SETUP -> R.string.notify_ai_setup_note
    NotificationCategory.SECURITY -> R.string.notify_security_note
    NotificationCategory.ACCOUNT -> R.string.notify_account_note
    NotificationCategory.MARKETING -> R.string.notify_marketing_note
}
