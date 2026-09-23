@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.web.content

private fun vibrateJs(ms: Int): Boolean = js("(function(){ try { return !!(navigator.vibrate && navigator.vibrate(ms)); } catch (e) { return false; } })()")

/** The phone's haptic constants as the browser's `navigator.vibrate`, where the device has one. */
object WebHaptics {
    fun perform(constant: Int): Boolean = vibrateJs(if (constant == 0) 25 else 8)
}
