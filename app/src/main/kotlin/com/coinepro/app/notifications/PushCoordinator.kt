package com.coinepro.app.notifications

import android.content.Context
import com.coinepro.app.BuildConfig
import com.coinepro.core.model.MarketPlatform
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
    /**
     * Every platform's device registry, not just the one on screen.
     *
     * A push token belongs to the install, and both backends need it to reach this device — a
     * token registered with one is meaningless to the other. Registering only the active platform
     * would silence the other one the moment the reader switched away from it.
     */
    private val controllers: Map<MarketPlatform, @JvmSuppressWildcards NotificationController>,
    private val scope: CoroutineScope,
) {
    @Volatile private var lastToken: String? = null

    fun registerCurrentToken() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(::registerToken)
    }

    fun registerToken(token: String) {
        if (token.isBlank()) return
        lastToken = token
        scope.launch {
            // Each independently: one backend refusing the token is no reason for the other not to
            // hold it, and a shared failure would silence both.
            controllers.values.forEach { controller ->
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

    suspend fun unregisterCurrentToken() {
        val token = lastToken ?: return
        // Signing out means signing out everywhere. A token left registered on the other platform
        // keeps delivering notifications for an account nobody is signed in to.
        controllers.values.forEach { controller ->
            runCatching { controller.unregisterDevice(token) }
        }
        lastToken = null
    }
}
