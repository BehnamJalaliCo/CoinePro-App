@file:Suppress("unused", "UNUSED_PARAMETER")

package com.google.firebase

/**
 * Firebase, absent. A page could receive web push only with a VAPID key and a service worker the
 * backends do not have; with no project configured the phone's own code already takes this path —
 * no app, no token, nothing registered — which is exactly what an unconfigured phone build does.
 */
class FirebaseApp private constructor() {
    companion object {
        fun getApps(context: android.content.Context): List<FirebaseApp> = emptyList()
        fun initializeApp(context: android.content.Context, options: FirebaseOptions): FirebaseApp? = null
        fun initializeApp(context: android.content.Context): FirebaseApp? = null
        fun getInstance(): FirebaseApp = throw IllegalStateException("Default FirebaseApp is not initialized in this process")
    }
}

class FirebaseOptions private constructor() {
    class Builder {
        fun setProjectId(id: String): Builder = this
        fun setApplicationId(id: String): Builder = this
        fun setApiKey(key: String): Builder = this
        fun setGcmSenderId(id: String): Builder = this
        fun build(): FirebaseOptions = FirebaseOptions()
    }
}
