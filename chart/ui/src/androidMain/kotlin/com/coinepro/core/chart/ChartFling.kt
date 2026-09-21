package com.coinepro.core.chart

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedDecayAnimationSpec
import androidx.compose.animation.core.exponentialDecay
import kotlin.math.abs
import kotlin.math.min

/**
 * The curve a chart flick coasts on: Compose's own [exponentialDecay], matched to TradingView.
 *
 * Velocity decays as `e^(−f·t)` with `f = 4.2 × frictionMultiplier`, and a flick covers `v / f`
 * pixels — so this constant *is* the distance one flick travels. It was 3.8, which put a hard
 * 4 300 px/s release at 1 130 px: one screen. The owner measured the same finger in TradingView on
 * the same phone covering about 2 900 px over two seconds, which is `f ≈ 1.5`; the number shipped is
 * 1.25, because the cut-off that ends the creep also costs distance and the friction pays it back.
 * See
 * `KineticScroll.EXPONENTIAL_FRICTION` in `:chart-core` for the full arithmetic and for what the
 * measurement ruled *out* — the velocity reaching this curve was never the problem.
 *
 * `KineticScroll` is the same curve written out for the JVM tests; this is the spec the chart
 * actually flings on, and the two are kept at the same numbers deliberately.
 */
internal const val FLING_FRICTION_MULTIPLIER = 1.25f / 4.2f

internal fun chartFlingSpec(): DecayAnimationSpec<Float> =
    exponentialDecay(frictionMultiplier = FLING_FRICTION_MULTIPLIER, absVelocityThreshold = KineticScroll.MIN_VELOCITY)

/**
 * A fling in flight, read off [chartFlingSpec] frame by frame.
 *
 * The position is read off the curve at the elapsed time rather than integrated per frame, so a
 * dropped frame lands the chart where it would have been, not further along. A velocity under the
 * cut-off starts nothing at all, so a slow drag that ends with the finger almost still does not
 * twitch after the release.
 *
 * ### The hand-off frame
 *
 * The finger lifts *between* two frames. The first frame after it is therefore not `t = 0` on the
 * curve — the release is already one frame in the past by the time anything can be drawn. Reading
 * the curve at zero there costs the reader a frame of stillness at the exact moment the chart is
 * moving fastest, which is the one frame a thumb can feel: the picture keeps the finger's speed
 * right up to the lift, stops dead for eight milliseconds, and then starts again. So the clock is
 * seeded [HANDOFF_NANOS] before the frame that first ticks it, and the first step the fling
 * returns is a real one.
 *
 * That interval is a frame at 120 Hz, the rate this chart asks the panel for. On a 60 Hz panel it
 * is half a frame, which under-counts rather than over-counts: the hand-off is smooth either way
 * and the total travel is the curve's, because position is read off the curve and not accumulated.
 */
internal class ChartFling(
    private val spec: VectorizedDecayAnimationSpec<AnimationVector1D> = chartFlingSpec().vectorize(Float.VectorConverter),
) {
    private var velocity = 0f
    private var durationNanos = 0L
    private var startedAt = 0L
    private var covered = 0f

    /**
     * Whether the clock has been set, as a flag rather than a sentinel on [startedAt].
     *
     * It has to be a flag: the clock is seeded a frame *before* the first tick, and a first frame
     * at or near zero — `withFrameNanos` hands out small numbers on a fresh process — seeds a
     * negative start. A negative sentinel would read that as «not started yet» and re-seed on every
     * frame, which is a fling that never advances past its first step.
     */
    private var started = false

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
        startedAt = 0L
        started = false
        covered = 0f
        isRunning = durationNanos > 0L
    }

    /** How far the content should move since the last tick, in signed pixels. */
    fun tick(nowNanos: Long): Float {
        if (!isRunning) return 0f
        if (!started) {
            started = true
            startedAt = nowNanos - HANDOFF_NANOS
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
        startedAt = 0L
        started = false
        covered = 0f
        isRunning = false
    }

    /** How long the current fling lasts, in milliseconds; zero when nothing is in flight. */
    val durationMillis: Long get() = durationNanos / 1_000_000L

    private companion object {
        val ORIGIN = AnimationVector1D(0f)

        /** One frame at 120 Hz — how old the release already is on the first frame that can draw it. */
        const val HANDOFF_NANOS = 8_333_333L
    }
}
