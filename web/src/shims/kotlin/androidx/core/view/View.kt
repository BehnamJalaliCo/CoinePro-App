@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.core.view

/** The system bars a page does not have; their icon colour is the browser's to choose. */
object WindowCompat {
    fun getInsetsController(window: android.view.Window, view: android.view.View): WindowInsetsControllerCompat = WindowInsetsControllerCompat()
    fun setDecorFitsSystemWindows(window: android.view.Window, fits: Boolean) {}
}

class WindowInsetsControllerCompat {
    var isAppearanceLightStatusBars: Boolean = false
    var isAppearanceLightNavigationBars: Boolean = false
    var systemBarsBehavior: Int = 0
    fun hide(types: Int) {}
    fun show(types: Int) {}
}
