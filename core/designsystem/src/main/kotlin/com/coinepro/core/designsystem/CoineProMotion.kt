package com.coinepro.core.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Whether continuous motion is allowed on this device right now.
 *
 * Reads the platform animator duration scale, which is what "Remove animations" in accessibility
 * settings and battery saver both drive. A looping indicator must consult this and hold a static
 * frame when it returns false: a permanent animation is exactly what a person who turned
 * animations off asked not to see, and for a vestibular disorder it is not a preference.
 *
 * Previews and screenshot renders report false so captures stay deterministic.
 */
@Composable
fun continuousMotionAllowed(): Boolean {
    if (LocalInspectionMode.current) return false
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
