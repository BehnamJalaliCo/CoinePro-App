@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.content

private fun showJs(tag: String, title: String, body: String, silent: Boolean, onClick: () -> Unit): Unit = js(
    """(function () {
        try {
            if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return;
            var n = new Notification(title, { body: body, tag: tag, silent: silent, dir: 'auto', icon: 'favicon.png' });
            n.onclick = function () { try { window.focus(); } catch (e) {} onClick(); n.close(); };
        } catch (e) {}
    })()""",
)

/**
 * The phone's notifications, as the browser's: a `Notification` while the page is open and the
 * reader has allowed them. A tap focuses the page and hands the notification's intent to the shell
 * ([onOpen]), exactly as the phone's `PendingIntent` opens `MainActivity` with it.
 */
object WebNotifications {
    var onOpen: (android.content.Intent) -> Unit = {}

    fun show(tag: String, notification: android.app.Notification) {
        showJs(tag, notification.title, notification.text, notification.silent) {
            notification.contentIntent?.intent?.let(onOpen)
        }
    }

    fun cancel(tag: String) {}
}
