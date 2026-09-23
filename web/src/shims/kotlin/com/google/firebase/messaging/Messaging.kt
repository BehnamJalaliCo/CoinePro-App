@file:Suppress("unused", "UNUSED_PARAMETER")

package com.google.firebase.messaging

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks

class FirebaseMessaging private constructor() {
    val token: Task<String> get() = Tasks.forException(IllegalStateException("No push service in a browser"))
    fun deleteToken(): Task<Void?> = Tasks.forResult(null)
    var isAutoInitEnabled: Boolean = false
    companion object { fun getInstance(): FirebaseMessaging = FirebaseMessaging() }
}

class Void

/** The phone's push receiver is kept and never called: a browser has no push channel to call it. */
abstract class FirebaseMessagingService : android.app.Application() {
    open fun onMessageReceived(message: RemoteMessage) {}
    open fun onNewToken(token: String) {}
}

class RemoteMessage internal constructor() {
    val data: Map<String, String> get() = emptyMap()
    val notification: Notification? get() = null
    val messageId: String? get() = null
    val sentTime: Long get() = 0L
    class Notification internal constructor() {
        val title: String? get() = null
        val body: String? get() = null
    }
}
