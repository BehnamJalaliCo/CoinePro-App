package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * The platform's fling curve, so a flick on the chart coasts exactly like a flick on a list.
 *
 * This is the spline Android's own scroller uses (`SplineOverScroller`): distance and duration are
 * closed-form functions of the release velocity and the screen density, and the shape of the
 * deceleration between them is a cubic Bézier sampled once into a table. A reader who flicks a
 * watchlist and then flicks the chart under it feels the same physics twice, which is the whole
 * point — the chart used to decay exponentially, which is a curve nothing else on the phone uses,
 * and a fling that stops a little too soon on a curve the thumb does not expect reads as the chart
 * fighting the finger.
 *
 * The maths is reproduced rather than borrowed from Compose's `splineBasedDecay` because the chart
 * quantises panning to whole bars and carries the remainder across frames (see [KineticScroll]),
 * and that needs a position at an arbitrary time, not an animation that owns its own clock.
 *
 * Density matters: the physical deceleration is in metres per second squared, and pixels per metre
 * is what the density says. At 1× everything coasts three times as far as at 2.6×, so the tests
 * pin a phone density rather than the JVM default.
 */
class FlingSpline(density: Float) {

    /** Friction times the physical coefficient — pixels per second squared, in effect. */
    private val deceleration: Float = FRICTION * GRAVITY_EARTH * INCHES_PER_METRE * (density * DPI_BASELINE) * TUNING

    /** How long a fling released at [velocity] pixels per second lasts, in milliseconds. */
    fun durationMillis(velocity: Float): Long {
        val l = splineDeceleration(velocity)
        return (MILLIS_PER_SECOND * exp(l / (DECELERATION_RATE - 1.0))).toLong()
    }

    /** How far it travels, in pixels, unsigned. */
    fun distance(velocity: Float): Float {
        val l = splineDeceleration(velocity)
        return (deceleration * exp(DECELERATION_RATE / (DECELERATION_RATE - 1.0) * l)).toFloat()
    }

    /**
     * The share of the total distance covered at [elapsedMillis] of a fling lasting
     * [durationMillis] — `0f` at release, `1f` once it has stopped.
     */
    fun progress(elapsedMillis: Long, durationMillis: Long): Float {
        if (durationMillis <= 0L || elapsedMillis >= durationMillis) return 1f
        if (elapsedMillis <= 0L) return 0f
        val t = elapsedMillis.toFloat() / durationMillis
        val index = (SAMPLES * t).toInt()
        if (index >= SAMPLES) return 1f
        val tInf = index.toFloat() / SAMPLES
        val tSup = (index + 1).toFloat() / SAMPLES
        val dInf = POSITION[index]
        val dSup = POSITION[index + 1]
        val velocityCoefficient = (dSup - dInf) / (tSup - tInf)
        return dInf + (t - tInf) * velocityCoefficient
    }

    private fun splineDeceleration(velocity: Float): Double =
        ln((INFLEXION * abs(velocity) / deceleration).toDouble())

    companion object {
        private const val SAMPLES = 100
        private val DECELERATION_RATE = ln(0.78) / ln(0.9)
        private const val INFLEXION = 0.35f
        private const val START_TENSION = 0.5f
        private const val END_TENSION = 1.0f
        private const val P1 = START_TENSION * INFLEXION
        private const val P2 = 1.0f - END_TENSION * (1.0f - INFLEXION)
        private const val FRICTION = 0.015f
        private const val GRAVITY_EARTH = 9.80665f
        private const val INCHES_PER_METRE = 39.37f
        private const val DPI_BASELINE = 160f
        private const val TUNING = 0.84f
        private const val MILLIS_PER_SECOND = 1000.0

        /** The Bézier sampled at [SAMPLES] points, position against normalised time. */
        private val POSITION: FloatArray = FloatArray(SAMPLES + 1).also { table ->
            var xMin = 0f
            for (i in 0 until SAMPLES) {
                val alpha = i.toFloat() / SAMPLES
                var xMax = 1f
                var x: Float
                var coefficient: Float
                while (true) {
                    x = xMin + (xMax - xMin) / 2f
                    coefficient = 3f * x * (1f - x)
                    val tx = coefficient * ((1f - x) * P1 + x * P2) + x * x * x
                    if (abs(tx - alpha) < 1e-5f) break
                    if (tx > alpha) xMax = x else xMin = x
                }
                table[i] = coefficient * ((1f - x) * START_TENSION + x) + x * x * x
            }
            table[SAMPLES] = 1f
        }
    }
}
