package com.coinepro.feature.heatmap

/**
 * What decides how much room a market gets on the map.
 *
 * Area is the map's loudest signal — it is read before any colour — so the choice here is a choice
 * about what the reader is being told is important. Three of these are figures the feed supplies
 * and one is a deliberate refusal to weight at all.
 */
enum class HeatmapSize {
    /** Capitalisation: the map of what the market *is*. */
    MARKET_CAP,

    /** Twenty-four-hour traded quantity: the map of what is being traded today. */
    VOLUME,

    /** Quantity times price — money moved rather than units moved. */
    TURNOVER,

    /**
     * Every tile the same size.
     *
     * Not a filler option. With equal areas the map becomes a pure colour field, which is the right
     * shape for the question "what is happening across the board" — on a weighted map that question
     * is drowned out by whichever three markets are largest, and a reader watching for a sector
     * turning cannot see it happen in the small tiles.
     */
    MONO,
}

/**
 * What the tile's colour means.
 *
 * All five map onto the same diverging ramp, so the reader learns one scale and not five. What
 * changes is the quantity being placed on it, and each one is a different question about the same
 * market — where it closed, how it did over the window, whether it is unusually agitated, where in
 * the day's range it is sitting, and whether it opened away from where it closed.
 */
enum class HeatmapColour {
    /** Percentage change over the session. The default, and what a heatmap normally means. */
    CHANGE,

    /** Percentage change over the longer window the screen was opened on. */
    PERFORMANCE,

    /**
     * How agitated the market is against its *own* normal, not against its neighbours'.
     *
     * A raw volatility figure cannot go on a diverging ramp: it has no sign, so half the scale
     * would be unreachable and every tile would read as a gain. What is plotted instead is the
     * excess over the instrument's typical range, which does have a sign and does answer the
     * question a reader is asking — is this market calmer or wilder than it usually is.
     */
    VOLATILITY,

    /**
     * Where the last price sits inside the session's own high-low range, from the low at one
     * extreme to the high at the other.
     *
     * Worth its own mode because it disagrees with [CHANGE] often enough to be informative: a
     * market up two percent but trading at the bottom of its range has given most of the day back.
     */
    RANGE,

    /** The opening gap against the previous close. */
    GAP,
}

/**
 * The three colour schemes.
 *
 * [COLOUR_BLIND] is the reason this enum exists rather than a boolean. Red-green colour vision
 * deficiency affects roughly one man in twelve, and a market heatmap is the single worst object in
 * a trading app for it: the entire content is encoded in the one axis those readers cannot resolve,
 * with no numbers large enough to fall back on. Every serious terminal ships an alternative and
 * almost no clone does, which is exactly why it is here.
 */
enum class HeatmapPalette {
    /**
     * The product's own market up and down colours.
     *
     * These follow the reader's buy/sell direction preference: the theme implements the red-up
     * convention by exchanging `buy` and `sell` on the palette, so the map has to ask which way
     * round the reader is before it decides which end of its ramp is green. See
     * [HeatmapColours.colourFor].
     */
    CLASSIC,

    /**
     * A blue-to-orange divergent ramp that does not use the red-green axis at all.
     *
     * Blue for a rise and orange for a fall, with the blue channel moving monotonically from one
     * end of the ramp to the other. That last property is the one that matters and the one the
     * tests assert: a scheme whose two halves differ only in their red and green channels is
     * indistinguishable to a deuteranope no matter which hues it names, so the ramp is built so
     * that even a reader who sees no red-green difference still reads a rise as lighter and bluer
     * and a fall as darker and warmer.
     */
    COLOUR_BLIND,

    /**
     * Value as lightness alone, from near-black through mid-grey to near-white.
     *
     * For a reader who wants the shape of the market without any colour argument at all, and for a
     * screenshot that has to survive being printed.
     */
    MONOCHROME,
}

/**
 * Whether markets of different kinds are kept apart.
 *
 * Grouping costs area — each group needs a strip for its name — so it is off by default. It earns
 * its cost on a mixed catalogue, where an ungrouped map interleaves coins with currency pairs and
 * the reader cannot see that one whole class has turned.
 */
enum class HeatmapGrouping {
    NONE,

    /** By the class [com.coinepro.core.symbols.SymbolClassifier] derives from the ticker. */
    BY_CLASS,
}

/**
 * The four choices the settings sheet writes and the map reads.
 *
 * One immutable object rather than four pieces of state because the plan is rebuilt whenever any
 * of them changes, and a single key makes that `remember` correct by construction instead of
 * correct if somebody remembered to list all four.
 */
data class HeatmapOptions(
    val size: HeatmapSize = HeatmapSize.MARKET_CAP,
    val colour: HeatmapColour = HeatmapColour.CHANGE,
    val palette: HeatmapPalette = HeatmapPalette.CLASSIC,
    val grouping: HeatmapGrouping = HeatmapGrouping.NONE,
)
