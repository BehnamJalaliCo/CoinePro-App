@file:Suppress("unused", "UNUSED_PARAMETER")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package android.app

import android.content.Context
import android.content.Intent

private fun reloadJs(): Unit = js("window.location.reload()")

/** The page is the one activity. `recreate` is what Android does to apply a new language: a reload. */
open class Activity : Context() {
    val window: android.view.Window = android.view.Window()
    open fun recreate() = reloadJs()
    open fun finish() {}
    val isFinishing: Boolean get() = false
    fun setResult(code: Int, data: Intent? = null) {}
    val intent: Intent get() = Intent()
    companion object {
        const val RESULT_OK = -1
        const val RESULT_CANCELED = 0
    }
}

open class Application : Context()

/** Where a notification's tap goes: an intent the page delivers to itself when the tap arrives. */
class PendingIntent private constructor(internal val intent: Intent?) {
    fun cancel() {}
    companion object {
        const val FLAG_UPDATE_CURRENT = 0x08000000
        const val FLAG_IMMUTABLE = 0x04000000
        const val FLAG_MUTABLE = 0x02000000
        const val FLAG_CANCEL_CURRENT = 0x10000000
        const val FLAG_ONE_SHOT = 0x40000000
        fun getActivity(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(intent)
        fun getBroadcast(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(intent)
        fun getService(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(intent)
    }
}

class NotificationChannel(val id: String, var name: CharSequence, val importance: Int) {
    var description: String? = null
    var group: String? = null
    var lightColor: Int = 0
    fun setShowBadge(show: Boolean) {}
    fun enableLights(on: Boolean) {}
    fun enableVibration(on: Boolean) {}
    fun setVibrationPattern(pattern: LongArray?) {}
    fun setSound(uri: android.net.Uri?, attributes: android.media.AudioAttributes?) {}
    fun setBypassDnd(bypass: Boolean) {}
    var lockscreenVisibility: Int = 0
}

class NotificationChannelGroup(val id: String, val name: CharSequence) {
    var description: String? = null
}

class Notification internal constructor(
    internal val title: String,
    internal val text: String,
    internal val tag: String?,
    internal val contentIntent: PendingIntent?,
    internal val silent: Boolean,
) {
    var flags: Int = 0
    companion object {
        const val FLAG_INSISTENT = 4
        const val FLAG_AUTO_CANCEL = 16
        const val CATEGORY_ALARM = "alarm"
        const val CATEGORY_REMINDER = "reminder"
        const val CATEGORY_RECOMMENDATION = "recommendation"
        const val CATEGORY_STATUS = "status"
        const val VISIBILITY_PUBLIC = 1
        const val VISIBILITY_PRIVATE = 0
        const val DEFAULT_ALL = -1
    }
}

class NotificationManager internal constructor() {
    private val channels = LinkedHashMap<String, NotificationChannel>()
    fun createNotificationChannel(channel: NotificationChannel) { channels[channel.id] = channel }
    fun createNotificationChannels(list: List<NotificationChannel>) = list.forEach(::createNotificationChannel)
    fun createNotificationChannelGroup(group: NotificationChannelGroup) {}
    fun deleteNotificationChannel(id: String) { channels.remove(id) }
    fun getNotificationChannel(id: String): NotificationChannel? = channels[id]
    val notificationChannels: List<NotificationChannel> get() = channels.values.toList()
    fun notify(id: Int, notification: Notification) = com.coinepro.web.content.WebNotifications.show(id.toString(), notification)
    fun notify(tag: String?, id: Int, notification: Notification) = com.coinepro.web.content.WebNotifications.show((tag ?: "") + id, notification)
    fun cancel(id: Int) = com.coinepro.web.content.WebNotifications.cancel(id.toString())
    fun cancel(tag: String?, id: Int) = com.coinepro.web.content.WebNotifications.cancel((tag ?: "") + id)
    fun cancelAll() {}
    fun areNotificationsEnabled(): Boolean = com.coinepro.web.content.WebPermissions.granted("android.permission.POST_NOTIFICATIONS")
    val currentInterruptionFilter: Int get() = INTERRUPTION_FILTER_ALL
    companion object {
        const val IMPORTANCE_NONE = 0
        const val IMPORTANCE_MIN = 1
        const val IMPORTANCE_LOW = 2
        const val IMPORTANCE_DEFAULT = 3
        const val IMPORTANCE_HIGH = 4
        const val IMPORTANCE_MAX = 5
        const val INTERRUPTION_FILTER_ALL = 1
        const val INTERRUPTION_FILTER_PRIORITY = 2
        const val INTERRUPTION_FILTER_NONE = 3
        const val INTERRUPTION_FILTER_ALARMS = 4
        internal val instance = NotificationManager()
    }
}

class PictureInPictureParams private constructor() {
    class Builder { fun setAspectRatio(r: android.util.Rational?): Builder = this; fun build(): PictureInPictureParams = PictureInPictureParams() }
}
