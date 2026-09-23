@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.content

private fun notificationStateJs(): String = js("(typeof Notification === 'undefined') ? 'denied' : Notification.permission")
private fun requestNotificationsJs(done: (Boolean) -> Unit): Unit = js(
    "(function(){ if (typeof Notification === 'undefined') { done(false); return; } Notification.requestPermission().then(function (p) { done(p === 'granted'); }, function () { done(false); }); })()",
)

/**
 * Android permissions, as a page has them. Notifications are the browser's own permission and are
 * asked for; the camera and the photo library are asked for by the browser at the moment they are
 * used, so here they count as granted; nothing else a page can hold is asked for at all.
 */
object WebPermissions {
    fun granted(permission: String): Boolean = when {
        permission.endsWith("POST_NOTIFICATIONS") -> notificationStateJs() == "granted"
        permission.endsWith("CAMERA") || permission.contains("MEDIA_") || permission.contains("STORAGE") -> true
        else -> false
    }

    fun request(permission: String, done: (Boolean) -> Unit) {
        if (permission.endsWith("POST_NOTIFICATIONS")) requestNotificationsJs(done) else done(granted(permission))
    }
}
