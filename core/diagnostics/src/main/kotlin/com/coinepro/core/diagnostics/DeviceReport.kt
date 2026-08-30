package com.coinepro.core.diagnostics

import android.content.Context
import android.os.Build
import android.os.StatFs

/**
 * The handset the app is running on, as a developer would need it described.
 *
 * ### Why an export without this is close to useless
 *
 * Almost every report that ever reached this project was "it does not work on my phone", and almost
 * every one of them turned on something in this list: an Android version whose behaviour changed, a
 * manufacturer's aggressive background killer, a locale that reorders a line, a device with no room
 * left to write a cache. A stack trace tells a developer what threw; this tells them where. Sending
 * the first without the second is sending half of a bug report and waiting a day for the reply that
 * asks for the other half.
 *
 * ### Nothing here identifies a person
 *
 * Model, manufacturer, Android release, ABI, locale, memory and free space — a description of a
 * class of device, not of its owner. No advertising id, no serial, no account, no IMEI: none of it
 * would help anybody fix anything, all of it would make this file something the app had no business
 * producing. The install id, which is the one value that does identify this install, is masked by
 * [maskSecret] wherever the panel and the export show it.
 */
data class DeviceReport(
    val manufacturer: String = ABSENT,
    val model: String = ABSENT,
    /** The marketing version — "14" — which is what a bug report is written in terms of. */
    val androidRelease: String = ABSENT,
    /** The API level, which is what the code branches on. Both, because they answer differently. */
    val sdkInt: Int = 0,
    /** The primary ABI, for the one class of bug that is a native library built for the wrong one. */
    val abi: String = ABSENT,
    /** The locale actually in force, which is not always the one the app asked for. */
    val locale: String = ABSENT,
    val layoutDirectionRtl: Boolean = true,
    /** Megabytes. The JVM heap ceiling, which is what an OutOfMemoryError is measured against. */
    val maxHeapMegabytes: Long = 0,
    val usedHeapMegabytes: Long = 0,
    /** Megabytes free in the app's own storage — a cache that cannot be written fails silently. */
    val freeStorageMegabytes: Long = 0,
    /** Bytes the persisted log is currently holding, so its cost is visible beside the rest. */
    val logBytes: Long = 0,
) {
    companion object {

        /**
         * Read at the moment the panel opens.
         *
         * Not cached in a singleton: heap use and free storage are the two fields that change while
         * the app runs, and they are also the two that a report about "it got slow" turns on. A
         * value captured at process start would be a description of a machine in a state nobody is
         * asking about.
         */
        fun capture(context: Context, logBytes: Long = 0): DeviceReport {
            val runtime = Runtime.getRuntime()
            val configuration = context.resources.configuration
            val locale = configuration.locales.takeIf { it.size() > 0 }?.get(0)
            val free = runCatching {
                val stat = StatFs(context.filesDir.absolutePath)
                stat.availableBytes / MEGABYTE
            }.getOrDefault(0L)

            return DeviceReport(
                manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { ABSENT },
                model = Build.MODEL.orEmpty().ifBlank { ABSENT },
                androidRelease = Build.VERSION.RELEASE.orEmpty().ifBlank { ABSENT },
                sdkInt = Build.VERSION.SDK_INT,
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ABSENT,
                locale = locale?.toLanguageTag() ?: ABSENT,
                layoutDirectionRtl = configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL,
                maxHeapMegabytes = runtime.maxMemory() / MEGABYTE,
                usedHeapMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / MEGABYTE,
                freeStorageMegabytes = free,
                logBytes = logBytes,
            )
        }

        private const val MEGABYTE = 1024L * 1024L
    }
}
