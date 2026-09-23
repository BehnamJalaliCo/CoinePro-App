@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.core.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context

/** The phone's notification builder, producing what the browser's `Notification` shows. */
object NotificationCompat {
    const val PRIORITY_MIN = -2
    const val PRIORITY_LOW = -1
    const val PRIORITY_DEFAULT = 0
    const val PRIORITY_HIGH = 1
    const val PRIORITY_MAX = 2
    const val CATEGORY_ALARM = "alarm"
    const val CATEGORY_REMINDER = "reminder"
    const val CATEGORY_RECOMMENDATION = "recommendation"
    const val CATEGORY_STATUS = "status"
    const val CATEGORY_MESSAGE = "msg"
    const val VISIBILITY_PUBLIC = 1
    const val VISIBILITY_PRIVATE = 0
    const val DEFAULT_ALL = -1

    abstract class Style
    class BigTextStyle : Style() {
        internal var text: CharSequence? = null
        fun bigText(text: CharSequence?): BigTextStyle = apply { this.text = text }
        fun setBigContentTitle(title: CharSequence?): BigTextStyle = this
        fun setSummaryText(text: CharSequence?): BigTextStyle = this
    }
    class BigPictureStyle : Style() {
        fun bigPicture(bitmap: android.graphics.Bitmap?): BigPictureStyle = this
        fun bigLargeIcon(bitmap: android.graphics.Bitmap?): BigPictureStyle = this
        fun setSummaryText(text: CharSequence?): BigPictureStyle = this
    }
    class InboxStyle : Style() {
        fun addLine(line: CharSequence?): InboxStyle = this
        fun setSummaryText(text: CharSequence?): InboxStyle = this
    }

    class Action(val icon: Int, val title: CharSequence?, val actionIntent: PendingIntent?) {
        class Builder(private val icon: Int, private val title: CharSequence?, private val intent: PendingIntent?) {
            fun build(): Action = Action(icon, title, intent)
        }
    }

    class Builder(private val context: Context, private val channelId: String) {
        constructor(context: Context) : this(context, "")
        private var title: CharSequence = ""
        private var text: CharSequence = ""
        private var style: Style? = null
        private var intent: PendingIntent? = null
        private var tag: String? = null
        private var silent = false
        fun setSmallIcon(icon: Int): Builder = this
        fun setLargeIcon(bitmap: android.graphics.Bitmap?): Builder = this
        fun setContentTitle(title: CharSequence?): Builder = apply { this.title = title ?: "" }
        fun setContentText(text: CharSequence?): Builder = apply { this.text = text ?: "" }
        fun setSubText(text: CharSequence?): Builder = this
        fun setStyle(style: Style?): Builder = apply { this.style = style }
        fun setContentIntent(intent: PendingIntent?): Builder = apply { this.intent = intent }
        fun setDeleteIntent(intent: PendingIntent?): Builder = this
        fun setAutoCancel(auto: Boolean): Builder = this
        fun setPriority(priority: Int): Builder = this
        fun setCategory(category: String?): Builder = this
        fun setColor(color: Int): Builder = this
        fun setColorized(colorized: Boolean): Builder = this
        fun setGroup(group: String?): Builder = apply { tag = group }
        fun setGroupSummary(summary: Boolean): Builder = this
        fun setOnlyAlertOnce(only: Boolean): Builder = this
        fun setSilent(silent: Boolean): Builder = apply { this.silent = silent }
        fun setWhen(time: Long): Builder = this
        fun setShowWhen(show: Boolean): Builder = this
        fun setTicker(ticker: CharSequence?): Builder = this
        fun setVisibility(visibility: Int): Builder = this
        fun setDefaults(defaults: Int): Builder = this
        fun setVibrate(pattern: LongArray?): Builder = this
        fun setSound(uri: android.net.Uri?): Builder = this
        fun setOngoing(ongoing: Boolean): Builder = this
        fun setTimeoutAfter(ms: Long): Builder = this
        fun setNumber(n: Int): Builder = this
        fun setFullScreenIntent(intent: PendingIntent?, high: Boolean): Builder = this
        fun addAction(icon: Int, title: CharSequence?, intent: PendingIntent?): Builder = this
        fun addAction(action: Action): Builder = this
        fun setProgress(max: Int, progress: Int, indeterminate: Boolean): Builder = this
        fun build(): Notification {
            val body = (style as? BigTextStyle)?.text ?: text
            return Notification(title.toString(), body.toString(), tag, intent, silent)
        }
    }
}

class NotificationManagerCompat private constructor() {
    fun notify(id: Int, notification: Notification) = android.app.NotificationManager.instance.notify(id, notification)
    fun notify(tag: String?, id: Int, notification: Notification) = android.app.NotificationManager.instance.notify(tag, id, notification)
    fun cancel(id: Int) = android.app.NotificationManager.instance.cancel(id)
    fun cancel(tag: String?, id: Int) = android.app.NotificationManager.instance.cancel(tag, id)
    fun areNotificationsEnabled(): Boolean = android.app.NotificationManager.instance.areNotificationsEnabled()
    fun createNotificationChannel(channel: android.app.NotificationChannel) = android.app.NotificationManager.instance.createNotificationChannel(channel)
    companion object {
        private val instance = NotificationManagerCompat()
        fun from(context: Context): NotificationManagerCompat = instance
        const val IMPORTANCE_HIGH = 4
        const val IMPORTANCE_DEFAULT = 3
    }
}

object ActivityCompat {
    fun requestPermissions(activity: android.app.Activity, permissions: Array<String>, code: Int) {}
    fun shouldShowRequestPermissionRationale(activity: android.app.Activity, permission: String): Boolean = false
}
