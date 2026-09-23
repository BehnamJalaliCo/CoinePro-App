@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:Suppress("unused", "UNUSED_PARAMETER")

package android.view

open class View(context: android.content.Context? = null) {
    val context: android.content.Context get() = android.content.Context.Page
    var keepScreenOn: Boolean = false
    var layoutParams: ViewGroup.LayoutParams? = null
    var visibility: Int = VISIBLE
    /** The page element this view is, when it is one; `AndroidView` places it over the canvas. */
    open val element: JsAny? get() = null
    open fun setBackgroundColor(color: Int) {}
    fun performHapticFeedback(constant: Int): Boolean = com.coinepro.web.content.WebHaptics.perform(constant)
    fun performHapticFeedback(constant: Int, flags: Int): Boolean = com.coinepro.web.content.WebHaptics.perform(constant)
    fun post(action: () -> Unit): Boolean { action(); return true }
    val rootView: View get() = this
    val isInEditMode: Boolean get() = false
    val width: Int get() = 0
    val height: Int get() = 0
    companion object {
        const val LAYOUT_DIRECTION_LTR = 0
        const val LAYOUT_DIRECTION_RTL = 1
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
    }
}

open class ViewGroup(context: android.content.Context? = null) : View(context) {
    open class LayoutParams(val width: Int, val height: Int) {
        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
}

object HapticFeedbackConstants {
    const val CLOCK_TICK = 4; const val CONFIRM = 16; const val REJECT = 17; const val KEYBOARD_TAP = 3
    const val LONG_PRESS = 0; const val VIRTUAL_KEY = 1; const val CONTEXT_CLICK = 6; const val GESTURE_START = 12
    const val GESTURE_END = 13; const val SEGMENT_TICK = 26; const val SEGMENT_FREQUENT_TICK = 27; const val TOGGLE_ON = 21
    const val TOGGLE_OFF = 22; const val DRAG_START = 25; const val TEXT_HANDLE_MOVE = 9
}

class MotionEvent
class Window {
    val decorView: View = View()
    var attributes: WindowManager.LayoutParams = WindowManager.LayoutParams()
    fun addFlags(flags: Int) {}
    fun clearFlags(flags: Int) {}
    fun setFlags(flags: Int, mask: Int) {}
}
class WindowManager { class LayoutParams { var preferredDisplayModeId: Int = 0; companion object { const val FLAG_SECURE = 0x2000; const val FLAG_KEEP_SCREEN_ON = 0x80 } } }
