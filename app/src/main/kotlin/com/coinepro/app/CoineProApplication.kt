package com.coinepro.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.coinepro.app.notifications.NotificationChannels
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.CrashReport
import com.coinepro.core.diagnostics.LogTag
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CoineProApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Field-injected rather than taken in the constructor, because an `Application` has none.
     *
     * Hilt populates this before `onCreate` returns, which is early enough for the crash handler
     * below — that is the one thing here that must be installed before anything else can throw.
     */
    @Inject lateinit var appLog: AppLog

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // First, before anything else can throw. A crash during start-up is the one this app had
        // no way to see: the process dies, the launcher restarts it at the first screen, and the
        // reader reports "it crashed" with nothing to go on.
        CrashReport(this, appLog).install()
        appLog.info(
            tag = LogTag.LIFECYCLE,
            message = "process start",
            fields = mapOf("version" to BuildConfig.VERSION_NAME, "debug" to BuildConfig.DEBUG.toString()),
        )
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
        if (values.any { it.isBlank() }) {
            // Worth a line, because it is invisible otherwise: with no Firebase configuration the
            // app runs and push simply never arrives, which reads as a broken server.
            appLog.warn(LogTag.LIFECYCLE, "Firebase not configured; push is off")
            return
        }
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
            .build()
        FirebaseApp.initializeApp(this, options)
    }
}
