@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package androidx.compose.ui.viewinterop

import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity

private fun attachJs(e: JsAny): Unit = js("(function(){ e.style.position = 'fixed'; e.style.zIndex = '10'; document.body.appendChild(e); })()")
private fun placeJs(e: JsAny, x: Double, y: Double, w: Double, h: Double): Unit =
    js("(function(){ e.style.left = x + 'px'; e.style.top = y + 'px'; e.style.width = w + 'px'; e.style.height = h + 'px'; e.style.display = (w > 0 && h > 0) ? 'block' : 'none'; })()")
private fun detachJs(e: JsAny): Unit = js("(function(){ if (e.parentNode) e.parentNode.removeChild(e); })()")
private fun pixelRatioJs(): Double = js("window.devicePixelRatio || 1")

/**
 * A platform view in a page: the view's own element (an `<iframe>`, a `<video>`), fixed over the
 * canvas at exactly the bounds Compose lays this out at, and removed when it leaves composition.
 * Canvas pixels are device pixels; the element is placed in CSS pixels.
 */
@Composable
fun <T : View> AndroidView(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    onReset: ((T) -> Unit)? = null,
    onRelease: (T) -> Unit = {},
    update: (T) -> Unit = {},
) {
    val view = remember { factory(Context.Page) }
    SideEffect { update(view) }
    DisposableEffect(view) {
        view.element?.let(::attachJs)
        onDispose {
            view.element?.let(::detachJs)
            onRelease(view)
        }
    }
    Box(
        modifier.onGloballyPositioned { coordinates ->
            val element = view.element ?: return@onGloballyPositioned
            val ratio = pixelRatioJs()
            val bounds = coordinates.boundsInWindow()
            placeJs(element, bounds.left / ratio, bounds.top / ratio, bounds.width / ratio, bounds.height / ratio)
        },
    )
}

