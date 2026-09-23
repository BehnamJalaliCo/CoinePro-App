@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package androidx.camera.view

import android.content.Context
import android.view.View

private fun videoJs(): JsAny = js("(function(){ var v = document.createElement('video'); v.autoplay = true; v.muted = true; v.setAttribute('playsinline', ''); v.style.objectFit = 'cover'; v.style.background = '#000'; return v; })()")

/** The camera's picture, as a `<video>` element the camera stream is attached to. */
class PreviewView(context: Context) : View(context) {
    val video: JsAny = videoJs()
    override val element: JsAny get() = video
    var implementationMode: ImplementationMode = ImplementationMode.PERFORMANCE
    var scaleType: ScaleType = ScaleType.FILL_CENTER
    val surfaceProvider: androidx.camera.core.Preview.SurfaceProvider = androidx.camera.core.Preview.SurfaceProvider(this)
    enum class ImplementationMode { PERFORMANCE, COMPATIBLE }
    enum class ScaleType { FILL_START, FILL_CENTER, FILL_END, FIT_START, FIT_CENTER, FIT_END }
}
