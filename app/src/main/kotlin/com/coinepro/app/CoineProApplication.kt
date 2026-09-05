package com.coinepro.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
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
class CoineProApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    /** The app's own client, so a logo fetch carries the same pins and timeouts as a price. */
    @Inject lateinit var httpClient: OkHttpClient

    /**
     * The one image loader. Disk-cached at two per cent of free space, memory-cached at Coil's
     * default, fetching over the injected OkHttp client. Built lazily on first use so a launch
     * that never shows a remote picture never pays for it.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { httpClient })) }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("images").toOkioPath())
                .maxSizePercent(IMAGE_DISK_CACHE_SHARE)
                .build()
        }
        .crossfade(true)
        .build()

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

/** Two per cent of the cache partition, which on any phone this app runs on is tens of megabytes of logos. */
private const val IMAGE_DISK_CACHE_SHARE = 0.02
