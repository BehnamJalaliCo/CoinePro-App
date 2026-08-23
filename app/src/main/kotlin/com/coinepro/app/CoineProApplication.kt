package com.coinepro.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.coinepro.app.notifications.NotificationChannels
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CoineProApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        initializeFirebaseIfConfigured()
        NotificationChannels.ensure(this)
    }

    private fun initializeFirebaseIfConfigured() {
        val values = listOf(
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_APPLICATION_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_SENDER_ID,
        )
        if (values.any { it.isBlank() } || FirebaseApp.getApps(this).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        FirebaseApp.initializeApp(this, options)
    }
}
