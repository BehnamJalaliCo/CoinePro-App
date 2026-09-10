package com.coinepro.core.chart

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedDecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import kotlin.math.abs
import kotlin.math.min

/**
 * The curve a chart flick coasts on: Compose's own [exponentialDecay], tuned to the brief.
 *
 * Velocity decays as `e^(−f·t)` with `f = 4.2 × frictionMultiplier`; the multiplier below puts
 * `f` at 3.8 per second, so a hard flick of 4 000 px/s coasts to the cut-off in ln(200)/3.8 ≈
 * 1.4 s and an ordinary one of 2 000 px/s in ≈ 1.2 s — the brief's «about 1.2 s». Compose's
 * default multiplier of 1.35 gave 0.9 s, which read as the chart stopping short. `KineticScroll`
 * in `:chart-core` is the same curve written out for the JVM tests; this is the spec the chart
 * actually flings on.
 */
internal const val FLING_FRICTION_MULTIPLIER = 3.8f / 4.2f

internal fun chartFlingSpec(): DecayAnimationSpec<Float> =
    exponentialDecay(frictionMultiplier = FLING_FRICTION_MULTIPLIER, absVelocityThreshold = KineticScroll.MIN_VELOCITY)

/**
 * A fling in flight, read off [chartFlingSpec] frame by frame.
 *
 * The position is read off the curve at the elapsed time rather than integrated per frame, so a
 * dropped frame lands the chart where it would have been, not further along. The first tick
 * establishes the clock and moves nothing; a velocity under the cut-off starts nothing at all,
 * so a slow drag that ends with the finger almost still does not twitch after the release.
 */
internal class ChartFling(
    private val spec: VectorizedDecayAnimationSpec<AnimationVector1D> = chartFlingSpec().vectorize(Float.VectorConverter),
) {
    private var velocity = 0f
    private var durationNanos = 0L
    private var startedAt = -1L
    private var covered = 0f

    var isRunning: Boolean = false
        private set

    /** Begin a fling at [velocity] pixels per second, positive meaning the content moves right. */
    fun start(velocity: Float) {
        if (!velocity.isFinite() || abs(velocity) < KineticScroll.MIN_VELOCITY) {
            stop()
            return
        }
        this.velocity = velocity
        durationNanos = spec.getDurationNanos(ORIGIN, AnimationVector1D(velocity))
        startedAt = -1L
        covered = 0f
        isRunning = durationNanos > 0L
    }

    /** How far the content should move since the last tick, in signed pixels. */
    fun tick(nowNanos: Long): Float {
        if (!isRunning) return 0f
        if (startedAt < 0L) {
            startedAt = nowNanos
            return 0f
        }
        val elapsed = min(nowNanos - startedAt, durationNanos)
        if (elapsed <= 0L) return 0f
        val position = spec.getValueFromNanos(elapsed, ORIGIN, AnimationVector1D(velocity)).value
        val step = position - covered
        covered = position
        if (elapsed >= durationNanos) stop()
        return step
    }

    /** Cancel the fling. Called on the next touch down, so a finger always beats the momentum. */
    fun stop() {
        velocity = 0f
        durationNanos = 0L
        startedAt = -1L
        covered = 0f
        isRunning = false
    }

    /** How long the current fling lasts, in milliseconds; zero when nothing is in flight. */
    val durationMillis: Long get() = durationNanos / 1_000_000L

    private companion object {
        val ORIGIN = AnimationVector1D(0f)
    }
}
