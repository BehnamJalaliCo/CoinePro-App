@file:Suppress("unused")

package androidx.compose.ui.platform

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

/** `LocalContext`: the page, as the phone's code expects to address the app. See `android.content.Context`. */
val LocalContext = staticCompositionLocalOf<Context> { Context.Page }

/** `LocalConfiguration`: provided by the web shell from the window it is given. */
val LocalConfiguration = staticCompositionLocalOf { Configuration() }

/** No Android view hosts the composition; code that asks gets a stand-in that does nothing. */
val LocalView = staticCompositionLocalOf { android.view.View() }

val LocalLifecycleOwnerCompat = staticCompositionLocalOf<Any?> { null }
