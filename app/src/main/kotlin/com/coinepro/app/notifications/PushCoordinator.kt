package com.coinepro.app.notifications

import android.content.Context
import com.coinepro.app.BuildConfig
import com.coinepro.core.notifications.NotificationController
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Singleton
class PushCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: NotificationController,
    private val scope: CoroutineScope,
) {
    fun registerCurrentToken() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(::registerToken)
    }

    fun registerToken(token: String) {
        if (token.isBlank()) return
        scope.launch {
            runCatching {
                controller.registerDevice(
                    token = token,
                    appVersion = BuildConfig.VERSION_NAME,
                    locale = Locale.getDefault().toLanguageTag(),
                )
            }
        }
    }
}
