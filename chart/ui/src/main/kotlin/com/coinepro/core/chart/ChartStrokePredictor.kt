package com.coinepro.core.chart

import android.view.MotionEvent
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.input.motionprediction.MotionEventPredictor

/**
 * Where the stylus will be next frame, from where it has just been.
 *
 * `androidx.input`'s [MotionEventPredictor] fits the recent path and answers with the event it
 * expects at the next vsync. The freehand tool draws its live stroke to that point rather than to
 * the last one delivered, which takes a frame of latency out of the ink — the difference, on a
 * 120 Hz tablet with an S Pen, between a line that follows the tip and one that trails it.
 *
 * Fed from a `pointerInteropFilter` because the predictor speaks `MotionEvent`; it never consumes
 * the event, so every Compose handler below it sees exactly what it saw before. [predicted] is
 * null whenever the predictor has nothing to say — a finger, a mouse, the first sample — and the
 * stroke then ends at the real point, as it always did.
 */
internal class ChartStrokePredictor(private val view: View) {
    /**
     * Built on the first event rather than on composition, and only where it can be: the library
     * reads the display's frame time from the view's context, which an off-screen render (a
     * screenshot test, a preview) does not have. Without a predictor the stroke ends at the real
     * point, which is the behaviour before this class existed.
     */
    private var predictor: MotionEventPredictor? = null
    private var attempted = false

    private fun predictorOrNull(): MotionEventPredictor? {
        if (!attempted) {
            attempted = true
            predictor = runCatching { MotionEventPredictor.newInstance(view) }.getOrNull()
        }
        return predictor
    }

    /** The predicted next position in the view's coordinates, or null. */
    var predicted: Offset? = null
        private set

    fun record(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val engine = predictorOrNull() ?: return
                engine.record(event)
                val next = engine.predict()
                predicted = next?.let { Offset(it.x, it.y) }
                next?.recycle()
            }
            else -> predicted = null
        }
    }

    fun reset() {
        predicted = null
    }
}
