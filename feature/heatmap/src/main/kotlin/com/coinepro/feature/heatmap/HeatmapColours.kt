package com.coinepro.feature.heatmap

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The map's colour, as arithmetic.
 *
 * Everything here returns a packed ARGB `Long` rather than a Compose `Color`, for the same reason
 * [Treemap] returns [Rect4]: the ramp is the part of a heatmap most likely to be wrong, and a
 * function that needs a Compose classpath cannot be tested on the JVM. The draw site converts once.
 *
 * ### The clamp, and why an outlier would otherwise wreck the map
 *
 * A diverging ramp needs a full-scale value, and the obvious choice — the largest absolute change
 * on the map — is the wrong one. Any real catalogue holds a coin that moved eighty percent
 * overnight, and normalising against it maps every other market into the first few percent of the
 * ramp: two hundred tiles of near-neutral grey around one screaming square. So the scale comes from
 * [scaleFor], which takes a high percentile rather than the maximum and then holds it inside a
 * sane band, and [colourFor] clamps anything beyond it to the end of the ramp. The outlier still
 * reads as "the most extreme thing here"; it just stops deciding what everything else looks like.
 */
object HeatmapColours {

    /**
     * The colour of a tile whose value is [value], on a ramp that reaches full strength at [scale].
     *
     * The value is divided by the scale and **clamped to ±1**, so a market that moved five times
     * the scale looks the same as one that moved twice it — both are simply "off the end", and the
     * figure printed on the tile is there to say which. At exactly zero every palette returns its
     * own neutral, which is what makes a flat market read as flat rather than as a faint gain.
     *
     * @param risingIsGreen the reader's buy/sell direction preference, which the theme implements
     *   by exchanging the palette's `buy` and `sell`. The canvas never sees a composable colour, so
     *   unlike every other direction colour in the app this one cannot pick the swap up for free
     *   and has to be told. Passing the wrong value here is the one failure that would draw a
     *   losing market green, so the screen derives it from the live palette rather than storing a
     *   second copy of the preference. It has no effect on [HeatmapPalette.MONOCHROME], where the
     *   axis is lightness and no convention claims that darker means up.
     */
    fun colourFor(
        value: Double,
        scale: Double,
        palette: HeatmapPalette,
        risingIsGreen: Boolean = true,
    ): Long {
        if (!value.isFinite() || !scale.isFinite() || scale <= 0.0) return neutralOf(palette)
        val t = (value / scale).coerceIn(-1.0, 1.0)
        if (t == 0.0) return neutralOf(palette)
        val warmIsUp = !risingIsGreen && palette != HeatmapPalette.MONOCHROME
        val rising = t > 0.0
        val stops = when (palette) {
            HeatmapPalette.CLASSIC ->
                if (rising != warmIsUp) CLASSIC_UP else CLASSIC_DOWN
            HeatmapPalette.COLOUR_BLIND ->
                if (rising != warmIsUp) BLIND_UP else BLIND_DOWN
            HeatmapPalette.MONOCHROME ->
                if (rising) MONO_UP else MONO_DOWN
        }
        return ramp(abs(t), neutralOf(palette), stops.first, stops.second)
    }

    /**
     * The neutral tile: no move, or nothing to say.
     *
     * A tile with no figure behind it gets this too. It has to be a colour that reads as *absent*
     * rather than as a small loss, which is why it is the surface grey the rest of the app uses for
     * a raised block and not the dark end of either ramp.
     */
    fun neutralOf(palette: HeatmapPalette): Long = when (palette) {
        HeatmapPalette.CLASSIC, HeatmapPalette.COLOUR_BLIND -> NEUTRAL
        HeatmapPalette.MONOCHROME -> MONO_NEUTRAL
    }

    /**
     * The full-scale value to normalise a set of figures against.
     *
     * The ninetieth percentile of the absolute values, held between [floor] and [ceiling]. The
     * percentile is what makes one runaway market harmless; the floor is what stops a dead-flat
     * session — every tile within a tenth of a percent — from being amplified into a map of violent
     * colour that says nothing; the ceiling is what stops a genuinely wild day from washing the
     * whole map back to neutral.
     *
     * An empty or entirely non-finite input answers [floor], which colours everything neutral. That
     * is the honest picture of "no data", and it is why this does not divide by zero.
     */
    fun scaleFor(values: List<Double>, floor: Double = 0.5, ceiling: Double = 25.0): Double {
        val magnitudes = values.filter { it.isFinite() }.map(::abs).sorted()
        if (magnitudes.isEmpty()) return floor
        // Truncated rather than rounded up: on ten values, rounding up lands on the tenth, which
        // is the maximum wearing a percentile's name and defeats the whole point of taking one.
        val index = (PERCENTILE * (magnitudes.size - 1)).toInt().coerceIn(0, magnitudes.size - 1)
        return magnitudes[index].coerceIn(floor, ceiling)
    }

    /**
     * Near-black or near-white, whichever the tile beneath can carry.
     *
     * Fixed ink would be wrong in both directions at once: white disappears on the bright end of
     * the orange ramp and black disappears on the dark end of every ramp, and the monochrome
     * palette runs the label across both within one screen. Relative luminance is computed properly
     * rather than by averaging the channels, because a mid green and a mid blue of the same
     * arithmetic mean are nowhere near the same brightness to the eye.
     */
    fun labelInkFor(argb: Long): Long =
        if (luminanceOf(argb) > INK_FLIP) INK_DARK else INK_LIGHT

    /** Relative luminance, sRGB linearised, in `0.0..1.0`. Exposed because the tests assert on it. */
    fun luminanceOf(argb: Long): Double {
        val r = channel(argb, 16)
        val g = channel(argb, 8)
        val b = channel(argb, 0)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }

    /**
     * A two-segment ramp: neutral to [mid] over the first half, [mid] to [strong] over the second.
     *
     * One straight interpolation from neutral to the saturated end would spend most of its length
     * in muddy near-neutral colours, which is where the majority of a real market's tiles sit — so
     * the map would be readable only at its extremes. The break at the halfway point buys back the
     * discrimination in the range that actually holds the data.
     */
    private fun ramp(magnitude: Double, neutral: Long, mid: Long, strong: Long): Long =
        if (magnitude <= 0.5) {
            blend(neutral, mid, magnitude / 0.5)
        } else {
            blend(mid, strong, (magnitude - 0.5) / 0.5)
        }

    /**
     * Straight sRGB interpolation, which is adequate *here* and would not be everywhere.
     *
     * Interpolating in sRGB between two distant hues goes through a dead grey, which is the usual
     * reason to reach for a perceptual space. These stops are deliberately close together — each
     * segment moves within one hue family — so nothing crosses the grey, and a colour-space
     * conversion per tile per frame would buy nothing a reader could see.
     */
    private fun blend(from: Long, to: Long, amount: Double): Long {
        val t = amount.coerceIn(0.0, 1.0)
        val r = mixChannel(from, to, 16, t)
        val g = mixChannel(from, to, 8, t)
        val b = mixChannel(from, to, 0, t)
        return OPAQUE or (r shl 16) or (g shl 8) or b
    }

    private fun mixChannel(from: Long, to: Long, shift: Int, t: Double): Long {
        val a = channel(from, shift)
        val b = channel(to, shift)
        return (a + (b - a) * t).roundToInt().toLong().coerceIn(0L, 255L)
    }

    private fun channel(argb: Long, shift: Int): Int = ((argb shr shift) and 0xFF).toInt()

    private fun linear(channel: Int): Double {
        val v = channel / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private const val OPAQUE = 0xFF000000L

    /**
     * Where the ink flips.
     *
     * Set from the ramps rather than at a round number: the two colours that land nearest it are
     * the saturated red, which wants white on it because that is the convention every terminal
     * uses, and the saturated orange, which wants black because white on it measures three to one.
     * They differ by four hundredths of a unit of luminance, so this constant sits between them.
     */
    private const val INK_FLIP = 0.25

    private const val PERCENTILE = 0.9

    private const val INK_LIGHT = 0xFFF0F1F2L
    private const val INK_DARK = 0xFF0B0E11L

    /**
     * The neutral for both colour ramps: the palette's pressed-surface grey.
     *
     * The same value in the light and the dark theme, and that is deliberate. A heatmap is a field
     * of saturated tiles that covers its own ground, so almost none of the page shows through; a
     * neutral that inverted with the theme would leave the *no-change* tiles as the brightest
     * objects on a light-theme map, which is the opposite of what neutral means.
     */
    private const val NEUTRAL = 0xFF2B3139L

    /** Mid-grey, so the monochrome ramp has the same distance to travel in both directions. */
    private const val MONO_NEUTRAL = 0xFF6E7681L

    // The stops. Each pair is (halfway, full scale); see `ramp`.

    /** The product's green, arrived at through a dark green rather than through grey. */
    private val CLASSIC_UP = 0xFF0F7A45L to 0xFF00C46AL

    /** The product's red, on the same construction. */
    private val CLASSIC_DOWN = 0xFF8C2331L to 0xFFF6465DL

    /**
     * The rise end of the colour-blind ramp.
     *
     * Blue rather than green, and the blue channel climbs from the neutral's 57 through 138 to 214
     * — monotonically, which is the property that carries the whole scheme for a reader who sees no
     * red-green difference at all.
     */
    private val BLIND_UP = 0xFF1B4F8AL to 0xFF2F86D6L

    /**
     * The fall end: orange, with the blue channel falling from 57 through 30 to 24.
     *
     * Warm rather than cool, so the two halves are separated by temperature and by lightness as
     * well as by hue. Nothing about this ramp depends on telling red from green.
     */
    private val BLIND_DOWN = 0xFF8A4A1EL to 0xFFE07B18L

    /** Lightness up. */
    private val MONO_UP = 0xFFACB3BCL to 0xFFEDEFF2L

    /** Lightness down. */
    private val MONO_DOWN = 0xFF3A4048L to 0xFF14171CL
}
