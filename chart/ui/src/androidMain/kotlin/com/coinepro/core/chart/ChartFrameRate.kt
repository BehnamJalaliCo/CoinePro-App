package com.coinepro.core.chart

import android.os.Build
import android.view.Surface
import android.view.SurfaceControl
import android.view.View
import androidx.annotation.RequiresApi
import java.util.WeakHashMap

/**
 * The frame-rate hint: while a finger, a stylus or a fling moves the picture the chart asks the
 * display for its highest rate, and lets go at rest, so a 120 Hz panel is spent on the gesture
 * and not on the idle.
 *
 * Two platform paths, both the platform's own frame-rate API:
 *
 * * **Android 15+**: `View.setRequestedFrameRate` with the `HIGH` category, which the view
 *   hierarchy folds into the window's vote. The one that is guaranteed to count.
 * * **Android 12–14**: `SurfaceControl.Transaction.setFrameRate` — the same call `Surface.setFrameRate`
 *   makes — on a child surface parented under the window's `AttachedSurfaceControl`, with the
 *   display's highest refresh rate while interacting and `0` (no preference) at rest. A vote
 *   rather than a command: the compositor weighs it with every other layer's, which is what the
 *   API promises and no more.
 *
 * Below Android 12 there is no frame-rate API and the display runs as it is.
 */
internal object ChartFrameRate {

    private val surfaces = WeakHashMap<View, SurfaceControl>()

    fun request(view: View, high: Boolean) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                view.requestedFrameRate =
                    if (high) View.REQUESTED_FRAME_RATE_CATEGORY_HIGH else View.REQUESTED_FRAME_RATE_CATEGORY_NO_PREFERENCE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> vote(view, high)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun vote(view: View, high: Boolean) {
        val root = view.rootSurfaceControl ?: return
        val surface = surfaces[view] ?: SurfaceControl.Builder().setName("coinepro-chart-frame-rate").build().also { child ->
            root.buildReparentTransaction(child)?.apply()
            surfaces[view] = child
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    surfaces.remove(v)?.release()
                    v.removeOnAttachStateChangeListener(this)
                }
            })
        }
        if (!surface.isValid) return
        val rate = if (high) highestRefreshRate(view) else NO_PREFERENCE
        SurfaceControl.Transaction()
            .setFrameRate(surface, rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
            .apply()
    }

    /** The fastest mode the display offers — 120 on a 120 Hz panel, 60 where that is all there is. */
    private fun highestRefreshRate(view: View): Float {
        val display = view.display ?: return NO_PREFERENCE
        return display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
    }

    /** `Surface.setFrameRate`'s «no preference». */
    private const val NO_PREFERENCE = 0f
}
