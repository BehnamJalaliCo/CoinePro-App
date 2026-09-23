@file:Suppress("unused", "UNUSED_PARAMETER")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package android.provider

import android.content.ContentResolver

private fun reducedMotionJs(): Boolean =
    js("(function () { try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; } })()")

/** `Settings.Global.ANIMATOR_DURATION_SCALE` is the reader's «remove animations»; the browser's is `prefers-reduced-motion`. */
object Settings {
    object Global {
        const val ANIMATOR_DURATION_SCALE = "animator_duration_scale"
        const val TRANSITION_ANIMATION_SCALE = "transition_animation_scale"
        fun getFloat(resolver: ContentResolver, name: String, fallback: Float): Float =
            if (name == ANIMATOR_DURATION_SCALE || name == TRANSITION_ANIMATION_SCALE) (if (reducedMotionJs()) 0f else 1f) else fallback
    }
    object System {
        fun getFloat(resolver: ContentResolver, name: String, fallback: Float): Float = fallback
        val DEFAULT_ALARM_ALERT_URI: android.net.Uri get() = android.net.Uri.parse("sound://alarm")
        val DEFAULT_NOTIFICATION_URI: android.net.Uri get() = android.net.Uri.parse("sound://notification")
    }
    object Secure { fun getString(resolver: ContentResolver, name: String): String? = null }
    const val ACTION_BIOMETRIC_ENROLL = "android.settings.BIOMETRIC_ENROLL"
    const val EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED = "android.provider.extra.BIOMETRIC_AUTHENTICATORS_ALLOWED"
    const val ACTION_SECURITY_SETTINGS = "android.settings.SECURITY_SETTINGS"
    const val EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE"
    const val ACTION_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS"
    const val ACTION_APPLICATION_DETAILS_SETTINGS = "android.settings.APPLICATION_DETAILS_SETTINGS"
}
